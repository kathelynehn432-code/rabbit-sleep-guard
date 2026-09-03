package com.rabbit.sleepguard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

public final class MoonlightBackgroundView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public MoonlightBackgroundView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        paint.setShader(new LinearGradient(0, 0, width, height,
                new int[]{Color.rgb(7, 10, 24), Color.rgb(16, 19, 45), Color.rgb(9, 15, 34)},
                new float[]{0f, .48f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);

        drawGlow(canvas, width * .88f, height * .08f, width * .62f,
                Color.argb(118, 89, 114, 255));
        drawGlow(canvas, width * .05f, height * .35f, width * .52f,
                Color.argb(82, 149, 97, 255));
        drawGlow(canvas, width * .8f, height * .74f, width * .7f,
                Color.argb(54, 171, 92, 225));

        paint.setShader(new LinearGradient(0, 0, width, 0,
                new int[]{Color.TRANSPARENT, Color.argb(24, 208, 220, 255), Color.TRANSPARENT},
                null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, height * .19f, width, height * .192f, paint);
        paint.setShader(null);
    }

    private void drawGlow(Canvas canvas, float x, float y, float radius, int color) {
        paint.setShader(new RadialGradient(x, y, radius,
                new int[]{color, Color.argb(20, Color.red(color), Color.green(color), Color.blue(color)), Color.TRANSPARENT},
                new float[]{0f, .46f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(x, y, radius, paint);
    }
}
