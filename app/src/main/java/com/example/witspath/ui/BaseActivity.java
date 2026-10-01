package com.example.witspath.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.witspath.R;
import com.example.witspath.util.AppConfiguration;
import com.example.witspath.util.Prefs;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.ShapeAppearanceModel;

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
        applyStatusBarPadding();
        setupCompanionFab();
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        applyStatusBarPadding();
        setupCompanionFab();
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        super.setContentView(view, params);
        applyStatusBarPadding();
        setupCompanionFab();
    }

    private void applyStatusBarPadding() {
        View contentRoot = findViewById(android.R.id.content);
        if (contentRoot != null) {
            ViewCompat.setOnApplyWindowInsetsListener(contentRoot, (v, insets) -> {
                Insets statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars());
                v.setPadding(v.getPaddingLeft(), statusBarInsets.top, v.getPaddingRight(), v.getPaddingBottom());
                return insets;
            });
        }
    }

    private void setupCompanionFab() {
        if (this instanceof LoginActivity || this instanceof SignUpActivity || this instanceof CompanionActivity) {
            return;
        }

        ViewGroup contentRoot = findViewById(android.R.id.content);
        if (contentRoot == null) return;

        if (contentRoot.findViewById(R.id.baseCompanionFab) != null) return;

        float dp = getResources().getDisplayMetrics().density;
        int sizePx = (int) (56 * dp);

        ShapeableImageView fabCompanion =
                new ShapeableImageView(this);
        fabCompanion.setId(R.id.baseCompanionFab);
        fabCompanion.setImageResource(R.drawable.ic_ai_companion_icon);
        fabCompanion.setScaleType(ImageView.ScaleType.CENTER_CROP);
        fabCompanion.setShapeAppearanceModel(
                ShapeAppearanceModel.builder()
                        .setAllCorners(CornerFamily.ROUNDED, sizePx / 2f)
                        .build()
        );
        fabCompanion.setElevation(12f);
        fabCompanion.setClickable(true);
        fabCompanion.setFocusable(true);
        fabCompanion.setContentDescription(getString(R.string.companion_entry));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(sizePx, sizePx);
        params.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
        params.setMargins((int) (16 * dp), 0, (int) (16 * dp), 0);
        fabCompanion.setLayoutParams(params);

        Prefs prefs = new Prefs(this);
        float savedX = prefs.getFloat(PREF_FAB_X, -1f);
        float savedY = prefs.getFloat(PREF_FAB_Y, -1f);

        fabCompanion.post(() -> {
            if (savedX != -1f && savedY != -1f && contentRoot.getWidth() > 0 && contentRoot.getHeight() > 0) {
                float maxX = contentRoot.getWidth() - fabCompanion.getWidth();
                float maxY = contentRoot.getHeight() - fabCompanion.getHeight();
                if (maxX > 0 && maxY > 0) {
                    float clampedX = Math.max(0, Math.min(savedX, maxX));
                    float clampedY = Math.max(0, Math.min(savedY, maxY));
                    fabCompanion.setX(clampedX);
                    fabCompanion.setY(clampedY);
                }
            }
        });

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
                            float targetTranslationX = (currentX < rootWidth / 2f) ? (16f * dp - v.getLeft()) : (rootWidth - v.getWidth() - 16f * dp - v.getLeft());
                            v.animate()
                                    .translationX(targetTranslationX)
                                    .setDuration(250)
                                    .withEndAction(() -> {
                                        Prefs p = new Prefs(v.getContext());
                                        p.setFloat(PREF_FAB_X, v.getX());
                                        p.setFloat(PREF_FAB_Y, v.getY());
                                    })
                                    .start();
                        }
                        return true;
                }
                return false;
            }
        });

        contentRoot.addView(fabCompanion);
    }
}
