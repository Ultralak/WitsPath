package com.example.witspath.ui;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.witspath.ui.BaseActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.example.witspath.R;
import com.example.witspath.util.Languages;
import com.example.witspath.util.Prefs;

import java.util.ArrayList;
import java.util.List;

public class LanguageActivity extends BaseActivity {

    private Prefs prefs;
    private LinearLayout listContainer;
    private final List<View> rowViews = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_language);
        prefs = new Prefs(this);

        MaterialToolbar toolbar = findViewById(R.id.languageToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        listContainer = findViewById(R.id.languageListContainer);
        populateList();
    }

    private void populateList() {
        listContainer.removeAllViews();
        rowViews.clear();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < Languages.TAGS.length; i++) {
            View row = inflater.inflate(R.layout.item_language_option, listContainer, false);
            
            ((TextView) row.findViewById(R.id.languageOptionNameText)).setText(Languages.DISPLAY_NAMES[i]);
            ((TextView) row.findViewById(R.id.languageOptionNativeText)).setText(Languages.NATIVE_NAMES[i]);

            // Bind click recursively so any pixel works
            final int index = i;
            setClickRecursive(row, index);
            
            listContainer.addView(row);
            rowViews.add(row);
        }
        refreshRows();
    }

    private void setClickRecursive(View view, int index) {
        view.setOnClickListener(v -> selectLanguage(index));
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                setClickRecursive(vg.getChildAt(i), index);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshRows();
    }

    private void selectLanguage(int index) {
        String tag = Languages.TAGS[index];
        prefs.setStringSync(Prefs.KEY_UI_LANGUAGE, tag);
        refreshRows();
        Languages.apply(this, tag);
    }

    private void refreshRows() {
        String currentTag = prefs.getString(Prefs.KEY_UI_LANGUAGE, "");
        int currentIndex = Languages.indexForTag(currentTag);
        int amber = getColor(R.color.colorRoute);

        for (int i = 0; i < rowViews.size(); i++) {
            View row = rowViews.get(i);
            ImageView check = row.findViewById(R.id.languageOptionCheck);
            View tick = row.findViewById(R.id.languageOptionTick);

            tick.setBackgroundColor(getColor(Languages.COLORS[i]));
            check.setImageTintList(ColorStateList.valueOf(amber));
            check.setVisibility(i == currentIndex ? View.VISIBLE : View.GONE);

            row.setBackgroundColor(i == currentIndex 
                    ? Languages.dimColorForTag(this, Languages.TAGS[i])
                    : getColor(R.color.colorPlate));
        }
    }
}
