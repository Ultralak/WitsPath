package com.example.witspath.util;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.example.witspath.model.EdgeDTO;
import com.example.witspath.model.FirestoreGraphConverter;
import com.example.witspath.model.NodeDTO;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class FirestorePopulator {

    private static final String TAG = "FirestorePopulator";

    private interface WriteOp {
        void apply(WriteBatch batch, FirebaseFirestore db);
    }

    public static void populateFromAssets(Context context) {
        try {
            String jsonString = loadJSONFromAsset(context, "graph_data.json");
            JSONObject jsonObject = new JSONObject(jsonString);

            JSONArray floorsArray = jsonObject.optJSONArray("floors");
            List<Map<String, Object>> floorMaps = new ArrayList<>();
            if (floorsArray != null) {
                for (int i = 0; i < floorsArray.length(); i++) {
                    JSONObject floorObj = floorsArray.getJSONObject(i);
                    Map<String, Object> map = new HashMap<>();
                    Iterator<String> keys = floorObj.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        Object val = floorObj.get(key);
                        if (val != JSONObject.NULL) {
                            map.put(key, val);
                        }
                    }
                    floorMaps.add(map);
                }
            }

            JSONArray nodesArray = jsonObject.getJSONArray("nodes");
            List<NodeDTO> nodeDTOs = new ArrayList<>();
            for (int i = 0; i < nodesArray.length(); i++) {
                JSONObject nodeObj = nodesArray.getJSONObject(i);
                NodeDTO node = new NodeDTO(
                        nodeObj.getString("nodeId"),
                        nodeObj.optString("floorId", null),
                        nodeObj.optString("type", "CLASSROOM"),
                        nodeObj.getDouble("x"),
                        nodeObj.getDouble("y"),
                        nodeObj.optString("label", null),
                        nodeObj.optString("bssid", "")
                );
                nodeDTOs.add(node);
            }

            JSONArray edgesArray = jsonObject.getJSONArray("edges");
            List<EdgeDTO> edgeDTOs = new ArrayList<>();
            for (int i = 0; i < edgesArray.length(); i++) {
                JSONObject edgeObj = edgesArray.getJSONObject(i);
                EdgeDTO edge = new EdgeDTO(
                        edgeObj.getString("edgeId"),
                        edgeObj.getString("fromNodeId"),
                        edgeObj.getString("toNodeId"),
                        edgeObj.getDouble("distance"),
                        edgeObj.optDouble("accessibilityCost", 1.0),
                        edgeObj.optString("status", "ok"),
                        edgeObj.optBoolean("ramp", false),
                        edgeObj.optBoolean("stairs", false),
                        edgeObj.optBoolean("elevator", false),
                        edgeObj.optString("label", "")
                );
                edgeDTOs.add(edge);
            }

            uploadToFirestore(context, floorMaps, nodeDTOs, edgeDTOs);

        } catch (IOException | JSONException e) {
            Log.e(TAG, "Error parsing JSON", e);
            Toast.makeText(context, "Population failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static String loadJSONFromAsset(Context context, String fileName) throws IOException {
        InputStream is = context.getAssets().open(fileName);
        int size = is.available();
        byte[] buffer = new byte[size];
        is.read(buffer);
        is.close();
        return new String(buffer, StandardCharsets.UTF_8);
    }

    private static void uploadToFirestore(Context context, List<Map<String, Object>> floors, List<NodeDTO> nodes, List<EdgeDTO> edges) {
        List<WriteOp> ops = new ArrayList<>();

        for (Map<String, Object> floorMap : floors) {
            String floorId = (String) floorMap.get("floorId");
            if (floorId != null) {
                ops.add((batch, db) -> batch.set(db.collection("floors").document(floorId), floorMap));
            }
        }

        for (NodeDTO node : nodes) {
            ops.add((batch, db) -> batch.set(db.collection("nodes").document(node.getNodeId()), node));
        }

        for (EdgeDTO edge : edges) {
            ops.add((batch, db) -> batch.set(db.collection("edges").document(edge.getEdgeId()), edge));
        }

        List<List<WriteOp>> batches = new ArrayList<>();
        int batchSize = 400;
        for (int i = 0; i < ops.size(); i += batchSize) {
            batches.add(ops.subList(i, Math.min(i + batchSize, ops.size())));
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        commitBatchesSequentially(context, db, batches, 0);
    }

    private static void commitBatchesSequentially(Context context, FirebaseFirestore db, List<List<WriteOp>> batches, int index) {
        if (index >= batches.size()) {
            Log.d(TAG, "Firestore population successful!");
            Toast.makeText(context, "Graph uploaded successfully!", Toast.LENGTH_SHORT).show();
            FirestoreGraphConverter.fetchGraphFromFirestore(context);
            return;
        }

        WriteBatch batch = db.batch();
        for (WriteOp op : batches.get(index)) {
            op.apply(batch, db);
        }

        batch.commit().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                commitBatchesSequentially(context, db, batches, index + 1);
            } else {
                Exception e = task.getException();
                Log.e(TAG, "Firestore population failed at batch " + index, e);
                String msg = e != null ? e.getMessage() : "Unknown error";
                Toast.makeText(context, "Upload failed: " + msg, Toast.LENGTH_LONG).show();
            }
        });
    }
}
