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
import java.util.List;

public class FirestorePopulator {

    private static final String TAG = "FirestorePopulator";

    public static void populateFromAssets(Context context) {
        try {
            String jsonString = loadJSONFromAsset(context, "graph_data.json");
            JSONObject jsonObject = new JSONObject(jsonString);

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
                        nodeObj.optString("label", null)
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
                        edgeObj.optBoolean("elevator", false)
                );
                edgeDTOs.add(edge);
            }

            uploadToFirestore(context, nodeDTOs, edgeDTOs);

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

    private static void uploadToFirestore(Context context, List<NodeDTO> nodes, List<EdgeDTO> edges) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        WriteBatch batch = db.batch();

        for (NodeDTO node : nodes) {
            batch.set(db.collection("nodes").document(node.getNodeId()), node);
        }

        for (EdgeDTO edge : edges) {
            batch.set(db.collection("edges").document(edge.getEdgeId()), edge);
        }

        batch.commit().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "Firestore population successful!");
                Toast.makeText(context, "Graph uploaded successfully!", Toast.LENGTH_SHORT).show();
                FirestoreGraphConverter.buildGraphFromDTOs(nodes, edges);
            } else {
                Log.e(TAG, "Firestore population failed", task.getException());
                Toast.makeText(context, "Upload failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }
}
