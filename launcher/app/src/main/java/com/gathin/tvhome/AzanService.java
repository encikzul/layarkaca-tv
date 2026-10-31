package com.gathin.tvhome;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.io.File;

/**
 * Plays the azan once. It asks for transient audio focus, so the video pauses and resumes after; pressing
 * Home, or playing something else, stops it.
 */
public final class AzanService extends Service implements AudioManager.OnAudioFocusChangeListener {
    private static final String CHANNEL = "azan";
    private MediaPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focus;

    static void stop(Context ctx) {
        ctx.stopService(new Intent(ctx, AzanService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int prayer = intent != null ? intent.getIntExtra(Prayer.EXTRA_PRAYER, -1) : -1;
        String name = "Azan " + (prayer >= 0 && prayer < Prayer.NAMES.length ? Prayer.NAMES[prayer] : "");
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Azan", NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(name)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(1, n);
        }
        if (player != null) return START_NOT_STICKY;   // already playing
        File file = Prayer.azanFile(this, prayer);
        if (file == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(this)
                .build();
        if (audio != null) audio.requestAudioFocus(focus);
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(attrs);
            player.setDataSource(file.getPath());
            player.setOnCompletionListener(mp -> stopSelf());
            player.setOnErrorListener((mp, what, extra) -> {
                stopSelf();
                return true;
            });
            player.prepare();
            player.start();
            Toast.makeText(this, name, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.w("TvHome.Prayer", "could not play " + file + ": " + e);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onAudioFocusChange(int change) {
        if (change == AudioManager.AUDIOFOCUS_LOSS) stopSelf();   // something else started playing
    }

    @Override
    public void onDestroy() {
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            player.release();
            player = null;
        }
        if (audio != null && focus != null) audio.abandonAudioFocusRequest(focus);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
