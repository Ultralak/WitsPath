package com.example.witspath.ui;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.example.witspath.R;
import com.example.witspath.util.Languages;
import com.example.witspath.util.Prefs;

public class LanguageActivity extends AppCompatActivity {

    private final int[] rowIds = {
            R.id.languageSystemRow, R.id.languageEnglishRow, R.id.languageZuluRow,
            R.id.languageSesothoRow, R.id.languageSetswanaRow, R.id.languageXhosaRow,
            R.id.languageAfrikaansRow
    };
    private final int[] tickIds = {
            R.id.languageSystemTick, R.id.languageEnglishTick, R.id.languageZuluTick,
            R.id.languageSesothoTick, R.id.languageSetswanaTick, R.id.languageXhosaTick,
            R.id.languageAfrikaansTick
    };
    private final int[] checkIds = {
            R.id.languageSystemCheck, R.id.languageEnglishCheck, R.id.languageZuluCheck,
            R.id.languageSesothoCheck, R.id.languageSetswanaCheck, R.id.languageXhosaCheck,
            R.id.languageAfrikaansCheck
    };

    private Prefs prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_language);
        prefs = new Prefs(this);

        MaterialToolbar toolbar = findViewById(R.id.languageToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        for (int i = 0; i < rowIds.length; i++) {
            int index = i;
            findViewById(rowIds[i]).setOnClickListener(v -> selectLanguage(index));
        }

        refreshRows();
    }

    private void selectLanguage(int index) {
        Languages.apply(this, Languages.TAGS[index]);
    }

    private void refreshRows() {
        String currentTag = prefs.getString(Prefs.KEY_UI_LANGUAGE, "");
        int currentIndex = Languages.indexForTag(currentTag);

        for (int i = 0; i < rowIds.length; i++) {
            int tint = getColor(Languages.COLORS[i]);

            View tick = findViewById(tickIds[i]);
            tick.setBackgroundColor(tint);

            ImageView check = findViewById(checkIds[i]);
            check.setImageTintList(ColorStateList.valueOf(tint));
            check.setVisibility(i == currentIndex ? View.VISIBLE : View.INVISIBLE);

            findViewById(rowIds[i]).setBackgroundColor(
                    i == currentIndex ? Languages.dimColorForTag(this, Languages.TAGS[i])
                            : getColor(R.color.colorPlate));
        }
    }
}
