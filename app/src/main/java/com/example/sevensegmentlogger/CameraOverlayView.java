package com.example.sevensegmentlogger;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Draws the actual analyzed camera frame using FIT_CENTER and a matched normalized ROI. */
public class CameraOverlayView extends View {
    private final Paint imagePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap frame;
    private RectF roi = new RectF(.18f, .35f, .82f, .65f);
    private String result = "—";

    public CameraOverlayView(Context context) {
        super(context);
        linePaint.setColor(Color.rgb(34, 211, 238));
        linePaint.setStrokeWidth(3f * getResources().getDisplayMetrics().density);
        linePaint.setStyle(Paint.Style.STROKE);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(16f * getResources().getDisplayMetrics().scaledDensity);
        textPaint.setShadowLayer(4, 0, 1, Color.BLACK);
    }

    public void setFrame(Bitmap value) {
        Bitmap previous = frame;
        frame = value;
        invalidate();
        if (previous != null && previous != value && !previous.isRecycled()) previous.recycle();
    }

    public void setRegion(RectF value) { roi = new RectF(value); invalidate(); }
    public void setResult(String value) { result = value == null ? "인식 대기" : value; invalidate(); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.BLACK);
        if (frame == null) return;
        float scale = Math.min(getWidth() / (float) frame.getWidth(), getHeight() / (float) frame.getHeight());
        float w = frame.getWidth() * scale, h = frame.getHeight() * scale;
        float ox = (getWidth() - w) * .5f, oy = (getHeight() - h) * .5f;
        RectF dest = new RectF(ox, oy, ox + w, oy + h);
        canvas.drawBitmap(frame, null, dest, imagePaint);
        RectF r = new RectF(ox + roi.left * w, oy + roi.top * h,
                ox + roi.right * w, oy + roi.bottom * h);
        canvas.drawRect(r, linePaint);
        canvas.drawText("ROI  |  " + result, 14f, 25f * getResources().getDisplayMetrics().density, textPaint);
    }
}
