package com.example.witspath.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.example.witspath.R;
import com.example.witspath.model.Floor;
import com.example.witspath.model.Graph;
import com.example.witspath.model.Node;
import com.example.witspath.util.Languages;
import com.example.witspath.util.Prefs;
import com.example.witspath.util.FirestorePopulator;

import java.util.Calendar;

public class HomeActivity extends BaseActivity {

    private DrawerLayout drawerLayout;
    private Prefs prefs;

    // Drawer views
    private TextView navUserNameText;
    private TextView navUserEmailText;
    private View navLogInRow;
    private View navSignUpRow;
    private View navLogOutRow;
    private View navLanguageSwatch;
    private TextView navLanguageValueText;

    // Main content
    private TextView greetingText;
    private TextView currentLocationNameText;
    private TextView currentLocationLabelText;
    private TextView toLocationNameText;
    private TextView toLocationLabelText;
    private View currentLocationPlate;
    private View toLocationPlate;
    private View autoDetectButton;
    private FloorPlanRouteView routeView;
    private ZoomableFrameLayout zoomContainer;
    
    private String selectedFromNodeId = null;
    private String selectedDestinationId = null;

    private final ActivityResultLauncher<Intent> fromPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String nodeId = result.getData().getStringExtra("selected_room");
                    if (nodeId != null) {
                        updateFromLocation(nodeId);
                    }
                }
            }
    );

    private final ActivityResultLauncher<Intent> destinationPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String nodeId = result.getData().getStringExtra("selected_room");
                    if (nodeId != null) {
                        updateToLocation(nodeId);
                    }
                }
            }
    );

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
        bindMainContent();
        initMap();

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
    }

    private void refreshAccountState() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        boolean signedIn = user != null && !user.isAnonymous();

        navLogInRow.setVisibility(signedIn ? View.GONE : View.VISIBLE);
        navSignUpRow.setVisibility(signedIn ? View.GONE : View.VISIBLE);
        navLogOutRow.setVisibility(signedIn ? View.VISIBLE : View.GONE);

        if (signedIn) {
            String name = user.getDisplayName();
            navUserNameText.setText(name != null && !name.isEmpty() ? name : getString(R.string.field_full_name));
            navUserEmailText.setText(user.getEmail());
            updateReportsCount(user.getUid());
        } else {
            navUserNameText.setText(R.string.nav_guest_title);
            navUserEmailText.setText(R.string.nav_guest_subtitle);
            findViewById(R.id.navMyReportsCount).setVisibility(View.GONE);
        }
    }

    private void updateReportsCount(String uid) {
        FirebaseFirestore.getInstance().collection("reports")
                .whereEqualTo("userId", uid)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    TextView countText = findViewById(R.id.navMyReportsCount);
                    int count = queryDocumentSnapshots.size();
                    if (count > 0) {
                        countText.setVisibility(View.VISIBLE);
                        countText.setText(String.valueOf(count));
                    } else {
                        countText.setVisibility(View.GONE);
                    }
                });
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

    private void bindMainContent() {
        greetingText = findViewById(R.id.greetingText);
        currentLocationNameText = findViewById(R.id.currentLocationNameText);
        currentLocationLabelText = findViewById(R.id.currentLocationLabelText);
        toLocationNameText = findViewById(R.id.toLocationNameText);
        toLocationLabelText = findViewById(R.id.toLocationLabelText);
        currentLocationPlate = findViewById(R.id.currentLocationPlate);
        toLocationPlate = findViewById(R.id.toLocationPlate);
        autoDetectButton = findViewById(R.id.autoDetectButton);

        currentLocationPlate.setOnClickListener(v ->
                fromPickerLauncher.launch(new Intent(this, RoomPickerActivity.class)));

        toLocationPlate.setOnClickListener(v ->
                destinationPickerLauncher.launch(new Intent(this, RoomPickerActivity.class)));

        autoDetectButton.setOnClickListener(v -> autoDetectLocation());

        findViewById(R.id.navigateButton).setOnClickListener(v -> {
            if (selectedDestinationId == null) {
                Toast.makeText(this, "Please select a destination", Toast.LENGTH_SHORT).show();
                return;
            }
            if (selectedFromNodeId == null) {
                Toast.makeText(this, "Please select a starting point", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(this, NavigationActivity.class);
            intent.putExtra("to_node", selectedDestinationId);
            intent.putExtra("from_node", selectedFromNodeId);
            startActivity(intent);
        });
    }

    private void updateFromLocation(String nodeId) {
        selectedFromNodeId = nodeId;
        Node node = Node.getByID(nodeId);
        String label = (node != null && node.label != null) ? node.label : nodeId;
        currentLocationNameText.setText(label);
        currentLocationNameText.setTextColor(getColor(R.color.colorInkText));
        currentLocationLabelText.setText("Selected Start");
        currentLocationLabelText.setVisibility(View.VISIBLE);
    }

    private void updateToLocation(String nodeId) {
        selectedDestinationId = nodeId;
        Node node = Node.getByID(nodeId);
        String label = (node != null && node.label != null) ? node.label : nodeId;
        toLocationNameText.setText(label);
        toLocationNameText.setTextColor(getColor(R.color.colorInkText));
        toLocationLabelText.setText("Selected Destination");
        toLocationLabelText.setVisibility(View.VISIBLE);
    }

    private void autoDetectLocation() {
        String homeNode = prefs.getString(Prefs.KEY_HOME_NODE_ID, "nd_mu2x3ima1");
        updateFromLocation(homeNode);
        currentLocationLabelText.setText(R.string.auto_detected);
        Toast.makeText(this, "Location detected", Toast.LENGTH_SHORT).show();
    }

    private void bindDrawerClicks() {
        findViewById(R.id.navHeaderAccount).setOnClickListener(v -> onAccountRowClicked());
        findViewById(R.id.navHomeRow).setOnClickListener(v -> closeDrawer());
        findViewById(R.id.navMyReportsRow).setOnClickListener(v -> openAndCloseDrawer(MyReportsActivity.class));
        findViewById(R.id.navSettingsRow).setOnClickListener(v -> openAndCloseDrawer(SettingsActivity.class));
        findViewById(R.id.navLanguageRow).setOnClickListener(v -> openAndCloseDrawer(LanguageActivity.class));
        findViewById(R.id.navAccessibilityRow).setOnClickListener(v -> {
            closeDrawer();
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.putExtra("scrollToSection", "accessibility");
            startActivity(intent);
        });
        findViewById(R.id.navPreferencesRow).setOnClickListener(v -> openAndCloseDrawer(PreferencesActivity.class));

        navLogInRow.setOnClickListener(v -> openAndCloseDrawer(LoginActivity.class));
        navSignUpRow.setOnClickListener(v -> openAndCloseDrawer(SignUpActivity.class));
        navLogOutRow.setOnClickListener(v -> confirmLogOut());

        findViewById(R.id.navHelpRow).setOnClickListener(v -> closeDrawer());
        findViewById(R.id.navAboutRow).setOnClickListener(v -> closeDrawer());
        findViewById(R.id.navPopulateFirestoreRow).setOnClickListener(v -> {
            closeDrawer();
            FirestorePopulator.populateFromAssets(this);
        });
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
        if (hour < 12) prefix = getString(R.string.greeting_morning);
        else if (hour < 18) prefix = getString(R.string.greeting_afternoon);
        else prefix = getString(R.string.greeting_evening);

        greetingText.setText(String.format("%s, %s!", prefix, name));
    }

    private void refreshCurrentLocation() {
        if (selectedFromNodeId == null) {
            String homeNode = prefs.getString(Prefs.KEY_HOME_NODE_ID, "");
            if (!homeNode.isEmpty()) {
                updateFromLocation(homeNode);
            } else {
                currentLocationNameText.setText("---");
                currentLocationLabelText.setVisibility(View.GONE);
            }
        }
    }

    private void initMap() {
        routeView = findViewById(R.id.homeFloorPlanRouteView);
        zoomContainer = findViewById(R.id.homeMapContainer);

        // Ensure graph is loaded to get floor dimensions
        if (Node.searchByName("").isEmpty()) {
            Graph.loadFromAssets(this, "graph_data.json");
        }

        // West Campus floor ID from JSON
        String floorId = "flr_mu2x3cer0";
        Floor floor = Floor.getById(floorId);
        if (floor != null && routeView != null) {
            routeView.setFloorPlanSize(floor.imageWidth, floor.imageHeight);
        }
    }

    private void closeDrawer() {
        drawerLayout.closeDrawer(GravityCompat.START);
    }

    private void openAndCloseDrawer(Class<? extends BaseActivity> target) {
        closeDrawer();
        startActivity(new Intent(this, target));
    }
}
