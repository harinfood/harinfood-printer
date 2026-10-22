package id.my.mra.harinfoodprinter;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/** Layar pembuka: logo PC kasir membesar dengan efek memantul, lalu pindah ke MainActivity. */
public class SplashActivity extends Activity {

    private static final long DURASI_TAMPIL_MS = 2600L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean sudahPindah = false;
    private final Runnable pindahKeUtama = () -> {
        if (sudahPindah || isFinishing()) return;
        sudahPindah = true;
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        View glow = findViewById(R.id.glow);
        View logo = findViewById(R.id.logoSplash);
        View judul = findViewById(R.id.judulSplash);
        View sub = findViewById(R.id.subSplash);

        // 1. Logo membesar dari kecil dengan efek memantul
        logo.animate()
            .scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(950)
            .setInterpolator(new OvershootInterpolator(1.3f))
            .withEndAction(() -> melayang(logo))
            .start();

        // 2. Cahaya di belakang logo muncul lalu berdenyut
        glow.animate()
            .alpha(1f)
            .setDuration(800)
            .withEndAction(() -> berdenyut(glow))
            .start();

        // 3. Judul dan subjudul muncul bergantian
        judul.animate().alpha(1f).translationY(0f).setStartDelay(550).setDuration(650).start();
        sub.animate().alpha(1f).setStartDelay(850).setDuration(600).start();

        handler.postDelayed(pindahKeUtama, DURASI_TAMPIL_MS);
    }

    /** Logo naik turun pelan agar terlihat hidup. */
    private void melayang(View logo) {
        ObjectAnimator naikTurun = ObjectAnimator.ofFloat(logo, "translationY", 0f, -16f);
        naikTurun.setDuration(1500);
        naikTurun.setRepeatCount(ObjectAnimator.INFINITE);
        naikTurun.setRepeatMode(ObjectAnimator.REVERSE);
        naikTurun.setInterpolator(new AccelerateDecelerateInterpolator());
        naikTurun.start();
    }

    /** Cahaya di belakang logo membesar dan mengecil terus-menerus. */
    private void berdenyut(View glow) {
        ObjectAnimator x = ObjectAnimator.ofFloat(glow, "scaleX", 0.92f, 1.12f);
        ObjectAnimator y = ObjectAnimator.ofFloat(glow, "scaleY", 0.92f, 1.12f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(x, y);
        set.setDuration(1100);
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        x.setRepeatCount(ObjectAnimator.INFINITE);
        x.setRepeatMode(ObjectAnimator.REVERSE);
        y.setRepeatCount(ObjectAnimator.INFINITE);
        y.setRepeatMode(ObjectAnimator.REVERSE);
        set.start();
    }

    @Override
    public void onBackPressed() {
        // Tombol kembali di layar pembuka langsung menutup aplikasi
        handler.removeCallbacks(pindahKeUtama);
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(pindahKeUtama);
        super.onDestroy();
    }
}
