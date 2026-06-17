package com.example.fightlog;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        Button btnNewWorkout = findViewById(R.id.btnNewWorkout);
        Button btnViewHistoryFromHome = findViewById(R.id.btnViewHistoryFromHome);

        // Navigate to MainActivity to start a new workout session
        btnNewWorkout.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, MainActivity.class);
            startActivity(intent);
        });

        // Navigate to HistoryActivity to view workout logs and statistics
        btnViewHistoryFromHome.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, HistoryActivity.class);
            startActivity(intent);
        });
    }
}