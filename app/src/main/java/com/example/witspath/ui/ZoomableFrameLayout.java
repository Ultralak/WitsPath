package com.example.witspath.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.FrameLayout;

public class ZoomableFrameLayout extends FrameLayout {

    private static final float MIN_RELATIVE_SCALE = 1f;
    private static final float MAX_RELATIVE_SCALE = 4f;
    private static final float DOUBLE_TAP_RELATIVE_SCALE = 2.5f;

    public interface OnTransformChangeListener {
        void onTransformChanged(Matrix matrix);
    }

    private final Matrix matrix = new Matrix();

    private float relativeScale = 1f;
    private float translateX = 0f;
    private float translateY = 0f;

    private float contentWidth = 0;
    private float contentHeight = 0;
    private float baseScale = 1f;

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

    public void setContentSize(int width, int height) {
        this.contentWidth = width;
        this.contentHeight = height;
        recalculateBaseScale();
        resetZoom();
    }

    private void recalculateBaseScale() {
        if (contentWidth > 0 && getWidth() > 0) {
            baseScale = (float) getWidth() / contentWidth;
        }
    }

    public void setOnTransformChangeListener(OnTransformChangeListener listener) {
        this.transformListener = listener;
    }

    public void resetZoom() {
        relativeScale = MIN_RELATIVE_SCALE;
        translateX = 0f;
        translateY = 0f;
        applyTransform();
    }

    public void zoomIn() {
        zoomTo(relativeScale * 1.5f, getWidth() / 2f, getHeight() / 2f);
    }

    public void zoomOut() {
        zoomTo(relativeScale / 1.5f, getWidth() / 2f, getHeight() / 2f);
    }

    private void zoomTo(float targetRelativeScale, float focusX, float focusY) {
        float newScale = Math.max(MIN_RELATIVE_SCALE, Math.min(targetRelativeScale, MAX_RELATIVE_SCALE));
        float scaleDelta = newScale / relativeScale;
        
        translateX = focusX - (focusX - translateX) * scaleDelta;
        translateY = focusY - (focusY - translateY) * scaleDelta;
        relativeScale = newScale;

        clampTranslation();
        applyTransform();
    }

    public Matrix getCurrentMatrix() {
        return new Matrix(matrix);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        recalculateBaseScale();
        clampTranslation();
        applyTransform();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
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
        if (width == 0 || height == 0 || contentWidth == 0 || contentHeight == 0) return;

        float displayWidth = contentWidth * baseScale * relativeScale;
        float displayHeight = contentHeight * baseScale * relativeScale;

        if (displayWidth <= width) {
            translateX = (width - displayWidth) / 2f;
        } else {
            float minX = width - displayWidth;
            translateX = Math.max(minX, Math.min(translateX, 0f));
        }

        if (displayHeight <= height) {
            translateY = (height - displayHeight) / 2f;
        } else {
            float minY = height - displayHeight;
            translateY = Math.max(minY, Math.min(translateY, 0f));
        }
    }

    private void applyTransform() {
        matrix.reset();
        matrix.postScale(relativeScale, relativeScale);
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
            zoomTo(relativeScale * detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
            return true;
        }
    }

    private class TapListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (relativeScale > MIN_RELATIVE_SCALE) {
                resetZoom();
            } else {
                zoomTo(DOUBLE_TAP_RELATIVE_SCALE, e.getX(), e.getY());
            }
            return true;
        }
    }
}
