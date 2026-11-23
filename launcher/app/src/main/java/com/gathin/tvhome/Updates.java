package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tells the home screen when a newer LayarKaca is on GitHub, so a TV set up once does not stay on an old version
 * without anyone knowing. Once a day it asks GitHub for the latest release's tag; nothing is sent but that request.
 * The release's APK and its SHA-256 are remembered too, so the Kemas kini tile (UpdateActivity) can install the
 * update on the TV itself; the installer (pasang.sh / pasang.ps1) still works as before.
 */
final class Updates {
    private static final String TAG = "TvHome.Update";
    static final String LATEST = "https://api.github.com/repos/encikzul/layarkaca-tv/releases/latest";
    private static final String PREFS = "updates";
    private static final String KEY_LATEST = "latest";
    private static final String KEY_CHECKED = "checked";
    private static final String KEY_APK = "apk_url";
    private static final String KEY_SHA = "sha_url";
    /** Release files are only ever downloaded from this repository's own Releases. */
    static final String DOWNLOADS = "https://github.com/encikzul/layarkaca-tv/releases/download/";
    private static final long DAY_MS = 24 * 60 * 60_000L;
    private static final Pattern VERSION = Pattern.compile("^v?(\\d+(?:\\.\\d+)*)$");

    private Updates() {
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** True when the last check is more than a day old (or in the future, after the TV's clock was wrong). */
    static boolean due(Context ctx) {
        long checked = prefs(ctx).getLong(KEY_CHECKED, 0), now = System.currentTimeMillis();
        return now - checked > DAY_MS || checked > now + DAY_MS;
    }

    /** Asks GitHub for the latest release and remembers its version. Blocking: never on the main thread. */
    static void check(Context ctx) {
        // Counted as checked even when it fails, so a TV without internet asks once a day, not on every Home.
        prefs(ctx).edit().putLong(KEY_CHECKED, System.currentTimeMillis()).apply();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(LATEST).openConnection();
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(15_000);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("User-Agent", "LayarKaca/" + installed(ctx));
            int code = conn.getResponseCode();
            if (code != 200) throw new Exception("HTTP " + code);
            byte[] body;
            try (InputStream in = conn.getInputStream()) {
                body = readAll(in, 256 * 1024);
            }
            JSONObject release = new JSONObject(new String(body, StandardCharsets.UTF_8));
            String tag = release.optString("tag_name", "");
            String version = clean(tag);
            if (version == null) throw new Exception("unreadable tag '" + tag + "'");
            String apk = null, sha = null;
            JSONArray assets = release.optJSONArray("assets");
            for (int i = 0; assets != null && i < assets.length(); i++) {
                JSONObject a = assets.optJSONObject(i);
                String url = a != null ? a.optString("browser_download_url", "") : "";
                if (!isReleaseFile(url)) continue;
                if (url.endsWith(".apk")) apk = url;
                else if (url.endsWith(".apk.sha256")) sha = url;
            }
            prefs(ctx).edit().putString(KEY_LATEST, version).putString(KEY_APK, apk).putString(KEY_SHA, sha).apply();
            Log.i(TAG, "latest release " + version + ", installed " + installed(ctx));
        } catch (Exception e) {
            Log.w(TAG, "update check failed: " + e);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** The newer version on GitHub ("1.2"), or null when this one is current or nothing is known yet. */
    static String newer(Context ctx) {
        String latest = prefs(ctx).getString(KEY_LATEST, null);
        return isNewer(latest, installed(ctx)) ? latest : null;
    }

    /** The newer release's APK and SHA-256 file, or null when either is missing. */
    static String[] files(Context ctx) {
        SharedPreferences p = prefs(ctx);
        String apk = p.getString(KEY_APK, null), sha = p.getString(KEY_SHA, null);
        return isReleaseFile(apk) && isReleaseFile(sha) ? new String[]{apk, sha} : null;
    }

    /** True for a file on this repository's Releases page over https, and nothing else. */
    static boolean isReleaseFile(String url) {
        if (url == null || !url.startsWith(DOWNLOADS)) return false;
        String rest = url.substring(DOWNLOADS.length());
        return rest.matches("v?\\d+(\\.\\d+)*/[A-Za-z0-9._-]+") && !rest.contains("..");
    }

    /** The 64 hex digits at the start of a .sha256 file ("hash  file.apk"), lower case; null otherwise. */
    static String shaOf(String text) {
        Matcher m = Pattern.compile("^\\s*([0-9A-Fa-f]{64})(\\s|$)").matcher(text == null ? "" : text);
        return m.find() ? m.group(1).toLowerCase(java.util.Locale.ROOT) : null;
    }

    /** This app's versionName, e.g. "1.1"; "" when Android cannot say. */
    static String installed(Context ctx) {
        try {
            String v = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
            return v != null ? v : "";
        } catch (Exception e) {
            return "";
        }
    }

    /** "v1.2" or "1.2" as "1.2"; null when it is not a plain version number. */
    static String clean(String tag) {
        Matcher m = VERSION.matcher(tag == null ? "" : tag.trim());
        return m.matches() ? m.group(1) : null;
    }

    /** True when {@code latest} is a higher version than {@code installed}: 1.10 is above 1.9, 1.1.1 above 1.1. */
    static boolean isNewer(String latest, String installed) {
        String a = clean(latest), b = clean(installed);
        if (a == null || b == null) return false;
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            long p = i < x.length ? parse(x[i]) : 0, q = i < y.length ? parse(y[i]) : 0;
            if (p != q) return p > q;
        }
        return false;
    }

    private static long parse(String part) {
        try {
            return Long.parseLong(part);
        } catch (NumberFormatException e) {
            return 0;   // longer than a long: treat as unknown rather than crash
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
