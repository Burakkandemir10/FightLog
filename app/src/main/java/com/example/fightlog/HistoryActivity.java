package com.example.fightlog;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

public class HistoryActivity extends AppCompatActivity {

    private Button btnBack;
    private ListView listViewHistory;
    private DatabaseHelper dbHelper;

    // Dashboard UI elements
    private TextView tvTotalSessions, tvTotalVolume, tvTotalMinutes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        btnBack = findViewById(R.id.btnBack);
        listViewHistory = findViewById(R.id.listViewHistory);

        tvTotalSessions = findViewById(R.id.tvTotalSessions);
        tvTotalVolume = findViewById(R.id.tvTotalVolume);
        tvTotalMinutes = findViewById(R.id.tvTotalMinutes);

        dbHelper = new DatabaseHelper(this);

        btnBack.setOnClickListener(v -> finish());

        // Initialize and load data sequentially
        loadStatistics();
        loadHistoryData();
    }

    private void loadStatistics() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        // Calculate total sessions, total volume, and total duration using SQL aggregate functions
        String statQuery = "SELECT COUNT(s.id), SUM(d.volume), SUM(d.duration) " +
                "FROM " + DatabaseHelper.TABLE_WORKOUT_SESSIONS + " s " +
                "JOIN " + DatabaseHelper.TABLE_WORKOUT_DETAILS + " d ON s.id = d.session_id";

        Cursor cursor = db.rawQuery(statQuery, null);

        if (cursor.moveToFirst()) {
            int totalSessions = cursor.getInt(0);

            // Handle potential null values from SUM operations if the database is empty
            int totalVolume = cursor.isNull(1) ? 0 : (int) cursor.getFloat(1);
            int totalMinutes = cursor.isNull(2) ? 0 : cursor.getInt(2);

            tvTotalSessions.setText(String.valueOf(totalSessions));
            tvTotalVolume.setText(String.valueOf(totalVolume));
            tvTotalMinutes.setText(String.valueOf(totalMinutes));
        }

        cursor.close();
        db.close();
    }

    private void loadHistoryData() {
        ArrayList<String> workoutList = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String query = "SELECT s.date, e.name, d.volume, e.unit, d.duration " +
                "FROM " + DatabaseHelper.TABLE_WORKOUT_SESSIONS + " s " +
                "JOIN " + DatabaseHelper.TABLE_WORKOUT_DETAILS + " d ON s.id = d.session_id " +
                "JOIN " + DatabaseHelper.TABLE_EXERCISE_TYPES + " e ON d.exercise_type_id = e.id " +
                "ORDER BY s.id DESC";

        Cursor cursor = db.rawQuery(query, null);

        if (cursor.moveToFirst()) {
            do {
                String date = cursor.getString(0);
                String exerciseName = cursor.getString(1);

                // Format volume to remove decimal if it's a whole number (e.g., 5.0 -> 5)
                float floatVolume = cursor.getFloat(2);
                String volumeStr = (floatVolume % 1 == 0) ? String.valueOf((int)floatVolume) : String.valueOf(floatVolume);

                String unit = cursor.getString(3);
                int duration = cursor.getInt(4);

                String record = date + "\n" + exerciseName + " - " + volumeStr + " " + unit + " (" + duration + " min)";
                workoutList.add(record);

            } while (cursor.moveToNext());
        } else {
            workoutList.add("No workout history found. Get to work!");
        }

        cursor.close();
        db.close();

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.list_item_workout, workoutList);
        listViewHistory.setAdapter(adapter);
    }
}