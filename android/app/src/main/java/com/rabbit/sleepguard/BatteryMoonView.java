package com.rabbit.sleepguard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

public final class BatteryMoonView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private int level = 0;
    private boolean charging = false;

    public BatteryMoonView(Context context) {
        super(context);
        setMinimumWidth(dp(154));
        setMinimumHeight(dp(154));
    }

    public void setBattery(int level, boolean charging) {
        this.level = Math.max(0, Math.min(100, level));
        this.charging = charging;
        setContentDescription("电量 " + this.level + "%" + (charging ? "，正在充电" : ""));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(getWidth(), getHeight()) * .39f;
        arc.set(cx - radius, cy - radius, cx + radius, cy + radius);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(dp(8));
        paint.setColor(Color.argb(42, 224, 232, 255));
        paint.setShader(null);
        canvas.drawArc(arc, -90, 360, false, paint);

        paint.setShader(new LinearGradient(arc.left, arc.top, arc.right, arc.bottom,
                charging
                        ? new int[]{Color.rgb(128, 236, 213), Color.rgb(162, 150, 255)}
                        : new int[]{Color.rgb(199, 215, 255), Color.rgb(146, 129, 255)},
                null, Shader.TileMode.CLAMP));
        canvas.drawArc(arc, -90, Math.max(3.6f, level * 3.6f), false, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(cx - radius, cy - radius, cx + radius, cy + radius,
                new int[]{Color.argb(128, 224, 231, 255), Color.argb(38, 155, 139, 255)},
                null, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius - dp(17), paint);

        paint.setShader(null);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.WHITE);
        paint.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        paint.setTextSize(dp(35));
        paint.setLetterSpacing(-.035f);
        canvas.drawText(level + "%", cx, cy + dp(7), paint);

        paint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        paint.setTextSize(dp(9));
        paint.setLetterSpacing(.13f);
        paint.setColor(Color.rgb(198, 211, 241));
        canvas.drawText(charging ? "CHARGING" : "BATTERY", cx, cy + dp(27), paint);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
