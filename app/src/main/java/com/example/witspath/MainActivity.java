package com.example.witspath;

import android.content.Intent;
import android.os.Bundle;

import com.example.witspath.ui.BaseActivity;

import com.example.witspath.ui.HomeActivity;

public class MainActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Redirect to HomeActivity as the main entry point.
        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }
}
