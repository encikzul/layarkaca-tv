package com.gathin.tvhome;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The prayer-time panel (AzanReceiver): a glass panel over whatever is on screen, naming the prayer,
 * with the reminder beeps (beep_seconds) or the theme's azan.mp3. Closes itself after 30 seconds.
 */
public final class AzanActivity extends Activity {
    static final String PREFS = "azan";
    private static final long SHOW_MS = 30_000L;
    private final AtomicBoolean stop = new AtomicBoolean();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean playingFile;

    static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
    }

    /** "Senyapkan hari ini" was chosen today. */
    static boolean mutedToday(Context ctx) {
        return today().equals(ctx.getSharedPreferences(PREFS, MODE_PRIVATE).getString("muted_day", ""));
    }

    static void open(Context ctx, int prayer, long at) {
        ctx.startActivity(new Intent(ctx, AzanActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(Prayer.EXTRA_PRAYER, prayer)
                .putExtra(Prayer.EXTRA_AT, at));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_azan);
        float d = getResources().getDisplayMetrics().density;
        Config cfg = Config.load(this);
        int prayer = getIntent().getIntExtra(Prayer.EXTRA_PRAYER, -1);
        long at = getIntent().getLongExtra(Prayer.EXTRA_AT, System.currentTimeMillis());
        if (prayer < 0 || prayer >= Prayer.NAMES.length) {
            finish();
            return;
        }

        View panel = findViewById(R.id.azanPanel);
        GradientDrawable glass = new GradientDrawable();
        glass.setCornerRadius(18 * d);
        // Solid dark glass: the TV does not blur what is behind, so a see-through panel clashes with it.
        glass.setColor(0xE6182026);
        glass.setStroke(Math.max(1, Math.round(1.5f * d)), 0x4DFFFFFF);
        panel.setBackground(glass);

        boolean h24 = android.text.format.DateFormat.is24HourFormat(this);
        SimpleDateFormat fmt = new SimpleDateFormat(h24 ? "H:mm" : "h:mm a", Locale.ROOT);
        String place = cfg.weatherPlace.isEmpty() ? "Zon " + cfg.prayerZone
                : cfg.weatherLocation.isEmpty() || cfg.weatherLocation.equalsIgnoreCase(cfg.weatherPlace) ? cfg.weatherPlace
                : cfg.weatherPlace + ", " + cfg.weatherLocation;
        ((TextView) findViewById(R.id.azanName)).setText(Prayer.NAMES[prayer]);
        ((TextView) findViewById(R.id.azanTime)).setText(fmt.format(new Date(at)) + "  ·  " + place);
        long[] next = Prayer.next(this, cfg.prayerZone, at + 60_000L);
        if (next != null && next[0] == prayer) next = Prayer.next(this, cfg.prayerZone, next[1] + 60_000L);
        ((TextView) findViewById(R.id.azanNext)).setText(next == null ? ""
                : Prayer.NAMES[(int) next[0]] + " seterusnya " + fmt.format(new Date(next[1])));

        TextView close = findViewById(R.id.azanClose), mute = findViewById(R.id.azanMute);
        close.setBackground(button(d));
        mute.setBackground(button(d));
        close.setOnClickListener(v -> finish());
        mute.setOnClickListener(v -> {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("muted_day", today()).apply();
            finish();
        });

        // The bars rise and fall while the reminder sounds.
        ViewGroup bars = findViewById(R.id.azanBars);
        for (int i = 0; i < bars.getChildCount(); i++) {
            View bar = bars.getChildAt(i);
            bar.setPivotY(28 * d);
            ObjectAnimator a = ObjectAnimator.ofFloat(bar, View.SCALE_Y, 1f, 0.35f);
            a.setDuration(380 + 70L * (i % 4));
            a.setRepeatMode(ValueAnimator.REVERSE);
            a.setRepeatCount(ValueAnimator.INFINITE);
            a.setStartDelay(60L * i);
            a.start();
        }

        if (Prayer.azanFile(this, prayer) != null) {
            playingFile = true;
            close.setText("Henti azan");
            try {
                startForegroundService(new Intent(this, AzanService.class).putExtra(Prayer.EXTRA_PRAYER, prayer));
            } catch (Exception e) {
                playingFile = false;
            }
        } else {
            final Context app = getApplicationContext();
            final long ms = cfg.beepSeconds * 1000L;
            new Thread(() -> AzanReceiver.beep(app, ms, stop), "azan-beep").start();
        }
        close.requestFocus();
        handler.postDelayed(this::finish, SHOW_MS);
    }

    private static StateListDrawable button(float d) {
        GradientDrawable focused = new GradientDrawable();
        focused.setCornerRadius(17 * d);
        focused.setColor(0x33FFFFFF);
        focused.setStroke(Math.max(2, Math.round(2 * d)), 0xEBFFFFFF);
        GradientDrawable normal = new GradientDrawable();
        normal.setCornerRadius(17 * d);
        normal.setColor(0x0FFFFFFF);
        normal.setStroke(Math.max(1, Math.round(d)), 0x38FFFFFF);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, focused);
        s.addState(new int[]{}, normal);
        return s;
    }

    @Override
    protected void onStop() {
        super.onStop();
        finish();   // Home, or another app, takes over: the panel goes and the sound stops
    }

    @Override
    protected void onDestroy() {
        stop.set(true);
        handler.removeCallbacksAndMessages(null);
        if (playingFile) AzanService.stop(this);
        super.onDestroy();
    }
}
