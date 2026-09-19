package com.example.witspath.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.example.witspath.R;
import com.example.witspath.util.AppConfiguration;
import com.example.witspath.util.Languages;
import com.example.witspath.util.Prefs;

public class SettingsActivity extends BaseActivity {

    private Prefs prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        prefs = new Prefs(this);

        MaterialToolbar toolbar = findViewById(R.id.settingsToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        bindAccountSection();
        bindLanguageAndDisplaySection();
        bindRoutingSection();
        bindNavigationSection();
        bindReportsSection();
        bindPrivacySection();
        bindAboutSection();
        bindLogOut();

        handleScrollToSection();
    }

    private void handleScrollToSection() {
        String scrollTo = getIntent().getStringExtra("scrollToSection");
        if ("accessibility".equals(scrollTo)) {
            View section = findViewById(R.id.settingsAccessibilitySectionLabel);
            View scroll = findViewById(R.id.settingsScrollView);
            if (section != null && scroll != null) {
                scroll.post(() -> ((NestedScrollView) scroll).smoothScrollTo(0, section.getTop()));
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindAccountSection();
        refreshLanguageRow();
    }

    private void bindAccountSection() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        boolean signedIn = user != null && !user.isAnonymous();

        TextView name = findViewById(R.id.settingsAccountNameText);
        TextView email = findViewById(R.id.settingsAccountEmailText);
        name.setText(signedIn && user.getDisplayName() != null
                ? user.getDisplayName() : getString(R.string.nav_guest_title));
        email.setText(signedIn ? user.getEmail() : getString(R.string.nav_guest_subtitle));

        findViewById(R.id.settingsAccountRow).setOnClickListener(v -> {
            if (!signedIn) {
                startActivity(new Intent(this, LoginActivity.class));
            }
        });

        bindToggleRow(R.id.settingsSyncSwitch, Prefs.KEY_SYNC_ENABLED, true);
    }

    private void bindLanguageAndDisplaySection() {
        findViewById(R.id.settingsLanguageRow).setOnClickListener(v ->
                startActivity(new Intent(this, LanguageActivity.class)));
        refreshLanguageRow();

        setupChipGroup(R.id.settingsTextSizeChipGroup, Prefs.KEY_TEXT_SIZE, "default",
                new int[]{R.id.textSizeSmallChip, R.id.textSizeDefaultChip, R.id.textSizeLargeChip, R.id.textSizeHugeChip},
                new String[]{"small", "default", "large", "huge"});

        bindToggleRow(R.id.settingsHighContrastSwitch, Prefs.KEY_HIGH_CONTRAST, false);
        bindDarkThemeToggleRow();
        bindToggleRow(R.id.settingsScreenReaderSwitch, Prefs.KEY_SCREEN_READER_HINTS, true);
    }

    private void bindDarkThemeToggleRow() {
        MaterialSwitch sw = findViewById(R.id.settingsDarkThemeSwitch);
        sw.setChecked(prefs.getBoolean("pref_dark_theme", false));
        sw.setOnCheckedChangeListener((b, isChecked) -> {
            prefs.setBoolean("pref_dark_theme", isChecked);
            AppCompatDelegate.setDefaultNightMode(isChecked
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        });
        View parentRow = (View) sw.getParent();
        parentRow.setFocusable(true);
        parentRow.setClickable(true);
        parentRow.setOnClickListener(v -> sw.setChecked(!sw.isChecked()));
        
        sw.setFocusable(false);
        sw.setClickable(false);
    }

    private void refreshLanguageRow() {
        String tag = prefs.getString(Prefs.KEY_UI_LANGUAGE, "");
        ((TextView) findViewById(R.id.settingsLanguageValueText)).setText(Languages.displayNameForTag(this, tag));
        findViewById(R.id.settingsLanguageSwatch).setBackgroundTintList(
                ColorStateList.valueOf(Languages.colorForTag(this, tag)));
    }

    private void bindRoutingSection() {
        setupChipGroup(R.id.settingsMobilityChipGroup, Prefs.KEY_MOBILITY_PROFILE, "wheelchair",
                new int[]{R.id.mobilityWheelchairChip, R.id.mobilityWalkingAidChip, R.id.mobilityLowVisionChip, R.id.mobilityNoneChip},
                new String[]{"wheelchair", "walking_aid", "low_vision", "none"}, true);

        bindToggleRow(R.id.settingsStepFreeSwitch, Prefs.KEY_STEP_FREE_ONLY, true, true);
        bindToggleRow(R.id.settingsPreferLiftsSwitch, Prefs.KEY_PREFER_LIFTS, true, true);
        bindToggleRow(R.id.settingsAvoidSteepSwitch, Prefs.KEY_AVOID_STEEP_RAMPS, true, true);

        Slider slider = findViewById(R.id.settingsMaxDistanceSlider);
        TextView valueText = findViewById(R.id.settingsMaxDistanceValueText);
        int savedMetres = prefs.getInt(Prefs.KEY_MAX_ROUTE_METRES, 600);
        float snapped = Math.round(savedMetres / 50.0f) * 50.0f;
        if (snapped < 100f) snapped = 100f;
        if (snapped > 2000f) snapped = 2000f;
        slider.setValue(snapped);
        valueText.setText(getString(R.string.units_metres_format, savedMetres));
        slider.addOnChangeListener((s, value, fromUser) -> {
            int metres = Math.round(value);
            valueText.setText(getString(R.string.units_metres_format, metres));
            if (fromUser) {
                prefs.setInt(Prefs.KEY_MAX_ROUTE_METRES, metres);
                onRoutingPreferenceChanged();
            }
        });
    }

    private void bindNavigationSection() {
        bindToggleRow(R.id.settingsVoiceSwitch, Prefs.KEY_VOICE_GUIDANCE, true);
        bindToggleRow(R.id.settingsHapticsSwitch, Prefs.KEY_HAPTICS, true);
        bindToggleRow(R.id.settingsAutoLocateSwitch, Prefs.KEY_AUTO_LOCATE, true);

        ((TextView) findViewById(R.id.settingsDefaultBuildingValueText)).setText(
                prefs.getString(Prefs.KEY_DEFAULT_BUILDING, getString(R.string.settings_default_building_value)));

        setupChipGroup(R.id.settingsUnitsChipGroup, Prefs.KEY_UNITS, "metres",
                new int[]{R.id.unitsMetresChip, R.id.unitsFeetChip, R.id.unitsMinutesChip},
                new String[]{"metres", "feet", "minutes"});
    }

    private void bindReportsSection() {
        bindToggleRow(R.id.settingsShowFlaggedSwitch, Prefs.KEY_SHOW_FLAGGED, true);
        findViewById(R.id.settingsMyReportsRow).setOnClickListener(v ->
                startActivity(new Intent(this, MyReportsActivity.class)));
    }

    private void bindPrivacySection() {
        bindToggleRow(R.id.settingsAnonUsageSwitch, Prefs.KEY_ANON_USAGE, true);
        findViewById(R.id.settingsClearCacheRow).setOnClickListener(v -> {});
        findViewById(R.id.settingsPrivacyPolicyRow).setOnClickListener(v -> {});
    }

    private void bindAboutSection() {
        findViewById(R.id.settingsHelpRow).setOnClickListener(v -> {});
        findViewById(R.id.settingsAboutRow).setOnClickListener(v -> {});
    }

    private void bindLogOut() {
        findViewById(R.id.settingsLogOutButton).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.dialog_log_out_title)
                        .setMessage(R.string.dialog_log_out_message)
                        .setNegativeButton(R.string.dialog_cancel, null)
                        .setPositiveButton(R.string.dialog_log_out_confirm, (d, w) -> {
                            FirebaseAuth.getInstance().signOut();
                            finish();
                        })
                        .show());
    }

    private void bindToggleRow(int switchId, String key, boolean def) {
        bindToggleRow(switchId, key, def, false);
    }

    private void bindToggleRow(int switchId, String key, boolean def, boolean triggerRouteUpdate) {
        MaterialSwitch sw = findViewById(switchId);
        sw.setChecked(prefs.getBoolean(key, def));
        sw.setOnCheckedChangeListener((b, isChecked) -> {
            prefs.setBoolean(key, isChecked);
            if (triggerRouteUpdate) onRoutingPreferenceChanged();
        });
        View parentRow = (View) sw.getParent();
        parentRow.setFocusable(true);
        parentRow.setClickable(true);
        parentRow.setOnClickListener(v -> sw.setChecked(!sw.isChecked()));
        
        sw.setFocusable(false);
        sw.setClickable(false);
    }

    private void setupChipGroup(int groupId, String key, String def, int[] ids, String[] vals) {
        setupChipGroup(groupId, key, def, ids, vals, false);
    }

    private void setupChipGroup(int groupId, String key, String def, int[] ids, String[] vals, boolean updateRoute) {
        ChipGroup group = findViewById(groupId);
        String saved = prefs.getString(key, def);
        for (int i = 0; i < vals.length; i++) {
            if (vals[i].equals(saved)) {
                ((Chip) findViewById(ids[i])).setChecked(true);
                break;
            }
        }
        group.setOnCheckedStateChangeListener((g, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            for (int i = 0; i < ids.length; i++) {
                if (ids[i] == checkedIds.get(0)) {
                    String newVal = vals[i];
                    prefs.setString(key, newVal);
                    
                    if (key.equals(Prefs.KEY_TEXT_SIZE)) {
                        AppConfiguration.refreshApp(this);
                        return;
                    }
                    
                    if (updateRoute) onRoutingPreferenceChanged();
                    return;
                }
            }
        });
    }

    private void onRoutingPreferenceChanged() {}
}
