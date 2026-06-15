package com.example.fightlog;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    // --- UI (Arayüz) Elemanları ---
    private Spinner spinnerExerciseType;
    private EditText etRounds, etRoundTime, etRestTime;
    private TextView tvStatus, tvRoundInfo, tvTimer;
    private Button btnStartPause, btnReset, btnHistory;

    // Dinamik Paneller ve Canlı İstatistikler
    private LinearLayout layoutBoxingSettings, layoutCardioSettings, layoutLiveStats;
    private TextView tvGpsWarning, tvLiveDistance, tvLiveCalories, tvLiveSpeed;

    // --- Durum Makinesi (State Machine) ---
    private enum TimerState { READY, WORK, REST, PAUSED, FINISHED }
    private TimerState currentState = TimerState.READY;
    private TimerState previousState = TimerState.READY;

    // --- Boks (Geri Sayım) Değişkenleri ---
    private int totalRounds = 12;
    private int roundTimeLimit = 180;
    private int restTimeLimit = 60;
    private int currentRound = 1;
    private int timeRemaining = 0;

    // --- Kardiyo (İleri Sayım) Değişkenleri ---
    private boolean isCountUpMode = false;
    private int elapsedTime = 0;
    private float distanceTraveled = 0;
    private float caloriesBurned = 0;

    // --- Motorlar ---
    private Handler handler = new Handler();
    private Runnable timerRunnable;
    private SoundPool soundPool;
    private int gongSoundId, warningSoundId;

    // --- GPS ve Konum ---
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private Location lastLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. UI Bağlantıları
        spinnerExerciseType = findViewById(R.id.spinnerExerciseType);
        etRounds = findViewById(R.id.etRounds);
        etRoundTime = findViewById(R.id.etRoundTime);
        etRestTime = findViewById(R.id.etRestTime);

        tvStatus = findViewById(R.id.tvStatus);
        tvRoundInfo = findViewById(R.id.tvRoundInfo);
        tvTimer = findViewById(R.id.tvTimer);

        btnStartPause = findViewById(R.id.btnStartPause);
        btnReset = findViewById(R.id.btnReset);
        btnHistory = findViewById(R.id.btnHistory);

        layoutBoxingSettings = findViewById(R.id.layoutBoxingSettings);
        layoutCardioSettings = findViewById(R.id.layoutCardioSettings);
        layoutLiveStats = findViewById(R.id.layoutLiveStats);

        tvGpsWarning = findViewById(R.id.tvGpsWarning);
        tvLiveDistance = findViewById(R.id.tvLiveDistance);
        tvLiveCalories = findViewById(R.id.tvLiveCalories);
        tvLiveSpeed = findViewById(R.id.tvLiveSpeed);

        // 2. GPS Motoru Kurulumu
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                if (currentState == TimerState.WORK && spinnerExerciseType.getSelectedItemPosition() == 3) {
                    if (lastLocation != null) {
                        float distanceInMeters = lastLocation.distanceTo(location);
                        distanceTraveled += (distanceInMeters / 1000f);

                        float speedMps = location.getSpeed();
                        float speedKmph = speedMps * 3.6f;

                        // Eski: tvLiveDistance.setText(String.format(java.util.Locale.US, "%.1f km", distanceTraveled));
                    // Yeni:
                        tvLiveDistance.setText(String.format(java.util.Locale.US, "%.2f km", distanceTraveled));
                        tvLiveSpeed.setText(String.format(Locale.US, "%.1f km/h", speedKmph));
                    }
                    lastLocation = location;
                }
            }
        };

        // 3. Spinner (Açılır Menü) ve Dinamik Arayüz Motoru
        String[] exerciseTypes = {"Sparring", "Heavy Bag", "Jump Rope", "Running"};
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, exerciseTypes);
        spinnerAdapter.setDropDownViewResource(R.layout.spinner_item);
        spinnerExerciseType.setAdapter(spinnerAdapter);

        spinnerExerciseType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position <= 1) { // Sparring veya Torba
                    layoutBoxingSettings.setVisibility(View.VISIBLE);
                    layoutCardioSettings.setVisibility(View.GONE);
                    layoutLiveStats.setVisibility(View.GONE);
                    tvRoundInfo.setVisibility(View.VISIBLE);
                    tvTimer.setText("03:00");
                    tvStatus.setText("READY");
                } else { // Kardiyo Modları
                    layoutBoxingSettings.setVisibility(View.GONE);
                    layoutCardioSettings.setVisibility(View.VISIBLE);
                    layoutLiveStats.setVisibility(View.VISIBLE);
                    tvRoundInfo.setVisibility(View.INVISIBLE);
                    tvTimer.setText("00:00");

                    // RASYONEL MÜHENDİSLİK: Mesafe ve Hız yazılarının içinde bulunduğu ana sütunları (Parent) yakalıyoruz
                    View distanceColumn = (View) tvLiveDistance.getParent();
                    View speedColumn = (View) tvLiveSpeed.getParent();

                    if (position == 3) { // Running
                        tvStatus.setText("RUNNING MODE");
                        tvGpsWarning.setVisibility(View.VISIBLE);

                        // Koşuda mesafe ve hızı göster
                        distanceColumn.setVisibility(View.VISIBLE);
                        speedColumn.setVisibility(View.VISIBLE);

                        checkLocationPermission();
                    } else { // Jump Rope
                        tvStatus.setText("JUMP ROPE MODE");
                        tvGpsWarning.setVisibility(View.GONE);

                        // İp atlarken mesafe ve hızı ekrandan tamamen sil (Sadece Kalori kalsın)
                        distanceColumn.setVisibility(View.GONE);
                        speedColumn.setVisibility(View.GONE);
                    }
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 4. Ses Motoru (SoundPool) Kurulumu
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder().setMaxStreams(2).setAudioAttributes(audioAttributes).build();
        gongSoundId = soundPool.load(this, R.raw.boxing_gong, 1);
        warningSoundId = soundPool.load(this, R.raw.timer_warning, 1);

        // 5. Buton Dinleyicileri
        btnStartPause.setOnClickListener(v -> toggleTimer());

        btnReset.setOnClickListener(v -> {
            if (currentState == TimerState.WORK || currentState == TimerState.PAUSED) {
                finishWorkout(); // Kaydet ve Bitir
            } else {
                resetTimer(); // Sıfırla
            }
        });

        btnHistory.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(MainActivity.this, HistoryActivity.class);
            startActivity(intent);
        });

        // 6. Zamanlayıcı ve Kalori Motoru (Akıllı Çift Mod)
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                int currentMode = spinnerExerciseType.getSelectedItemPosition();

                if (isCountUpMode) {
                    // --- KARDİYO (İleri Sayım) ---
                    elapsedTime++;

                    if (currentMode == 2) { // İp Atlama MET: 10
                        caloriesBurned += (10.0f * 75.0f) / 3600f;
                    } else if (currentMode == 3) { // Koşu MET: 9.8
                        caloriesBurned += (9.8f * 75.0f) / 3600f;
                    }
                    tvLiveCalories.setText(String.format(Locale.US, "%d kcal", (int)caloriesBurned));

                    updateUI();
                    handler.postDelayed(this, 1000);

                } else {
                    // --- BOKS (Geri Sayım) ---
                    if (timeRemaining > 0) {
                        timeRemaining--;
                        if (timeRemaining == 10) {
                            soundPool.play(warningSoundId, 1, 1, 0, 0, 1);
                        }
                        updateUI();
                        handler.postDelayed(this, 1000);
                    } else {
                        soundPool.play(gongSoundId, 1, 1, 0, 0, 1);
                        switchState();
                    }
                }
            }
        };
    }

    // --- TEMEL MANTIK FONKSİYONLARI ---

    private void toggleTimer() {
        if (currentState == TimerState.READY || currentState == TimerState.FINISHED) {
            startWorkout();
        } else if (currentState == TimerState.WORK || currentState == TimerState.REST) {
            pauseWorkout();
        } else if (currentState == TimerState.PAUSED) {
            resumeWorkout();
        }
    }

    private void startWorkout() {
        int currentMode = spinnerExerciseType.getSelectedItemPosition();

        try {
            if (currentMode <= 1) {
                // Boks Modu
                isCountUpMode = false;
                totalRounds = Integer.parseInt(etRounds.getText().toString());
                roundTimeLimit = Integer.parseInt(etRoundTime.getText().toString());
                restTimeLimit = Integer.parseInt(etRestTime.getText().toString());
                timeRemaining = roundTimeLimit;
            } else {
                // Kardiyo Modu
                isCountUpMode = true;
                elapsedTime = 0;
                distanceTraveled = 0;
                caloriesBurned = 0;
                tvLiveDistance.setText("0.00 km");
                tvLiveCalories.setText("0 kcal");
                tvLiveSpeed.setText("0.0 km/h");
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Geçersiz değer girdiniz!", Toast.LENGTH_SHORT).show();
            return;
        }

        currentRound = 1;
        currentState = TimerState.WORK;

        lockSettings(false);
        soundPool.play(gongSoundId, 1, 1, 0, 0, 1);

        btnStartPause.setText("PAUSE");
        btnStartPause.setBackgroundTintList(getResources().getColorStateList(android.R.color.holo_orange_dark, null));

        handler.postDelayed(timerRunnable, 1000);
        updateUI();

        // GPS'i Ateşle (Sadece Koşu ise)
        if (currentMode == 3) {

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 2, locationListener);
                // startWorkout içindeki GPS bloğuna ekle:
                Toast.makeText(this, "GPS Motoru ateşlendi, uydu bekleniyor...", Toast.LENGTH_SHORT).show();
            }
            // startWorkout'un en sonuna:
            lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        }
    }

    private void pauseWorkout() {
        previousState = currentState;
        currentState = TimerState.PAUSED;
        handler.removeCallbacks(timerRunnable);

        btnStartPause.setText("RESUME");
        btnStartPause.setBackgroundTintList(getResources().getColorStateList(android.R.color.holo_green_dark, null));

        // GPS'i Uyut
        if (locationManager != null) {
            locationManager.removeUpdates(locationListener);
        }
        updateUI();
    }

    private void resumeWorkout() {
        currentState = previousState;
        btnStartPause.setText("PAUSE");
        btnStartPause.setBackgroundTintList(getResources().getColorStateList(android.R.color.holo_orange_dark, null));

        handler.postDelayed(timerRunnable, 1000);

        // GPS'i Uyandır (Sadece Koşu ise)
        if (spinnerExerciseType.getSelectedItemPosition() == 3 && currentState == TimerState.WORK) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 2, locationListener);
            }
        }
        updateUI();
    }

    private void resetTimer() {
        handler.removeCallbacks(timerRunnable);
        currentState = TimerState.READY;
        currentRound = 1;

        // Ekranı varsayılana döndür
        if (spinnerExerciseType.getSelectedItemPosition() <= 1) {
            timeRemaining = Integer.parseInt(etRoundTime.getText().toString());
        } else {
            elapsedTime = 0;
        }

        lockSettings(true);
        btnStartPause.setText("START");
        btnStartPause.setBackgroundTintList(getResources().getColorStateList(android.R.color.holo_green_dark, null));

        if (locationManager != null) {
            locationManager.removeUpdates(locationListener);
        }
        lastLocation = null;
        updateUI();
    }

    private void switchState() {
        if (currentState == TimerState.WORK) {
            if (currentRound < totalRounds) {
                currentState = TimerState.REST;
                timeRemaining = restTimeLimit;
            } else {
                finishWorkout();
                return;
            }
        } else if (currentState == TimerState.REST) {
            currentRound++;
            currentState = TimerState.WORK;
            timeRemaining = roundTimeLimit;
        }
        updateUI();
        handler.postDelayed(timerRunnable, 1000);
    }

    private void finishWorkout() {
        currentState = TimerState.FINISHED;
        handler.removeCallbacks(timerRunnable);

        tvStatus.setText("WORKOUT COMPLETE!");
        tvStatus.setTextColor(getResources().getColor(android.R.color.holo_blue_light, null));
        btnStartPause.setText("START NEW");
        btnStartPause.setBackgroundTintList(getResources().getColorStateList(android.R.color.holo_green_dark, null));

        lockSettings(true);
        saveWorkoutToDb();

        if (locationManager != null) {
            locationManager.removeUpdates(locationListener);
        }
        lastLocation = null;
        updateUI();
    }

    // --- YARDIMCI FONKSİYONLAR ---

    private void updateUI() {
        int minutes, seconds;
        if (isCountUpMode && currentState != TimerState.READY) {
            minutes = elapsedTime / 60;
            seconds = elapsedTime % 60;
        } else {
            minutes = timeRemaining / 60;
            seconds = timeRemaining % 60;
        }
        tvTimer.setText(String.format(Locale.US, "%02d:%02d", minutes, seconds));

        tvRoundInfo.setText("Round " + currentRound + " / " + totalRounds);

        if (currentState == TimerState.READY) {
            if(spinnerExerciseType.getSelectedItemPosition() <= 1) tvStatus.setText("READY");
            tvStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark, null));
        } else if (currentState == TimerState.WORK) {
            tvStatus.setText(isCountUpMode ? "WORKOUT ACTIVE" : "WORK");
            tvStatus.setTextColor(getResources().getColor(android.R.color.holo_red_light, null));
        } else if (currentState == TimerState.REST) {
            tvStatus.setText("REST");
            tvStatus.setTextColor(getResources().getColor(android.R.color.holo_blue_light, null));
        } else if (currentState == TimerState.PAUSED) {
            tvStatus.setText("PAUSED");
            tvStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_light, null));
        }

        // Akıllı Buton Değişimi
        if (currentState == TimerState.WORK || currentState == TimerState.PAUSED) {
            btnReset.setText("FINISH & SAVE");
            btnReset.setBackgroundTintList(getResources().getColorStateList(android.R.color.holo_blue_dark, null));
        } else {
            btnReset.setText("RESET");
            btnReset.setBackgroundTintList(getResources().getColorStateList(android.R.color.holo_red_dark, null));
        }
    }

    private void lockSettings(boolean isEnabled) {
        etRounds.setEnabled(isEnabled);
        etRoundTime.setEnabled(isEnabled);
        etRestTime.setEnabled(isEnabled);
        spinnerExerciseType.setEnabled(isEnabled);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        handler.removeCallbacks(timerRunnable);
    }

    // --- VERİTABANI İŞLEMLERİ ---

    private void saveWorkoutToDb() {
        DatabaseHelper dbHelper = new DatabaseHelper(this);
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        try {
            int currentMode = spinnerExerciseType.getSelectedItemPosition();
            int totalDurationMinutes;

            if (currentMode <= 1) { // Boks süresi hesaplama
                totalDurationMinutes = (totalRounds * roundTimeLimit) / 60;
            } else { // Kardiyo süresi hesaplama
                totalDurationMinutes = elapsedTime / 60;
            }

            ContentValues sessionValues = new ContentValues();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            String currentDate = sdf.format(new Date());

            sessionValues.put("date", currentDate);
            sessionValues.put("total_duration", totalDurationMinutes);

            long sessionId = db.insert(DatabaseHelper.TABLE_WORKOUT_SESSIONS, null, sessionValues);

            if (sessionId != -1) {
                ContentValues detailValues = new ContentValues();
                detailValues.put("session_id", sessionId);
                detailValues.put("exercise_type_id", currentMode + 1);

                // Boks ise raund sayısı, kardiyo ise 1 yazıyoruz
                detailValues.put("volume", (currentMode <= 1) ? totalRounds : 1);
                detailValues.put("duration", totalDurationMinutes);
                detailValues.put("distance", distanceTraveled);
                detailValues.put("calories", caloriesBurned);

                float avgSpeed = 0;
                if (elapsedTime > 0) {
                    float hours = elapsedTime / 3600f;
                    avgSpeed = distanceTraveled / hours;
                }
                detailValues.put("avg_speed", avgSpeed);

                db.insert(DatabaseHelper.TABLE_WORKOUT_DETAILS, null, detailValues);
                dbHelper.keepOnlyRecentWorkouts(30);

                Toast.makeText(this, "Workout saved successfully!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Failed to save workout!", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            db.close();
        }
    }

    // --- GPS İZİN YÖNETİMİ ---

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "GPS İzni Alındı! Koşuya hazırız.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "GPS İzni Reddedildi! Koşuda mesafe ölçülemez.", Toast.LENGTH_LONG).show();
            }
        }
    }
}