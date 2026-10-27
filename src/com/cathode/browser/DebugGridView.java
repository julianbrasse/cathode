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
 * Renders the 60px x 60px virtual cursor grid, coordinate crosshairs,
 * and container bounding boxes perfectly aligned with the browser design system.
 */
public final class DebugGridView extends View {

    private final Paint gridPaint = new Paint();
    private final Paint crosshairPaint = new Paint();
    private final Paint textPaint = new Paint();
    private final Paint textBgPaint = new Paint();
    private final Paint textBorderPaint = new Paint();
    private final Paint modalPaint = new Paint();
    private final Paint modalStrokePaint = new Paint();

    private final RectF badgeRect = new RectF();

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

    @SuppressWarnings("this-escape")
    private void init() {
        gridPaint.setColor(0x1A2979FF); // Uniform subtle electric blue grid
        gridPaint.setStrokeWidth(1f);
        gridPaint.setStyle(Paint.Style.STROKE);

        crosshairPaint.setColor(0x802979FF); // Electric blue dashed cursor projection
        crosshairPaint.setStrokeWidth(1.5f);
        crosshairPaint.setStyle(Paint.Style.STROKE);
        crosshairPaint.setPathEffect(new DashPathEffect(new float[]{6f, 6f}, 0));

        textPaint.setColor(0xFFFFFFFF); // Crisp white typography
        textPaint.setTextSize(18f);
        textPaint.setAntiAlias(true);
        textPaint.setFakeBoldText(true);

        textBgPaint.setColor(0xEB101116); // High-contrast dark glass background
        textBgPaint.setStyle(Paint.Style.FILL);

        textBorderPaint.setColor(0x33FFFFFF); // Subtle glass border
        textBorderPaint.setStrokeWidth(1f);
        textBorderPaint.setStyle(Paint.Style.STROKE);
        textBorderPaint.setAntiAlias(true);

        modalPaint.setColor(0x152979FF); // Soft electric blue container fill
        modalPaint.setStyle(Paint.Style.FILL);

        modalStrokePaint.setColor(0x802979FF); // Dashed electric blue container stroke
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

        // 1. Draw coordinate grid lines every 60px (uniform width and color)
        for (int x = 0; x <= w; x += 60) {
            canvas.drawLine(x, 0, x, h, gridPaint);
        }
        for (int y = 0; y <= h; y += 60) {
            canvas.drawLine(0, y, w, y, gridPaint);
        }

        // 2. Highlight active modal bounding box if present
        if (activeModalRect != null) {
            canvas.drawRect(activeModalRect, modalPaint);
            canvas.drawRect(activeModalRect, modalStrokePaint);
            drawBadge(canvas, activeModalRect.left + 4f, activeModalRect.top - 26f, "CONTAINER BOUNDS");
        }

        // 3. Draw cursor crosshairs and coordinate readout
        if (cursorX >= 0 && cursorY >= 0) {
            canvas.drawLine(0, cursorY, w, cursorY, crosshairPaint);
            canvas.drawLine(cursorX, 0, cursorX, h, crosshairPaint);

            String coordStr = "X: " + (int) cursorX + "  Y: " + (int) cursorY;
            float textW = textPaint.measureText(coordStr);
            float tagX = (cursorX + 38 + textW > w) ? (cursorX - textW - 24) : (cursorX + 36);
            float tagY = (cursorY - 32 < 30) ? (cursorY + 44) : (cursorY - 18);

            drawBadge(canvas, tagX, tagY - 18f, coordStr);
        }
    }

    private void drawBadge(Canvas canvas, float x, float y, String text) {
        float textW = textPaint.measureText(text);
        badgeRect.set(x - 6f, y - 2f, x + textW + 6f, y + 22f);
        canvas.drawRoundRect(badgeRect, 6f, 6f, textBgPaint);
        canvas.drawRoundRect(badgeRect, 6f, 6f, textBorderPaint);
        canvas.drawText(text, x, y + 16f, textPaint);
    }
}
