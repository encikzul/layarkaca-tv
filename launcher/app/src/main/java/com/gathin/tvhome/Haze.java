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
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * The Air Pollutant Index (IPU) of one station from the Department of Environment's APIMS map service,
 * the public feed behind eqms.doe.gov.my (config haze_station, e.g. "Shah Alam"). Hourly data, fetched at
 * most every 30 minutes. Not a documented API: when it changes or fails, the card simply stays hidden.
 */
final class Haze {
    private static final String TAG = "TvHome.Haze";
    /** The feed the APIMS portal's own map reads (eqms.doe.gov.my/APIMS/main): {"data": [station, ...]}. */
    private static final String URL_PORTAL = "https://eqms.doe.gov.my/api3/publicportalapims/mapcaqm";
    /** The ArcGIS map service: {"features": [{"attributes": station}, ...]}; a fallback, often blank. */
    private static final String URL_CURRENT = "https://eqms.doe.gov.my/api3/publicmapproxy/PUBLIC_DISPLAY/"
            + "CAQM_MCAQM_Current_Reading/MapServer/0/query?where=1%3D1&outFields=*&returnGeometry=false&f=json";
    private static final String FILE = "apims-portal.json";
    private static final long MAX_AGE = 30 * 60_000L;
    static final String[] BANDS = {"Baik", "Sederhana", "Tidak Sihat", "Sangat Tidak Sihat", "Berbahaya"};
    static final int[] COLORS = {0xFF4F8EF7, 0xFF3FB37F, 0xFFF2C94C, 0xFFF2994A, 0xFFE5484D};
    static final int[] LOW = {0, 51, 101, 201, 301}, HIGH = {50, 100, 200, 300, 500};
    private static volatile long lastFailure;

    static final class Reading {
        int api;
        String station = "";
        /** APIMS's clock reading, Malaysian time stored as if it were UTC. */
        long wallTime;
        /** The configured station had no reading, so this is the nearest one that has. */
        boolean nearest;
        double lat, lon;
    }

    private Haze() {
    }

    static boolean due(Context ctx) {
        File f = new File(ctx.getFilesDir(), FILE);
        return !f.isFile() || System.currentTimeMillis() - f.lastModified() > MAX_AGE;
    }

    static void refresh(Context ctx) {
        if (!due(ctx) || System.currentTimeMillis() - lastFailure < 10 * 60_000L) return;
        if (!fetch(ctx, URL_PORTAL + "?_ts=" + System.currentTimeMillis())) {
            if (!fetch(ctx, URL_CURRENT)) lastFailure = System.currentTimeMillis();
        }
    }

    private static boolean fetch(Context ctx, String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestProperty("Referer", "https://eqms.doe.gov.my/APIMS/main");
            c.setConnectTimeout(15_000);
            c.setReadTimeout(20_000);
            c.setRequestProperty("Accept", "application/json");
            int code = c.getResponseCode();
            if (code != 200) throw new Exception("HTTP " + code);
            byte[] body;
            try (InputStream in = c.getInputStream()) {
                body = readAll(in, 2 * 1024 * 1024);
            }
            JSONObject json = new JSONObject(new String(body, StandardCharsets.UTF_8));
            if (json.optJSONArray("features") == null && json.optJSONArray("data") == null) {
                throw new Exception("no stations in the response");
            }
            File out = new File(ctx.getFilesDir(), FILE), tmp = new File(out.getPath() + ".tmp");
            try (FileOutputStream o = new FileOutputStream(tmp)) {
                o.write(body);
            }
            if (!tmp.renameTo(out)) throw new Exception("could not save");
            Log.i(TAG, "cached " + body.length + " bytes from " + url);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "fetch " + url + " failed: " + e);
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** The newest reading of the station whose name contains {@code station}, or null. */
    static Reading reading(Context ctx, String station) {
        if (station == null || station.isEmpty()) return null;
        File f = new File(ctx.getFilesDir(), FILE);
        if (!f.isFile()) return null;
        JSONArray features;
        boolean portal;
        try (InputStream in = new FileInputStream(f)) {
            JSONObject json = new JSONObject(new String(readAll(in, 2 * 1024 * 1024), StandardCharsets.UTF_8));
            features = json.optJSONArray("features");
            portal = features == null;
            if (portal) features = json.optJSONArray("data");
        } catch (Exception e) {
            Log.w(TAG, "unreadable cache: " + e);
            return null;
        }
        if (features == null) return null;
        String want = station.toLowerCase(Locale.ROOT);
        StringBuilder seen = new StringBuilder();
        Reading best = null, empty = null;
        java.util.List<Reading> others = new java.util.ArrayList<>();
        for (int i = 0; i < features.length(); i++) {
            JSONObject feature = features.optJSONObject(i);
            JSONObject a = feature == null ? null : portal ? feature : feature.optJSONObject("attributes");
            if (a == null) continue;
            String name = a.optString("STATION_LOCATION", "");
            if (a.optString("STATE_NAME", "").toLowerCase(Locale.ROOT).contains("selangor")) seen.append(name).append(" | ");
            if (!name.toLowerCase(Locale.ROOT).contains(want)) {
                int v = number(a.opt("API"));
                if (v >= 0 && a.has("LATITUDE") && a.has("LONGITUDE")) {
                    Reading o = new Reading();
                    o.api = v;
                    o.station = name;
                    o.wallTime = time(a);
                    o.lat = a.optDouble("LATITUDE");
                    o.lon = a.optDouble("LONGITUDE");
                    others.add(o);
                }
                continue;
            }
            int api = number(a.opt("API"));
            if (api < 0) {
                // Other spellings of the reading, else any numeric field named like it.
                for (String k : new String[]{"API_VALUE", "APIVALUE", "API_READING", "IPU", "READING", "VALUE"}) {
                    api = number(a.opt(k));
                    if (api >= 0) break;
                }
            }
            if (api < 0) {
                Log.w(TAG, "station " + name + " has no reading: " + a);
                if (empty == null) {
                    empty = new Reading();
                    empty.api = -1;
                    empty.station = name;
                    empty.lat = a.optDouble("LATITUDE", Double.NaN);
                    empty.lon = a.optDouble("LONGITUDE", Double.NaN);
                }
                continue;
            }
            long time = time(a);
            if (best != null && time <= best.wallTime) continue;
            best = new Reading();
            best.api = api;
            best.station = name;
            best.wallTime = time;
        }
        Log.i(TAG, others.size() + (best != null ? 1 : 0) + " of " + features.length() + " stations have a reading");
        android.content.SharedPreferences prefs = ctx.getSharedPreferences("haze", Context.MODE_PRIVATE);
        if (best != null) {
            // Remember it: around the hour the feed goes blank for a while.
            prefs.edit().putInt("api", best.api).putString("station", best.station)
                    .putLong("time", best.wallTime).putLong("saved", System.currentTimeMillis()).apply();
            return best;
        }
        if (empty != null && System.currentTimeMillis() - prefs.getLong("saved", 0) < 6 * 3600_000L
                && prefs.getString("station", "").equals(empty.station)) {
            Reading last = new Reading();
            last.api = prefs.getInt("api", -1);
            last.station = empty.station;
            last.wallTime = prefs.getLong("time", 0);
            if (last.api >= 0) return last;   // shown with its own time
        }
        if (best == null && empty != null) {
            // The station is there but silent: the nearest station that reports stands in, marked as such.
            Reading near = null;
            double nearKm = 40;
            if (!Double.isNaN(empty.lat) && !Double.isNaN(empty.lon)) {
                for (Reading o : others) {
                    double dLat = Math.toRadians(o.lat - empty.lat), dLon = Math.toRadians(o.lon - empty.lon);
                    double h = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(empty.lat))
                            * Math.cos(Math.toRadians(o.lat)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
                    double km = 6371 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
                    if (km < nearKm) {
                        nearKm = km;
                        near = o;
                    }
                }
            }
            if (near != null) {
                near.nearest = true;
                return near;
            }
            return empty;
        }
        if (best == null) Log.w(TAG, "no station named " + station + " among " + features.length()
                + " stations; Selangor: " + seen);
        return best;
    }

    /** The reading's clock time as Malaysian wall time stored as UTC: DATETIME (map service) or RECORD_DT (portal). */
    private static long time(JSONObject a) {
        long t = a.optLong("DATETIME", 0);
        if (t > 0) return t;
        String s = a.optString("RECORD_DT", "");
        try {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT);
            f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            return f.parse(s).getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    static int band(int api) {
        for (int b = 0; b < 4; b++) if (api <= HIGH[b]) return b;
        return 4;
    }

    private static int number(Object v) {
        if (v instanceof Number) return (int) Math.round(((Number) v).doubleValue());
        if (v instanceof String) {
            String digits = ((String) v).replaceAll("[^0-9]", "");
            if (!digits.isEmpty() && digits.length() < 5) return Integer.parseInt(digits);
        }
        return -1;
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
