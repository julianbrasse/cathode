package com.cathode.browser;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * High-performance debug overlay view.
 * Renders the virtual cursor grid, coordinate crosshairs,
 * and container bounding boxes with zero CPU overhead when hidden.
 */
public class DebugGridView extends View {

    private final Paint minorGridPaint = new Paint();
    private final Paint majorGridPaint = new Paint();
    private final Paint crosshairPaint = new Paint();
    private final Paint textPaint = new Paint();
    private final Paint textBgPaint = new Paint();
    private final Paint modalPaint = new Paint();
    private final Paint modalStrokePaint = new Paint();

    private float cursorX = -1f;
    private float cursorY = -1f;
    private RectF activeModalRect = null;

    public DebugGridView(Context context) {
        super(context);
        init();
    }

    public DebugGridView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DebugGridView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        minorGridPaint.setColor(0x182979FF); // Very faint blue
        minorGridPaint.setStrokeWidth(1f);
        minorGridPaint.setStyle(Paint.Style.STROKE);

        majorGridPaint.setColor(0x382979FF); // Slightly brighter blue
        majorGridPaint.setStrokeWidth(1.5f);
        majorGridPaint.setStyle(Paint.Style.STROKE);

        crosshairPaint.setColor(0x6000E5FF); // Cyan dashed projection
        crosshairPaint.setStrokeWidth(1.2f);
        crosshairPaint.setStyle(Paint.Style.STROKE);
        crosshairPaint.setPathEffect(new DashPathEffect(new float[]{6f, 6f}, 0));

        textPaint.setColor(0xFF00E5FF);
        textPaint.setTextSize(20f);
        textPaint.setAntiAlias(true);
        textPaint.setFakeBoldText(true);

        textBgPaint.setColor(0xCC101116);
        textBgPaint.setStyle(Paint.Style.FILL);

        modalPaint.setColor(0x1AFF5252); // Faint red modal fill
        modalPaint.setStyle(Paint.Style.FILL);

        modalStrokePaint.setColor(0x66FF5252);
        modalStrokePaint.setStrokeWidth(2f);
        modalStrokePaint.setStyle(Paint.Style.STROKE);
        modalStrokePaint.setPathEffect(new DashPathEffect(new float[]{8f, 4f}, 0));

        setFocusable(false);
        setClickable(false);
    }

    public void setCursorPos(float x, float y, RectF modal) {
        this.cursorX = x;
        this.cursorY = y;
        this.activeModalRect = modal;
        if (getVisibility() == View.VISIBLE) {
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getVisibility() != View.VISIBLE) return;

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // 1. Draw coordinate grid lines every 100px
        for (int x = 0; x <= w; x += 100) {
            Paint p = (x % 500 == 0) ? majorGridPaint : minorGridPaint;
            canvas.drawLine(x, 0, x, h, p);
        }
        for (int y = 0; y <= h; y += 100) {
            Paint p = (y % 500 == 0) ? majorGridPaint : minorGridPaint;
            canvas.drawLine(0, y, w, y, p);
        }

        // 2. Corner and center coordinate labels
        canvas.drawRect(8, 8, 88, 34, textBgPaint);
        canvas.drawText("0, 0", 12, 28, textPaint);

        canvas.drawRect(w / 2f - 54, h / 2f - 24, w / 2f + 54, h / 2f + 4, textBgPaint);
        canvas.drawText((w / 2) + ", " + (h / 2), w / 2f - 48, h / 2f - 6, textPaint);

        canvas.drawRect(w - 110, h - 34, w - 8, h - 8, textBgPaint);
        canvas.drawText(w + ", " + h, w - 104, h - 14, textPaint);

        // 3. Highlight active modal bounding box if present
        if (activeModalRect != null) {
            canvas.drawRect(activeModalRect, modalPaint);
            canvas.drawRect(activeModalRect, modalStrokePaint);
            canvas.drawRect(activeModalRect.left, activeModalRect.top - 24, activeModalRect.left + 150, activeModalRect.top, textBgPaint);
            canvas.drawText("CONTAINER BOUNDS", activeModalRect.left + 4, activeModalRect.top - 6, textPaint);
        }

        // 4. Draw cursor crosshairs and coordinate readout
        if (cursorX >= 0 && cursorY >= 0) {
            canvas.drawLine(0, cursorY, w, cursorY, crosshairPaint);
            canvas.drawLine(cursorX, 0, cursorX, h, crosshairPaint);

            String coordStr = "X: " + (int) cursorX + "  Y: " + (int) cursorY;
            float textW = textPaint.measureText(coordStr);
            float tagX = (cursorX + 32 + textW > w) ? (cursorX - textW - 20) : (cursorX + 28);
            float tagY = (cursorY - 28 < 30) ? (cursorY + 36) : (cursorY - 14);

            canvas.drawRect(tagX - 6, tagY - 20, tagX + textW + 6, tagY + 6, textBgPaint);
            canvas.drawText(coordStr, tagX, tagY, textPaint);
        }
    }
}
