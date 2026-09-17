package com.example.witspath.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.example.witspath.R;
import com.example.witspath.model.SavedPlace;
import com.example.witspath.util.Languages;
import com.example.witspath.util.Prefs;

import java.util.Calendar;
import java.util.List;

/**
 * Team Wavelets - WitsPath
 * Home screen. Owns the hamburger drawer (nav_drawer_content.xml, included inside
 * activity_home.xml's NavigationView) and every row's navigation target.
 * Frontend only: FirebaseAuth.getCurrentUser() is read to decide what the drawer
 * header shows, but no Firestore reads/writes happen here.
 */
public class HomeActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private Prefs prefs;

    // Drawer header
    private TextView navUserNameText;
    private TextView navUserEmailText;

    // Drawer account rows (visibility toggles with sign-in state)
    private View navLogInRow;
    private View navSignUpRow;
    private View navLogOutRow;

    // Language swatch + value shown in the drawer
    private View navLanguageSwatch;
    private TextView navLanguageValueText;

    // Main content
    private TextView greetingText;
    private TextView currentLocationNameText;
    private LinearLayout frequentedLocationsContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        prefs = new Prefs(this);
        drawerLayout = findViewById(R.id.homeDrawerLayout);

        MaterialToolbar toolbar = findViewById(R.id.homeToolbar);
        toolbar.setNavigationOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        bindDrawerViews();
        bindDrawerClicks();
        bindMainContentClicks();

        // Back press closes an open drawer before it does anything else.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAccountState();
        refreshLanguageIndicator();
        refreshGreeting();
        refreshCurrentLocation();
        refreshFrequentedLocations();
    }

    private void refreshAccountState() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        boolean signedIn = user != null && !user.isAnonymous();

        navLogInRow.setVisibility(signedIn ? View.GONE : View.VISIBLE);
        navSignUpRow.setVisibility(signedIn ? View.GONE : View.VISIBLE);
        navLogOutRow.setVisibility(signedIn ? View.VISIBLE : View.GONE);

        navUserNameText.setText(signedIn ? user.getDisplayName() : getString(R.string.nav_guest_title));
        navUserEmailText.setText(signedIn ? user.getEmail() : getString(R.string.nav_guest_subtitle));
    }

    private void bindDrawerViews() {
        navUserNameText = findViewById(R.id.navUserNameText);
        navUserEmailText = findViewById(R.id.navUserEmailText);
        navLogInRow = findViewById(R.id.navLogInRow);
        navSignUpRow = findViewById(R.id.navSignUpRow);
        navLogOutRow = findViewById(R.id.navLogOutRow);
        navLanguageSwatch = findViewById(R.id.navLanguageSwatch);
        navLanguageValueText = findViewById(R.id.navLanguageValueText);

        greetingText = findViewById(R.id.greetingText);
        currentLocationNameText = findViewById(R.id.currentLocationNameText);
        frequentedLocationsContainer = findViewById(R.id.frequentedLocationsContainer);
    }

    private void bindMainContentClicks() {
        findViewById(R.id.navigateButton).setOnClickListener(v ->
                startActivity(new Intent(this, RoomPickerActivity.class)));
    }

    private void bindDrawerClicks() {
        findViewById(R.id.navHeaderAccount).setOnClickListener(v -> onAccountRowClicked());

        findViewById(R.id.navHomeRow).setOnClickListener(v -> closeDrawer());
        findViewById(R.id.navDirectoryRow).setOnClickListener(v ->
                openAndCloseDrawer(RoomPickerActivity.class));
        findViewById(R.id.navSavedPlacesRow).setOnClickListener(v ->
                openAndCloseDrawer(PreferencesActivity.class));
        findViewById(R.id.navMyReportsRow).setOnClickListener(v ->
                openAndCloseDrawer(PreferencesActivity.class));

        findViewById(R.id.navSettingsRow).setOnClickListener(v ->
                openAndCloseDrawer(SettingsActivity.class));
        findViewById(R.id.navLanguageRow).setOnClickListener(v ->
                openAndCloseDrawer(LanguageActivity.class));
        findViewById(R.id.navAccessibilityRow).setOnClickListener(v ->
                openAndCloseDrawer(SettingsActivity.class));
        findViewById(R.id.navPreferencesRow).setOnClickListener(v ->
                openAndCloseDrawer(PreferencesActivity.class));
        findViewById(R.id.navNotificationsRow).setOnClickListener(v ->
                openAndCloseDrawer(SettingsActivity.class));

        navLogInRow.setOnClickListener(v -> openAndCloseDrawer(LoginActivity.class));
        navSignUpRow.setOnClickListener(v -> openAndCloseDrawer(SignUpActivity.class));
        navLogOutRow.setOnClickListener(v -> confirmLogOut());

        findViewById(R.id.navHelpRow).setOnClickListener(v -> closeDrawer());
        findViewById(R.id.navAboutRow).setOnClickListener(v -> closeDrawer());
    }

    private void onAccountRowClicked() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.isAnonymous()) {
            openAndCloseDrawer(LoginActivity.class);
        } else {
            openAndCloseDrawer(SettingsActivity.class);
        }
    }

    private void confirmLogOut() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_log_out_title)
                .setMessage(R.string.dialog_log_out_message)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.dialog_log_out_confirm, (d, w) -> {
                    FirebaseAuth.getInstance().signOut();
                    Toast.makeText(this, R.string.toast_logged_out, Toast.LENGTH_SHORT).show();
                    recreate();
                })
                .show();
    }


    private void refreshLanguageIndicator() {
        String tag = prefs.getString(Prefs.KEY_UI_LANGUAGE, "");
        navLanguageValueText.setText(Languages.displayNameForTag(this, tag));
        navLanguageSwatch.setBackgroundTintList(
                ColorStateList.valueOf(Languages.colorForTag(this, tag)));
    }

    private void refreshGreeting() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String name = (user != null && user.getDisplayName() != null)
                ? user.getDisplayName().split(" ")[0]
                : getString(R.string.nav_guest_title);

        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String prefix;
        if (hour < 12) prefix = "Good Morning";
        else if (hour < 18) prefix = "Good Afternoon";
        else prefix = "Good Evening";

        greetingText.setText(String.format("%s, %s!", prefix, name));
    }

    private void refreshCurrentLocation() {
        String homeNode = prefs.getString(Prefs.KEY_HOME_NODE_ID, "");
        currentLocationNameText.setText(homeNode.isEmpty() ? "---" : homeNode);
    }

    private void refreshFrequentedLocations() {
        frequentedLocationsContainer.removeAllViews();
        List<SavedPlace> places = SavedPlace.fromJsonArray(
                prefs.getString(Prefs.KEY_SAVED_PLACES_JSON, ""));

        if (places.isEmpty()) {
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (SavedPlace place : places) {
            View row = inflater.inflate(R.layout.item_frequented_location, frequentedLocationsContainer, false);
            ((TextView) row.findViewById(R.id.locationNameText)).setText(place.label);
            ((TextView) row.findViewById(R.id.lastVisitedText)).setText(place.detail);

            row.setOnClickListener(v -> {
                Intent intent = new Intent(this, FloorPlanActivity.class);
                intent.putExtra("targetNodeId", place.nodeId);
                startActivity(intent);
            });

            frequentedLocationsContainer.addView(row);
        }
    }
    

    private void closeDrawer() {
        drawerLayout.closeDrawer(GravityCompat.START);
    }

    private void openAndCloseDrawer(Class<? extends AppCompatActivity> target) {
        closeDrawer();
        startActivity(new Intent(this, target));
    }
}
