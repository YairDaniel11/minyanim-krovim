package com.meir.minyanimkrovim;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * דפדפן קבצים פשוט מבוסס File, לשימוש רק במכשירים ישנים (SDK < 29 - לפני
 * scoped storage) שבהם ACTION_OPEN_DOCUMENT/ACTION_GET_CONTENT לא מציגים
 * אפשרות לעיין באחסון המקומי.
 *
 * נפתח תמיד למסך "שורשי אחסון" (אחסון פנימי / כרטיס SD אם קיים) - לא
 * לתיקיית השורש הכללית של המכשיר - כדי לחסוך ניווט מיותר. עצם השורה
 * משתמשת בפריסה משלנו (list_item_file) עם צבעים מפורשים מערכת הנושא שלנו,
 * ולא בפריסת ברירת המחדל של המערכת - כדי למנוע מצב של טקסט לבן על רקע
 * לבן שקורה כשערכת ברירת המחדל של המערכת שונה מערכת הנושא של האפליקציה.
 */
public class FileBrowserActivity extends Activity {

    public static final String EXTRA_SELECTED_PATH = "selected_path";

    private static class Entry {
        final String label;
        final File file;
        final boolean isDirectory;
        final boolean isUp;
        final boolean isRoot;

        Entry(String label, File file, boolean isDirectory, boolean isUp, boolean isRoot) {
            this.label = label;
            this.file = file;
            this.isDirectory = isDirectory;
            this.isUp = isUp;
            this.isRoot = isRoot;
        }
    }

    private File currentDir;
    private File currentRoot; // שורש האחסון הנוכחי (פנימי/חיצוני) - לצורך חסימת ניווט "למעלה" ממנו
    private boolean showingRoots = true;
    private TextView tvPath;
    private ListView listView;
    private final List<Entry> entries = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtil.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_browser);
        setTitle(R.string.file_browser_title);

        tvPath = (TextView) findViewById(R.id.tvCurrentPath);
        listView = (ListView) findViewById(R.id.listFiles);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                onEntryClicked(position);
            }
        });

        showRoots();
    }

    /** מסך הפתיחה - שורשי אחסון בלבד (לא כל עץ הקבצים של המכשיר). */
    private void showRoots() {
        showingRoots = true;
        currentDir = null;
        currentRoot = null;
        tvPath.setText(R.string.file_browser_roots_title);

        entries.clear();
        File internal = Environment.getExternalStorageDirectory();
        if (internal != null) {
            entries.add(new Entry(getString(R.string.file_browser_internal_storage), internal, true, false, true));
        }
        File externalSd = findExternalSdCard();
        if (externalSd != null) {
            entries.add(new Entry(getString(R.string.file_browser_external_storage), externalSd, true, false, true));
        }

        listView.setAdapter(new FileAdapter());
    }

    /**
     * מזהה כרטיס SD חיצוני (בנוסף לאחסון הפנימי) דרך getExternalFilesDirs -
     * שיטה שעובדת גם במכשירים ישנים בלי הרשאות מיוחדות, בהסתמך על כך
     * שהנתיב המוחזר תמיד מסתיים ב-Android/data/<package>/files.
     */
    private File findExternalSdCard() {
        try {
            File[] dirs = getExternalFilesDirs(null);
            if (dirs == null) return null;
            String primaryPath = Environment.getExternalStorageDirectory() != null
                    ? Environment.getExternalStorageDirectory().getAbsolutePath() : null;
            for (File dir : dirs) {
                if (dir == null) continue;
                String path = dir.getAbsolutePath();
                int idx = path.indexOf("/Android/data/");
                if (idx <= 0) continue;
                String root = path.substring(0, idx);
                if (primaryPath != null && root.equals(primaryPath)) continue; // זה האחסון הפנימי - כבר מוצג
                File rootFile = new File(root);
                if (rootFile.exists() && rootFile.canRead()) return rootFile;
            }
        } catch (Exception e) {
            // אם המנגנון לא זמין במכשיר הזה - פשוט לא מציגים אפשרות כרטיס חיצוני
        }
        return null;
    }

    private void onEntryClicked(int position) {
        Entry entry = entries.get(position);
        if (entry.isUp) {
            if (currentDir != null && currentRoot != null && currentDir.equals(currentRoot)) {
                showRoots();
            } else if (currentDir != null) {
                openDirectory(currentDir.getParentFile());
            }
            return;
        }
        if (entry.isRoot) {
            currentRoot = entry.file;
            openDirectory(entry.file);
            return;
        }
        if (entry.isDirectory) {
            openDirectory(entry.file);
        } else {
            setResult(RESULT_OK, getIntent().putExtra(EXTRA_SELECTED_PATH, entry.file.getAbsolutePath()));
            finish();
        }
    }

    private void openDirectory(File dir) {
        if (dir == null) {
            showRoots();
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            Toast.makeText(this, R.string.file_browser_cannot_open, Toast.LENGTH_SHORT).show();
            return;
        }
        showingRoots = false;
        currentDir = dir;
        tvPath.setText(dir.getAbsolutePath());

        Arrays.sort(children, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });

        entries.clear();
        boolean atRootTop = currentRoot != null && currentDir.equals(currentRoot);
        String upLabel = atRootTop ? getString(R.string.file_browser_back_to_roots) : getString(R.string.file_browser_up);
        entries.add(new Entry(upLabel, dir.getParentFile(), true, true, false));

        for (File child : children) {
            if (child.isHidden()) continue;
            String icon = child.isDirectory() ? "📁" : "📄";
            entries.add(new Entry(icon + " " + child.getName(), child, child.isDirectory(), false, false));
        }

        listView.setAdapter(new FileAdapter());
    }

    @Override
    public void onBackPressed() {
        if (showingRoots) {
            super.onBackPressed();
        } else if (currentRoot != null && currentDir != null && currentDir.equals(currentRoot)) {
            showRoots();
        } else if (currentDir != null && currentDir.getParentFile() != null) {
            openDirectory(currentDir.getParentFile());
        } else {
            super.onBackPressed();
        }
    }

    /**
     * אדפטר עם פריסה מותאמת אישית (list_item_file) שמושכת צבעים מפורשים
     * מערכת הנושא של האפליקציה עצמה - כדי שהטקסט תמיד קריא, בלי תלות
     * בערכת הנושא של המערכת. מוקד ה-D-pad מקבל צבע רקע בולט משלו.
     */
    private class FileAdapter extends ArrayAdapter<Entry> {
        private final LayoutInflater inflater;
        private final int mainTextColor;
        private final int secondaryTextColor;
        private final int accentColor;

        FileAdapter() {
            super(FileBrowserActivity.this, 0, new ArrayList<>(entries));
            inflater = getLayoutInflater();
            mainTextColor = resolveAttrColor(R.attr.colorTextMain);
            secondaryTextColor = resolveAttrColor(R.attr.colorTextSecondary);
            accentColor = resolveAttrColor(R.attr.colorAccent);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView != null ? convertView
                    : inflater.inflate(R.layout.list_item_file, parent, false);

            final Entry entry = getItem(position);
            TextView tvIcon = (TextView) row.findViewById(R.id.tvFileIcon);
            TextView tvName = (TextView) row.findViewById(R.id.tvFileName);

            if (entry.isUp) {
                tvIcon.setText("⬅");
                tvName.setText(entry.label);
                tvName.setTextColor(secondaryTextColor);
            } else if (entry.isRoot) {
                tvIcon.setText("");
                tvName.setText(entry.label);
                tvName.setTextColor(mainTextColor);
            } else {
                int spaceIdx = entry.label.indexOf(' ');
                tvIcon.setText(spaceIdx > 0 ? entry.label.substring(0, spaceIdx) : "");
                tvName.setText(spaceIdx > 0 ? entry.label.substring(spaceIdx + 1) : entry.label);
                tvName.setTextColor(mainTextColor);
            }

            row.setBackgroundColor(0x00000000);
            row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    v.setBackgroundColor(hasFocus ? withAlpha(accentColor, 0x55) : 0x00000000);
                }
            });

            return row;
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private int resolveAttrColor(int attrResId) {
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(attrResId, typedValue, true);
        return typedValue.data;
    }
}
