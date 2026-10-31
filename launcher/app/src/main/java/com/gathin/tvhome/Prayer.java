package com.gathin.tvhome;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Malaysian prayer times for one JAKIM zone (config prayer_zone, e.g. SGR01), from api.waktusolat.app,
 * cached one file per month in the app's files directory, and the alarm that plays the azan (AzanReceiver).
 * One request per month and zone; nothing is sent but the zone code.
 */
final class Prayer {
    private static final String TAG = "TvHome.Prayer";
    static final String[] NAMES = {"Subuh", "Zohor", "Asar", "Maghrib", "Isyak"};
    private static final String[][] KEYS = {
            {"fajr", "subuh"}, {"dhuhr", "zohor", "zuhur"}, {"asr", "asar"}, {"maghrib"}, {"isha", "isyak"}};
    static final String AZAN_FILE = "azan.mp3";
    static final String AZAN_SUBUH_FILE = "azan_subuh.mp3";
    static final String EXTRA_PRAYER = "prayer";
    static final String EXTRA_AT = "at";
    private static final Pattern CLOCK = Pattern.compile("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?");
    private static volatile long lastFailure;

    private Prayer() {
    }

    private static File cacheFile(Context ctx, String zone, int year, int month) {
        return new File(ctx.getFilesDir(), "solat-" + zone + "-" + year + "-" + month + ".json");
    }

    /** Downloads the months of today and tomorrow when they are not cached yet. Blocking: never on the main thread. */
    static void ensure(Context ctx, String zone) {
        if (zone == null || zone.isEmpty()) return;
        Calendar c = Calendar.getInstance();
        fetchIfMissing(ctx, zone, c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1);
        c.add(Calendar.DAY_OF_MONTH, 1);
        fetchIfMissing(ctx, zone, c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1);
    }

    private static void fetchIfMissing(Context ctx, String zone, int year, int month) {
        File f = cacheFile(ctx, zone, year, month);
        if (f.isFile() && f.length() > 0) return;
        if (System.currentTimeMillis() - lastFailure < 30 * 60_000L) return;   // offline: try again later
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://api.waktusolat.app/v2/solat/" + zone + "?year=" + year + "&month=" + month);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(15_000);
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            if (code != 200) throw new Exception("HTTP " + code);
            byte[] body;
            try (InputStream in = conn.getInputStream()) {
                body = readAll(in, 512 * 1024);
            }
            JSONObject json = new JSONObject(new String(body, StandardCharsets.UTF_8));
            if (days(json) == null) throw new Exception("no prayer list in the response");
            File tmp = new File(f.getPath() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(body);
            }
            if (!tmp.renameTo(f)) throw new Exception("could not save " + f.getName());
            Log.i(TAG, "cached " + f.getName());
        } catch (Exception e) {
            lastFailure = System.currentTimeMillis();
            Log.w(TAG, "fetching " + zone + " " + year + "-" + month + " failed: " + e);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** The five times of that day in epoch milliseconds, Subuh first, or null when they are not cached. */
    static long[] times(Context ctx, String zone, Calendar day) {
        int y = day.get(Calendar.YEAR), m = day.get(Calendar.MONTH) + 1, d = day.get(Calendar.DAY_OF_MONTH);
        JSONObject json = readJson(cacheFile(ctx, zone, y, m));
        JSONArray list = json != null ? days(json) : null;
        if (list == null) return null;
        JSONObject entry = null;
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i);
            if (o != null && o.optInt("day", -1) == d) {
                entry = o;
                break;
            }
        }
        if (entry == null && list.length() >= d) entry = list.optJSONObject(d - 1);   // no "day": by position
        if (entry == null) return null;
        long[] out = new long[5];
        for (int p = 0; p < 5; p++) {
            Object v = null;
            for (String k : KEYS[p]) {
                if (entry.has(k)) {
                    v = entry.opt(k);
                    break;
                }
            }
            long t = toMillis(v, day);
            if (t <= 0) return null;
            out[p] = t;
        }
        // Timestamps carry their own date: never show another month's times if the server sent one.
        Calendar check = Calendar.getInstance();
        check.setTimeInMillis(out[0]);
        if (check.get(Calendar.DAY_OF_MONTH) != d || check.get(Calendar.MONTH) + 1 != m) {
            Log.w(TAG, "cached times for " + y + "-" + m + "-" + d + " are for another day; ignoring them");
            return null;
        }
        return out;
    }

    private static final String[] HIJRI_MONTHS = {"Muharam", "Safar", "Rabiulawal", "Rabiulakhir", "Jamadilawal",
            "Jamadilakhir", "Rejab", "Syaaban", "Ramadan", "Syawal", "Zulkaedah", "Zulhijah"};

    /** JAKIM's Hijri date for that day, e.g. "17 Jamadilawal 1448H", or null when not cached. */
    static String hijri(Context ctx, String zone, Calendar day) {
        int[] h = hijriParts(ctx, zone, day);
        return h == null ? null : h[2] + " " + HIJRI_MONTHS[h[1] - 1] + " " + h[0] + "H";
    }

    /** {year, month, day} of JAKIM's Hijri date for that day, or null. Ramadan is month 9. */
    static int[] hijriParts(Context ctx, String zone, Calendar day) {
        JSONObject o = entry(ctx, zone, day);
        if (o == null) return null;
        Matcher h = Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})").matcher(o.optString("hijri", ""));
        if (!h.find()) return null;
        int month = Integer.parseInt(h.group(2));
        if (month < 1 || month > 12) return null;
        return new int[]{Integer.parseInt(h.group(1)), month, Integer.parseInt(h.group(3))};
    }

    /** Imsak for that day: JAKIM's own time when the data has one, else 10 minutes before Subuh; -1 when not cached. */
    static long imsak(Context ctx, String zone, Calendar day) {
        JSONObject o = entry(ctx, zone, day);
        long t = o != null ? toMillis(o.opt("imsak"), day) : -1;
        if (t > 0) return t;
        long[] all = times(ctx, zone, day);
        return all != null ? all[0] - 10 * 60_000L : -1;
    }

    private static JSONObject entry(Context ctx, String zone, Calendar day) {
        int y = day.get(Calendar.YEAR), m = day.get(Calendar.MONTH) + 1, d = day.get(Calendar.DAY_OF_MONTH);
        JSONObject json = readJson(cacheFile(ctx, zone, y, m));
        JSONArray list = json != null ? days(json) : null;
        if (list == null) return null;
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i);
            if (o != null && o.optInt("day", -1) == d) return o;
        }
        return null;
    }

    /** {prayer index, time} of the first prayer after {@code after}, today or tomorrow; null when not cached. */
    static long[] next(Context ctx, String zone, long after) {
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(after);
        for (int i = 0; i < 2; i++) {
            long[] t = times(ctx, zone, day);
            if (t != null) {
                for (int p = 0; p < 5; p++) if (t[p] > after) return new long[]{p, t[p]};
            }
            day.add(Calendar.DAY_OF_MONTH, 1);
        }
        return null;
    }

    /** "Subuh 5:52   Zohor 1:05   Asar 4:28 ..." with the next prayer bright, or null when not cached. */
    static CharSequence line(Context ctx, String zone) {
        long now = System.currentTimeMillis();
        Calendar today = Calendar.getInstance();
        long[] t = times(ctx, zone, today);
        if (t == null) return null;
        int next = -1;
        for (int p = 0; p < 5; p++) {
            if (t[p] > now) {
                next = p;
                break;
            }
        }
        SimpleDateFormat fmt = new SimpleDateFormat(
                android.text.format.DateFormat.is24HourFormat(ctx) ? "H:mm" : "h:mm", Locale.ROOT);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (int p = 0; p < 5; p++) {
            if (p > 0) sb.append("    ");
            int start = sb.length();
            sb.append(NAMES[p]).append(' ').append(fmt.format(new Date(t[p])));
            if (p == next) {
                sb.setSpan(new ForegroundColorSpan(0xFFFFFFFF), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return sb;
    }

    /** The recording to play: azan_subuh.mp3 for Subuh when the theme has one, else azan.mp3; null for none. */
    static File azanFile(Context ctx, int prayer) {
        File dir = Config.dir(ctx);
        if (dir == null) return null;
        File subuh = new File(dir, AZAN_SUBUH_FILE);
        if (prayer == 0 && subuh.isFile() && subuh.length() > 0) return subuh;
        File f = new File(dir, AZAN_FILE);
        return f.isFile() && f.length() > 0 ? f : null;
    }

    /** Sets the alarm for the next azan after {@code after}, or cancels it when the azan is off or not possible. */
    static void schedule(Context ctx, String zone, boolean azan, long after) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        // With no azan.mp3 the alarm still fires: AzanReceiver beeps instead.
        long[] next = azan && zone != null && !zone.isEmpty() ? next(ctx, zone, after) : null;
        Intent intent = new Intent(ctx, AzanReceiver.class);
        if (next == null) {
            am.cancel(PendingIntent.getBroadcast(ctx, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            return;
        }
        intent.putExtra(EXTRA_PRAYER, (int) next[0]).putExtra(EXTRA_AT, next[1]);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC, next[1], pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC, next[1], pi);
            }
            Log.i(TAG, "azan " + NAMES[(int) next[0]] + " at " + new Date(next[1]));
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC, next[1], pi);
        }
    }

    private static JSONArray days(JSONObject json) {
        for (String k : new String[]{"prayers", "prayerTime", "prayer_time", "prayerTimes", "data"}) {
            JSONArray a = json.optJSONArray(k);
            if (a != null && a.length() > 0 && a.optJSONObject(0) != null) return a;
        }
        Iterator<String> it = json.keys();
        while (it.hasNext()) {
            JSONArray a = json.optJSONArray(it.next());
            JSONObject first = a != null && a.length() > 0 ? a.optJSONObject(0) : null;
            if (first != null && (first.has("fajr") || first.has("subuh"))) return a;
        }
        return null;
    }

    private static long toMillis(Object v, Calendar day) {
        if (v instanceof Number) {
            long s = ((Number) v).longValue();
            return s > 100_000_000_000L ? s : s * 1000L;   // seconds or milliseconds
        }
        if (v instanceof String) {
            String s = ((String) v).trim();
            if (s.matches("\\d{9,13}")) return toMillis(Long.parseLong(s), day);
            Matcher m = CLOCK.matcher(s);
            if (m.find()) {
                Calendar c = (Calendar) day.clone();
                c.set(Calendar.HOUR_OF_DAY, Integer.parseInt(m.group(1)));
                c.set(Calendar.MINUTE, Integer.parseInt(m.group(2)));
                c.set(Calendar.SECOND, m.group(3) != null ? Integer.parseInt(m.group(3)) : 0);
                c.set(Calendar.MILLISECOND, 0);
                return c.getTimeInMillis();
            }
        }
        return -1;
    }

    private static JSONObject readJson(File f) {
        if (!f.isFile()) return null;
        try (InputStream in = new FileInputStream(f)) {
            return new JSONObject(new String(readAll(in, 512 * 1024), StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.w(TAG, "unreadable " + f.getName() + ": " + e);
            return null;
        }
    }

    private static byte[] readAll(InputStream in, int max) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > max) throw new Exception("response too large");
        }
        return out.toByteArray();
    }
}
