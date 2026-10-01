package com.example.witspath.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.witspath.R;
import com.example.witspath.model.FloorPlanEdge;
import com.example.witspath.model.FloorPlanGraphConverter;
import com.example.witspath.model.FloorPlanNode;
import com.example.witspath.routing.CampusGraph;
import com.example.witspath.routing.Floor;
import com.example.witspath.routing.Node;
import com.example.witspath.routing.PhraseBook;
import com.example.witspath.routing.RouteOptions;
import com.example.witspath.routing.TravelTimeConfig;
import com.example.witspath.util.FirestorePopulator;
import com.example.witspath.util.GraphStore;
import com.example.witspath.util.Languages;
import com.example.witspath.util.Prefs;
import com.example.witspath.util.RoutePlanner;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class HomeActivity extends BaseActivity {

    private DrawerLayout drawerLayout;
    private Prefs prefs;

    private TextView navUserNameText;
    private TextView navUserEmailText;
    private View navLogInRow;
    private View navSignUpRow;
    private View navLogOutRow;
    private View navLanguageSwatch;
    private TextView navLanguageValueText;

    private TextView greetingText;
    private Spinner fromSpinner;
    private Spinner toSpinner;
    private TextView estimatedTimeText;
    private TextView estimatedDistanceText;
    private TextView guidanceText;
    private TextView statusMessage;
    private LinearLayout stepsContainer;
    private ImageView refreshLocation;
    private FloorPlanRouteView routeView;
    private ZoomableFrameLayout zoomContainer;

    private final List<Node> selectableNodes = new ArrayList<>();
    private String selectedFromNodeId;
    private String selectedDestinationId;
    private String mobilityProfile = "wheelchair";
    private boolean navigationStarted = false;
    private CampusGraph graph;
    private RoutePlanner.Plan currentPlan;
    private List<Node> currentRoute;

    private enum Mode {
        WHEELCHAIR("wheelchair"), WALKING_AID("walking-aid"), VISUAL("low-vision"), GENERAL("no-preference");
        final String profile;
        Mode(String profile) { this.profile = profile; }
    }

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
        loadGraphAndMap();
        handleNavigationIntent(getIntent());

        if (savedInstanceState != null) {
            selectedFromNodeId = savedInstanceState.getString("selectedFromNodeId");
            selectedDestinationId = savedInstanceState.getString("selectedDestinationId");
            mobilityProfile = savedInstanceState.getString("mobilityProfile", "wheelchair");
            navigationStarted = savedInstanceState.getBoolean("navigationStarted", false);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNavigationIntent(intent);
    }

    private void handleNavigationIntent(Intent intent) {
        if (intent == null) return;
        String fromNode = intent.getStringExtra("from_node");
        String toNode = intent.getStringExtra("to_node");
        boolean autoStart = intent.getBooleanExtra("start_navigation", false) || intent.getBooleanExtra("startNav", false);

        if (fromNode != null && !fromNode.isEmpty()) {
            selectedFromNodeId = findNodeOrFirst(fromNode);
        }
        if (toNode != null && !toNode.isEmpty()) {
            selectedDestinationId = findNodeOrSecond(toNode);
        }
        if (fromNode != null || toNode != null) {
            restoreSpinnerSelections();
            requestRoute(false);
            if (autoStart) {
                if (!navigationStarted) {
                    toggleNavigation();
                } else {
                    speakCurrentStep();
                }
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("selectedFromNodeId", selectedFromNodeId);
        outState.putString("selectedDestinationId", selectedDestinationId);
        outState.putString("mobilityProfile", mobilityProfile);
        outState.putBoolean("navigationStarted", navigationStarted);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAccountState();
        refreshLanguageIndicator();
        refreshGreeting();
        if (fromSpinner != null && toSpinner != null) {
            restoreSpinnerSelections();
        }
    }

    private void bindMainContent() {
        greetingText = findViewById(R.id.navProductTitle);
        fromSpinner = findViewById(R.id.fromLocationSpinner);
        toSpinner = findViewById(R.id.toLocationSpinner);
        estimatedTimeText = findViewById(R.id.estimatedTime);
        estimatedDistanceText = findViewById(R.id.estimatedDistance);
        guidanceText = findViewById(R.id.guidanceText);
        statusMessage = findViewById(R.id.statusMessage);
        stepsContainer = findViewById(R.id.stepsContainer);
        refreshLocation = findViewById(R.id.refreshLocation);

        refreshLocation.setOnClickListener(v -> autoDetectLocation());

        fromSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < selectableNodes.size()) selectedFromNodeId = selectableNodes.get(position).nodeId;
                requestRoute(false);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        toSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < selectableNodes.size()) selectedDestinationId = selectableNodes.get(position).nodeId;
                requestRoute(false);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        bindMode(R.id.modeWheelchair, Mode.WHEELCHAIR);
        bindMode(R.id.modeWalkingAid, Mode.WALKING_AID);
        bindMode(R.id.modeVisual, Mode.VISUAL);
        bindMode(R.id.modeGeneral, Mode.GENERAL);

        findViewById(R.id.navigateButton).setOnClickListener(v -> toggleNavigation());
    }

    private void bindMode(int id, Mode mode) {
        View view = findViewById(id);
        view.setOnClickListener(v -> {
            mobilityProfile = mode.profile;
            updateModeSelection(id);
            requestRoute(false);
        });
    }

    private void updateModeSelection(int selectedId) {
        int[] ids = {R.id.modeWheelchair, R.id.modeWalkingAid, R.id.modeVisual, R.id.modeGeneral};
        for (int id : ids) findViewById(id).setSelected(id == selectedId);
    }

    private int modeIdForProfile(String profile) {
        switch (RouteOptions.normaliseProfile(profile)) {
            case RouteOptions.WALKING_AID: return R.id.modeWalkingAid;
            case RouteOptions.LOW_VISION: return R.id.modeVisual;
            case RouteOptions.NONE: return R.id.modeGeneral;
            default: return R.id.modeWheelchair;
        }
    }

    private void loadGraphAndMap() {
        graph = GraphStore.get(this);

        selectableNodes.clear();
        if (graph != null) selectableNodes.addAll(graph.nodes());
        selectableNodes.removeIf(node -> "ramp".equals(node.type));
        Collections.sort(selectableNodes, Comparator.comparing(Node::displayName, String.CASE_INSENSITIVE_ORDER));

        List<String> labels = new ArrayList<>();
        for (Node node : selectableNodes) labels.add(node.displayName());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fromSpinner.setAdapter(adapter);
        toSpinner.setAdapter(adapter);

        String storedProfile = prefs.getString(Prefs.KEY_MOBILITY_PROFILE, "wheelchair");
        mobilityProfile = storedProfile == null || storedProfile.isEmpty() ? "wheelchair" : storedProfile;
        updateModeSelection(modeIdForProfile(mobilityProfile));

        String defaultFrom = prefs.getString(Prefs.KEY_HOME_NODE_ID, "nd_mu84hhsut");
        String defaultTo = "nd_mu842rrrm";
        selectedFromNodeId = findNodeOrFirst(defaultFrom);
        selectedDestinationId = findNodeOrSecond(defaultTo);
        restoreSpinnerSelections();

        routeView = findViewById(R.id.homeFloorPlanRouteView);
        zoomContainer = findViewById(R.id.homeMapContainer);
        ImageView imageView = findViewById(R.id.homeFloorPlanImageView);
        routeView.setOnFitMatrixChangeListener(imageView::setImageMatrix);

        findViewById(R.id.btnZoomIn).setOnClickListener(v -> zoomContainer.zoomIn());
        findViewById(R.id.btnZoomOut).setOnClickListener(v -> zoomContainer.zoomOut());
        findViewById(R.id.btnResetZoom).setOnClickListener(v -> zoomContainer.resetZoom());

        Floor floor = null;
        if (graph != null) {
            floor = graph.floor("flr_mu83yzmd0");
            if (floor == null && !graph.floors().isEmpty()) floor = graph.floors().get(0);
        }
        if (floor != null) {
            zoomContainer.setContentSize(floor.imageWidth, floor.imageHeight);
            routeView.setFloorPlanSize(floor.imageWidth, floor.imageHeight);
            List<FloorPlanNode> renderNodes = FloorPlanGraphConverter.toFloorPlanNodes(graph, floor.floorId);
            List<FloorPlanEdge> renderEdges = FloorPlanGraphConverter.toFloorPlanEdges(graph, floor.floorId);
            routeView.setGraph(renderNodes, renderEdges);
        }

        requestRoute(false);
    }

    private String findNodeOrFirst(String id) {
        if (graph != null && graph.node(id) != null) return id;
        return selectableNodes.isEmpty() ? null : selectableNodes.get(0).nodeId;
    }

    private String findNodeOrSecond(String id) {
        if (graph != null && graph.node(id) != null) return id;
        return selectableNodes.size() > 1 ? selectableNodes.get(1).nodeId : findNodeOrFirst(id);
    }

    private void restoreSpinnerSelections() {
        if (selectableNodes.isEmpty()) return;
        int fromIndex = indexOfNode(selectedFromNodeId);
        int toIndex = indexOfNode(selectedDestinationId);
        if (fromIndex >= 0) fromSpinner.setSelection(fromIndex, false);
        if (toIndex >= 0) toSpinner.setSelection(toIndex, false);
    }

    private int indexOfNode(String nodeId) {
        for (int i = 0; i < selectableNodes.size(); i++) if (selectableNodes.get(i).nodeId.equals(nodeId)) return i;
        return -1;
    }

    private void requestRoute(boolean showErrors) {
        if (selectedFromNodeId == null || selectedDestinationId == null || selectedFromNodeId.equals(selectedDestinationId)) {
            if (showErrors) showStatus(getString(R.string.home_toast_select_destination), true);
            return;
        }
        if (graph == null) return;
        Node from = graph.node(selectedFromNodeId);
        Node to = graph.node(selectedDestinationId);
        if (from == null || to == null) return;

        boolean stepFree = "wheelchair".equals(mobilityProfile) || "low-vision".equals(mobilityProfile);
        boolean preferLifts = prefs.getBoolean(Prefs.KEY_PREFER_LIFTS, false);
        boolean avoidSteepRamps = prefs.getBoolean(Prefs.KEY_AVOID_STEEP_RAMPS, false);

        // Same A* module as the navigation screen and the companion.
        RouteOptions options = new RouteOptions(mobilityProfile, stepFree, preferLifts, avoidSteepRamps);
        currentPlan = RoutePlanner.plan(graph, from, to, options, RoutePlanner.speedMultiplier(prefs), PhraseBook.english());
        currentRoute = currentPlan.ok() ? currentPlan.nodes : null;
        if (currentRoute == null || currentRoute.size() < 2) {
            estimatedTimeText.setText("—");
            estimatedDistanceText.setText("");
            guidanceText.setText(stepFree ? getString(R.string.web_no_step_free_route) : getString(R.string.web_no_route));
            stepsContainer.setVisibility(View.GONE);
            if (routeView != null) routeView.setHighlightedRoute(Collections.emptyList());
            return;
        }

        double distance = currentPlan.distanceMetres;
        estimatedTimeText.setText(currentPlan.minutes() + " min");
        estimatedDistanceText.setText("(" + formatDistance(distance) + ")");
        guidanceText.setText(navigationStarted ? getString(R.string.web_total_route_length, Math.round(distance)) : getString(R.string.web_route_ready));
        showStatus(getString(R.string.web_route_ready_status, Math.round(distance)), false);

        if (routeView != null) {
            List<String> ids = new ArrayList<>();
            for (Node n : currentRoute) ids.add(n.nodeId);
            routeView.setHighlightedRoute(ids);
            routeView.setCurrentPosition(from.nodeId);
            routeView.setDestination(to.nodeId);
        }
        if (navigationStarted) renderSteps(currentRoute);
        else stepsContainer.setVisibility(View.GONE);
    }

    private double calculateDistance(Node a, Node b) {
        if (a.point == null || b.point == null) return 0;
        return Math.hypot(a.point.x - b.point.x, a.point.y - b.point.y);
    }

    private String formatDistance(double metres) {
        String units = prefs.getString(Prefs.KEY_UNITS, "meters");
        if ("feet".equalsIgnoreCase(units)) return Math.round(metres * 3.28084) + " ft";
        if ("minutes".equalsIgnoreCase(units)) return Math.max(1, Math.round(metres / TravelTimeConfig.speedFor(mobilityProfile) / 60)) + " min";
        return Math.round(metres) + " m";
    }

    private void toggleNavigation() {
        if (currentRoute == null || currentRoute.size() < 2) {
            requestRoute(true);
            if (currentRoute == null) return;
        }
        navigationStarted = !navigationStarted;
        TextView button = findViewById(R.id.navigateButton);
        if (button != null) {
            button.setText(navigationStarted ? R.string.web_end_navigation : R.string.start_navigation);
        }
        if (navigationStarted) {
            renderSteps(currentRoute);
            guidanceText.setText(getString(R.string.web_total_route_length, Math.round(currentPlan.distanceMetres)));
            showStatus(getString(R.string.web_navigation_started), false);
            speakCurrentStep();

            if (routeView != null && selectedFromNodeId != null && selectedDestinationId != null) {
                List<String> ids = new ArrayList<>();
                for (Node n : currentRoute) ids.add(n.nodeId);
                routeView.setHighlightedRoute(ids);
                routeView.setCurrentPosition(selectedFromNodeId);
                routeView.setDestination(selectedDestinationId);
            }
        } else {
            if (navTts != null) navTts.stop();
            stepsContainer.setVisibility(View.GONE);
            guidanceText.setText(getString(R.string.web_route_ready));
            showStatus(getString(R.string.web_navigation_ended), false);
        }
    }

    private TextToSpeech navTts;
    private boolean navTtsReady = false;

    private void initNavTts() {
        if (navTts == null) {
            navTts = new TextToSpeech(getApplicationContext(), status -> {
                if (status == TextToSpeech.SUCCESS) {
                    navTtsReady = true;
                    if (navigationStarted) {
                        speakCurrentStep();
                    }
                }
            });
        }
    }

    private void speakCurrentStep() {
        if (!prefs.getBoolean(Prefs.KEY_VOICE_GUIDANCE, true)) return;
        if (currentRoute == null || currentRoute.size() < 2) return;
        initNavTts();
        Node first = currentRoute.get(0);
        Node second = currentRoute.get(1);
        double dist = calculateDistance(first, second);
        String text = getString(R.string.web_continue_for, Math.round(dist)) + " " + getString(R.string.web_follow_toward, safeLabel(second));
        if (navTts != null && navTtsReady) {
            navTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav_step_" + System.currentTimeMillis());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (navTts != null) {
            navTts.stop();
            navTts.shutdown();
        }
    }

    private void renderSteps(List<Node> route) {
        stepsContainer.removeAllViews();
        for (int i = 0; i < route.size(); i++) {
            Node node = route.get(i);
            View stepView = getLayoutInflater().inflate(R.layout.item_route_step, stepsContainer, false);
            TextView numberText = stepView.findViewById(R.id.stepNumberText);
            ImageView iconView = stepView.findViewById(R.id.stepIcon);
            TextView titleText = stepView.findViewById(R.id.stepTitleText);
            TextView subtitleText = stepView.findViewById(R.id.stepSubtitleText);

            numberText.setText(String.valueOf(i + 1));

            if (i == route.size() - 1) {
                iconView.setImageResource(R.drawable.ic_flag);
                titleText.setText(getString(R.string.web_arrive_at, safeLabel(node)));
                subtitleText.setText(R.string.web_main_entrance);
            } else {
                double distance = calculateDistance(node, route.get(i + 1));
                iconView.setImageResource(R.drawable.ic_next);
                titleText.setText(getString(R.string.web_continue_for, Math.round(distance)));
                subtitleText.setText(getString(R.string.web_follow_toward, safeLabel(route.get(i + 1))));
            }
            stepsContainer.addView(stepView);
        }
        stepsContainer.setVisibility(View.VISIBLE);
    }

    private String safeLabel(Node node) { return node.displayName(); }

    private void showStatus(String message, boolean error) {
        statusMessage.setText(message);
        statusMessage.setTextColor(getColor(error ? R.color.colorFlag : R.color.colorSuccess));
    }

    private void autoDetectLocation() {
        String homeNode = prefs.getString(Prefs.KEY_HOME_NODE_ID, "nd_mu84hhsut");
        selectedFromNodeId = findNodeOrFirst(homeNode);
        restoreSpinnerSelections();
        showStatus(getString(R.string.home_toast_location_detected), false);
        requestRoute(false);
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
        findViewById(R.id.navMyReportsRow).setOnClickListener(v -> openAndCloseDrawer(MyReportsActivity.class));
        findViewById(R.id.navSettingsRow).setOnClickListener(v -> openAndCloseDrawer(SettingsActivity.class));
        findViewById(R.id.navLanguageRow).setOnClickListener(v -> openAndCloseDrawer(LanguageActivity.class));
        navLogInRow.setOnClickListener(v -> openAndCloseDrawer(LoginActivity.class));
        navSignUpRow.setOnClickListener(v -> openAndCloseDrawer(SignUpActivity.class));
        navLogOutRow.setOnClickListener(v -> confirmLogOut());
        findViewById(R.id.navHelpRow).setOnClickListener(v -> closeDrawer());
        findViewById(R.id.navAboutRow).setOnClickListener(v -> closeDrawer());
        findViewById(R.id.navPopulateFirestoreRow).setOnClickListener(v -> { closeDrawer(); FirestorePopulator.populateFromAssets(this); });
    }

    private void refreshAccountState() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        boolean signedIn = user != null && !user.isAnonymous();
        navLogInRow.setVisibility(signedIn ? View.GONE : View.VISIBLE);
        navSignUpRow.setVisibility(signedIn ? View.GONE : View.VISIBLE);
        navLogOutRow.setVisibility(signedIn ? View.VISIBLE : View.GONE);
        if (signedIn) {
            navUserNameText.setText(user.getDisplayName() != null && !user.getDisplayName().isEmpty() ? user.getDisplayName() : getString(R.string.field_full_name));
            navUserEmailText.setText(user.getEmail());
            updateReportsCount(user.getUid());
        } else {
            navUserNameText.setText(R.string.nav_guest_title);
            navUserEmailText.setText(R.string.nav_guest_subtitle);
            findViewById(R.id.navMyReportsCount).setVisibility(View.GONE);
        }
    }

    private void updateReportsCount(String uid) {
        FirebaseFirestore.getInstance().collection("reports").whereEqualTo("userId", uid).get().addOnSuccessListener(snap -> {
            TextView countText = findViewById(R.id.navMyReportsCount);
            if (snap.size() > 0) { countText.setVisibility(View.VISIBLE); countText.setText(String.valueOf(snap.size())); }
            else countText.setVisibility(View.GONE);
        });
    }

    private void refreshLanguageIndicator() {
        String tag = prefs.getString(Prefs.KEY_UI_LANGUAGE, "");
        navLanguageValueText.setText(Languages.displayNameForTag(this, tag));
        navLanguageSwatch.setBackgroundTintList(ColorStateList.valueOf(Languages.colorForTag(this, tag)));
    }

    private void refreshGreeting() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String name = (user != null && user.getDisplayName() != null) ? user.getDisplayName().split(" ")[0] : getString(R.string.nav_guest_title);
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String prefix = hour < 12 ? getString(R.string.greeting_morning) : hour < 18 ? getString(R.string.greeting_afternoon) : getString(R.string.greeting_evening);
        // The header owns the title
        TextView title = findViewById(R.id.navProductTitle);
        if (title != null) title.setText(getString(R.string.app_name));
    }

    private void onAccountRowClicked() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.isAnonymous()) openAndCloseDrawer(LoginActivity.class);
        else openAndCloseDrawer(SettingsActivity.class);
    }

    private void confirmLogOut() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_log_out_title)
                .setMessage(R.string.dialog_log_out_message)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.dialog_log_out_confirm, (d, w) -> { FirebaseAuth.getInstance().signOut(); Toast.makeText(this, R.string.toast_logged_out, Toast.LENGTH_SHORT).show(); recreate(); })
                .show();
    }

    private void closeDrawer() { drawerLayout.closeDrawer(GravityCompat.START); }
    private void openAndCloseDrawer(Class<? extends BaseActivity> target) { closeDrawer(); startActivity(new Intent(this, target)); }
}
