package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.util.Log;
import android.widget.Toast;

/**
 * Android's installer reports here on the update UpdateActivity started: first that it needs the user to confirm
 * (its own Install screen, opened here), then whether it worked. On success Android restarts LayarKaca as the
 * new version, so there is nothing more to do.
 */
public final class UpdateReceiver extends BroadcastReceiver {
    private static final String TAG = "TvHome.Update";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm == null) return;
            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                ctx.startActivity(confirm);
            } catch (RuntimeException e) {
                Log.w(TAG, "could not open the install screen: " + e);
                Toast.makeText(ctx, "Tidak dapat membuka skrin pemasangan. Gunakan pemasang dari komputer.",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }
        String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        Log.i(TAG, "installer status " + status + (msg != null ? ": " + msg : ""));
        if (status == PackageInstaller.STATUS_SUCCESS || status == PackageInstaller.STATUS_FAILURE_ABORTED) return;
        Toast.makeText(ctx, status == PackageInstaller.STATUS_FAILURE_CONFLICT || status == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE
                        ? "Kemas kini ditolak: LayarKaca di TV ini dipasang dengan cara lain. Gunakan pemasang dari komputer."
                        : "Kemas kini tidak berjaya. Tiada apa-apa diubah.",
                Toast.LENGTH_LONG).show();
    }
}
