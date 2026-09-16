package com.creativedisability.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;

import androidx.appcompat.app.AppCompatActivity;

import com.creativedisability.app.R;
import com.creativedisability.app.databinding.ActivityHomeBinding;
import com.creativedisability.app.databinding.ItemFrequentedLocationBinding;
import com.creativedisability.app.model.FrequentedLocation;

import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends AppCompatActivity {

    private ActivityHomeBinding homeBinding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        homeBinding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(homeBinding.getRoot());

        homeBinding.greetingText.setText(getString(R.string.greeting_format, "Ailton"));
        homeBinding.currentLocationNameText.setText("MHR");

        List<FrequentedLocation> frequentedLocations = loadFrequentedLocations();
        populateFrequentedLocations(frequentedLocations);

        homeBinding.navigateButton.setOnClickListener(view -> onNavigateClicked());
    }

    private List<FrequentedLocation> loadFrequentedLocations() {
        // TODO: replace with real data source
        List<FrequentedLocation> frequentedLocations = new ArrayList<>();
        frequentedLocations.add(new FrequentedLocation("wss_102", "WSS 102", "25 July"));
        return frequentedLocations;
    }

    private void populateFrequentedLocations(List<FrequentedLocation> frequentedLocations) {
        homeBinding.frequentedLocationsContainer.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(this);
        for (FrequentedLocation frequentedLocation : frequentedLocations) {
            ItemFrequentedLocationBinding itemBinding = ItemFrequentedLocationBinding.inflate(
                    inflater, homeBinding.frequentedLocationsContainer, false);

            itemBinding.locationNameText.setText(frequentedLocation.getLocationName());
            itemBinding.lastVisitedText.setText(
                    getString(R.string.last_visited_format, frequentedLocation.getLastVisitedDate()));
            itemBinding.locateIconButton.setOnClickListener(
                    view -> onLocateClicked(frequentedLocation));

            homeBinding.frequentedLocationsContainer.addView(itemBinding.getRoot());
        }
    }

    private void onLocateClicked(FrequentedLocation frequentedLocation) {
        // TODO: pan/zoom map to this location
    }

    private void onNavigateClicked() {
        // TODO: start navigation to the current location
    }
}
