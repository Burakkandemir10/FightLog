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
import android.view.animation.AnimationUtils;
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

    // UI Components
    private Spinner spinnerExerciseType;

    // Navigation Buttons
    private Button btnTopBack, btnReturnToMainMenu;

    private EditText etRounds, etRoundTime, etRestTime;
    private TextView tvStatus, tvRoundInfo, tvTimer;
    private Button btnStartPause, btnReset, btnHistory;

    // Dynamic Layouts & Live Stats
    private LinearLayout layoutBoxingSettings, layoutCardioSettings, layoutLiveStats;
    private TextView tvGpsWarning, tvLiveDistance, tvLiveCalories, tvLiveSpeed;

    // State Machine
    private enum TimerState { READY, WORK, REST, PAUSED, FINISHED }
    private TimerState currentState = TimerState.READY;
    private TimerState previousState = TimerState.READY;

    // Boxing Mode Variables (Countdown)
    private int totalRounds = 12;
    private int roundTimeLimit = 180;
    private int restTimeLimit = 60;
    private int currentRound = 1;
    private int timeRemaining = 0;

    // Cardio Mode Variables (Count-up)
    private boolean isCountUpMode = false;
    private int elapsedTime = 0;
    private float distanceTraveled = 0;
    private float caloriesBurned = 0;

    // Core Handlers & Audio
    private Handler handler = new Handler();
    private Runnable timerRunnable;
    private SoundPool soundPool;
    private int gongSoundId, warningSoundId;

    // GPS & Location Services
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private Location lastLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI components
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

        // Setup top back button with press animation
        btnTopBack = findViewById(R.id.btnTopBack);
        btnTopBack.setOnClickListener(v -> {
            v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.button_press));
            new android.os.Handler().postDelayed(() -> {
                finish();
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }, 150);
        });

        // Setup return to main menu button with press animation
        btnReturnToMainMenu = findViewById(R.id.btnReturnToMainMenu);
        btnReturnToMainMenu.setOnClickListener(v -> {
            v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.button_press));
            new android.os.Handler().postDelayed(() -> {
                finish();
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }, 150);
        });

        layoutBoxingSettings = findViewById(R.id.layoutBoxingSettings);
        layoutCardioSettings = findViewById(R.id.layoutCardioSettings);
        layoutLiveStats = findViewById(R.id.layoutLiveStats);

        tvGpsWarning = findViewById(R.id.tvGpsWarning);
        tvLiveDistance = findViewById(R.id.tvLiveDistance);
        tvLiveCalories = findViewById(R.id.tvLiveCalories);
        tvLiveSpeed = findViewById(R.id.tvLiveSpeed);

        // Initialize GPS Location Listener
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                // Ignore inaccurate location data (accuracy > 10 meters)
                if (!location.hasAccuracy() || location.getAccuracy() > 10.0f) return;

                if (lastLocation == null) {
                    lastLocation = location;
                    return;
                }

                float distanceInMeters = lastLocation.distanceTo(location);
                float speedKmH = 0.0f;

                // Calculate current speed
                if (location.hasSpeed()) {
                    speedKmH = location.getSpeed() * 3.6f;
                } else {
                    long timeDelta = location.getTime() - lastLocation.getTime();
                    if (timeDelta > 0) {
                        speedKmH = (distanceInMeters / (timeDelta / 1000.0f)) * 3.6f;
                    }
                }

                // Filter unrealistic movements (Speed > 35km/h or teleportation > 40m)
                if (speedKmH > 35.0f || distanceInMeters > 40.0f) {
                    lastLocation = location;
                    return;
                }

                // Update stats if displacement is significant enough to avoid GPS drift
                if (distanceInMeters > 4.0f) {
                    distanceTraveled += (distanceInMeters / 1000.0);
                    lastLocation = location;

                    tvLiveDistance.setText(String.format(java.util.Locale.US, "%.2f km", distanceTraveled));
                    tvLiveSpeed.setText(String.format(java.util.Locale.US, "%.1f km/h", speedKmH));
                } else if (speedKmH < 2.0f) {
                    tvLiveSpeed.setText("0.0 km/h");
                }
            }
        };

        // Setup Exercise Type Spinner and dynamic UI logic
        String[] exerciseTypes = {"Sparring", "Heavy Bag", "Jump Rope", "Running"};

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, exerciseTypes);
        spinnerAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerExerciseType.setAdapter(spinnerAdapter);

        spinnerExerciseType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position <= 1) { // Boxing modes
                    layoutBoxingSettings.setVisibility(View.VISIBLE);
                    layoutCardioSettings.setVisibility(View.GONE);
                    layoutLiveStats.setVisibility(View.GONE);
                    tvRoundInfo.setVisibility(View.VISIBLE);
                    tvTimer.setText("03:00");
                    tvStatus.setText("READY");
                } else { // Cardio modes
                    layoutBoxingSettings.setVisibility(View.GONE);
                    layoutCardioSettings.setVisibility(View.VISIBLE);
                    layoutLiveStats.setVisibility(View.VISIBLE);
                    tvRoundInfo.setVisibility(View.INVISIBLE);
                    tvTimer.setText("00:00");

                    View distanceColumn = (View) tvLiveDistance.getParent();
                    View speedColumn = (View) tvLiveSpeed.getParent();

                    if (position == 3) { // Running
                        tvStatus.setText("RUNNING MODE");
                        tvGpsWarning.setVisibility(View.VISIBLE);
                        distanceColumn.setVisibility(View.VISIBLE);
                        speedColumn.setVisibility(View.VISIBLE);
                        checkLocationPermission();
                    } else { // Jump Rope
                        tvStatus.setText("JUMP ROPE MODE");
                        tvGpsWarning.setVisibility(View.GONE);
                        distanceColumn.setVisibility(View.GONE);
                        speedColumn.setVisibility(View.GONE);
                    }
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Initialize SoundPool for audio feedback
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder().setMaxStreams(2).setAudioAttributes(audioAttributes).build();
        gongSoundId = soundPool.load(this, R.raw.boxing_gong, 1);
        warningSoundId = soundPool.load(this, R.raw.timer_warning, 1);

        // Setup control buttons
        btnStartPause.setOnClickListener(v -> toggleTimer());

        btnReset.setOnClickListener(v -> {
            if (currentState == TimerState.WORK || currentState == TimerState.PAUSED) {
                finishWorkout();
            } else {
                resetTimer();
            }
        });

        btnHistory.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(MainActivity.this, HistoryActivity.class);
            startActivity(intent);
        });

        // Main Timer Runnable handling both count-up and countdown modes
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                int currentMode = spinnerExerciseType.getSelectedItemPosition();

                if (isCountUpMode) {
                    elapsedTime++;


                    float currentSpeed = lastLocation != null && lastLocation.hasSpeed() ? lastLocation.getSpeed() * 3.6f : 0.0f;

                    if (currentSpeed > 2.0f) {
                        if (currentMode == 2) {
                            caloriesBurned += (10.0f * 75.0f) / 3600f;
                        } else if (currentMode == 3) {
                            caloriesBurned += (9.8f * 75.0f) / 3600f;
                        }
                    }

                    tvLiveCalories.setText(String.format(Locale.US, "%d kcal", (int)caloriesBurned));
                    updateUI();
                    handler.postDelayed(this, 1000);
                } else {
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

    // --- CORE LOGIC METHODS ---

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
                isCountUpMode = false;
                String roundsStr = etRounds.getText().toString().trim();
                String roundTimeStr = etRoundTime.getText().toString().trim();
                String restTimeStr = etRestTime.getText().toString().trim();

                totalRounds = roundsStr.isEmpty() ? 12 : Integer.parseInt(roundsStr);
                roundTimeLimit = roundTimeStr.isEmpty() ? 180 : Integer.parseInt(roundTimeStr);
                restTimeLimit = restTimeStr.isEmpty() ? 60 : Integer.parseInt(restTimeStr);

                timeRemaining = roundTimeLimit;
            } else {
                isCountUpMode = true;
                elapsedTime = 0;
                distanceTraveled = 0;
                caloriesBurned = 0;
                tvLiveDistance.setText("0.00 km");
                tvLiveCalories.setText("0 kcal");
                tvLiveSpeed.setText("0.0 km/h");
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "You entered an invalid value!", Toast.LENGTH_SHORT).show();
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

        if (currentMode == 3) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 2, locationListener);
                Toast.makeText(this, "GPS engine fired, satellite awaited...", Toast.LENGTH_SHORT).show();
            }
            lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        }
    }

    private void pauseWorkout() {
        previousState = currentState;
        currentState = TimerState.PAUSED;
        handler.removeCallbacks(timerRunnable);

        btnStartPause.setText("RESUME");
        btnStartPause.setBackgroundTintList(getResources().getColorStateList(android.R.color.holo_green_dark, null));

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

        if (spinnerExerciseType.getSelectedItemPosition() <= 1) {
            // Assign default value to prevent crash if input is empty
            String roundTimeStr = etRoundTime.getText().toString().trim();
            timeRemaining = roundTimeStr.isEmpty() ? 180 : Integer.parseInt(roundTimeStr);
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

        showWorkoutSummaryDialog();
    }

    // --- HELPER FUNCTIONS ---

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

        if (currentState == TimerState.WORK || currentState == TimerState.PAUSED) {
            btnReset.setText("FINISH & SAVE");
            btnReset.setBackgroundTintList(getResources().getColorStateList(android.R.color.holo_blue_dark, null));
        } else {
            btnReset.setText("RESET");
            btnReset.setBackgroundTintList(getResources().getColorStateList(android.R.color.holo_red_dark, null));
        }

        // Manage navigation buttons visibility based on timer state
        switch (currentState) {
            case READY:
            case PAUSED:
                btnTopBack.setVisibility(View.VISIBLE);
                btnReturnToMainMenu.setVisibility(View.GONE);
                break;
            case WORK:
            case REST:
                btnTopBack.setVisibility(View.GONE);
                btnReturnToMainMenu.setVisibility(View.GONE);
                break;
            case FINISHED:
                btnTopBack.setVisibility(View.GONE);
                btnReturnToMainMenu.setVisibility(View.VISIBLE);
                break;
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

    // --- DATABASE OPERATIONS ---

    private void showWorkoutSummaryDialog() {
        int currentMode = spinnerExerciseType.getSelectedItemPosition();

        if (currentMode <= 1) {
            return;
        }

        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.setContentView(R.layout.dialog_workout_summary);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.setCancelable(false);

        TextView tvDialogClose = dialog.findViewById(R.id.tvDialogClose);
        TextView tvDialogTime = dialog.findViewById(R.id.tvDialogTime);
        TextView tvDialogDistance = dialog.findViewById(R.id.tvDialogDistance);
        TextView tvDialogSpeed = dialog.findViewById(R.id.tvDialogSpeed);
        TextView tvDialogCalories = dialog.findViewById(R.id.tvDialogCalories);

        TextView tvLabelDistance = dialog.findViewById(R.id.tvLabelDistance);
        TextView tvLabelSpeed = dialog.findViewById(R.id.tvLabelSpeed);

        int minutes = elapsedTime / 60;
        int seconds = elapsedTime % 60;
        tvDialogTime.setText(String.format(Locale.US, "%02d:%02d", minutes, seconds));
        tvDialogCalories.setText(String.format(Locale.US, "%d kcal", (int)caloriesBurned));

        if (currentMode == 2) {
            tvLabelDistance.setVisibility(View.GONE);
            tvDialogDistance.setVisibility(View.GONE);
            tvLabelSpeed.setVisibility(View.GONE);
            tvDialogSpeed.setVisibility(View.GONE);

        } else if (currentMode == 3) {
            float totalHours = elapsedTime / 3600.0f;
            float avgSpeedKmH = 0.0f;
            if (totalHours > 0 && distanceTraveled > 0) {
                avgSpeedKmH = (float) (distanceTraveled / totalHours);
            }
            tvDialogDistance.setText(String.format(Locale.US, "%.2f km", distanceTraveled));
            tvDialogSpeed.setText(String.format(Locale.US, "%.1f km/h", avgSpeedKmH));
        }

        tvDialogClose.setOnClickListener(v -> {
            dialog.dismiss();
        });

        dialog.show();
    }

    private void saveWorkoutToDb() {
        DatabaseHelper dbHelper = new DatabaseHelper(this);
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        try {
            int currentMode = spinnerExerciseType.getSelectedItemPosition();
            int totalDurationMinutes;

            if (currentMode <= 1) {
                totalDurationMinutes = (totalRounds * roundTimeLimit) / 60;
            } else {
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

    // --- LOCATION PERMISSION MANAGEMENT ---

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
                Toast.makeText(this, "GPS permission obtained! We're ready for the run.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "GPS permission denied! Distance cannot be measured during running.", Toast.LENGTH_LONG).show();
            }
        }
    }
}