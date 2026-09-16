package com.example.witspath.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.example.witspath.R;
import com.example.witspath.util.Languages;
import com.example.witspath.util.Prefs;

public class SettingsActivity extends AppCompatActivity {

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

        findViewById(R.id.settingsAccountRow).setOnClickListener(v ->
                startActivity(new Intent(this, signedIn ? SettingsActivity.class : LoginActivity.class)));

        bindToggleRow(R.id.settingsSyncRow, R.id.settingsSyncSwitch, Prefs.KEY_SYNC_ENABLED, true);
    }

    private void bindLanguageAndDisplaySection() {
        findViewById(R.id.settingsLanguageRow).setOnClickListener(v ->
                startActivity(new Intent(this, LanguageActivity.class)));
        refreshLanguageRow();

        String size = prefs.getString(Prefs.KEY_TEXT_SIZE, "default");
        ChipGroup textSizeGroup = findViewById(R.id.settingsTextSizeChipGroup);
        selectChipForValue(textSizeGroup, size,
                new int[]{R.id.textSizeSmallChip, R.id.textSizeDefaultChip,
                        R.id.textSizeLargeChip, R.id.textSizeHugeChip},
                new String[]{"small", "default", "large", "huge"});
        textSizeGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            String value = valueForChip(checkedIds.get(0),
                    new int[]{R.id.textSizeSmallChip, R.id.textSizeDefaultChip,
                            R.id.textSizeLargeChip, R.id.textSizeHugeChip},
                    new String[]{"small", "default", "large", "huge"});
            prefs.setString(Prefs.KEY_TEXT_SIZE, value);
        });

        bindToggleRow(R.id.settingsHighContrastSwitch, Prefs.KEY_HIGH_CONTRAST, false);
        bindToggleRow(R.id.settingsScreenReaderSwitch, Prefs.KEY_SCREEN_READER_HINTS, true);
    }

    private void refreshLanguageRow() {
        String tag = prefs.getString(Prefs.KEY_UI_LANGUAGE, "");
        TextView value = findViewById(R.id.settingsLanguageValueText);
        value.setText(Languages.displayNameForTag(this, tag));
        View swatch = findViewById(R.id.settingsLanguageSwatch);
        swatch.setBackgroundTintList(
                ColorStateList.valueOf(Languages.colorForTag(this, tag)));
    }

    private void bindRoutingSection() {
        String profile = prefs.getString(Prefs.KEY_MOBILITY_PROFILE, "wheelchair");
        ChipGroup mobilityGroup = findViewById(R.id.settingsMobilityChipGroup);
        int[] mobilityChipIds = {R.id.mobilityWheelchairChip, R.id.mobilityWalkingAidChip,
                R.id.mobilityLowVisionChip, R.id.mobilityNoneChip};
        String[] mobilityValues = {"wheelchair", "walking_aid", "low_vision", "none"};
        selectChipForValue(mobilityGroup, profile, mobilityChipIds, mobilityValues);
        mobilityGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            prefs.setString(Prefs.KEY_MOBILITY_PROFILE,
                    valueForChip(checkedIds.get(0), mobilityChipIds, mobilityValues));
            onRoutingPreferenceChanged();
        });

        bindRoutingToggle(R.id.settingsStepFreeSwitch, Prefs.KEY_STEP_FREE_ONLY, true);
        bindRoutingToggle(R.id.settingsPreferLiftsSwitch, Prefs.KEY_PREFER_LIFTS, true);
        bindRoutingToggle(R.id.settingsAvoidSteepSwitch, Prefs.KEY_AVOID_STEEP_RAMPS, true);

        Slider slider = findViewById(R.id.settingsMaxDistanceSlider);
        TextView valueText = findViewById(R.id.settingsMaxDistanceValueText);
        int savedMetres = prefs.getInt(Prefs.KEY_MAX_ROUTE_METRES, 600);
        slider.setValue(savedMetres);
        valueText.setText(savedMetres + " m");
        slider.addOnChangeListener((s, value, fromUser) -> {
            int metres = Math.round(value);
            valueText.setText(metres + " m");
            if (fromUser) {
                prefs.setInt(Prefs.KEY_MAX_ROUTE_METRES, metres);
                onRoutingPreferenceChanged();
            }
        });
    }

    private void onRoutingPreferenceChanged() {
    }

    private void bindRoutingToggle(int switchId, String key, boolean defaultValue) {
        MaterialSwitch sw = findViewById(switchId);
        sw.setChecked(prefs.getBoolean(key, defaultValue));
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.setBoolean(key, isChecked);
            onRoutingPreferenceChanged();
        });
        View row = (View) sw.getParent();
        row.setOnClickListener(v -> sw.setChecked(!sw.isChecked()));
    }

    private void bindNavigationSection() {
        bindToggleRow(R.id.settingsVoiceSwitch, Prefs.KEY_VOICE_GUIDANCE, true);
        bindToggleRow(R.id.settingsHapticsSwitch, Prefs.KEY_HAPTICS, true);
        bindToggleRow(R.id.settingsAutoLocateSwitch, Prefs.KEY_AUTO_LOCATE, true);

        TextView buildingValue = findViewById(R.id.settingsDefaultBuildingValueText);
        buildingValue.setText(prefs.getString(Prefs.KEY_DEFAULT_BUILDING,
                getString(R.string.settings_default_building_value)));
        findViewById(R.id.settingsDefaultBuildingRow).setOnClickListener(v -> {
        });

        ChipGroup unitsGroup = findViewById(R.id.settingsUnitsChipGroup);
        int[] unitsChipIds = {R.id.unitsMetresChip, R.id.unitsFeetChip, R.id.unitsMinutesChip};
        String[] unitsValues = {"metres", "feet", "minutes"};
        selectChipForValue(unitsGroup, prefs.getString(Prefs.KEY_UNITS, "metres"),
                unitsChipIds, unitsValues);
        unitsGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            prefs.setString(Prefs.KEY_UNITS, valueForChip(checkedIds.get(0), unitsChipIds, unitsValues));
        });
    }

    private void bindReportsSection() {
        bindToggleRow(R.id.settingsShowFlaggedSwitch, Prefs.KEY_SHOW_FLAGGED, true);
        bindToggleRow(R.id.settingsAlertsSwitch, Prefs.KEY_ROUTE_ALERTS, true);
        findViewById(R.id.settingsMyReportsRow).setOnClickListener(v ->
                startActivity(new Intent(this, PreferencesActivity.class)));
    }

    private void bindPrivacySection() {
        bindToggleRow(R.id.settingsAnonUsageSwitch, Prefs.KEY_ANON_USAGE, true);
        findViewById(R.id.settingsClearCacheRow).setOnClickListener(v -> {
        });
        findViewById(R.id.settingsPrivacyPolicyRow).setOnClickListener(v -> {
        });
    }

    private void bindAboutSection() {
        findViewById(R.id.settingsHelpRow).setOnClickListener(v -> {
        });
        findViewById(R.id.settingsAboutRow).setOnClickListener(v -> {
        });
    }

    private void bindLogOut() {
        findViewById(R.id.settingsLogOutButton).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.dialog_log_out_title)
                        .setMessage(R.string.dialog_log_out_message)
                        .setNegativeButton(R.string.dialog_cancel, null)
                        .setPositiveButton(R.string.dialog_log_out_confirm, (d, w) -> {
                            FirebaseAuth.getInstance().signOut();
                            bindAccountSection();
                            finish();
                        })
                        .show());
    }

    private void bindToggleRow(int rowContainingSwitchId, String key, boolean defaultValue) {
        MaterialSwitch sw = findViewById(rowContainingSwitchId);
        sw.setChecked(prefs.getBoolean(key, defaultValue));
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> prefs.setBoolean(key, isChecked));
        View row = (View) sw.getParent();
        row.setOnClickListener(v -> sw.setChecked(!sw.isChecked()));
    }

    private void bindToggleRow(int rowId, int switchId, String key, boolean defaultValue) {
        MaterialSwitch sw = findViewById(switchId);
        sw.setChecked(prefs.getBoolean(key, defaultValue));
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> prefs.setBoolean(key, isChecked));
        findViewById(rowId).setOnClickListener(v -> sw.setChecked(!sw.isChecked()));
    }

    private void selectChipForValue(ChipGroup group, String value, int[] chipIds, String[] values) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) {
                ((Chip) findViewById(chipIds[i])).setChecked(true);
                return;
            }
        }
    }

    private String valueForChip(int checkedChipId, int[] chipIds, String[] values) {
        for (int i = 0; i < chipIds.length; i++) {
            if (chipIds[i] == checkedChipId) return values[i];
        }
        return values[0];
    }
}
