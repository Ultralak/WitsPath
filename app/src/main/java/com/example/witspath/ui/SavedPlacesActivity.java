package com.example.witspath.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.witspath.ui.BaseActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.example.witspath.R;
import com.example.witspath.model.SavedPlace;
import com.example.witspath.util.Prefs;

import java.util.ArrayList;
import java.util.List;

public class SavedPlacesActivity extends BaseActivity {

    private Prefs prefs;
    private LinearLayout container;
    private View emptyText;
    private final List<SavedPlace> savedPlaces = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved_places);
        prefs = new Prefs(this);

        MaterialToolbar toolbar = findViewById(R.id.savedPlacesToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        container = findViewById(R.id.savedPlacesContainer);
        emptyText = findViewById(R.id.savedPlacesEmptyText);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSavedPlaces();
    }

    private void loadSavedPlaces() {
        savedPlaces.clear();
        savedPlaces.addAll(SavedPlace.fromJsonArray(prefs.getString(Prefs.KEY_SAVED_PLACES_JSON, "")));
        renderSavedPlaces();
    }

    private void renderSavedPlaces() {
        container.removeAllViews();
        emptyText.setVisibility(savedPlaces.isEmpty() ? View.VISIBLE : View.GONE);
        container.setVisibility(savedPlaces.isEmpty() ? View.GONE : View.VISIBLE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (SavedPlace place : savedPlaces) {
            View row = inflater.inflate(R.layout.item_saved_place, container, false);
            ((TextView) row.findViewById(R.id.savedPlaceNameText)).setText(place.label);
            ((TextView) row.findViewById(R.id.savedPlaceDetailText)).setText(place.detail);

            row.setOnClickListener(v -> {
                Intent intent = new Intent(this, FloorPlanActivity.class);
                intent.putExtra("targetNodeId", place.nodeId);
                startActivity(intent);
            });
            row.findViewById(R.id.savedPlaceDeleteButton).setOnClickListener(v -> {
                savedPlaces.remove(place);
                prefs.setStringSync(Prefs.KEY_SAVED_PLACES_JSON, SavedPlace.toJsonArray(savedPlaces));
                renderSavedPlaces();
            });

            container.addView(row);
        }
    }
}
