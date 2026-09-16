package com.example.witspath;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.FrameLayout;

/**
 * A FrameLayout that supports pinch-to-zoom and one-finger pan over its children,
 * double-tap to reset. Used to host {@code floorPlanImageView} and
 * {@code floorPlanRouteView} together so they zoom/pan as one unit and stay
 * pixel-aligned (both are transformed by the same canvas matrix in dispatchDraw).
 *
 * All touch handling happens here — children are treated as display-only. If a
 * child later needs its own taps (e.g. a tappable room pin), onInterceptTouchEvent
 * will need to become conditional instead of unconditional.
 */
public class ZoomableFrameLayout extends FrameLayout {

    private static final float MIN_SCALE = 1f;
    private static final float MAX_SCALE = 4f;
    private static final float DOUBLE_TAP_SCALE = 2.5f;

    /** Notified whenever the zoom/pan matrix changes, in case something outside needs it. */
    public interface OnTransformChangeListener {
        void onTransformChanged(Matrix matrix);
    }

    private final Matrix matrix = new Matrix();

    private float scale = MIN_SCALE;
    private float translateX = 0f;
    private float translateY = 0f;

    private float lastTouchX;
    private float lastTouchY;
    private int activePointerId = MotionEvent.INVALID_POINTER_ID;

    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;

    private OnTransformChangeListener transformListener;

    public ZoomableFrameLayout(Context context) {
        this(context, null);
    }

    public ZoomableFrameLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ZoomableFrameLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setWillNotDraw(false);
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new TapListener());
    }

    public void setOnTransformChangeListener(OnTransformChangeListener listener) {
        this.transformListener = listener;
    }

    /** Snaps back to the un-zoomed, centred state. */
    public void resetZoom() {
        scale = MIN_SCALE;
        translateX = 0f;
        translateY = 0f;
        applyTransform();
    }

    public Matrix getCurrentMatrix() {
        return new Matrix(matrix);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // Floor plan + route overlay are display-only, so this view always owns touch.
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleGestureDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                activePointerId = event.getPointerId(0);
                break;

            case MotionEvent.ACTION_MOVE:
                if (!scaleGestureDetector.isInProgress() && activePointerId != MotionEvent.INVALID_POINTER_ID) {
                    int pointerIndex = event.findPointerIndex(activePointerId);
                    if (pointerIndex != -1) {
                        float x = event.getX(pointerIndex);
                        float y = event.getY(pointerIndex);
                        translateX += (x - lastTouchX);
                        translateY += (y - lastTouchY);
                        clampTranslation();
                        applyTransform();
                        lastTouchX = x;
                        lastTouchY = y;
                    }
                }
                break;

            case MotionEvent.ACTION_POINTER_UP: {
                int pointerIndex = event.getActionIndex();
                int pointerId = event.getPointerId(pointerIndex);
                if (pointerId == activePointerId) {
                    // The finger driving the drag lifted — hand off to whichever finger remains.
                    int newIndex = (pointerIndex == 0) ? 1 : 0;
                    if (newIndex < event.getPointerCount()) {
                        activePointerId = event.getPointerId(newIndex);
                        lastTouchX = event.getX(newIndex);
                        lastTouchY = event.getY(newIndex);
                    }
                }
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                activePointerId = MotionEvent.INVALID_POINTER_ID;
                break;
        }
        return true;
    }

    private void clampTranslation() {
        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) return;

        float scaledWidth = width * scale;
        float scaledHeight = height * scale;

        if (scaledWidth <= width) {
            translateX = (width - scaledWidth) / 2f;
        } else {
            float minX = width - scaledWidth;
            translateX = Math.max(minX, Math.min(translateX, 0f));
        }

        if (scaledHeight <= height) {
            translateY = (height - scaledHeight) / 2f;
        } else {
            float minY = height - scaledHeight;
            translateY = Math.max(minY, Math.min(translateY, 0f));
        }
    }

    private void applyTransform() {
        matrix.reset();
        matrix.postScale(scale, scale);
        matrix.postTranslate(translateX, translateY);
        if (transformListener != null) {
            transformListener.onTransformChanged(new Matrix(matrix));
        }
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.save();
        canvas.concat(matrix);
        super.dispatchDraw(canvas);
        canvas.restore();
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float newScale = scale * detector.getScaleFactor();
            newScale = Math.max(MIN_SCALE, Math.min(newScale, MAX_SCALE));

            // Keep the point under the fingers fixed in place while scaling.
            float focusX = detector.getFocusX();
            float focusY = detector.getFocusY();
            float scaleDelta = newScale / scale;
            translateX = focusX - (focusX - translateX) * scaleDelta;
            translateY = focusY - (focusY - translateY) * scaleDelta;
            scale = newScale;

            clampTranslation();
            applyTransform();
            return true;
        }
    }

    private class TapListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (scale > MIN_SCALE) {
                resetZoom();
            } else {
                float targetScale = Math.min(MAX_SCALE, DOUBLE_TAP_SCALE);
                float scaleDelta = targetScale / scale;
                translateX = e.getX() - (e.getX() - translateX) * scaleDelta;
                translateY = e.getY() - (e.getY() - translateY) * scaleDelta;
                scale = targetScale;
                clampTranslation();
                applyTransform();
            }
            return true;
        }
    }
}
