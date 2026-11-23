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
import java.util.ArrayList;
import java.util.List;

/**
 * Football scores for the Bola Sepak display, from this project's own server (bola/ in the repository), which
 * holds the API-Football key and asks API-Football for every TV at once. Only a TV showing Bola Sepak asks:
 * every two minutes while a match is live, every half hour otherwise. Nothing is sent but the request.
 */
final class Bola {
    private static final String TAG = "TvHome.Bola";
    static final String URL_FEED = "https://layarkaca-bola.pages.dev/bola.json";
    private static final String FILE = "bola.json";
    private static final long LIVE_MS = 2 * 60_000L, IDLE_MS = 30 * 60_000L;
    private static volatile long lastFailure;

    static final class Match {
        long id, ko;
        String comp = "", home = "", away = "", st = "NS";
        int hs = -1, as = -1, min = -1;
        List<String> goalsHome = new ArrayList<>(), goalsAway = new ArrayList<>();

        boolean involves(String team) {
            return team != null && !team.isEmpty() && (team.equals(home) || team.equals(away));
        }
    }

    static final class Feed {
        long updated;
        final List<Match> live = new ArrayList<>(), upcoming = new ArrayList<>(), results = new ArrayList<>();
    }

    private Bola() {
    }

    static boolean due(Context ctx) {
        File f = new File(ctx.getFilesDir(), FILE);
        if (!f.isFile() || f.length() == 0) return System.currentTimeMillis() - lastFailure > 5 * 60_000L;
        Feed feed = read(ctx);
        long age = System.currentTimeMillis() - f.lastModified();
        return age > (feed != null && !feed.live.isEmpty() ? LIVE_MS : IDLE_MS) || age < 0;
    }

    /** Downloads the feed. Blocking: never on the main thread. */
    static void refresh(Context ctx) {
        if (System.currentTimeMillis() - lastFailure < 5 * 60_000L) return;
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(URL_FEED).openConnection();
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(15_000);
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            if (code != 200) throw new Exception("HTTP " + code);
            byte[] body;
            try (InputStream in = conn.getInputStream()) {
                body = readAll(in, 256 * 1024);
            }
            parse(new String(body, StandardCharsets.UTF_8));   // refuse what we cannot read
            File f = new File(ctx.getFilesDir(), FILE), tmp = new File(ctx.getFilesDir(), FILE + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(body);
            }
            if (!tmp.renameTo(f)) throw new Exception("could not save");
        } catch (Exception e) {
            lastFailure = System.currentTimeMillis();
            Log.w(TAG, "refresh failed: " + e);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** The last downloaded feed, or null. */
    static Feed read(Context ctx) {
        File f = new File(ctx.getFilesDir(), FILE);
        if (!f.isFile()) return null;
        try (InputStream in = new FileInputStream(f)) {
            return parse(new String(readAll(in, 256 * 1024), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    static Feed parse(String text) throws Exception {
        JSONObject json = new JSONObject(text);
        Feed feed = new Feed();
        feed.updated = json.optLong("updated");
        list(json.optJSONArray("live"), feed.live);
        list(json.optJSONArray("upcoming"), feed.upcoming);
        list(json.optJSONArray("results"), feed.results);
        return feed;
    }

    private static void list(JSONArray a, List<Match> out) {
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            Match m = new Match();
            m.id = o.optLong("id");
            m.ko = o.optLong("ko");
            m.comp = o.optString("comp");
            m.home = o.optString("home");
            m.away = o.optString("away");
            m.st = o.optString("st", "NS");
            m.hs = o.isNull("hs") ? -1 : o.optInt("hs", -1);
            m.as = o.isNull("as") ? -1 : o.optInt("as", -1);
            m.min = o.isNull("min") ? -1 : o.optInt("min", -1);
            JSONObject g = o.optJSONObject("goals");
            if (g != null) {
                strings(g.optJSONArray("h"), m.goalsHome);
                strings(g.optJSONArray("a"), m.goalsAway);
            }
            out.add(m);
        }
    }

    private static void strings(JSONArray a, List<String> out) {
        for (int i = 0; a != null && i < a.length(); i++) out.add(a.optString(i));
    }

    /** The minute or state of a live match: "67'", "Rehat", "Penalti"... */
    static String clock(Match m) {
        switch (m.st) {
            case "HT": return "Rehat";
            case "BT": return "Rehat";
            case "P": return "Penalti";
            case "SUSP": case "INT": return "Tergendala";
            default: return m.min >= 0 ? m.min + "'" : "Langsung";
        }
    }

    /** Every team in the feed, sorted, for picking a favourite. */
    static List<String> teams(Feed feed) {
        java.util.TreeSet<String> set = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (feed != null) {
            for (List<Match> l : java.util.Arrays.asList(feed.live, feed.upcoming, feed.results)) {
                for (Match m : l) {
                    set.add(m.home);
                    set.add(m.away);
                }
            }
        }
        return new ArrayList<>(set);
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
