package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

/** The prayer-time alarm (Prayer.schedule): plays the azan when the TV is on, then sets the next one. */
public final class AzanReceiver extends BroadcastReceiver {
    private static final long LATE_MS = 3 * 60_000L;

    /** Double-beeps once a second for {@code ms}, over whatever is playing, which is lowered rather than paused. */
    static void beep(Context ctx, long ms, java.util.concurrent.atomic.AtomicBoolean stop) {
        android.media.AudioManager audio = (android.media.AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        android.media.AudioAttributes attrs = new android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        android.media.AudioFocusRequest focus = new android.media.AudioFocusRequest.Builder(
                android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs).build();
        if (audio != null) audio.requestAudioFocus(focus);
        android.media.ToneGenerator tone = null;
        try {
            tone = new android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 90);
            long end = android.os.SystemClock.uptimeMillis() + Math.max(1000L, ms);
            while (android.os.SystemClock.uptimeMillis() < end && (stop == null || !stop.get())) {
                tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 450);
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            Log.w("TvHome.Prayer", "could not beep: " + e);
        } finally {
            if (tone != null) tone.release();
            if (audio != null) audio.abandonAudioFocusRequest(focus);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final Context ctx = context.getApplicationContext();
        final Config cfg = Config.load(ctx);
        int prayer = intent.getIntExtra(Prayer.EXTRA_PRAYER, -1);
        long at = intent.getLongExtra(Prayer.EXTRA_AT, 0);
        long late = System.currentTimeMillis() - at;
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        boolean screenOn = pm != null && pm.isInteractive();
        // A TV in standby gets the alarm only when it wakes: never play an azan that is already past.
        boolean opened = false;
        if (cfg.azan && prayer >= 0 && screenOn && late < LATE_MS && late > -LATE_MS && AzanActivity.mutedToday(ctx)) {
            Log.i("TvHome.Prayer", "reminder muted for today");
        } else if (cfg.azan && prayer >= 0 && screenOn && late < LATE_MS && late > -LATE_MS
                && android.provider.Settings.canDrawOverlays(ctx)) {
            try {
                AzanActivity.open(ctx, prayer, at);   // the panel, with the beeps or the azan
                opened = true;
            } catch (Exception e) {
                Log.w("TvHome.Prayer", "could not open the panel: " + e);
            }
        }
        if (!opened && !AzanActivity.mutedToday(ctx) && cfg.azan && prayer >= 0 && screenOn && late < LATE_MS && late > -LATE_MS) {
            // No permission to draw over other apps: a small notice and the beeps.
            if (Prayer.azanFile(ctx, prayer) != null) {
                try {
                    ctx.startForegroundService(new Intent(ctx, AzanService.class).putExtra(Prayer.EXTRA_PRAYER, prayer));
                } catch (Exception e) {
                    Log.w("TvHome.Prayer", "could not start the azan: " + e);
                }
            } else {
                android.widget.Toast.makeText(ctx, "Masuk waktu " + Prayer.NAMES[Math.min(prayer, Prayer.NAMES.length - 1)],
                        android.widget.Toast.LENGTH_LONG).show();
                final long ms = cfg.beepSeconds * 1000L;
                new Thread(() -> beep(ctx, ms, null), "azan-beep").start();
            }
        }
        final long after = Math.max(System.currentTimeMillis(), at) + 60_000L;
        final PendingResult result = goAsync();
        new Thread(() -> {
            try {
                Prayer.ensure(ctx, cfg.prayerZone);
                Prayer.schedule(ctx, cfg.prayerZone, cfg.azan, after);
            } finally {
                result.finish();
            }
        }, "azan-next").start();
    }
}
