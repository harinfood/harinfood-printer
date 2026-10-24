package id.my.mra.harinfoodprinter;

import android.content.Context;
import android.media.AudioAttributes;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Mengucapkan teks lewat TTS (Bahasa Indonesia): jam saat ini, atau teks bebas.
 * Setiap ucapan bisa membawa callback yang dijalankan setelah ucapan selesai.
 * Volume dijaga 100% selama diucapkan.
 */
public final class JamBicara {
    private static final Object LOCK = new Object();
    private static final String[] SATUAN = {"nol", "satu", "dua", "tiga", "empat", "lima", "enam", "tujuh", "delapan", "sembilan"};

    private static TextToSpeech tts;
    private static boolean ready = false;
    private static Context appCtx;
    private static final List<Object[]> pending = new ArrayList<>();          // {String teks, Runnable setelah}
    private static final Map<String, Runnable> callbacks = new HashMap<>();   // utteranceId -> callback

    private JamBicara() {}

    /** Ucapkan jam sekarang. Aman dipanggil dari thread mana pun. */
    public static void speakNow(Context c) {
        speak(c, kalimatJam(), null);
    }

    /** Ucapkan teks bebas, lalu jalankan "setelah" (misalnya memutar MP3). */
    public static void speak(Context c, String teks, Runnable setelah) {
        final Context app = c.getApplicationContext();
        List<Runnable> jalan;
        synchronized (LOCK) {
            appCtx = app;
            VolumeBoost.acquire(app); // dilepas kembali saat ucapan selesai
            pending.add(new Object[]{teks, setelah});
            if (tts == null) {
                tts = new TextToSpeech(app, status -> onInit(status));
                jalan = new ArrayList<>();
            } else if (ready) {
                jalan = flush();
            } else {
                jalan = new ArrayList<>(); // menunggu TTS siap
            }
        }
        for (Runnable r : jalan) r.run();
    }

    private static void onInit(int status) {
        List<Runnable> jalan = new ArrayList<>();
        synchronized (LOCK) {
            if (status == TextToSpeech.SUCCESS && tts != null) {
                tts.setLanguage(new Locale("id", "ID"));
                tts.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
                tts.setSpeechRate(0.95f);
                tts.setOnUtteranceProgressListener(listener);
                ready = true;
                jalan = flush();
            } else {
                // TTS tidak tersedia: lepas volume dan tetap jalankan "setelah" agar musik tidak tertahan
                tts = null;
                ready = false;
                for (Object[] item : pending) {
                    VolumeBoost.release(appCtx);
                    if (item[1] != null) jalan.add((Runnable) item[1]);
                }
                pending.clear();
            }
        }
        for (Runnable r : jalan) r.run();
    }

    /** Dipanggil saat LOCK dipegang. Mengembalikan callback yang harus dijalankan di luar LOCK. */
    private static List<Runnable> flush() {
        List<Runnable> jalan = new ArrayList<>();
        for (Object[] item : pending) {
            String id = "tts-" + UUID.randomUUID();
            Runnable setelah = (Runnable) item[1];
            int hasil = tts.speak((String) item[0], TextToSpeech.QUEUE_ADD, null, id);
            if (hasil == TextToSpeech.SUCCESS) {
                if (setelah != null) callbacks.put(id, setelah);
            } else {
                VolumeBoost.release(appCtx);
                if (setelah != null) jalan.add(setelah);
            }
        }
        pending.clear();
        return jalan;
    }

    private static final UtteranceProgressListener listener = new UtteranceProgressListener() {
        @Override public void onStart(String utteranceId) {}

        @Override public void onDone(String utteranceId) { selesai(utteranceId); }

        @Override public void onError(String utteranceId) { selesai(utteranceId); }
    };

    private static void selesai(String id) {
        Runnable setelah;
        synchronized (LOCK) {
            setelah = callbacks.remove(id);
            if (appCtx != null) VolumeBoost.release(appCtx);
        }
        if (setelah != null) setelah.run();
    }

    /**
     * Format 12 jam, seperti cara orang mengucapkan jam:
     * 10.42 -> "Sekarang jam sepuluh lebih empat puluh dua pagi"
     * 13.05 -> "Sekarang jam satu lebih lima siang"
     * 12.00 -> "Sekarang jam dua belas tepat siang"
     */
    public static String kalimatJam() {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta"));
        int jam24 = cal.get(Calendar.HOUR_OF_DAY);
        int menit = cal.get(Calendar.MINUTE);
        int jam12 = jam24 % 12;
        if (jam12 == 0) jam12 = 12;
        String periode = periode(jam24);
        if (menit == 0) return "Sekarang jam " + sebut(jam12) + " tepat " + periode;
        return "Sekarang jam " + sebut(jam12) + " lebih " + sebut(menit) + " " + periode;
    }

    /** Kata waktu harian berdasarkan jam 24 (0-23). */
    static String periode(int jam24) {
        if (jam24 < 4) return "dini hari";
        if (jam24 < 11) return "pagi";
        if (jam24 < 15) return "siang";
        if (jam24 < 18) return "sore";
        return "malam";
    }

    /** Angka 0-59 dalam kata Bahasa Indonesia. */
    static String sebut(int n) {
        if (n < 10) return SATUAN[n];
        if (n == 10) return "sepuluh";
        if (n == 11) return "sebelas";
        if (n < 20) return SATUAN[n - 10] + " belas";
        String s = SATUAN[n / 10] + " puluh";
        if (n % 10 > 0) s += " " + SATUAN[n % 10];
        return s;
    }
}
