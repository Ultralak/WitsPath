package com.example.witspath.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import android.widget.ImageView;
import com.example.witspath.R;
import com.example.witspath.model.Edge;
import com.example.witspath.model.Floor;
import com.example.witspath.model.FloorPlanEdge;
import com.example.witspath.model.FloorPlanGraphConverter;
import com.example.witspath.model.FloorPlanNode;
import com.example.witspath.model.Graph;
import com.example.witspath.model.Node;
import com.example.witspath.model.PathFinder;
import com.example.witspath.util.WifiPositionManager;

import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class NavigationActivity extends BaseActivity {

    private ZoomableFrameLayout zoomContainer;
    private FloorPlanRouteView routeView;
    private GraphOverlayView graphOverlay;
    private TextView fromNodeText;
    private TextView toNodeText;
    private TextView instructionText;
    private ProgressBar progressBar;
    private RecyclerView stepsRecyclerView;
    private StepsAdapter stepsAdapter;

    private LinkedList<Node> routeNodes;
    private int currentStepIndex = 0;
    private double metresPerPixel = 1.0;
    private WifiPositionManager wifiPositionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigation);

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
        progressBar = findViewById(R.id.navigationProgressBar);
        stepsRecyclerView = findViewById(R.id.navigationStepsList);

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

        if (Node.searchByName("").isEmpty()) {
            Graph.loadFromAssets(this, "graph_data.json");
        }

        Node fromNode = Node.getByID(fromId);
        Node toNode = Node.getByID(toId);

        if (fromNode == null || toNode == null) {
            Toast.makeText(this, "Invalid locations", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        fromNodeText.setText(fromNode.label != null ? fromNode.label : fromNode.nodeId);
        toNodeText.setText(toNode.label != null ? toNode.label : toNode.nodeId);

        PathFinder pathFinder = new PathFinder();
        routeNodes = pathFinder.aStarSearch(fromNode, toNode, false);

        if (routeNodes == null || routeNodes.isEmpty()) {
            Toast.makeText(this, "No route found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupMap(fromNode, toNode);
        setupStepsList();
        updateUI();
        initWifiSnapping();
    }

    private void initWifiSnapping() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
            return;
        }

        Collection<Node> allNodes = Node.searchByName("");
        wifiPositionManager = new WifiPositionManager(this, allNodes, this::onNodeSnapped);
        wifiPositionManager.start();
    }

    private void onNodeSnapped(Node node) {
        if (routeNodes == null) return;
        
        int index = routeNodes.indexOf(node);
        if (index != -1 && index != currentStepIndex) {
            currentStepIndex = index;
            runOnUiThread(this::updateUI);
            Toast.makeText(this, "Snapped to " + (node.label != null ? node.label : node.nodeId), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (wifiPositionManager != null) {
            wifiPositionManager.stop();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initWifiSnapping();
        }
    }

    private void setupMap(Node fromNode, Node toNode) {
        String floorId = fromNode.floorId;
        Floor floor = Floor.getById(floorId);
        this.metresPerPixel = (floor != null) ? floor.metresPerPixel : 1.0;

        Collection<Node> allNodes = Node.searchByName("");
        List<FloorPlanNode> renderNodes = FloorPlanGraphConverter.toFloorPlanNodes(allNodes, floorId, metresPerPixel);
        
        List<Edge> allEdges = new ArrayList<>();
        for (Node node : allNodes) {
            for (Edge edge : node.edges) {
                if (!allEdges.contains(edge)) allEdges.add(edge);
            }
        }
        List<FloorPlanEdge> renderEdges = FloorPlanGraphConverter.toFloorPlanEdges(allEdges);

        routeView.setGraph(renderNodes, renderEdges);
        graphOverlay.setData(renderNodes, renderEdges);
        
        List<String> routeIds = new ArrayList<>();
        for (Node n : routeNodes) routeIds.add(n.nodeId);
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
        stepsAdapter = new StepsAdapter(this, routeNodes);
        stepsRecyclerView.setAdapter(stepsAdapter);
    }

    private void updateUI() {
        if (routeNodes == null || currentStepIndex >= routeNodes.size()) return;

        Node currentNode = routeNodes.get(currentStepIndex);
        routeView.setCurrentPosition(currentNode.nodeId);

        // Center map on current node
        if (currentNode.point != null) {
            float pxX = (float) (currentNode.point.x / metresPerPixel);
            float pxY = (float) (currentNode.point.y / metresPerPixel);
            zoomContainer.panTo(pxX, pxY);
        }

        // Update instruction
        if (currentStepIndex == routeNodes.size() - 1) {
            instructionText.setText("You have arrived at " + (currentNode.label != null ? currentNode.label : currentNode.nodeId));
        } else {
            Node nextNode = routeNodes.get(currentStepIndex + 1);
            double dist = calculateDistance(currentNode, nextNode);
            instructionText.setText("Walk " + (int)dist + "m towards " + (nextNode.label != null ? nextNode.label : nextNode.nodeId));
        }

        // Update progress
        int progress = (int) (((float) currentStepIndex / (routeNodes.size() - 1)) * 100);
        progressBar.setProgress(progress);

        // Update steps list highlighting
        stepsAdapter.setCurrentIndex(currentStepIndex);
        stepsRecyclerView.scrollToPosition(currentStepIndex);
    }

    private void nextStep() {
        if (currentStepIndex < routeNodes.size() - 1) {
            currentStepIndex++;
            updateUI();
        } else {
            Toast.makeText(this, "Destination reached", Toast.LENGTH_SHORT).show();
        }
    }

    private void prevStep() {
        if (currentStepIndex > 0) {
            currentStepIndex--;
            updateUI();
        }
    }

    private double calculateDistance(Node a, Node b) {
        if (a.point == null || b.point == null) return 0;
        double dx = a.point.x - b.point.x;
        double dy = a.point.y - b.point.y;
        return Math.sqrt(dx * dx + dy * dy); // Simplified distance
    }

    private static class StepsAdapter extends RecyclerView.Adapter<StepsAdapter.ViewHolder> {
        private final Context context;
        private final List<Node> steps;
        private int currentIndex = 0;

        StepsAdapter(Context context, List<Node> steps) {
            this.context = context;
            this.steps = steps;
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
            holder.labelText.setText(node.label != null ? node.label : node.nodeId);
            
            if (position == steps.size() - 1) {
                holder.distanceText.setText("Arrival");
            } else {
                Node nextNode = steps.get(position + 1);
                double dist = ((NavigationActivity)context).calculateDistance(node, nextNode);
                holder.distanceText.setText((int)dist + "m to next");
            }

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
