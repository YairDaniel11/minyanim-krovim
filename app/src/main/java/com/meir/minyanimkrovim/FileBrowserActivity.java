package com.meir.minyanimkrovim;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
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
 * אפשרות לעיין באחסון המקומי (חלק ממנהלי הקבצים המובנים במכשירים ישנים/
 * מותאמים לא מממשים Storage Access Framework בכלל).
 */
public class FileBrowserActivity extends Activity {

    public static final String EXTRA_SELECTED_PATH = "selected_path";

    private File currentDir;
    private TextView tvPath;
    private ListView listView;
    private final List<File> entries = new ArrayList<>();
    private boolean hasUpEntry;

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

        File start = Environment.getExternalStorageDirectory();
        openDirectory(start != null ? start : new File("/"));
    }

    private void onEntryClicked(int position) {
        if (hasUpEntry && position == 0) {
            openDirectory(currentDir.getParentFile());
            return;
        }
        File selected = entries.get(hasUpEntry ? position - 1 : position);
        if (selected.isDirectory()) {
            openDirectory(selected);
        } else {
            setResult(RESULT_OK, getIntent().putExtra(EXTRA_SELECTED_PATH, selected.getAbsolutePath()));
            finish();
        }
    }

    private void openDirectory(File dir) {
        File[] children = dir.listFiles();
        if (children == null) {
            Toast.makeText(this, R.string.file_browser_cannot_open, Toast.LENGTH_SHORT).show();
            return;
        }
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
        List<String> labels = new ArrayList<>();
        hasUpEntry = dir.getParentFile() != null;
        if (hasUpEntry) {
            labels.add(getString(R.string.file_browser_up));
        }
        for (File child : children) {
            if (child.isHidden()) continue;
            entries.add(child);
            labels.add((child.isDirectory() ? "📁 " : "📄 ") + child.getName());
        }

        listView.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels));
    }

    @Override
    public void onBackPressed() {
        if (currentDir.getParentFile() != null) {
            openDirectory(currentDir.getParentFile());
        } else {
            super.onBackPressed();
        }
    }
}
