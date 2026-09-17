package com.example.witspath.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.witspath.R;
import com.example.witspath.model.Node;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RoomPickerActivity extends AppCompatActivity {

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
        
        List<String> names = new ArrayList<>();
        for (Node node : Node.searchByName(query)) {
            if (node.name != null) {
                names.add(node.name);
            }
        }
        
        Collections.sort(names);

        float density = getResources().getDisplayMetrics().density;
        int padding = (int) (16 * density);

        for (String name : names) {
            TextView tv = new TextView(this);
            tv.setText(name);
            tv.setTextSize(16);
            tv.setTextColor(Color.WHITE);
            tv.setPadding(padding, padding, padding, padding);
            tv.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            tv.setClickable(true);
            tv.setFocusable(true);
            tv.setBackgroundResource(android.R.drawable.list_selector_background);
            
            tv.setOnClickListener(v -> {
                Intent result = new Intent();
                result.putExtra("selected_room", name);
                setResult(RESULT_OK, result);
                finish();
            });

            container.addView(tv);
        }
    }
}
