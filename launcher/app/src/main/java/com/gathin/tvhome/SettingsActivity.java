package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
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

import java.util.List;

/**
 * The Tetapan tile: where the TV is (PlaceActivity), which home screen layout it shows, the favourite team for
 * Bola Sepak, and updates. Everything with the remote; a choice takes effect on the home screen at once.
 */
public final class SettingsActivity extends Activity {
    private LinearLayout list;
    private ScrollView scroll;
    private TextView title, subtitle;
    private Typeface font;
    private int step;
    private float d;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        d = getResources().getDisplayMetrics().density;
        font = getResources().getFont(R.font.outfit);
        FrameLayout root = new FrameLayout(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(px(28), px(24), px(28), px(20));
        GradientDrawable glass = new GradientDrawable();
        glass.setCornerRadius(18 * d);
        glass.setColor(0xF0182026);
        glass.setStroke(Math.max(1, Math.round(1.5f * d)), 0x4DFFFFFF);
        panel.setBackground(glass);
        root.addView(panel, new FrameLayout.LayoutParams(px(480), px(440), Gravity.CENTER));
        title = text(22, 0xFFF4F6F5, 500);
        subtitle = text(11, 0xB8F4F6F5, 400);
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
        TextView hint = text(9, 0x9EF4F6F5, 400);
        hint.setText("Anak panah untuk memilih, OK untuk teruskan, Back untuk kembali");
        hint.setPadding(0, px(10), 0, 0);
        panel.addView(hint);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (step == 0) showMenu();   // back from Lokasi or Kemas kini: show what changed
    }

    private void showMenu() {
        step = 0;
        title.setText("Tetapan LayarKaca");
        subtitle.setText("Versi " + Updates.installed(this));
        list.removeAllViews();
        String place = PlaceActivity.label(this);
        add("Lokasi", place != null ? place : "Belum dipilih", v -> startActivity(new Intent(this, PlaceActivity.class)));
        String display = Display.get(this);
        add("Paparan skrin utama", Display.name(display), v -> showDisplays());
        if (Display.BOLA.equals(display)) {
            String team = Display.team(this);
            add("Pasukan pilihan", team.isEmpty() ? "Tiada" : team, v -> showTeams());
        }
        String newer = Updates.newer(this);
        add("Kemas kini", newer != null ? "Versi " + newer + " tersedia" : "Terkini",
                v -> startActivity(new Intent(this, UpdateActivity.class)));
        focusFirst();
    }

    private void showDisplays() {
        step = 1;
        title.setText("Paparan skrin utama");
        subtitle.setText("Pilih dan tekan OK. Skrin utama terus bertukar; boleh tukar balik bila-bila masa.");
        list.removeAllViews();
        String now = Display.get(this);
        int focus = 0;
        for (int i = 0; i < Display.IDS.length; i++) {
            String id = Display.IDS[i];
            if (id.equals(now)) focus = i;
            addTwoLine(Display.NAMES[i] + (id.equals(now) ? "   ·   sedang digunakan" : ""), Display.NOTES[i], v -> {
                Display.set(this, id);
                reloadHome();
            });
        }
        scroll.scrollTo(0, 0);
        list.getChildAt(focus).requestFocus();
    }

    private void showTeams() {
        step = 2;
        title.setText("Pasukan pilihan");
        subtitle.setText("Perlawanan pasukan ini didahulukan dan ditanda kuning.");
        list.removeAllViews();
        add("Tiada", null, v -> {
            Display.setTeam(this, "");
            reloadHome();
        });
        List<String> teams = Bola.teams(Bola.read(this));
        for (String t : teams) add(t, null, v -> {
            Display.setTeam(this, t);
            reloadHome();
        });
        if (teams.isEmpty()) subtitle.setText("Senarai pasukan muncul selepas paparan Bola Sepak memuat turun keputusan.");
        focusFirst();
    }

    private void reloadHome() {
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_RELOAD, true));
        finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && step > 0) {
            showMenu();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void add(String name, String note, View.OnClickListener click) {
        TextView row = text(14, 0xFFF4F6F5, 400);
        row.setText(note == null ? name : name + "   ·   " + note);
        row.setPadding(px(14), 0, px(14), 0);
        row.setGravity(Gravity.CENTER_VERTICAL);
        focusable(row, click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(36));
        lp.bottomMargin = px(4);
        list.addView(row, lp);
    }

    private void addTwoLine(String name, String note, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(14), px(7), px(14), px(7));
        TextView a = text(14, 0xFFF4F6F5, 500);
        a.setText(name);
        TextView b = text(10, 0xB8F4F6F5, 400);
        b.setText(note);
        b.setPadding(0, px(3), 0, 0);
        row.addView(a);
        row.addView(b);
        focusable(row, click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = px(4);
        list.addView(row, lp);
    }

    private void focusable(View row, View.OnClickListener click) {
        row.setFocusable(true);
        row.setClickable(true);
        row.setOnClickListener(click);
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
        row.setBackground(s);
    }

    private void focusFirst() {
        scroll.scrollTo(0, 0);
        if (list.getChildCount() > 0) list.getChildAt(0).requestFocus();
    }

    private TextView text(float sp, int color, int weight) {
        TextView t = new TextView(this);
        t.setTypeface(font);
        t.setFontVariationSettings("'wght' " + weight);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return t;
    }

    private int px(float dp) {
        return Math.round(dp * d);
    }
}
