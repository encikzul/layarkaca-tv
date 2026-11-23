package com.gathin.tvhome;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * The Kemas kini tile: installs a newer LayarKaca on the TV itself, with the remote, no computer needed.
 * Downloads the release's APK from this repository's Releases, checks it against the release's SHA-256, checks it
 * is LayarKaca and newer, and hands it to Android's own installer, which asks the user to confirm. Android also
 * refuses any APK not signed with the same key as the installed app, so only our own releases can replace it.
 */
public final class UpdateActivity extends Activity {
    private static final String TAG = "TvHome.Update";
    private static final int MAX_APK = 20 * 1024 * 1024;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView title, status, go, later;
    private float d;
    private boolean working;
    /** Pressed Kemas kini, then went to allow installing: carry on when the user comes back. */
    private boolean waitingForPermission;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        d = getResources().getDisplayMetrics().density;
        Typeface font = getResources().getFont(R.font.outfit);
        FrameLayout root = new FrameLayout(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(px(28), px(24), px(28), px(22));
        GradientDrawable glass = new GradientDrawable();
        glass.setCornerRadius(18 * d);
        glass.setColor(0xF0182026);
        glass.setStroke(Math.max(1, Math.round(1.5f * d)), 0x4DFFFFFF);
        panel.setBackground(glass);
        root.addView(panel, new FrameLayout.LayoutParams(px(460), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        title = text(font, 22, 0xFFF4F6F5, 500);
        panel.addView(title);
        status = text(font, 13, 0xD9F4F6F5, 400);
        status.setSingleLine(false);
        status.setLineSpacing(0, 1.25f);
        status.setPadding(0, px(10), 0, px(18));
        panel.addView(status);
        go = button(font, "Kemas kini sekarang");
        go.setOnClickListener(v -> start());
        panel.addView(go, rowParams());
        later = button(font, "Nanti");
        later.setOnClickListener(v -> finish());
        panel.addView(later, rowParams());
        setContentView(root);

        String v = Updates.newer(this);
        if (v == null) {
            title.setText("LayarKaca terkini");
            status.setText("Versi " + Updates.installed(this) + " sudah yang terbaru.");
            go.setVisibility(View.GONE);
            later.setText("Tutup");
            later.requestFocus();
            return;
        }
        title.setText("LayarKaca " + v);
        status.setText("Versi baharu tersedia (sekarang " + Updates.installed(this) + "). Tetapan, lokasi dan susunan "
                + "aplikasi anda kekal. Skrin utama akan dimulakan semula selepas dipasang.");
        go.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (waitingForPermission) {
            waitingForPermission = false;
            if (canInstall()) start();
            else show("Kebenaran belum diberi. Pilih Kemas kini sekarang untuk cuba lagi, atau gunakan pemasang dari "
                    + "komputer.", true);
        }
    }

    private boolean canInstall() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || getPackageManager().canRequestPackageInstalls();
    }

    private void start() {
        if (working) return;
        if (!canInstall()) {
            show("Langkah sekali sahaja: di skrin seterusnya, hidupkan suis untuk LayarKaca, kemudian tekan Back.", false);
            try {
                waitingForPermission = true;
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())));
            } catch (ActivityNotFoundException | SecurityException e) {
                waitingForPermission = false;
                show("TV ini tidak membenarkan aplikasi memasang kemas kini sendiri. Gunakan pemasang dari komputer: "
                        + "jalankan arahan pemasang dan pilih 1.", true);
            }
            return;
        }
        String[] files = Updates.files(this);
        if (files == null) {
            show("Fail kemas kini tidak dijumpai. Cuba lagi esok, atau gunakan pemasang dari komputer.", true);
            return;
        }
        working = true;
        go.setVisibility(View.GONE);
        later.setVisibility(View.GONE);
        show("Memuat turun...", false);
        final Context app = getApplicationContext();
        new Thread(() -> {
            String error = null;
            try {
                install(app, files[0], files[1]);
            } catch (Exception e) {
                Log.w(TAG, "update failed: " + e);
                error = e.getMessage();
            }
            final String err = error;
            handler.post(() -> {
                working = false;
                if (err == null) {
                    show("Sahkan di skrin seterusnya: pilih Pasang (Install).", false);
                } else {
                    show(err + " Tiada apa-apa diubah. Cuba lagi nanti, atau gunakan pemasang dari komputer.", true);
                }
            });
        }, "update-install").start();
    }

    /** Download, check, and pass to Android's installer. Messages of the exceptions are shown to the user. */
    private void install(Context app, String apkUrl, String shaUrl) throws Exception {
        String want = Updates.shaOf(new String(download(shaUrl, 4096, null), StandardCharsets.UTF_8));
        if (want == null) throw new Exception("Fail semakan SHA-256 tidak sah.");
        File apk = new File(app.getCacheDir(), "update.apk");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        download(apkUrl, MAX_APK, apk);
        try (InputStream in = new FileInputStream(apk)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) digest.update(buf, 0, n);
        }
        if (!want.equals(hex(digest.digest()))) {
            apk.delete();
            throw new Exception("Fail yang dimuat turun tidak sepadan dengan SHA-256 rasmi.");
        }
        handler.post(() -> show("Menyemak...", false));
        PackageManager pm = app.getPackageManager();
        PackageInfo archive = pm.getPackageArchiveInfo(apk.getPath(), 0);
        PackageInfo current = pm.getPackageInfo(app.getPackageName(), 0);
        if (archive == null || !app.getPackageName().equals(archive.packageName)) {
            apk.delete();
            throw new Exception("Fail itu bukan LayarKaca.");
        }
        if (versionCode(archive) <= versionCode(current)) {
            apk.delete();
            throw new Exception("Fail itu bukan versi yang lebih baharu.");
        }

        PackageInstaller installer = pm.getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(app.getPackageName());
        int id = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(id)) {
            try (InputStream in = new FileInputStream(apk);
                 OutputStream out = session.openWrite("layarkaca.apk", 0, apk.length())) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                session.fsync(out);
            }
            // Mutable, so the installer can add its status; explicit, so only UpdateReceiver gets it.
            PendingIntent done = PendingIntent.getBroadcast(app, id, new Intent(app, UpdateReceiver.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            session.commit(done.getIntentSender());
        } catch (Exception e) {
            installer.abandonSession(id);
            throw new Exception("Android tidak dapat memulakan pemasangan.");
        } finally {
            apk.delete();
        }
        Log.i(TAG, "update " + archive.versionName + " handed to the installer");
    }

    @SuppressWarnings("deprecation")
    private static long versionCode(PackageInfo p) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? p.getLongVersionCode() : p.versionCode;
    }

    /** Fetches a release file into {@code to}, or returns it when {@code to} is null. GitHub redirects to its CDN. */
    private static byte[] download(String address, int max, File to) throws Exception {
        URL url = new URL(address);
        for (int hop = 0; hop < 5; hop++) {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(30_000);
            conn.setRequestProperty("User-Agent", "LayarKaca");
            try {
                int code = conn.getResponseCode();
                if (code / 100 == 3) {
                    String next = conn.getHeaderField("Location");
                    if (next == null) break;
                    url = new URL(url, next);
                    if (!"https".equals(url.getProtocol())) throw new Exception("Muat turun tidak selamat ditolak.");
                    continue;
                }
                if (code != 200) throw new Exception("Muat turun gagal (HTTP " + code + "). Semak internet TV.");
                long total = 0;
                try (InputStream in = conn.getInputStream();
                     OutputStream out = to != null ? new FileOutputStream(to)
                             : new java.io.ByteArrayOutputStream()) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        total += n;
                        if (total > max) throw new Exception("Fail terlalu besar.");
                        out.write(buf, 0, n);
                    }
                    return out instanceof java.io.ByteArrayOutputStream
                            ? ((java.io.ByteArrayOutputStream) out).toByteArray() : null;
                }
            } catch (java.io.IOException e) {
                throw new Exception("Muat turun gagal. Semak internet TV.");
            } finally {
                conn.disconnect();
            }
        }
        throw new Exception("Muat turun gagal: terlalu banyak lencongan.");
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private void show(String text, boolean retry) {
        status.setText(text);
        if (retry) {
            go.setVisibility(View.VISIBLE);
            later.setVisibility(View.VISIBLE);
            later.setText("Tutup");
            go.requestFocus();
        }
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(38));
        lp.bottomMargin = px(6);
        return lp;
    }

    private TextView button(Typeface font, String label) {
        TextView b = text(font, 14, 0xFFF4F6F5, 500);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true);
        b.setClickable(true);
        GradientDrawable focused = new GradientDrawable();
        focused.setCornerRadius(10 * d);
        focused.setColor(0x33FFFFFF);
        focused.setStroke(Math.max(2, Math.round(2 * d)), 0xEBFFFFFF);
        GradientDrawable normal = new GradientDrawable();
        normal.setCornerRadius(10 * d);
        normal.setColor(0x14FFFFFF);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, focused);
        s.addState(new int[]{}, normal);
        b.setBackground(s);
        return b;
    }

    private TextView text(Typeface font, float sp, int color, int weight) {
        TextView t = new TextView(this);
        t.setTypeface(font);
        t.setFontVariationSettings("'wght' " + weight);
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
