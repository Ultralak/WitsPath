package com.example.witspath.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.example.witspath.ui.BaseActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.example.witspath.R;
import com.example.witspath.model.SavedPlace;
import com.example.witspath.util.Prefs;

import java.util.ArrayList;
import java.util.List;

public class PreferencesActivity extends BaseActivity {

    private Prefs prefs;
    private LinearLayout savedPlacesContainer;
    private View savedEmptyText;
    private final List<SavedPlace> savedPlaces = new ArrayList<>();

    private final ActivityResultLauncher<Intent> roomPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String selectedRoom = result.getData().getStringExtra("selected_room");
                    // For now, let's assume we were picking the Home Location.
                    // A real app would track which row launched the picker.
                    if (selectedRoom != null) {
                        prefs.setStringSync(Prefs.KEY_HOME_NODE_ID, selectedRoom);
                        bindMyPlacesSection();
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preferences);
        prefs = new Prefs(this);

        MaterialToolbar toolbar = findViewById(R.id.preferencesToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        savedPlacesContainer = findViewById(R.id.preferencesSavedPlacesContainer);
        savedEmptyText = findViewById(R.id.preferencesSavedEmptyText);

        bindMyPlacesSection();
        bindHistorySection();
        bindResetButton();
        loadSavedPlaces();
    }

    private void bindMyPlacesSection() {
        TextView homeValue = findViewById(R.id.preferencesHomeLocationValueText);
        String homeNodeId = prefs.getString(Prefs.KEY_HOME_NODE_ID, "");
        homeValue.setText(homeNodeId.isEmpty()
                ? getString(R.string.preferences_not_set) : homeNodeId);

        findViewById(R.id.preferencesHomeLocationRow).setOnClickListener(v -> {
            Intent intent = new Intent(this, RoomPickerActivity.class);
            roomPickerLauncher.launch(intent);
        });
    }

    private void loadSavedPlaces() {
        savedPlaces.clear();
        savedPlaces.addAll(SavedPlace.fromJsonArray(prefs.getString(Prefs.KEY_SAVED_PLACES_JSON, "")));
        renderSavedPlaces();
    }

    private void renderSavedPlaces() {
        savedPlacesContainer.removeAllViews();
        savedEmptyText.setVisibility(savedPlaces.isEmpty() ? View.VISIBLE : View.GONE);
        savedPlacesContainer.setVisibility(savedPlaces.isEmpty() ? View.GONE : View.VISIBLE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (SavedPlace place : savedPlaces) {
            View row = inflater.inflate(R.layout.item_saved_place, savedPlacesContainer, false);

            ((TextView) row.findViewById(R.id.savedPlaceNameText)).setText(place.label);
            ((TextView) row.findViewById(R.id.savedPlaceDetailText)).setText(place.detail);

            row.setOnClickListener(v -> {
            });
            row.findViewById(R.id.savedPlaceDeleteButton).setOnClickListener(v ->
                    removeSavedPlace(place));

            savedPlacesContainer.addView(row);
        }
    }

    private void removeSavedPlace(SavedPlace place) {
        savedPlaces.remove(place);
        prefs.setStringSync(Prefs.KEY_SAVED_PLACES_JSON, SavedPlace.toJsonArray(savedPlaces));
        renderSavedPlaces();
    }

    public static void addSavedPlace(Context context, SavedPlace place) {
        Prefs prefs = new Prefs(context);
        List<SavedPlace> places = SavedPlace.fromJsonArray(
                prefs.getString(Prefs.KEY_SAVED_PLACES_JSON, ""));
        places.add(place);
        prefs.setStringSync(Prefs.KEY_SAVED_PLACES_JSON, SavedPlace.toJsonArray(places));
    }

    private void bindHistorySection() {
        MaterialSwitch trackSwitch = findViewById(R.id.preferencesTrackFrequentSwitch);
        trackSwitch.setChecked(prefs.getBoolean(Prefs.KEY_TRACK_FREQUENT, true));
        trackSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.setBoolean(Prefs.KEY_TRACK_FREQUENT, isChecked));

        findViewById(R.id.preferencesClearHistoryRow).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.preferences_clear_history_title)
                        .setMessage(R.string.preferences_clear_history_subtitle)
                        .setNegativeButton(R.string.dialog_cancel, null)
                        .setPositiveButton(R.string.dialog_reset_confirm, (d, w) -> {
                            // TODO: Clear actual history nodes in Firestore/Local DB
                            Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
                        })
                        .show());
    }

    private void bindResetButton() {
        MaterialButton resetButton = findViewById(R.id.preferencesResetButton);
        resetButton.setOnClickListener(v ->
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.dialog_reset_title)
                        .setMessage(R.string.dialog_reset_message)
                        .setNegativeButton(R.string.dialog_cancel, null)
                        .setPositiveButton(R.string.dialog_reset_confirm, (d, w) -> {
                            prefs.clearAll();
                            loadSavedPlaces();
                            recreate();
                        })
                        .show());
    }
}
