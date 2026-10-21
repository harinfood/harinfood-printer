package id.my.mra.harinfoodprinter;

import android.content.Context;
import android.media.AudioAttributes;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Mengucapkan jam saat ini (WIB) dalam Bahasa Indonesia lewat TTS.
 * Dipanggil setelah musik bel selesai diputar. Volume dijaga 100% selama diucapkan.
 */
public final class JamBicara {
    private static final Object LOCK = new Object();
    private static final String[] SATUAN = {"nol", "satu", "dua", "tiga", "empat", "lima", "enam", "tujuh", "delapan", "sembilan"};

    private static TextToSpeech tts;
    private static boolean ready = false;
    private static String pending = null;
    private static Context appCtx;

    private JamBicara() {}

    /** Ucapkan jam sekarang. Aman dipanggil dari thread mana pun. */
    public static void speakNow(Context c) {
        final Context app = c.getApplicationContext();
        final String text = kalimatJam();
        synchronized (LOCK) {
            appCtx = app;
            VolumeBoost.acquire(app); // dilepas kembali saat ucapan selesai
            if (tts == null) {
                pending = text;
                tts = new TextToSpeech(app, status -> {
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
                            if (pending != null) {
                                String p = pending;
                                pending = null;
                                bicara(p);
                            }
                        } else {
                            // TTS tidak tersedia: lepas volume yang tadi dinaikkan
                            tts = null;
                            ready = false;
                            pending = null;
                            VolumeBoost.release(appCtx);
                        }
                    }
                });
            } else if (ready) {
                bicara(text);
            } else {
                if (pending != null) VolumeBoost.release(appCtx); // ganti ucapan tertunda, seimbangkan hitungan
                pending = text;
            }
        }
    }

    private static void bicara(String text) {
        int hasil = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jam-" + UUID.randomUUID());
        if (hasil != TextToSpeech.SUCCESS) VolumeBoost.release(appCtx);
    }

    private static final UtteranceProgressListener listener = new UtteranceProgressListener() {
        @Override public void onStart(String utteranceId) {}

        @Override public void onDone(String utteranceId) { selesai(); }

        @Override public void onError(String utteranceId) { selesai(); }
    };

    private static void selesai() {
        synchronized (LOCK) {
            if (appCtx != null) VolumeBoost.release(appCtx);
        }
    }

    /** Contoh: "Sekarang pukul empat belas lewat tiga puluh lima menit". */
    public static String kalimatJam() {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta"));
        int jam = cal.get(Calendar.HOUR_OF_DAY);
        int menit = cal.get(Calendar.MINUTE);
        if (menit == 0) return "Sekarang pukul " + sebut(jam) + " tepat";
        if (menit <= 30) return "Sekarang pukul " + sebut(jam) + " lewat " + sebut(menit) + " menit";
        int jamBerikut = (jam + 1) % 24;
        return "Sekarang pukul " + sebut(jamBerikut) + " kurang " + sebut(60 - menit) + " menit";
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
