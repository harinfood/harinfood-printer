package id.my.mra.harinfoodprinter;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.os.PowerManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Memutar musik pembuka (musik yang sama dengan saat aplikasi dibuka) ketika tombol iTag ditekan.
 * Berkas MP3 diunduh sekali dari URL musik pembuka lalu disimpan di cache; kalau belum ada, dipakai nada notifikasi bawaan HP.
 * Setelah musik selesai, jam saat ini diucapkan lewat TTS (Bahasa Indonesia).
 * Menekan tombol lagi saat musik masih berbunyi = menghentikan musik (tanpa ucapan jam).
 * Tekan 1x: MP3 bel lalu jam. Tekan 2x: teks dari pengaturan dibacakan, lalu MP3 khusus tekan 2x.
 * Tekan 3x: jam saja.
 */
public class BellPlayer {
    private static final Object LOCK = new Object();
    private static MediaPlayer player;
    private static Context volCtx; // dipakai untuk mengembalikan volume saat musik berhenti
    private static boolean sedangUcap = false; // true saat teks sebelum MP3 sedang dibacakan

    public static void play(final Context ctx) {
        onButton(ctx, 1);
    }

    /**
     * mode 1 (tekan 1x): MP3 bel lalu jam.
     * mode 2 (tekan 2x): teks pengaturan dibacakan, lalu MP3 khusus tekan 2x (bila belum diatur, memakai MP3 bel + jam).
     * mode 3 (tekan 3x): hanya jam.
     */
    public static void onButton(final Context ctx, final int mode) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> playBlocking(app, mode)).start();
    }

    public static void stop() {
        synchronized (LOCK) { releaseLocked(); }
    }

    public static boolean isPlaying() {
        synchronized (LOCK) { return player != null; }
    }

    /** Hapus cache musik (dipanggil saat URL musik pembuka berganti). */
    public static void invalidate(Context ctx) {
        try { musicFile(ctx).delete(); } catch (Exception ignored) {}
    }

    /** Hapus cache MP3 khusus tekan 3x (dipanggil saat MP3 khusus diganti). */
    public static void invalidateTriple(Context ctx) {
        try { tripleFile(ctx).delete(); } catch (Exception ignored) {}
    }

    private static File musicFile(Context c) { return new File(c.getCacheDir(), "bell_music.mp3"); }
    private static File tripleFile(Context c) { return new File(c.getCacheDir(), "bell_triple.mp3"); }

    private static void releaseLocked() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            try { player.release(); } catch (Exception ignored) {}
            player = null;
            if (volCtx != null) VolumeBoost.release(volCtx);
        }
    }

    private static boolean ensureDownloaded(Context c, String prefKey, File f) {
        if (f.exists() && f.length() > 1000) return true;
        String url = c.getSharedPreferences("harin", Context.MODE_PRIVATE).getString(prefKey, "");
        if (url == null || url.isEmpty()) return false;
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(20000);
            if (conn.getResponseCode() != 200) return false;
            InputStream in = conn.getInputStream();
            FileOutputStream out = new FileOutputStream(f);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.close();
            in.close();
            return f.length() > 1000;
        } catch (Exception e) {
            try { f.delete(); } catch (Exception ignored) {}
            return false;
        }
    }

    /** Teks yang dibacakan sebelum MP3 khusus tekan 2x (diatur dari web). */
    private static String teksSebelum(Context c) {
        String t = c.getSharedPreferences("harin", Context.MODE_PRIVATE).getString("bell_pre_text", "");
        return t == null ? "" : t.trim();
    }

    private static void playBlocking(Context c, int mode) {
        synchronized (LOCK) {
            volCtx = c.getApplicationContext();
            if (player != null) { releaseLocked(); return; } // ditekan lagi saat berbunyi -> berhenti
            if (sedangUcap) return;                         // masih membacakan teks, tunggu sebentar
            if (mode == 3) { JamBicara.speakNow(volCtx); return; } // tekan 3x: hanya jam
            if (mode == 2) {                                // tekan 2x: teks dulu, lalu MP3 khusus
                String teks = teksSebelum(c);
                if (!teks.isEmpty()) {
                    sedangUcap = true;
                    final Context app = volCtx;
                    JamBicara.speak(app, teks, () -> mulaiMp3(app, 2));
                    return;
                }
            }
            mulaiMp3(c, mode);
        }
    }

    /** Memutar MP3: MP3 khusus (tekan 2x) atau MP3 bel biasa (lalu jam). Dipanggil langsung atau setelah teks selesai. */
    private static void mulaiMp3(Context c, int mode) {
        synchronized (LOCK) {
            sedangUcap = false;
            if (player != null) return;
            // tekan 2x: MP3 khusus; kalau belum diatur, memakai MP3 bel biasa + jam
            final boolean pakaiTriple = mode == 2 && ensureDownloaded(c, "bell_triple_music_url", tripleFile(c));
            final boolean sebutJam = !pakaiTriple;
            try {
                MediaPlayer mp = new MediaPlayer();
                mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
                mp.setWakeMode(c, PowerManager.PARTIAL_WAKE_LOCK); // tetap berbunyi walau layar mati
                if (pakaiTriple) mp.setDataSource(tripleFile(c).getAbsolutePath());
                else if (ensureDownloaded(c, "bell_music_url", musicFile(c))) mp.setDataSource(musicFile(c).getAbsolutePath());
                else mp.setDataSource(c, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
                mp.setOnCompletionListener(m -> { synchronized (LOCK) { if (player == m) { if (sebutJam) JamBicara.speakNow(volCtx); releaseLocked(); } } });
                mp.setOnErrorListener((m, what, extra) -> { synchronized (LOCK) { if (player == m) { if (sebutJam) JamBicara.speakNow(volCtx); releaseLocked(); } } return true; });
                mp.prepare();
                mp.start();
                player = mp;
                VolumeBoost.acquire(volCtx); // volume 100% selama musik bel berbunyi
            } catch (Exception e) {
                releaseLocked();
            }
        }
    }
}
