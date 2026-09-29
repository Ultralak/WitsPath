package com.example.witspath.ui;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.witspath.R;
import com.example.witspath.model.EdgeUpdateListener;
import com.example.witspath.model.FloorPlanEdge;
import com.example.witspath.model.FloorPlanGraphConverter;
import com.example.witspath.model.FloorPlanNode;
import com.example.witspath.routing.CampusGraph;
import com.example.witspath.routing.Directions;
import com.example.witspath.routing.Floor;
import com.example.witspath.routing.Node;
import com.example.witspath.routing.PhraseBook;
import com.example.witspath.util.GraphStore;
import com.example.witspath.util.Languages;
import com.example.witspath.util.PathStatusChecker;
import com.example.witspath.util.PhraseBookLoader;
import com.example.witspath.util.Prefs;
import com.example.witspath.util.RoutePlanner;
import com.example.witspath.util.WifiPositionManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class NavigationActivity extends BaseActivity {

    private ZoomableFrameLayout zoomContainer;
    private FloorPlanRouteView routeView;
    private GraphOverlayView graphOverlay;
    private TextView fromNodeText;
    private TextView toNodeText;
    private TextView instructionText;
    private TextView noticeText;
    private ProgressBar progressBar;
    private RecyclerView stepsRecyclerView;
    private StepsAdapter stepsAdapter;

    private Prefs prefs;
    private CampusGraph graph;
    private Node toNode;
    private Node fromNode;
    private RoutePlanner.Plan plan;
    private PhraseBook phraseBook = PhraseBook.english();
    private int currentStepIndex = 0;
    private double metresPerPixel = 1.0;
    private WifiPositionManager wifiPositionManager;
    private ListenerRegistration edgeUpdates;

    // Notices shown under the route summary
    private String blockedNotice = "";
    private String uncheckedNotice = "";
    private String rerouteNotice = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigation);

        prefs = new Prefs(this);
        initViews();
        loadData();
    }

    private void initViews() {
        zoomContainer = findViewById(R.id.navigationMapContainer);
        routeView = findViewById(R.id.navigationFloorPlanRouteView);
        graphOverlay = findViewById(R.id.graph_overlay_view);
        ImageView imageView = findViewById(R.id.map_image_view);
        fromNodeText = findViewById(R.id.navFromNodeText);
        toNodeText = findViewById(R.id.navToNodeText);
        instructionText = findViewById(R.id.navigationInstructionText);
        noticeText = findViewById(R.id.navigationNoticeText);
        progressBar = findViewById(R.id.navigationProgressBar);
        stepsRecyclerView = findViewById(R.id.navigationStepsList);

        ViewCompat.setAccessibilityLiveRegion(instructionText, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE);

        findViewById(R.id.btnZoomIn).setOnClickListener(v -> zoomContainer.zoomIn());
        findViewById(R.id.btnZoomOut).setOnClickListener(v -> zoomContainer.zoomOut());
        findViewById(R.id.btnResetZoom).setOnClickListener(v -> zoomContainer.resetZoom());

        ((MaterialToolbar) findViewById(R.id.navigationToolbar)).setNavigationOnClickListener(v -> finish());
        findViewById(R.id.nextStepButton).setOnClickListener(v -> nextStep());
        findViewById(R.id.prevStepButton).setOnClickListener(v -> prevStep());

        stepsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    private void loadData() {
        String fromId = getIntent().getStringExtra("from_node");
        String toId = getIntent().getStringExtra("to_node");

        graph = GraphStore.get(this);
        fromNode = graph == null ? null : graph.node(fromId);
        toNode = graph == null ? null : graph.node(toId);

        if (fromNode == null || toNode == null) {
            Toast.makeText(this, R.string.route_invalid_locations, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        fromNodeText.setText(fromNode.displayName());
        toNodeText.setText(toNode.displayName());

        plan = RoutePlanner.plan(graph, fromNode, toNode, prefs, phraseBook);
        if (!plan.ok()) {
            Toast.makeText(this, getString(R.string.route_no_route_found, plan.error), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setupMap();
        setupStepsList();
        updateUI();
        initWifiSnapping();
        checkLivePathStatus();
        loadTranslatedDirections();

        edgeUpdates = new EdgeUpdateListener(this, this::onEdgesChanged).listenForEdgeUpdates();
    }

    /** Directions come from phrase templates; only phrases a native speaker verified are translated. */
    private void loadTranslatedDirections() {
        String tag = prefs.getString(Prefs.KEY_UI_LANGUAGE, "");
        if (tag.isEmpty()) return;
        PhraseBookLoader.load(tag, book -> {
            if (isFinishing() || isDestroyed()) return;
            phraseBook = book;
            RoutePlanner.Plan translated = RoutePlanner.plan(graph, plan.nodes.get(0), toNode, prefs, phraseBook);
            if (!translated.ok()) return;
            plan = translated;
            setupStepsList();
            updateUI();
        });
    }

    private void checkLivePathStatus() {
        blockedNotice = "";
        uncheckedNotice = "";
        PathStatusChecker.check(plan.nodes, new PathStatusChecker.Callback() {
            @Override
            public void onResult(List<String> blockedPlaces) {
                if (isFinishing() || isDestroyed()) return;
                if (!blockedPlaces.isEmpty()) {
                    blockedNotice = getString(R.string.route_blocked_notice, String.join(", ", blockedPlaces));
                }
                updateNotice();
            }

            @Override
            public void onUnavailable() {
                if (isFinishing() || isDestroyed()) return;
                uncheckedNotice = getString(R.string.route_status_unchecked);
                updateNotice();
            }
        });
    }

    /** A path on the route changed status: plan again from where the user is. */
    private void onEdgesChanged(Set<String> changedEdgeIds) {
        if (plan == null || !plan.ok()) return;
        boolean affectsRoute = false;
        for (String id : plan.edgeIds) {
            if (changedEdgeIds.contains(id)) {
                affectsRoute = true;
                break;
            }
        }
        if (!affectsRoute) return;

        Node here = plan.nodes.get(Math.min(currentStepIndex, plan.nodes.size() - 1));
        RoutePlanner.Plan replanned = RoutePlanner.plan(graph, here, toNode, prefs, phraseBook);
        if (!replanned.ok()) {
            blockedNotice = getString(R.string.route_cannot_confirm);
            updateNotice();
            return;
        }
        plan = replanned;
        fromNode = here;
        currentStepIndex = 0;
        rerouteNotice = getString(R.string.route_rerouted);
        setupMap();
        setupStepsList();
        updateUI();
        checkLivePathStatus();
    }

    private void updateNotice() {
        StringBuilder b = new StringBuilder(plan.directions.summaryText());
        String[] extras = {rerouteNotice, blockedNotice, uncheckedNotice, fallbackNotice()};
        for (String e : extras) {
            if (e != null && !e.isEmpty()) b.append('\n').append(e);
        }
        noticeText.setText(b.toString());
        noticeText.setVisibility(View.VISIBLE);
    }

    private String fallbackNotice() {
        if (!plan.directions.fallbackToEnglish()) return "";
        String tag = prefs.getString(Prefs.KEY_UI_LANGUAGE, "");
        return getString(R.string.route_directions_english_fallback, Languages.displayNameForTag(this, tag));
    }

    private void initWifiSnapping() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
            return;
        }
        if (wifiPositionManager != null) return;

        wifiPositionManager = new WifiPositionManager(this, graph.nodes(), this::onNodeSnapped);
        wifiPositionManager.start();
    }

    private void onNodeSnapped(Node node) {
        if (plan == null || !plan.ok()) return;

        int index = plan.nodes.indexOf(node);
        if (index != -1 && index != currentStepIndex) {
            currentStepIndex = index;
            runOnUiThread(this::updateUI);
            Toast.makeText(this, "Snapped to " + node.displayName(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (wifiPositionManager != null) {
            wifiPositionManager.stop();
        }
        if (edgeUpdates != null) {
            edgeUpdates.remove();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initWifiSnapping();
        }
    }

    private void setupMap() {
        String floorId = fromNode.floorId;
        Floor floor = graph.floor(floorId);
        this.metresPerPixel = (floor != null) ? floor.metresPerPixel : 1.0;

        List<FloorPlanNode> renderNodes = FloorPlanGraphConverter.toFloorPlanNodes(graph, floorId);
        List<FloorPlanEdge> renderEdges = FloorPlanGraphConverter.toFloorPlanEdges(graph, floorId);

        routeView.setGraph(renderNodes, renderEdges);
        graphOverlay.setData(renderNodes, renderEdges);

        List<String> routeIds = new ArrayList<>();
        for (Node n : plan.nodes) routeIds.add(n.nodeId);
        routeView.setHighlightedRoute(routeIds);
        graphOverlay.setRoute(routeIds, fromNode.nodeId, toNode.nodeId);

        routeView.setDestination(toNode.nodeId);
        if (floor != null) {
            zoomContainer.setContentSize(floor.imageWidth, floor.imageHeight);
            routeView.setFloorPlanSize(floor.imageWidth, floor.imageHeight);

            // Assume bitmap matches asset dimensions for simplicity, or fetch from ImageView
            // For now, setting JSON dimensions as reference
            graphOverlay.setupTransform(floor.imageWidth, floor.imageHeight, 647, 717);
        }
    }

    private void setupStepsList() {
        stepsAdapter = new StepsAdapter(this, plan.nodes, plan.directions);
        stepsRecyclerView.setAdapter(stepsAdapter);
    }

    private void updateUI() {
        if (plan == null || currentStepIndex >= plan.nodes.size()) return;

        Node currentNode = plan.nodes.get(currentStepIndex);
        routeView.setCurrentPosition(currentNode.nodeId);

        // Center map on current node
        if (currentNode.point != null) {
            float pxX = (float) (currentNode.point.x / metresPerPixel);
            float pxY = (float) (currentNode.point.y / metresPerPixel);
            zoomContainer.panTo(pxX, pxY);
        }

        instructionText.setText(plan.directions.instructionAt(currentStepIndex));
        updateNotice();

        int progress = (int) (((float) currentStepIndex / (plan.nodes.size() - 1)) * 100);
        progressBar.setProgress(progress);

        stepsAdapter.setCurrentIndex(currentStepIndex);
        stepsRecyclerView.scrollToPosition(currentStepIndex);
    }

    private void nextStep() {
        if (currentStepIndex < plan.nodes.size() - 1) {
            currentStepIndex++;
            updateUI();
        } else {
            Toast.makeText(this, R.string.route_destination_reached, Toast.LENGTH_SHORT).show();
        }
    }

    private void prevStep() {
        if (currentStepIndex > 0) {
            currentStepIndex--;
            updateUI();
        }
    }

    private static class StepsAdapter extends RecyclerView.Adapter<StepsAdapter.ViewHolder> {
        private final Context context;
        private final List<Node> steps;
        private final Directions directions;
        private int currentIndex = 0;

        StepsAdapter(Context context, List<Node> steps, Directions directions) {
            this.context = context;
            this.steps = steps;
            this.directions = directions;
        }

        void setCurrentIndex(int index) {
            this.currentIndex = index;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_route_option, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Node node = steps.get(position);
            holder.labelText.setText(node.displayName());
            holder.distanceText.setText(directions.instructionAt(position));

            // Highlighting
            if (position == currentIndex) {
                holder.stripe.setBackgroundColor(ContextCompat.getColor(context, R.color.colorAccent));
                holder.card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.colorPlateRaised));
            } else if (position < currentIndex) {
                holder.stripe.setBackgroundColor(ContextCompat.getColor(context, R.color.colorInkTextMuted));
                holder.card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.colorPlate));
                holder.card.setAlpha(0.6f);
            } else {
                holder.stripe.setBackgroundColor(ContextCompat.getColor(context, R.color.colorRoute));
                holder.card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.colorPlate));
                holder.card.setAlpha(1.0f);
            }
        }

        @Override
        public int getItemCount() {
            return steps.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            MaterialCardView card;
            View stripe;
            TextView labelText;
            TextView distanceText;

            ViewHolder(View itemView) {
                super(itemView);
                card = itemView.findViewById(R.id.routeCard);
                stripe = itemView.findViewById(R.id.routeStripe);
                labelText = itemView.findViewById(R.id.routeLabelText);
                distanceText = itemView.findViewById(R.id.routeDistanceText);
            }
        }
    }
}
