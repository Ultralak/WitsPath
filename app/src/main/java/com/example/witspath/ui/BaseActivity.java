package com.example.witspath.ui;

import android.content.Context;
import androidx.appcompat.app.AppCompatActivity;
import com.example.witspath.util.AppConfiguration;

/**
 * Team Wavelets - WitsPath
 * All activities in the app should extend this to ensure Language and Text Size
 * settings are applied universally.
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppConfiguration.updateContext(newBase));
    }
}
