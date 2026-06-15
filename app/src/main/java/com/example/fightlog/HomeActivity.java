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

        // Yeni İdmana Tıklayınca -> MainActivity (Sayaç Ekranı) Açılır
        btnNewWorkout.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, MainActivity.class);
            startActivity(intent);
        });

        // Geçmişe Tıklayınca -> HistoryActivity (İstatistikler) Açılır
        btnViewHistoryFromHome.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, HistoryActivity.class);
            startActivity(intent);
        });
    }
}