package com.gathin.tvhome;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import java.util.TimeZone;

/**
 * Runs before every screen, receiver and service of the app. Waktu solat, Ramadan, the Hijri date and the azan
 * alarm are all Malaysian, so when the TV's own time zone is not Malaysia's (a box bought abroad keeps e.g.
 * America/New_York or Asia/Kolkata), the app uses Malaysian time for itself. Without this the prayer times land
 * on the wrong calendar day, the prayer card stays hidden and the panel never comes up.
 */
public final class LayarKacaApp extends Application {
    private static final String TAG = "TvHome.Time";
    static final TimeZone MALAYSIA = TimeZone.getTimeZone("Asia/Kuala_Lumpur");

    @Override
    public void onCreate() {
        super.onCreate();
        useMalaysianTime(this);
    }

    /**
     * Makes Malaysian time the app's default when the TV's zone has a different offset and a prayer zone is set.
     * Cheap to call again: Android resets the default when the TV's zone changes, so screens and the azan alarm
     * call this on every start. Returns true when the TV's own zone is not Malaysian.
     */
    static boolean useMalaysianTime(Context ctx) {
        TimeZone.setDefault(null);   // the TV's own zone, in case it was changed since
        TimeZone tv = TimeZone.getDefault();
        long now = System.currentTimeMillis();
        if (tv.getOffset(now) == MALAYSIA.getOffset(now)) return false;
        String zone;
        try {
            zone = Config.load(ctx).prayerZone;
        } catch (RuntimeException e) {
            zone = "";
        }
        if (zone.isEmpty()) return true;   // not set up for Malaysia: leave the TV's time alone
        TimeZone.setDefault(MALAYSIA);
        Log.w(TAG, "TV time zone is " + tv.getID() + "; using Asia/Kuala_Lumpur for waktu solat");
        return true;
    }
}
