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
import android.widget.ScrollView;
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
import com.example.witspath.routing.PlaceResolver;
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
    private int currentStepIndex = 0;
    private Integer pendingTtsStepIndex = null;
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

        if (savedInstanceState != null) {
            selectedFromNodeId = savedInstanceState.getString("selectedFromNodeId");
            selectedDestinationId = savedInstanceState.getString("selectedDestinationId");
            mobilityProfile = savedInstanceState.getString("mobilityProfile", "wheelchair");
            navigationStarted = savedInstanceState.getBoolean("navigationStarted", false);
            currentStepIndex = savedInstanceState.getInt("currentStepIndex", 0);
        }

        bindDrawerViews();
        bindDrawerClicks();
        bindMainContent();
        loadGraphAndMap();
        handleNavigationIntent(getIntent());

        if (navigationStarted && currentRoute != null && currentRoute.size() >= 2 && currentPlan != null) {
            TextView button = findViewById(R.id.navigateButton);
            if (button != null) button.setText(R.string.web_end_navigation);
            renderSteps(currentRoute);
            guidanceText.setText(getString(R.string.web_total_route_length, Math.round(currentPlan.distanceMetres)));
            showStatus(getString(R.string.web_navigation_started), false);
            View navControls = findViewById(R.id.navStepControls);
            if (navControls != null) navControls.setVisibility(View.VISIBLE);
            highlightCurrentStep();
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
        boolean hasFrom = fromNode != null && !fromNode.isEmpty();
        boolean hasTo = toNode != null && !toNode.isEmpty();
        if (!hasFrom && !hasTo) return;

        // The ids may come from another copy of the map (the companion's backend). Never swap in a different
        // place if one cannot be found: say so and leave the current route alone.
        Node from = hasFrom ? PlaceResolver.resolve(graph, fromNode, intent.getStringExtra("from_label")) : null;
        Node to = hasTo ? PlaceResolver.resolve(graph, toNode, intent.getStringExtra("to_label")) : null;
        if ((hasFrom && from == null) || (hasTo && to == null)) {
            showStatus(getString(R.string.route_unknown_places), true);
            return;
        }

        if (from != null) selectedFromNodeId = from.nodeId;
        if (to != null) selectedDestinationId = to.nodeId;
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

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("selectedFromNodeId", selectedFromNodeId);
        outState.putString("selectedDestinationId", selectedDestinationId);
        outState.putString("mobilityProfile", mobilityProfile);
        outState.putBoolean("navigationStarted", navigationStarted);
        outState.putInt("currentStepIndex", currentStepIndex);
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

        bindMode(R.id.modeWheelchair, Mode.WHEELCHAIR);
        bindMode(R.id.modeWalkingAid, Mode.WALKING_AID);
        bindMode(R.id.modeVisual, Mode.VISUAL);
        bindMode(R.id.modeGeneral, Mode.GENERAL);

        findViewById(R.id.navigateButton).setOnClickListener(v -> toggleNavigation());

        View btnPrev = findViewById(R.id.btnPrevStep);
        if (btnPrev != null) {
            btnPrev.setOnClickListener(v -> {
                if (currentStepIndex > 0) {
                    currentStepIndex--;
                    highlightCurrentStep();
                    speakStep(currentStepIndex);
                }
            });
        }
        View btnNext = findViewById(R.id.btnNextStep);
        if (btnNext != null) {
            btnNext.setOnClickListener(v -> {
                List<String> stepTexts = buildStepTexts(currentRoute);
                if (currentStepIndex < stepTexts.size() - 1) {
                    currentStepIndex++;
                    highlightCurrentStep();
                    speakStep(currentStepIndex);
                }
            });
        }
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
        selectableNodes.removeIf(node -> "ramp".equals(node.type) || "node".equals(node.type) || "stairs".equals(node.type));
        Collections.sort(selectableNodes, Comparator.comparing(Node::displayName, String.CASE_INSENSITIVE_ORDER));

        List<String> labels = new ArrayList<>();
        for (Node node : selectableNodes) labels.add(node.displayName());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fromSpinner.setAdapter(adapter);
        toSpinner.setAdapter(adapter);

        if (mobilityProfile == null) {
            String storedProfile = prefs.getString(Prefs.KEY_MOBILITY_PROFILE, "wheelchair");
            mobilityProfile = storedProfile == null || storedProfile.isEmpty() ? "wheelchair" : storedProfile;
        }
        updateModeSelection(modeIdForProfile(mobilityProfile));

        if (selectedFromNodeId == null) {
            String defaultFrom = prefs.getString(Prefs.KEY_HOME_NODE_ID, "nd_muosnjmh6h");
            selectedFromNodeId = findNodeOrFirst(defaultFrom);
        } else {
            selectedFromNodeId = findNodeOrFirst(selectedFromNodeId);
        }

        if (selectedDestinationId == null) {
            String defaultTo = "nd_muosnjmh6r";
            selectedDestinationId = findNodeOrSecond(defaultTo);
        } else {
            selectedDestinationId = findNodeOrSecond(selectedDestinationId);
        }
        restoreSpinnerSelections();

        fromSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < selectableNodes.size()) {
                    String newId = selectableNodes.get(position).nodeId;
                    if (!newId.equals(selectedFromNodeId)) {
                        selectedFromNodeId = newId;
                        currentStepIndex = 0;
                        requestRoute(false);
                    }
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        toSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < selectableNodes.size()) {
                    String newId = selectableNodes.get(position).nodeId;
                    if (!newId.equals(selectedDestinationId)) {
                        selectedDestinationId = newId;
                        currentStepIndex = 0;
                        requestRoute(false);
                    }
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        routeView = findViewById(R.id.homeFloorPlanRouteView);
        zoomContainer = findViewById(R.id.homeMapContainer);

        findViewById(R.id.btnZoomIn).setOnClickListener(v -> zoomContainer.zoomIn());
        findViewById(R.id.btnZoomOut).setOnClickListener(v -> zoomContainer.zoomOut());
        findViewById(R.id.btnResetZoom).setOnClickListener(v -> zoomContainer.resetZoom());

        Floor floor = null;
        if (graph != null && !graph.floors().isEmpty()) {
            floor = graph.floors().get(0);
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
        if (navigationStarted) {
            currentStepIndex = 0;
            renderSteps(currentRoute);
        } else {
            stepsContainer.setVisibility(View.GONE);
            View navControls = findViewById(R.id.navStepControls);
            if (navControls != null) navControls.setVisibility(View.GONE);
        }
    }

    private double calculateDistance(Node a, Node b) {
        if (a.point == null || b.point == null) return 0;
        return Math.hypot(a.point.x - b.point.x, a.point.y - b.point.y);
    }

    private List<Node> namedStops(List<Node> route) {
        List<Node> stops = new ArrayList<>();
        if (route == null || route.isEmpty()) return stops;
        for (int i = 0; i < route.size(); i++) {
            Node n = route.get(i);
            if (i == 0 || i == route.size() - 1 || !"node".equals(n.type)) {
                if (!stops.contains(n)) {
                    stops.add(n);
                }
            }
        }
        return stops;
    }

    private double routeDistance(List<Node> route, int fromIndex, int toIndex) {
        if (route == null || fromIndex < 0 || toIndex < 0) return 0;
        double dist = 0;
        for (int i = fromIndex; i < toIndex && i + 1 < route.size(); i++) {
            dist += calculateDistance(route.get(i), route.get(i + 1));
        }
        return dist;
    }

    private String formatDistance(double metres) {
        String units = prefs.getString(Prefs.KEY_UNITS, "meters");
        if ("feet".equalsIgnoreCase(units)) return Math.round(metres * 3.28084) + " ft";
        if ("minutes".equalsIgnoreCase(units)) return Math.max(1, Math.round(metres / TravelTimeConfig.speedFor(mobilityProfile) / 60)) + " min";
        return Math.round(metres) + " m";
    }

    private List<String> buildStepTexts(List<Node> route) {
        List<String> stepTexts = new ArrayList<>();
        if (route == null || route.isEmpty()) return stepTexts;
        List<Node> stops = namedStops(route);
        for (int i = 0; i < stops.size(); i++) {
            Node node = stops.get(i);
            String title;
            String subtitle;
            if (i == stops.size() - 1) {
                title = getString(R.string.web_arrive_at, safeLabel(node));
                subtitle = getString(R.string.web_main_entrance);
            } else {
                Node nextStop = stops.get(i + 1);
                double distance = routeDistance(route, route.indexOf(node), route.indexOf(nextStop));
                title = getString(R.string.web_continue_for, Math.round(distance));
                subtitle = getString(R.string.web_follow_toward, safeLabel(nextStop));
            }
            stepTexts.add(title + " " + subtitle);
        }
        return stepTexts;
    }

    private void highlightCurrentStep() {
        int count = stepsContainer.getChildCount();
        if (currentStepIndex < 0) currentStepIndex = 0;
        if (count > 0 && currentStepIndex >= count) currentStepIndex = count - 1;

        for (int i = 0; i < count; i++) {
            View child = stepsContainer.getChildAt(i);
            boolean isCurrent = (i == currentStepIndex);
            child.setSelected(isCurrent);
            child.setBackgroundResource(isCurrent ? R.drawable.bg_mode_selected : 0);
        }

        if (count > 0 && currentStepIndex < count) {
            View target = stepsContainer.getChildAt(currentStepIndex);
            if (target != null) {
                View phoneScroll = findViewById(R.id.homePhoneScroll);
                if (phoneScroll instanceof ScrollView) {
                    ((ScrollView) phoneScroll).smoothScrollTo(0, target.getTop() + stepsContainer.getTop());
                } else {
                    View routeScroll = findViewById(R.id.homeRouteScroll);
                    if (routeScroll instanceof ScrollView) {
                        ((ScrollView) routeScroll).smoothScrollTo(0, target.getTop() + stepsContainer.getTop());
                    }
                }
            }
        }

        View navControls = findViewById(R.id.navStepControls);
        if (navControls != null && navigationStarted) {
            navControls.setVisibility(View.VISIBLE);
            View btnPrev = findViewById(R.id.btnPrevStep);
            View btnNext = findViewById(R.id.btnNextStep);
            if (btnPrev != null) {
                boolean enablePrev = currentStepIndex > 0;
                btnPrev.setEnabled(enablePrev);
                btnPrev.setAlpha(enablePrev ? 1.0f : 0.4f);
            }
            if (btnNext != null) {
                boolean enableNext = currentStepIndex < count - 1;
                btnNext.setEnabled(enableNext);
                btnNext.setAlpha(enableNext ? 1.0f : 0.4f);
            }
        }
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
        View navControls = findViewById(R.id.navStepControls);
        if (navigationStarted) {
            currentStepIndex = 0;
            renderSteps(currentRoute);
            guidanceText.setText(getString(R.string.web_total_route_length, Math.round(currentPlan.distanceMetres)));
            showStatus(getString(R.string.web_navigation_started), false);
            if (navControls != null) navControls.setVisibility(View.VISIBLE);
            highlightCurrentStep();
            speakStep(0);

            if (routeView != null && selectedFromNodeId != null && selectedDestinationId != null) {
                List<String> ids = new ArrayList<>();
                for (Node n : currentRoute) ids.add(n.nodeId);
                routeView.setHighlightedRoute(ids);
                routeView.setCurrentPosition(selectedFromNodeId);
                routeView.setDestination(selectedDestinationId);
            }
        } else {
            if (navTts != null) navTts.stop();
            if (navControls != null) navControls.setVisibility(View.GONE);
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
                    if (navigationStarted && pendingTtsStepIndex != null) {
                        int idx = pendingTtsStepIndex;
                        pendingTtsStepIndex = null;
                        speakStep(idx);
                    }
                }
            });
        }
    }

    private void speakStep(int index) {
        if (!prefs.getBoolean(Prefs.KEY_VOICE_GUIDANCE, true)) return;
        List<String> stepTexts = buildStepTexts(currentRoute);
        if (stepTexts.isEmpty() || index < 0 || index >= stepTexts.size()) return;
        if (!navTtsReady) {
            pendingTtsStepIndex = index;
            initNavTts();
            return;
        }
        String text = stepTexts.get(index);
        if (navTts != null) {
            navTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav_step_" + System.currentTimeMillis());
        }
    }

    private void speakCurrentStep() {
        speakStep(currentStepIndex);
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
        List<Node> stops = namedStops(route);
        for (int i = 0; i < stops.size(); i++) {
            Node node = stops.get(i);
            View stepView = getLayoutInflater().inflate(R.layout.item_route_step, stepsContainer, false);
            TextView numberText = stepView.findViewById(R.id.stepNumberText);
            ImageView iconView = stepView.findViewById(R.id.stepIcon);
            TextView titleText = stepView.findViewById(R.id.stepTitleText);
            TextView subtitleText = stepView.findViewById(R.id.stepSubtitleText);

            numberText.setText(String.valueOf(i + 1));

            if (i == stops.size() - 1) {
                iconView.setImageResource(R.drawable.ic_flag);
                titleText.setText(getString(R.string.web_arrive_at, safeLabel(node)));
                subtitleText.setText(R.string.web_main_entrance);
            } else {
                Node nextStop = stops.get(i + 1);
                double distance = routeDistance(route, route.indexOf(node), route.indexOf(nextStop));
                iconView.setImageResource(R.drawable.ic_next);
                titleText.setText(getString(R.string.web_continue_for, Math.round(distance)));
                subtitleText.setText(getString(R.string.web_follow_toward, safeLabel(nextStop)));
            }
            stepsContainer.addView(stepView);
        }
        stepsContainer.setVisibility(View.VISIBLE);
        highlightCurrentStep();
    }

    private String safeLabel(Node node) { return node.displayName(); }

    private void showStatus(String message, boolean error) {
        statusMessage.setText(message);
        statusMessage.setTextColor(getColor(error ? R.color.colorFlag : R.color.colorSuccess));
    }

    private void autoDetectLocation() {
        String homeNode = prefs.getString(Prefs.KEY_HOME_NODE_ID, "nd_muosnjmh6h");
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
