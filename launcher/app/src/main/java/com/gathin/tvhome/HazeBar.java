package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** The five IPU bands as a bar in the DOE's colours, with a marker at the current reading. */
public final class HazeBar extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float at = -1f;

    public HazeBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    void setValue(int api) {
        int b = Haze.band(api);
        float f = (api - Haze.LOW[b]) / (float) (Haze.HIGH[b] - Haze.LOW[b] + 1);
        at = (b + Math.max(0f, Math.min(1f, f))) / 5f;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float d = getResources().getDisplayMetrics().density;
        float w = getWidth(), h = getHeight(), bar = 5 * d, gap = 2 * d;
        float seg = (w - 4 * gap) / 5f, top = (h - bar) / 2f;
        for (int i = 0; i < 5; i++) {
            float x = i * (seg + gap);
            paint.setColor(Haze.COLORS[i]);
            rect.set(x, top, x + seg, top + bar);
            canvas.drawRoundRect(rect, bar / 2f, bar / 2f, paint);
        }
        if (at < 0f) return;
        float x = Math.max(d, Math.min(w - d, at * w));
        paint.setColor(0xCC05090B);
        rect.set(x - 2.5f * d, 0, x + 2.5f * d, h);
        canvas.drawRoundRect(rect, 2.5f * d, 2.5f * d, paint);
        paint.setColor(0xFFF4F6F5);
        rect.set(x - 1.2f * d, d, x + 1.2f * d, h - d);
        canvas.drawRoundRect(rect, 1.2f * d, 1.2f * d, paint);
    }
}
