package com.example.witspath.ui;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.example.witspath.R;
import com.example.witspath.util.Prefs;

public class PreferencesActivity extends BaseActivity {

    private Prefs prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preferences);
        prefs = new Prefs(this);

        MaterialToolbar toolbar = findViewById(R.id.preferencesToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        bindMyPlacesSection();
        bindResetButton();
    }

    private void bindMyPlacesSection() {
        findViewById(R.id.preferencesHomeLocationRow).setOnClickListener(v ->
                Toast.makeText(this, "Home location editing coming soon", Toast.LENGTH_SHORT).show());
        
        refreshHomeLocation();
    }

    private void refreshHomeLocation() {
        String homeNode = prefs.getString(Prefs.KEY_HOME_NODE_ID, "");
        ((TextView) findViewById(R.id.preferencesHomeLocationValueText))
                .setText(homeNode.isEmpty() ? getString(R.string.preferences_not_set) : homeNode);
    }

    private void bindResetButton() {
        findViewById(R.id.preferencesResetButton).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.preferences_reset_all)
                        .setMessage("This will clear all your personalized locations and app preferences.")
                        .setNegativeButton(R.string.dialog_cancel, null)
                        .setPositiveButton(R.string.preferences_reset_all, (d, w) -> {
                            prefs.clearAll();
                            Toast.makeText(this, "All data reset", Toast.LENGTH_SHORT).show();
                            finish();
                        })
                        .show());
    }
}
