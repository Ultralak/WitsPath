package com.example.witspath.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
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
import com.example.witspath.util.Languages;
import com.example.witspath.util.Prefs;

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
    }

    private void refreshAccountState() {
    }

    private void bindDrawerViews() {
        navUserNameText = findViewById(R.id.navUserNameText);
        navUserEmailText = findViewById(R.id.navUserEmailText);
        navLogInRow = findViewById(R.id.navLogInRow);
        navSignUpRow = findViewById(R.id.navSignUpRow);
        navLogOutRow = findViewById(R.id.navLogOutRow);
        navLanguageSwatch = findViewById(R.id.navLanguageSwatch);
        navLanguageValueText = findViewById(R.id.navLanguageValueText);
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
    }

    private void confirmLogOut() {
    }


    private void refreshLanguageIndicator() {
        String tag = prefs.getString(Prefs.KEY_UI_LANGUAGE, "");
        navLanguageValueText.setText(Languages.displayNameForTag(this, tag));
        navLanguageSwatch.setBackgroundTintList(
                ColorStateList.valueOf(Languages.colorForTag(this, tag)));
    }
    

    private void closeDrawer() {
        drawerLayout.closeDrawer(GravityCompat.START);
    }

    private void openAndCloseDrawer(Class<? extends AppCompatActivity> target) {
        closeDrawer();
        startActivity(new Intent(this, target));
    }
}
