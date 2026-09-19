package com.example.witspath.util;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.witspath.model.Node;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class WifiPositionManager {

    private static final String TAG = "WifiPositionManager";
    private static final long SCAN_INTERVAL_MS = 10000; // 10 seconds to avoid aggressive throttling

    public interface OnPositionDetectedListener {
        void onNodeDetected(Node node);
    }

    private final Context context;
    private final WifiManager wifiManager;
    private final OnPositionDetectedListener listener;
    private final List<Node> nodesWithBssid;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isScanning = false;

    public WifiPositionManager(Context context, Collection<Node> allNodes, OnPositionDetectedListener listener) {
        this.context = context;
        this.wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        this.listener = listener;
        this.nodesWithBssid = new ArrayList<>();
        
        for (Node n : allNodes) {
            if (n.bssid != null && !n.bssid.isEmpty()) {
                nodesWithBssid.add(n);
            }
        }
    }

    private final BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
            if (success) {
                processScanResults();
            }
        }
    };

    public void start() {
        if (isScanning) return;
        isScanning = true;
        
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        context.registerReceiver(wifiScanReceiver, intentFilter);
        
        scheduleNextScan();
    }

    public void stop() {
        isScanning = false;
        handler.removeCallbacksAndMessages(null);
        try {
            context.unregisterReceiver(wifiScanReceiver);
        } catch (Exception e) {
            // Ignore
        }
    }

    private void scheduleNextScan() {
        if (!isScanning) return;
        
        try {
            wifiManager.startScan();
        } catch (Exception e) {
            Log.e(TAG, "Scan trigger failed", e);
        }
        
        handler.postDelayed(this::scheduleNextScan, SCAN_INTERVAL_MS);
    }

    @SuppressLint("MissingPermission")
    private void processScanResults() {
        List<ScanResult> results = wifiManager.getScanResults();
        if (results == null || results.isEmpty()) return;

        ScanResult bestMatch = null;
        Node matchedNode = null;

        for (ScanResult result : results) {
            String bssid = result.BSSID;
            for (Node node : nodesWithBssid) {
                if (node.bssid.equalsIgnoreCase(bssid)) {
                    if (bestMatch == null || result.level > bestMatch.level) {
                        bestMatch = result;
                        matchedNode = node;
                    }
                }
            }
        }

        if (matchedNode != null) {
            Log.d(TAG, "Detected position: " + matchedNode.label + " (RSSI: " + bestMatch.level + ")");
            listener.onNodeDetected(matchedNode);
        }
    }
}
