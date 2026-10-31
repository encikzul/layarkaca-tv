package com.gathin.tvhome;

import android.content.Context;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Today's forecast and active warnings from MET Malaysia, through the open data.gov.my weather API
 * (config weather_location, e.g. "Petaling"). Cached in the app's files directory: the forecast is
 * fetched again after 3 hours, the warnings after 20 minutes. No key, nothing sent but the place name.
 */
final class Weather {
    private static final String TAG = "TvHome.Weather";
    private static final String FORECAST = "met-forecast.json", WARNINGS = "met-warnings.json";
    private static final long FORECAST_MS = 3 * 3600_000L, WARNINGS_MS = 20 * 60_000L;
    private static final Pattern STAMP = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})[T ](\\d{2}):(\\d{2})(?::(\\d{2}))?");
    private static volatile long lastFailure;

    static final class Today {
        int min, max;
        String summary = "", when = "", morning = "", afternoon = "", night = "";
    }

    static final class Alert {
        String title = "";
        long validTo;
    }

    private Weather() {
    }

    static boolean due(Context ctx) {
        return stale(new File(ctx.getFilesDir(), FORECAST), FORECAST_MS)
                || stale(new File(ctx.getFilesDir(), WARNINGS), WARNINGS_MS);
    }

    /** Fetches whatever is stale. Blocking: never on the main thread. */
    static void refresh(Context ctx, String location) {
        if (location == null || location.isEmpty()) return;
        if (System.currentTimeMillis() - lastFailure < 10 * 60_000L) return;
        try {
            File fc = new File(ctx.getFilesDir(), FORECAST);
            if (stale(fc, FORECAST_MS)) {
                fetch("https://api.data.gov.my/weather/forecast?contains="
                        + URLEncoder.encode(location, "UTF-8") + "@location__location_name", fc);
            }
            File wf = new File(ctx.getFilesDir(), WARNINGS);
            if (stale(wf, WARNINGS_MS)) {
                Calendar since = Calendar.getInstance();
                since.add(Calendar.DAY_OF_MONTH, -2);
                String start = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(since.getTime());
                fetch("https://api.data.gov.my/weather/warning?timestamp_start="
                        + URLEncoder.encode(start, "UTF-8") + "@warning_issue__issued", wf);
            }
        } catch (Exception e) {
            lastFailure = System.currentTimeMillis();
            Log.w(TAG, "refresh failed: " + e);
        }
    }

    /** Today's forecast for the configured place, or null when not cached. */
    static Today today(Context ctx) {
        JSONArray days = readArray(new File(ctx.getFilesDir(), FORECAST));
        if (days == null) return null;
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
        for (int i = 0; i < days.length(); i++) {
            JSONObject d = days.optJSONObject(i);
            if (d == null || !date.equals(d.optString("date"))) continue;
            Today t = new Today();
            t.min = d.optInt("min_temp", Integer.MIN_VALUE);
            t.max = d.optInt("max_temp", Integer.MIN_VALUE);
            if (t.min == Integer.MIN_VALUE || t.max == Integer.MIN_VALUE) return null;
            t.summary = text(d, "summary_forecast");
            t.when = text(d, "summary_when");
            t.morning = text(d, "morning_forecast");
            t.afternoon = text(d, "afternoon_forecast");
            t.night = text(d, "night_forecast");
            return t;
        }
        return null;
    }

    /** The warning in force now that names one of {@code places} (case-insensitive), latest ending first; or null. */
    static Alert active(Context ctx, List<String> places) {
        JSONArray all = readArray(new File(ctx.getFilesDir(), WARNINGS));
        if (all == null || places.isEmpty()) return null;
        long now = System.currentTimeMillis();
        Alert best = null;
        for (int i = 0; i < all.length(); i++) {
            JSONObject w = all.optJSONObject(i);
            if (w == null) continue;
            String words = allText(w, new StringBuilder()).toString().toLowerCase(Locale.ROOT);
            boolean here = false;
            for (String p : places) if (!p.isEmpty() && words.contains(p.toLowerCase(Locale.ROOT))) here = true;
            if (!here) continue;
            long to = stamp(w.optString("valid_to"));
            if (to <= 0) {
                JSONObject issue = w.optJSONObject("warning_issue");
                long issued = stamp(issue != null ? issue.optString("issued") : w.optString("issued"));
                if (issued <= 0 || now - issued > 12 * 3600_000L) continue;   // no end time: only fresh ones
            } else if (to < now) {
                continue;
            }
            long from = stamp(w.optString("valid_from"));
            if (from > now + 6 * 3600_000L) continue;   // not yet in force
            Alert a = new Alert();
            JSONObject issue = w.optJSONObject("warning_issue");
            a.title = first(text(w, "heading_bm"), issue != null ? text(issue, "title_bm") : "",
                    text(w, "heading_en"), issue != null ? text(issue, "title_en") : "", "Amaran cuaca");
            a.validTo = to;
            if (best == null || to > best.validTo) best = a;
        }
        return best;
    }

    static List<String> places(String location, String place) {
        List<String> out = new ArrayList<>();
        if (location != null && !location.isEmpty()) out.add(location);
        if (place != null && !place.isEmpty() && !place.equalsIgnoreCase(location)) out.add(place);
        return out;
    }

    private static boolean stale(File f, long maxAge) {
        return !f.isFile() || f.length() == 0 || System.currentTimeMillis() - f.lastModified() > maxAge;
    }

    private static void fetch(String url, File out) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(15_000);
            c.setReadTimeout(20_000);
            c.setRequestProperty("Accept", "application/json");
            int code = c.getResponseCode();
            if (code != 200) throw new Exception("HTTP " + code + " for " + url);
            byte[] body;
            try (InputStream in = c.getInputStream()) {
                body = readAll(in, 2 * 1024 * 1024);
            }
            new JSONArray(new String(body, StandardCharsets.UTF_8));   // must be a list, or keep the old file
            File tmp = new File(out.getPath() + ".tmp");
            try (FileOutputStream o = new FileOutputStream(tmp)) {
                o.write(body);
            }
            if (!tmp.renameTo(out)) throw new Exception("could not save " + out.getName());
            Log.i(TAG, "cached " + out.getName() + " (" + body.length + " bytes)");
        } finally {
            c.disconnect();
        }
    }

    private static StringBuilder allText(Object v, StringBuilder sb) {
        if (v instanceof JSONObject) {
            JSONObject o = (JSONObject) v;
            Iterator<String> it = o.keys();
            while (it.hasNext()) allText(o.opt(it.next()), sb);
        } else if (v instanceof JSONArray) {
            JSONArray a = (JSONArray) v;
            for (int i = 0; i < a.length(); i++) allText(a.opt(i), sb);
        } else if (v instanceof String) {
            sb.append((String) v).append(' ');
        }
        return sb;
    }

    private static long stamp(String s) {
        if (s == null) return -1;
        Matcher m = STAMP.matcher(s);
        if (!m.find()) return -1;
        Calendar c = Calendar.getInstance();
        c.set(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)) - 1, Integer.parseInt(m.group(3)),
                Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)), m.group(6) != null ? Integer.parseInt(m.group(6)) : 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static String text(JSONObject o, String key) {
        Object v = o.opt(key);
        return v instanceof String ? ((String) v).trim() : "";
    }

    private static String first(String... values) {
        for (String v : values) if (v != null && !v.isEmpty()) return v;
        return "";
    }

    private static JSONArray readArray(File f) {
        if (!f.isFile()) return null;
        try (InputStream in = new FileInputStream(f)) {
            return new JSONArray(new String(readAll(in, 2 * 1024 * 1024), StandardCharsets.UTF_8));
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
