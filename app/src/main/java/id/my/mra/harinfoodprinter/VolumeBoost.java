package id.my.mra.harinfoodprinter;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;

/**
 * Menaikkan volume musik & notifikasi ke 100% selama ada bunyi (notifikasi atau bel iTag),
 * lalu mengembalikannya ke nilai semula saat bunyi selesai. Dihitung dengan penghitung
 * (holders), jadi beberapa bunyi yang bertumpuk tetap aman.
 */
public final class VolumeBoost {
    private static final Object LOCK = new Object();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static int holders = 0;
    private static int savedMusic = -1;
    private static int savedNotif = -1;

    private VolumeBoost() {}

    public static void acquire(Context ctx) {
        synchronized (LOCK) {
            if (holders++ > 0) return;
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            try {
                savedMusic = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                savedNotif = am.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
                am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
                am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION), 0);
            } catch (SecurityException ignored) {
                // Izin MODIFY_AUDIO_SETTINGS tidak ada: biarkan volume apa adanya
            }
        }
    }

    public static void release(Context ctx) {
        synchronized (LOCK) {
            if (holders <= 0) return;
            if (--holders > 0) return;
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            try {
                if (savedMusic >= 0) am.setStreamVolume(AudioManager.STREAM_MUSIC, savedMusic, 0);
                if (savedNotif >= 0) am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, savedNotif, 0);
            } catch (SecurityException ignored) {
            }
            savedMusic = -1;
            savedNotif = -1;
        }
    }

    /** Naikkan volume selama holdMs milidetik, lalu kembalikan otomatis. */
    public static void boost(final Context ctx, long holdMs) {
        final Context app = ctx.getApplicationContext();
        acquire(app);
        MAIN.postDelayed(() -> release(app), holdMs);
    }
}
