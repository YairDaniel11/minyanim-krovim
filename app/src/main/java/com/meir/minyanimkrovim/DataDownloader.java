package com.meir.minyanimkrovim;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * מוריד את קובץ המניינים הארצי העדכני מ-GitHub (data/minyanim-israel.json בענף main -
 * הקובץ מתעדכן אוטומטית כל שבוע ע"י GitHub Action) ושומר אותו אם הוא חדש יותר
 * ממה שיש באפליקציה.
 */
public class DataDownloader {

    public static final String DATA_URL =
            "https://raw.githubusercontent.com/YairDaniel11/minyanim-krovim/main/data/minyanim-israel.json";
    private static final int MAX_BYTES = 8 * 1024 * 1024;

    public interface Callback {
        /** @param updated true אם נשמרה גרסה חדשה; false אם כבר מעודכנים. */
        void onSuccess(boolean updated, String generated);
        void onError(String message);
    }

    /** מוריד ברקע ומחזיר תוצאה ב-thread הראשי. */
    public static void download(final Context context, final Callback cb) {
        final Context app = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String json = fetch(DATA_URL);
                    final boolean updated = MinyanDataStore.saveDownloadedNational(app, json);
                    final String gen = MinyanDataStore.nationalGenerated(app);
                    main.post(new Runnable() { @Override public void run() { cb.onSuccess(updated, gen); } });
                } catch (final Exception e) {
                    main.post(new Runnable() {
                        @Override public void run() { cb.onError(String.valueOf(e.getMessage())); }
                    });
                }
            }
        }, "data-download").start();
    }

    private static String fetch(String address) throws IOException {
        HttpsURLConnection conn = (HttpsURLConnection) new URL(address).openConnection();
        // אנדרואיד 4.x: TLS 1.2 קיים אבל כבוי כברירת מחדל, ו-GitHub דורש אותו
        if (Build.VERSION.SDK_INT < 21) {
            try {
                conn.setSSLSocketFactory(new Tls12SocketFactory());
            } catch (Exception ignored) { }
        }
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("User-Agent", "minyanim-krovim-app");
        try {
            int code = conn.getResponseCode();
            if (code != 200) throw new IOException("השרת החזיר שגיאה " + code);
            InputStream in = conn.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 20);
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > MAX_BYTES) throw new IOException("הקובץ גדול מהצפוי");
            }
            return out.toString("UTF-8");
        } finally {
            conn.disconnect();
        }
    }

    /** עוטף SSLSocketFactory ומפעיל TLSv1.2 על כל סוקט (נדרש ל-API 16-20). */
    private static class Tls12SocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;

        Tls12SocketFactory() throws Exception {
            SSLContext ctx = SSLContext.getInstance("TLSv1.2");
            ctx.init(null, null, null);
            delegate = ctx.getSocketFactory();
        }

        private Socket enable(Socket s) {
            if (s instanceof SSLSocket) ((SSLSocket) s).setEnabledProtocols(new String[]{"TLSv1.1", "TLSv1.2"});
            return s;
        }

        @Override public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }
        @Override public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }
        @Override public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            return enable(delegate.createSocket(s, host, port, autoClose));
        }
        @Override public Socket createSocket(String host, int port) throws IOException {
            return enable(delegate.createSocket(host, port));
        }
        @Override public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return enable(delegate.createSocket(host, port, localHost, localPort));
        }
        @Override public Socket createSocket(InetAddress host, int port) throws IOException {
            return enable(delegate.createSocket(host, port));
        }
        @Override public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
            return enable(delegate.createSocket(address, port, localAddress, localPort));
        }
    }

    private DataDownloader() { }
}
