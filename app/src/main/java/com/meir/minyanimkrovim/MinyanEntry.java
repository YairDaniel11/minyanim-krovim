package com.meir.minyanimkrovim;

/** רשומה שטוחה אחת: בית כנסת + נוסח + סוג תפילה + שעה (בדקות מחצות) + סוג יום. */
public class MinyanEntry {
    public final String shulName;
    public final String nusach;      // אשכנז / ספרד / ע"מ / וכו' - טקסט חופשי, יכול להיות ""
    public final String prayerType;  // "shacharit" | "mincha" | "arvit"
    public final String dayType;     // "weekday" | "friday" | "shabbat"
    public final int minutesOfDay;   // 0-1439
    public final String note;        // תיאור הלכתי נלווה (למשל "נץ החמה") - עשוי להיות null

    public MinyanEntry(String shulName, String nusach, String prayerType,
                        String dayType, int minutesOfDay, String note) {
        this.shulName = shulName;
        this.nusach = nusach;
        this.prayerType = prayerType;
        this.dayType = dayType;
        this.minutesOfDay = minutesOfDay;
        this.note = note;
    }

    public String prayerLabel(android.content.Context ctx) {
        if ("shacharit".equals(prayerType)) return ctx.getString(R.string.prayer_shacharit);
        if ("mincha".equals(prayerType)) return ctx.getString(R.string.prayer_mincha);
        if ("arvit".equals(prayerType)) return ctx.getString(R.string.prayer_arvit);
        return prayerType;
    }

    public String timeLabel() {
        int h = (minutesOfDay / 60) % 24;
        int m = minutesOfDay % 60;
        String clock = String.format(java.util.Locale.US, "%02d:%02d", h, m);
        return note != null ? clock + " (" + note + ")" : clock;
    }
}
