package com.example.witspath.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.witspath.R;
import com.example.witspath.util.AppConfiguration;
import com.example.witspath.util.Prefs;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

/**
 * Team Wavelets - WitsPath
 * All activities in the app should extend this to ensure Language and Text Size
 * settings are applied universally, and provides the floating companion chat button overlay.
 */
public abstract class BaseActivity extends AppCompatActivity {

    private static final String PREF_FAB_X = "pref_fab_x";
    private static final String PREF_FAB_Y = "pref_fab_y";

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppConfiguration.updateContext(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        setupCompanionFab();
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        setupCompanionFab();
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        super.setContentView(view, params);
        setupCompanionFab();
    }

    private void setupCompanionFab() {
        if (this instanceof LoginActivity || this instanceof SignUpActivity || this instanceof CompanionActivity) {
            return;
        }

        ViewGroup contentRoot = findViewById(android.R.id.content);
        if (contentRoot == null) return;

        if (contentRoot.findViewById(R.id.baseCompanionFab) != null) return;

        FloatingActionButton fabCompanion = new FloatingActionButton(this);
        fabCompanion.setId(R.id.baseCompanionFab);
        fabCompanion.setImageResource(R.drawable.ic_app_logo);
        fabCompanion.setImageTintList(null);
        fabCompanion.setBackgroundTintList(getResources().getColorStateList(R.color.colorRoute, getTheme()));
        fabCompanion.setContentDescription(getString(R.string.companion_entry));
        fabCompanion.setMinimumWidth(48);
        fabCompanion.setMinimumHeight(48);
        fabCompanion.setSize(FloatingActionButton.SIZE_NORMAL);
        fabCompanion.setElevation(12f);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.BOTTOM | Gravity.END;
        params.setMargins(16, 16, 16, 120);
        fabCompanion.setLayoutParams(params);

        Prefs prefs = new Prefs(this);
        float savedX = prefs.getFloat(PREF_FAB_X, -1f);
        float savedY = prefs.getFloat(PREF_FAB_Y, -1f);
        if (savedX != -1f && savedY != -1f) {
            fabCompanion.setX(savedX);
            fabCompanion.setY(savedY);
        }

        fabCompanion.setOnClickListener(v -> {
            startActivity(new Intent(this, CompanionActivity.class));
        });

        fabCompanion.setOnTouchListener(new View.OnTouchListener() {
            private float startX, startY;
            private boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getRawX();
                        startY = event.getRawY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - startX;
                        float dy = event.getRawY() - startY;
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            moved = true;
                        }
                        v.setTranslationX(v.getTranslationX() + dx);
                        v.setTranslationY(v.getTranslationY() + dy);
                        startX = event.getRawX();
                        startY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            v.performClick();
                        } else {
                            View root = v.getRootView();
                            float rootWidth = root.getWidth();
                            float currentX = v.getX();
                            float targetTranslationX = (currentX < rootWidth / 2f) ? (16f - v.getLeft()) : (rootWidth - v.getWidth() - 16f - v.getLeft());
                            v.animate().translationX(targetTranslationX).setDuration(250).start();
                            Prefs p = new Prefs(v.getContext());
                            p.setFloat(PREF_FAB_X, v.getX());
                            p.setFloat(PREF_FAB_Y, v.getY());
                        }
                        return true;
                }
                return false;
            }
        });

        contentRoot.addView(fabCompanion);
    }
}
