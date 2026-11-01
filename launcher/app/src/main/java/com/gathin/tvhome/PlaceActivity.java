package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.List;

/**
 * Choosing where the TV is with the remote: state, then area, then the air quality station (the nearest
 * one first). Sets the prayer zone, the MET district, the name on the cards and the station in one go,
 * so nobody has to edit config.json. Saved on the TV; overrides those four keys of a theme (Config).
 */
public final class PlaceActivity extends Activity {
    static final String PREFS = "place";
    private LinearLayout list;
    private ScrollView scroll;
    private TextView title, subtitle;
    private int step;
    private String state;
    private String[] area;
    private float d;

    /** The area chosen on this TV, or null. */
    static String label(Context ctx) {
        return ctx.getSharedPreferences(PREFS, MODE_PRIVATE).getString("weather_place", null);
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        d = getResources().getDisplayMetrics().density;
        Typeface font = getResources().getFont(R.font.outfit);

        FrameLayout root = new FrameLayout(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(px(28), px(24), px(28), px(20));
        GradientDrawable glass = new GradientDrawable();
        glass.setCornerRadius(18 * d);
        glass.setColor(0xF0182026);
        glass.setStroke(Math.max(1, Math.round(1.5f * d)), 0x4DFFFFFF);
        panel.setBackground(glass);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(px(460), px(440), Gravity.CENTER);
        root.addView(panel, plp);

        title = text(font, 22, 0xFFF4F6F5, 500);
        subtitle = text(font, 11, 0xB8F4F6F5, 400);
        subtitle.setPadding(0, px(6), 0, px(14));
        panel.addView(title);
        panel.addView(subtitle);
        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(px(6), px(4), px(6), px(4));
        scroll.addView(list);
        panel.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        TextView hint = text(font, 9, 0x9EF4F6F5, 400);
        hint.setText("Anak panah untuk memilih, OK untuk teruskan, Back untuk kembali");
        hint.setPadding(0, px(10), 0, 0);
        panel.addView(hint);
        setContentView(root);
        showStates();
    }

    private void showStates() {
        step = 0;
        title.setText("Di mana TV ini?");
        String now = label(this);
        subtitle.setText(now != null ? "Sekarang: " + now + ". Pilih negeri anda." : "Pilih negeri anda.");
        list.removeAllViews();
        for (String s : Places.STATES) add(s, null, v -> {
            state = s;
            showAreas();
        });
        focusFirst();
    }

    private void showAreas() {
        step = 1;
        title.setText(state);
        subtitle.setText("Pilih kawasan anda. Waktu solat ikut zon JAKIM kawasan ini.");
        list.removeAllViews();
        for (String[] a : Places.areasOf(state)) add(a[1], a[2], v -> {
            area = a;
            showStations();
        });
        focusFirst();
    }

    private void showStations() {
        step = 2;
        title.setText(area[1]);
        subtitle.setText("Stesen kualiti udara (IPU). Yang terdekat dicadangkan di atas.");
        list.removeAllViews();
        List<String> stations = Places.stationsFor(area);
        for (int i = 0; i < stations.size(); i++) {
            String s = stations.get(i);
            add(s, i == 0 ? "dicadangkan" : null, v -> save(s));
        }
        focusFirst();
    }

    private void save(String station) {
        SharedPreferences.Editor e = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        e.putString("prayer_zone", area[2]);
        e.putString("weather_location", area[3]);
        e.putString("weather_place", area[1]);
        e.putString("haze_station", station);
        e.commit();
        // The forecast and warnings were for the old district: fetch them again.
        new File(getFilesDir(), "met-forecast.json").delete();
        new File(getFilesDir(), "met-warnings.json").delete();
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_RELOAD, true));
        finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && step > 0) {
            if (step == 2) showAreas();
            else showStates();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void add(String name, String note, View.OnClickListener click) {
        TextView row = text(getResources().getFont(R.font.outfit), 14, 0xFFF4F6F5, 400);
        row.setText(note == null ? name : name + "   ·   " + note);
        row.setPadding(px(14), 0, px(14), 0);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setOnClickListener(click);
        row.setBackground(rowBackground());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(34));
        lp.bottomMargin = px(4);
        list.addView(row, lp);
    }

    private void focusFirst() {
        scroll.scrollTo(0, 0);
        if (list.getChildCount() > 0) list.getChildAt(0).requestFocus();
    }

    private StateListDrawable rowBackground() {
        GradientDrawable focused = new GradientDrawable();
        focused.setCornerRadius(10 * d);
        focused.setColor(0x33FFFFFF);
        focused.setStroke(Math.max(2, Math.round(2 * d)), 0xEBFFFFFF);
        GradientDrawable normal = new GradientDrawable();
        normal.setCornerRadius(10 * d);
        normal.setColor(0x0AFFFFFF);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, focused);
        s.addState(new int[]{}, normal);
        return s;
    }

    private TextView text(Typeface font, float sp, int color, int weight) {
        TextView t = new TextView(this);
        t.setTypeface(font);
        t.setFontVariationSettings("'wght' " + weight);   // Outfit is a variable font; works from Android 8
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        return t;
    }

    private int px(float dp) {
        return Math.round(dp * d);
    }
}
