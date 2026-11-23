package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The panel that takes the three cards' place in the Fokus Solat, Maklumat Penuh and Bola Sepak displays
 * (Tetapan › Paparan). Built in code and rebuilt on every refresh: it is small, and the data changes as a whole.
 */
final class AltPanel {
    private static final int WHITE = 0xFFF4F6F5, DIM = 0xB8F4F6F5, FAINT = 0x8CF4F6F5, AMBER = 0xFFF4B740,
            RAIN = 0xFF6FB1FF, RED = 0xFFE5484D, NONE = 0x24FFFFFF;
    private static final String[] DAYS = {"Ahd", "Isn", "Sel", "Rab", "Kha", "Jum", "Sab"};
    private static final String[] DAYS_LONG = {"Ahad", "Isnin", "Selasa", "Rabu", "Khamis", "Jumaat", "Sabtu"};
    private final Context ctx;
    private final FrameLayout root;
    private final float d;
    private final Typeface font;

    AltPanel(Context ctx, FrameLayout root) {
        this.ctx = ctx;
        this.root = root;
        this.d = ctx.getResources().getDisplayMetrics().density;
        this.font = ctx.getResources().getFont(R.font.outfit);
    }

    // ---- B. Fokus Solat --------------------------------------------------------------------------

    void showFocus(String zone, String place, Weather.Today wx, Haze.Reading hz) {
        root.removeAllViews();
        long now = System.currentTimeMillis();
        long[] today = zone.isEmpty() ? null : Prayer.times(ctx, zone, Calendar.getInstance());
        long[] next = zone.isEmpty() ? null : Prayer.next(ctx, zone, now);
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        if (today == null || next == null) {
            col.addView(text(14, DIM, 400, zone.isEmpty() ? "Pilih lokasi di Tetapan untuk waktu solat."
                    : "Waktu solat belum dimuat turun."));
            return;
        }
        col.addView(text(11, DIM, 400, "Seterusnya"));
        TextView big = text(34, AMBER, 500, Prayer.NAMES[(int) next[0]] + " " + time(next[1], true));
        big.setPadding(0, px(2), 0, px(2));
        col.addView(big);
        col.addView(text(13, WHITE, 400, "dalam " + countdown(next[1], now)));
        LinearLayout boxes = new LinearLayout(ctx);
        boxes.setPadding(0, px(12), 0, px(10));
        for (int p = 0; p < 5; p++) {
            LinearLayout box = new LinearLayout(ctx);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER);
            boolean on = today[p] == next[1];
            box.setBackground(rounded(10, on ? 0x1FF4B740 : 0x29182026, on ? AMBER : 0x40FFFFFF));
            box.addView(text(10, on ? AMBER : DIM, 400, Prayer.NAMES[p]));
            box.addView(text(17, WHITE, 400, time(today[p], false)));
            box.setAlpha(today[p] <= now ? 0.5f : 1f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(80), px(48));
            if (p > 0) lp.leftMargin = px(8);
            boxes.addView(box, lp);
        }
        col.addView(boxes);
        StringBuilder info = new StringBuilder(place.isEmpty() ? "" : place);
        if (wx != null) info.append(info.length() > 0 ? "   ·   " : "").append(wx.max).append("° ").append(shortWeather(wx.summary));
        if (hz != null && hz.api >= 0) {
            info.append(info.length() > 0 ? "   ·   " : "").append("IPU ").append(hz.api).append(' ')
                    .append(Haze.BANDS[Haze.band(hz.api)]);
        }
        if (info.length() > 0) col.addView(text(11, DIM, 400, info.toString()));
    }

    // ---- D. Maklumat Penuh -----------------------------------------------------------------------

    void showInfo(String zone, List<Weather.Today> days, Haze.Reading hz, String station) {
        root.removeAllViews();
        LinearLayout row = new LinearLayout(ctx);
        root.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        row.addView(prayerListCard(zone), weight(1f, 0));
        row.addView(weekCard(days), weight(1.9f, 12));
        row.addView(hazeCard(hz, station), weight(1f, 12));
    }

    private View prayerListCard(String zone) {
        LinearLayout c = card();
        c.addView(label("Waktu solat"));
        long now = System.currentTimeMillis();
        long[] today = zone.isEmpty() ? null : Prayer.times(ctx, zone, Calendar.getInstance());
        long[] next = zone.isEmpty() ? null : Prayer.next(ctx, zone, now);
        if (today == null || next == null) {
            c.addView(text(11, DIM, 400, "Belum dimuat turun."));
            return c;
        }
        TextView n = text(20, AMBER, 500, Prayer.NAMES[(int) next[0]] + " " + time(next[1], false));
        n.setPadding(0, px(6), 0, px(2));
        c.addView(n);
        c.addView(text(10, WHITE, 400, "dalam " + countdown(next[1], now)));
        c.addView(spacer());
        for (int p = 0; p < 5; p++) {
            boolean on = today[p] == next[1];
            LinearLayout line = new LinearLayout(ctx);
            TextView name = text(11, on ? AMBER : DIM, on ? 500 : 400, Prayer.NAMES[p]);
            line.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            line.addView(text(11, on ? AMBER : WHITE, on ? 500 : 400, time(today[p], false)));
            line.setAlpha(today[p] <= now && !on ? 0.55f : 1f);
            line.setPadding(0, px(2), 0, px(2));
            c.addView(line);
        }
        return c;
    }

    private View weekCard(List<Weather.Today> days) {
        LinearLayout c = card();
        if (days.isEmpty()) {
            c.addView(label("Cuaca · 7 hari"));
            c.addView(text(11, DIM, 400, "Ramalan belum dimuat turun."));
            return c;
        }
        Weather.Today t = days.get(0);
        LinearLayout head = new LinearLayout(ctx);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(icon(Weather.kind(t.summary), false, 30));
        LinearLayout temps = new LinearLayout(ctx);
        temps.setOrientation(LinearLayout.VERTICAL);
        temps.setPadding(px(10), 0, 0, 0);
        temps.addView(text(22, WHITE, 400, t.max + "° / " + t.min + "°"));
        String when = t.when.isEmpty() || t.when.equalsIgnoreCase("tiada") ? "" : " · " + t.when.toLowerCase(Locale.ROOT);
        temps.addView(text(9.5f, DIM, 400, t.summary + when));
        head.addView(temps, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView lab = label("Cuaca · 7 hari");
        head.addView(lab);
        c.addView(head);
        c.addView(spacer());
        LinearLayout week = new LinearLayout(ctx);
        for (int i = 0; i < days.size(); i++) {
            Weather.Today day = days.get(i);
            LinearLayout cell = new LinearLayout(ctx);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(px(3), px(5), px(3), px(5));
            if (i == 0) cell.setBackground(rounded(8, 0x24FFFFFF, 0));
            cell.addView(text(9.5f, WHITE, 500, dayName(day.date, false)));
            ImageView ic = icon(Weather.kind(day.summary), false, 18);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(px(18), px(18));
            ilp.topMargin = px(3);
            ilp.bottomMargin = px(3);
            cell.addView(ic, ilp);
            cell.addView(text(9, WHITE, 400, day.max + "° " + day.min + "°"));
            LinearLayout bars = new LinearLayout(ctx);
            bars.setPadding(0, px(4), 0, 0);
            String[] parts = {day.morning, day.afternoon, day.night};
            for (int p = 0; p < 3; p++) {
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, px(3), 1f);
                if (p > 0) blp.leftMargin = px(2);
                bars.addView(bar(Weather.kind(parts[p])), blp);
            }
            cell.addView(bars, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.leftMargin = px(3);
            week.addView(cell, lp);
        }
        c.addView(week);
        TextView legend = text(8, FAINT, 400, "Bar: pagi · petang · malam   ·   biru hujan, kuning ribut petir   ·   MET Malaysia");
        legend.setPadding(0, px(5), 0, 0);
        c.addView(legend);
        return c;
    }

    private View hazeCard(Haze.Reading hz, String station) {
        LinearLayout c = card();
        c.addView(label("Kualiti udara · IPU"));
        if (hz == null || hz.api < 0) {
            c.addView(text(11, DIM, 400, station.isEmpty() ? "Pilih lokasi di Tetapan." : "Tiada bacaan buat masa ini."));
            return c;
        }
        int band = Haze.band(hz.api);
        LinearLayout v = new LinearLayout(ctx);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(0, px(6), 0, px(6));
        v.addView(text(26, WHITE, 400, String.valueOf(hz.api)));
        TextView chip = text(9.5f, band == 4 ? 0xFFFFFFFF : 0xFF0B0F12, 500, Haze.BANDS[band]);
        chip.setPadding(px(8), px(3), px(8), px(3));
        chip.setBackground(rounded(20, Haze.COLORS[band], 0));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = px(8);
        v.addView(chip, clp);
        c.addView(v);
        // The same coloured scale as the Klasik card: Baik to Berbahaya, with a mark at this reading.
        HazeBar scale = new HazeBar(ctx, null);
        scale.setValue(hz.api);
        c.addView(scale, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(12)));
        LinearLayout marks = new LinearLayout(ctx);
        marks.setPadding(0, px(3), 0, 0);
        for (String m : new String[]{"0", "51", "101", "201", "301+"}) {
            marks.addView(text(7.5f, 0xA6F4F6F5, 400, m), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        c.addView(marks);
        c.addView(spacer());
        String[] notes = {"Udara bersih.", "Tiada jerebu. Selamat untuk aktiviti luar.", "Kurangkan aktiviti luar.",
                "Elakkan aktiviti luar. Tutup tingkap.", "Kekal di dalam rumah."};
        TextView note = text(10, WHITE, 400, notes[band]);
        note.setSingleLine(false);
        note.setMaxLines(2);
        c.addView(note);
        String s = hz.station.contains(",") ? hz.station.substring(0, hz.station.indexOf(',')) : hz.station;
        TextView src = text(8, FAINT, 400, "JAS APIMS · " + s);
        src.setPadding(0, px(4), 0, 0);
        c.addView(src);
        return c;
    }

    // ---- E. Bola Sepak ---------------------------------------------------------------------------

    void showBola(Bola.Feed feed, String team, Weather.Today wx, Haze.Reading hz) {
        root.removeAllViews();
        LinearLayout row = new LinearLayout(ctx);
        root.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout left = new LinearLayout(ctx);
        left.setOrientation(LinearLayout.VERTICAL);
        row.addView(left, weight(1.25f, 0));
        if (feed == null) {
            LinearLayout c = card();
            c.addView(label("Bola sepak"));
            c.addView(text(12, DIM, 400, "Memuatkan keputusan..."));
            left.addView(c, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        } else if (!feed.live.isEmpty()) {
            List<Bola.Match> live = favouriteFirst(feed.live, team);
            left.addView(liveCard(live.get(0), team), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.4f));
            if (live.size() > 1) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.6f);
                lp.topMargin = px(10);
                left.addView(liveRow(live.get(1), team), lp);
            }
        } else {
            left.addView(nextCard(favouriteFirst(feed.upcoming, team), team),
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        row.addView(resultsCard(feed, team, wx, hz), weight(1f, 12));
    }

    private View liveCard(Bola.Match m, String team) {
        LinearLayout c = card();
        LinearLayout head = new LinearLayout(ctx);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView chip = text(8.5f, 0xFFFFFFFF, 600, "LANGSUNG");
        chip.setLetterSpacing(0.1f);
        chip.setPadding(px(7), px(3), px(7), px(3));
        chip.setBackground(rounded(20, RED, 0));
        head.addView(chip);
        TextView comp = text(10, DIM, 400, m.comp);
        comp.setPadding(px(8), 0, 0, 0);
        head.addView(comp, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(text(14, AMBER, 600, Bola.clock(m)));
        c.addView(head);
        c.addView(spacer());
        LinearLayout score = new LinearLayout(ctx);
        score.setGravity(Gravity.CENTER_VERTICAL);
        TextView h = text(15, m.home.equals(team) ? AMBER : WHITE, 500, m.home);
        score.addView(h, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView s = text(28, WHITE, 600, (m.hs < 0 ? 0 : m.hs) + " – " + (m.as < 0 ? 0 : m.as));
        s.setPadding(px(10), 0, px(10), 0);
        score.addView(s);
        TextView a = text(15, m.away.equals(team) ? AMBER : WHITE, 500, m.away);
        a.setGravity(Gravity.END);
        score.addView(a, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        c.addView(score);
        c.addView(spacer());
        LinearLayout goals = new LinearLayout(ctx);
        TextView gh = text(9, DIM, 400, TextUtils.join(", ", m.goalsHome));
        goals.addView(gh, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView ga = text(9, DIM, 400, TextUtils.join(", ", m.goalsAway));
        ga.setGravity(Gravity.END);
        goals.addView(ga, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        c.addView(goals);
        return c;
    }

    private View liveRow(Bola.Match m, String team) {
        LinearLayout c = card();
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(ctx);
        dot.setBackground(rounded(4, RED, 0));
        c.addView(dot, new LinearLayout.LayoutParams(px(6), px(6)));
        TextView comp = text(9.5f, DIM, 400, m.comp + " · " + Bola.clock(m));
        comp.setPadding(px(6), 0, 0, 0);
        c.addView(comp, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        c.addView(text(12, m.involves(team) ? AMBER : WHITE, 400,
                m.home + "  " + (m.hs < 0 ? 0 : m.hs) + " – " + (m.as < 0 ? 0 : m.as) + "  " + m.away));
        return c;
    }

    private View nextCard(List<Bola.Match> upcoming, String team) {
        LinearLayout c = card();
        c.addView(label("Perlawanan seterusnya"));
        if (upcoming.isEmpty()) {
            c.addView(text(12, DIM, 400, "Tiada perlawanan dalam tiga hari."));
            return c;
        }
        Bola.Match m = upcoming.get(0);
        c.addView(spacer());
        c.addView(text(10, DIM, 400, m.comp));
        TextView teams = text(17, m.involves(team) ? AMBER : WHITE, 500, m.home + "  lwn  " + m.away);
        teams.setPadding(0, px(4), 0, px(4));
        c.addView(teams);
        c.addView(text(13, AMBER, 400, kickoff(m.ko)));
        c.addView(spacer());
        for (int i = 1; i < Math.min(3, upcoming.size()); i++) {
            Bola.Match o = upcoming.get(i);
            TextView t = text(10, o.involves(team) ? AMBER : DIM, 400, kickoff(o.ko) + "   " + o.home + " lwn " + o.away);
            t.setPadding(0, px(2), 0, 0);
            c.addView(t);
        }
        return c;
    }

    private View resultsCard(Bola.Feed feed, String team, Weather.Today wx, Haze.Reading hz) {
        LinearLayout c = card();
        LinearLayout head = new LinearLayout(ctx);
        head.addView(label("Keputusan"), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        StringBuilder side = new StringBuilder();
        if (wx != null) side.append(wx.max).append("° ").append(shortWeather(wx.summary));
        if (hz != null && hz.api >= 0) side.append(side.length() > 0 ? "  ·  " : "").append("IPU ").append(hz.api);
        head.addView(text(9, DIM, 400, side.toString()));
        c.addView(head);
        List<Bola.Match> results = feed == null ? new ArrayList<>() : favouriteFirst(feed.results, team);
        if (results.isEmpty()) {
            TextView none = text(11, DIM, 400, feed == null ? "" : "Tiada keputusan semalam dan hari ini.");
            none.setPadding(0, px(8), 0, 0);
            c.addView(none);
        }
        for (int i = 0; i < Math.min(5, results.size()); i++) {
            Bola.Match m = results.get(i);
            LinearLayout line = new LinearLayout(ctx);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(0, px(i == 0 ? 6 : 3), 0, px(3));
            line.addView(text(7.5f, FAINT, 600, code(m.comp)), new LinearLayout.LayoutParams(px(30), ViewGroup.LayoutParams.WRAP_CONTENT));
            boolean hw = m.hs > m.as, aw = m.as > m.hs;
            TextView h = text(11, m.home.equals(team) ? AMBER : hw ? WHITE : DIM, hw ? 600 : 400, m.home);
            line.addView(h, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView s = text(12, WHITE, 600, m.hs + " – " + m.as);
            s.setGravity(Gravity.CENTER);
            line.addView(s, new LinearLayout.LayoutParams(px(44), ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView a = text(11, m.away.equals(team) ? AMBER : aw ? WHITE : DIM, aw ? 600 : 400, m.away);
            a.setGravity(Gravity.END);
            line.addView(a, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            c.addView(line);
        }
        c.addView(spacer());
        String ago = "";
        if (feed != null && feed.updated > 0) {
            long mins = Math.max(0, (System.currentTimeMillis() - feed.updated) / 60_000L);
            ago = mins < 1 ? " · baru dikemas kini" : " · " + (mins < 60 ? mins + " minit lalu" : (mins / 60) + " jam lalu");
        }
        c.addView(text(7.5f, FAINT, 400, "Data: API-Football" + ago));
        return c;
    }

    private static List<Bola.Match> favouriteFirst(List<Bola.Match> in, String team) {
        List<Bola.Match> out = new ArrayList<>();
        for (Bola.Match m : in) if (m.involves(team)) out.add(m);
        for (Bola.Match m : in) if (!m.involves(team)) out.add(m);
        return out;
    }

    /** A short tag for the results list. */
    static String code(String comp) {
        switch (comp) {
            case "Liga Super": return "LSM";
            case "Piala Malaysia": return "PM";
            case "Piala FA": return "PFA";
            case "Piala MFL": return "MFL";
            case "EPL": return "EPL";
            case "Liga Juara-Juara": return "UCL";
            default:
                String w = comp.trim().split("\\s+")[0].toUpperCase(Locale.ROOT);
                return w.length() > 4 ? w.substring(0, 4) : w;
        }
    }

    private String kickoff(long ko) {
        Calendar c = Calendar.getInstance(), now = Calendar.getInstance();
        c.setTimeInMillis(ko);
        String day;
        int diff = (int) ((startOfDay(c) - startOfDay(now)) / 86_400_000L);
        day = diff == 0 ? "Hari ini" : diff == 1 ? "Esok" : DAYS_LONG[c.get(Calendar.DAY_OF_WEEK) - 1];
        return day + ", " + time(ko, true);
    }

    private static long startOfDay(Calendar c) {
        Calendar x = (Calendar) c.clone();
        x.set(Calendar.HOUR_OF_DAY, 0);
        x.set(Calendar.MINUTE, 0);
        x.set(Calendar.SECOND, 0);
        x.set(Calendar.MILLISECOND, 0);
        return x.getTimeInMillis();
    }

    // ---- shared pieces ---------------------------------------------------------------------------

    /** "Ribut petir di beberapa tempat" becomes "Ribut petir". */
    static String shortWeather(String s) {
        String out = s == null ? "" : s.replace(" di beberapa tempat", "").replace(" di kebanyakan tempat", "")
                .replace(" di satu dua tempat", "").trim();
        return out;
    }

    static int iconRes(int kind, boolean night) {
        switch (kind) {
            case 0: return night ? R.drawable.ic_wx_moon : R.drawable.ic_wx_sun;
            case 1: return R.drawable.ic_wx_rain;
            case 2: return R.drawable.ic_wx_storm;
            default: return R.drawable.ic_wx_cloud;
        }
    }

    static int barColor(int kind) {
        return kind == 1 ? RAIN : kind == 2 ? AMBER : NONE;
    }

    private String dayName(String date, boolean full) {
        try {
            Date dt = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(date);
            Calendar c = Calendar.getInstance();
            c.setTime(dt);
            int i = c.get(Calendar.DAY_OF_WEEK) - 1;
            return full ? DAYS_LONG[i] : DAYS[i];
        } catch (Exception e) {
            return "";
        }
    }

    private String time(long ms, boolean ampm) {
        boolean h24 = android.text.format.DateFormat.is24HourFormat(ctx);
        return new SimpleDateFormat(h24 ? "H:mm" : ampm ? "h:mm a" : "h:mm", Locale.ROOT).format(new Date(ms));
    }

    private static String countdown(long target, long now) {
        long mins = Math.max(0, (target - now + 59_999L) / 60_000L);
        return mins >= 60 ? (mins / 60) + " jam " + (mins % 60) + " minit" : mins + " minit";
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(ctx);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(px(14), px(12), px(14), px(10));
        c.setBackground(new GlassDrawable(c, 16 * d, d, null, 0.5f));
        return c;
    }

    private TextView label(String s) {
        TextView t = text(8.5f, DIM, 500, s.toUpperCase(Locale.ROOT));
        t.setLetterSpacing(0.14f);
        return t;
    }

    private TextView text(float sp, int color, int weight, String s) {
        TextView t = new TextView(ctx);
        t.setTypeface(font);
        t.setFontVariationSettings("'wght' " + weight);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setText(s);
        return t;
    }

    private ImageView icon(int kind, boolean night, int sizeDp) {
        ImageView v = new ImageView(ctx);
        v.setImageResource(iconRes(kind, night));
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        v.setLayoutParams(new LinearLayout.LayoutParams(px(sizeDp), px(sizeDp)));
        return v;
    }

    private View bar(int kind) {
        View v = new View(ctx);
        v.setBackground(rounded(2, barColor(kind), 0));
        return v;
    }

    private GradientDrawable rounded(float radiusDp, int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(radiusDp * d);
        g.setColor(fill);
        if (stroke != 0) g.setStroke(Math.max(1, Math.round(1.2f * d)), stroke);
        return g;
    }

    private View spacer() {
        View v = new View(ctx);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, 0, 1f));
        return v;
    }

    private LinearLayout.LayoutParams weight(float w, int leftMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, w);
        lp.leftMargin = px(leftMarginDp);
        return lp;
    }

    private int px(float dp) {
        return Math.round(dp * d);
    }
}
