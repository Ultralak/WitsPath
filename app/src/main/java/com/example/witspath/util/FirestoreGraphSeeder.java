package com.example.witspath.util;

import android.content.Context;
import android.util.Log;

import com.example.witspath.model.NodeDTO;
import com.google.firebase.firestore.DocumentSnapshot;
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

public class FirestoreGraphSeeder {

    private static final String TAG = "FirestoreGraphSeeder";

    public interface OnSeederCompleteListener {
        void onSuccess();
        void onError(Exception e);
    }

    public static void seedDatabase(Context context, OnSeederCompleteListener listener) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("nodes").get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                if (listener != null) listener.onError(task.getException());
                return;
            }

            List<DocumentSnapshot> documents = task.getResult().getDocuments();
            int oldNodeCount = documents.size();
            WriteBatch deleteBatch = db.batch();

            for (DocumentSnapshot doc : documents) {
                deleteBatch.delete(doc.getReference());
            }

            deleteBatch.commit().addOnCompleteListener(deleteTask -> {
                if (!deleteTask.isSuccessful()) {
                    Log.e(TAG, "Failed to delete old nodes", deleteTask.getException());
                    if (listener != null) listener.onError(deleteTask.getException());
                    return;
                }

                Log.d(TAG, "Successfully deleted " + oldNodeCount + " stale nodes.");
                performImport(context, db, listener);
            });
        });
    }

    private static void performImport(Context context, FirebaseFirestore db, OnSeederCompleteListener listener) {
        try {
            String jsonString = loadJSONFromAsset(context, "graph_data.json");
            JSONObject jsonObject = new JSONObject(jsonString);
            JSONArray nodesArray = jsonObject.getJSONArray("nodes");
            
            WriteBatch importBatch = db.batch();
            int newNodeCount = nodesArray.length();

            for (int i = 0; i < nodesArray.length(); i++) {
                JSONObject nodeObj = nodesArray.getJSONObject(i);
                NodeDTO node = new NodeDTO(
                        nodeObj.getString("nodeId"),
                        nodeObj.optString("floorId", ""),
                        nodeObj.optString("type", "junction"),
                        nodeObj.getDouble("x"),
                        nodeObj.getDouble("y"),
                        nodeObj.optString("label", ""),
                        nodeObj.optString("bssid", "")
                );
                importBatch.set(db.collection("nodes").document(node.getNodeId()), node);
            }

            importBatch.commit().addOnCompleteListener(importTask -> {
                if (importTask.isSuccessful()) {
                    Log.d(TAG, "Successfully imported " + newNodeCount + " nodes.");
                    if (listener != null) listener.onSuccess();
                } else {
                    Log.e(TAG, "Failed to import new nodes", importTask.getException());
                    if (listener != null) listener.onError(importTask.getException());
                }
            });

        } catch (IOException | JSONException e) {
            Log.e(TAG, "Error parsing wavelets-graph.json", e);
            if (listener != null) listener.onError(e);
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
}
