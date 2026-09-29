package com.example.witspath.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.witspath.ui.BaseActivity;

import com.example.witspath.R;
import com.example.witspath.routing.CampusGraph;
import com.example.witspath.routing.Node;
import com.example.witspath.util.GraphStore;
import java.util.Locale;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RoomPickerActivity extends BaseActivity {

    private LinearLayout container;
    private EditText searchInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room_picker);

        container = findViewById(R.id.roomListContainer);
        searchInput = findViewById(R.id.roomSearchInput);
        
        MaterialToolbar toolbar = findViewById(R.id.roomPickerToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                populateNodes(s.toString());
            }
        });

        populateNodes("");
    }

    private void populateNodes(String query) {
        container.removeAllViews();
        
        List<Node> filteredNodes = new ArrayList<>();
        CampusGraph graph = GraphStore.get(this);
        String q = query.toLowerCase(Locale.ROOT);
        if (graph != null) {
            for (Node node : graph.nodes()) {
                if (node.label.toLowerCase(Locale.ROOT).contains(q)
                        || node.nodeId.toLowerCase(Locale.ROOT).contains(q)) {
                    filteredNodes.add(node);
                }
            }
        }

        // Sort by label for user convenience
        Collections.sort(filteredNodes, (n1, n2) -> n1.displayName().compareToIgnoreCase(n2.displayName()));

        for (Node node : filteredNodes) {
            if ("ramp".equals(node.type)) {
                continue;
            }
            String displayLabel = node.displayName();

            View row = getLayoutInflater().inflate(R.layout.item_room, container, false);
            TextView nameText = row.findViewById(R.id.roomNameText);
            //TextView detailText = row.findViewById(R.id.roomDetailText);
            
            nameText.setText(displayLabel);
            
            //detailText.setText(resId != 0 ? getString(resId) : node.type.name());
            
            row.setOnClickListener(v -> {
                Intent result = new Intent();
                result.putExtra("selected_room", node.nodeId);
                setResult(RESULT_OK, result);
                finish();
            });

            container.addView(row);
        }
    }
}
