package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

/** Which home screen layout this TV shows (Tetapan › Paparan), and the favourite team for Bola Sepak. */
final class Display {
    static final String KLASIK = "klasik", FOKUS = "fokus", MINIMAL = "minimal", MAKLUMAT = "maklumat", BOLA = "bola";
    static final String[] IDS = {KLASIK, FOKUS, MINIMAL, MAKLUMAT, BOLA};
    static final String[] NAMES = {"Klasik", "Fokus Solat", "Minimal", "Maklumat Penuh", "Bola Sepak"};
    static final String[] NOTES = {
            "Waktu solat, cuaca dan IPU dalam tiga kad.",
            "Waktu solat seterusnya besar di tengah.",
            "Jam dan satu baris waktu solat; gambar latar penuh.",
            "Solat, ramalan cuaca 7 hari dan IPU.",
            "Keputusan dan skor langsung: Malaysia, EPL, Liga Juara-Juara."};
    private static final String PREFS = "display", KEY = "mode", TEAM = "team";

    private Display() {
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The chosen layout; Klasik when none was chosen or the saved one is unknown. */
    static String get(Context ctx) {
        return valid(prefs(ctx).getString(KEY, KLASIK));
    }

    static void set(Context ctx, String id) {
        prefs(ctx).edit().putString(KEY, valid(id)).commit();
    }

    static String valid(String id) {
        for (String s : IDS) if (s.equals(id)) return s;
        return KLASIK;
    }

    static String name(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return NAMES[i];
        return NAMES[0];
    }

    /** B, D and E draw their own panel in place of the three cards. */
    static boolean ownPanel(String id) {
        return FOKUS.equals(id) || MAKLUMAT.equals(id) || BOLA.equals(id);
    }

    /** The favourite team's name as the score feed spells it, or "" for none. */
    static String team(Context ctx) {
        return prefs(ctx).getString(TEAM, "");
    }

    static void setTeam(Context ctx, String name) {
        prefs(ctx).edit().putString(TEAM, name == null ? "" : name).commit();
    }
}
