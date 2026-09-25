package vn.edu.vhu.ltdd.a2stopwatch;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    // MSSV: 231A290036
    private static final String TAG = "A2_231A290036";

    // Khóa lưu trạng thái vào Bundle
    private static final String KEY_RUNNING = "running";
    private static final String KEY_ACCUMULATED = "accumulated";
    private static final String KEY_START = "start";
    private static final String KEY_RECREATE = "recreate";
    private static final String KEY_LAPS = "laps";
    private static final String KEY_PAUSE_ON_STOP = "pause_on_stop";

    private TextView tvTime, tvStatus, tvRecreate, tvLaps;
    private Button btnStartPause, btnReset, btnLap;
    private CheckBox cbPauseOnStop;

    // Trạng thái của đồng hồ
    private boolean running = false;   // đang chạy hay không
    private long accumulated = 0L;     // số mili-giây đã tích lũy
    private long startTime = 0L;       // mốc elapsedRealtime() lúc bắt đầu
    private int recreateCount = 0;     // số lần Activity được tạo lại

    // Danh sách vòng (Bài nâng cao NC1)
    private ArrayList<String> lapList = new ArrayList<>();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateTimeText();
            handler.postDelayed(this, 100); // cập nhật 10 lần/giây
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        tvTime = findViewById(R.id.tvTime);
        tvStatus = findViewById(R.id.tvStatus);
        tvRecreate = findViewById(R.id.tvRecreate);
        tvLaps = findViewById(R.id.tvLaps);

        btnStartPause = findViewById(R.id.btnStartPause);
        btnReset = findViewById(R.id.btnReset);
        btnLap = findViewById(R.id.btnLap);

        cbPauseOnStop = findViewById(R.id.cbPauseOnStop);

        if (savedInstanceState != null) {
            running = savedInstanceState.getBoolean(KEY_RUNNING);
            accumulated = savedInstanceState.getLong(KEY_ACCUMULATED);
            startTime = savedInstanceState.getLong(KEY_START);
            recreateCount = savedInstanceState.getInt(KEY_RECREATE) + 1;

            // Khôi phục danh sách lap (NC1)
            ArrayList<String> savedLaps = savedInstanceState.getStringArrayList(KEY_LAPS);
            if (savedLaps != null) {
                lapList = savedLaps;
            }

            // Khôi phục trạng thái CheckBox (NC2)
            if (cbPauseOnStop != null) {
                cbPauseOnStop.setChecked(savedInstanceState.getBoolean(KEY_PAUSE_ON_STOP, false));
            }

            Log.d(TAG, "onCreate: KHÔI PHỤC trạng thái, running=" + running
                    + ", accumulated=" + accumulated + "ms, recreateCount=" + recreateCount);
        } else {
            Log.d(TAG, "onCreate: khởi tạo mới (savedInstanceState = null)");
        }

        btnStartPause.setOnClickListener(v -> {
            if (running) {
                pauseStopwatch();
            } else {
                startStopwatch();
            }
        });

        btnReset.setOnClickListener(v -> resetStopwatch());

        btnLap.setOnClickListener(v -> recordLap());

        updateUi();
    }

    // ---------------- Logic đồng hồ ----------------

    /** Tổng thời gian đã trôi qua (ms). */
    private long elapsed() {
        return running ? accumulated + (SystemClock.elapsedRealtime() - startTime) : accumulated;
    }

    private void startStopwatch() {
        running = true;
        startTime = SystemClock.elapsedRealtime();
        startTicking();
        updateUi();
        Log.i(TAG, "BẮT ĐẦU đếm giờ");
    }

    private void pauseStopwatch() {
        if (running) {
            accumulated += SystemClock.elapsedRealtime() - startTime;
            running = false;
            stopTicking();
            updateUi();
            Log.i(TAG, "TẠM DỪNG tại " + accumulated + "ms");
        }
    }

    private void resetStopwatch() {
        running = false;
        accumulated = 0L;
        startTime = 0L;
        lapList.clear();
        stopTicking();
        vibrate(); // Rung nhẹ khi Đặt lại (NC3)
        updateUi();
        Log.i(TAG, "ĐẶT LẠI về 00:00.0");
    }

    /** Ghi lại mốc thời gian hiện tại vào danh sách Lap (Bài nâng cao NC1) */
    private void recordLap() {
        long ms = elapsed();
        long phut = ms / 60000;
        long giay = (ms % 60000) / 1000;
        long phanMuoi = (ms % 1000) / 100;
        String timeStr = String.format(Locale.getDefault(), "%02d:%02d.%d", phut, giay, phanMuoi);

        int lapIndex = lapList.size() + 1;
        String lapEntry = String.format(Locale.getDefault(), "Vòng %d: %s", lapIndex, timeStr);
        lapList.add(0, lapEntry); // Đưa lap mới nhất lên đầu
        updateLapsDisplay();
        Log.i(TAG, "GHI VÒNG: " + lapEntry);
    }

    /** Rung phản hồi haptic khi Đặt lại (Bài nâng cao NC3) */
    private void vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vibratorManager = (VibratorManager) getSystemService(Context.Vibrator_MANAGER_SERVICE);
                if (vibratorManager != null) {
                    vibratorManager.getDefaultVibrator().vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
                }
            } else {
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null && v.hasVibrator()) {
                    v.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Không thể kích hoạt rung: " + e.getMessage());
        }
    }

    private void startTicking() {
        handler.removeCallbacks(ticker);   // Tránh chạy chồng nhiều ticker
        handler.post(ticker);
    }

    private void stopTicking() {
        handler.removeCallbacks(ticker);
    }

    // ---------------- Cập nhật giao diện ----------------

    private void updateTimeText() {
        long ms = elapsed();
        long phut = ms / 60000;
        long giay = (ms % 60000) / 1000;
        long phanMuoi = (ms % 1000) / 100;
        tvTime.setText(String.format(Locale.getDefault(), "%02d:%02d.%d", phut, giay, phanMuoi));

        // Bài nâng cao NC3: Đổi màu chữ sang Đỏ khi thời gian vượt quá 60 giây (60.000 ms)
        if (ms >= 60000) {
            tvTime.setTextColor(Color.parseColor("#E53935")); // Màu đỏ cảnh báo
        } else {
            tvTime.setTextColor(ContextCompat.getColor(this, R.color.primary)); // Màu chính
        }
    }

    private void updateLapsDisplay() {
        if (tvLaps != null) {
            StringBuilder sb = new StringBuilder();
            for (String lap : lapList) {
                sb.append(lap).append("\n");
            }
            tvLaps.setText(sb.toString());
        }
    }

    private void updateUi() {
        updateTimeText();
        btnStartPause.setText(running ? R.string.pause : R.string.start);
        tvStatus.setText(running ? R.string.status_running : R.string.status_paused);
        tvRecreate.setText(getString(R.string.recreate_count, recreateCount));
        updateLapsDisplay();
    }

    // ---------------- Vòng đời ----------------

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume – bật lại việc cập nhật giao diện nếu đồng hồ đang chạy");
        if (running) {
            startTicking();
        }
        updateUi();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Dừng cập nhật giao diện để tiết kiệm pin; đồng hồ vẫn tính đúng theo SystemClock.elapsedRealtime()
        stopTicking();
        Log.d(TAG, "onPause – tạm dừng cập nhật giao diện");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");

        // Bài nâng cao NC2: Nếu CheckBox "Dừng khi ra nền" được chọn -> pause đồng hồ
        if (cbPauseOnStop != null && cbPauseOnStop.isChecked() && running) {
            pauseStopwatch();
            Log.i(TAG, "onStop – Đã tự động tạm dừng đồng hồ do CheckBox NC2 được chọn");
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "onRestart");
    }

    @Override
    protected void onDestroy() {
        stopTicking();               // Dọn dẹp Handler triệt để tránh rò rỉ bộ nhớ
        Log.d(TAG, "onDestroy");
        super.onDestroy();
    }

    // ---------------- Lưu & khôi phục trạng thái ----------------

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_RUNNING, running);
        outState.putLong(KEY_ACCUMULATED, accumulated);
        outState.putLong(KEY_START, startTime);
        outState.putInt(KEY_RECREATE, recreateCount);

        // Lưu danh sách vòng (NC1) & CheckBox (NC2)
        outState.putStringArrayList(KEY_LAPS, lapList);
        if (cbPauseOnStop != null) {
            outState.putBoolean(KEY_PAUSE_ON_STOP, cbPauseOnStop.isChecked());
        }

        Log.d(TAG, "onSaveInstanceState – đã lưu " + elapsed() + "ms vào Bundle");
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        Log.d(TAG, "onRestoreInstanceState – được gọi sau onStart()");
    }
}
// Commit update: Hoan thien module A2 Stopwatch va cac bai nang cao NC1, NC2, NC3, NC4
