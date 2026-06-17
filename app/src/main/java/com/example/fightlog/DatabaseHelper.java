package com.example.fightlog;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Database manager for FightLog.
 * Implements a relational database schema adhering to 3NF standards.
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "FightLog.db";
    private static final int DATABASE_VERSION = 2;

    // Table Names
    public static final String TABLE_EXERCISE_TYPES = "ExerciseTypes";
    public static final String TABLE_WORKOUT_SESSIONS = "WorkoutSessions";
    public static final String TABLE_WORKOUT_DETAILS = "WorkoutDetails";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // 1. Exercise Types Table (Static data and measurement units)
        String createExerciseTypesTable = "CREATE TABLE " + TABLE_EXERCISE_TYPES + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "name TEXT NOT NULL, "
                + "unit TEXT NOT NULL)";
        db.execSQL(createExerciseTypesTable);

        // 2. Workout Sessions Table (General date and total duration)
        String createWorkoutSessionsTable = "CREATE TABLE " + TABLE_WORKOUT_SESSIONS + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "date TEXT DEFAULT CURRENT_TIMESTAMP, "
                + "total_duration INTEGER)"; // Duration in minutes
        db.execSQL(createWorkoutSessionsTable);

        // 3. Workout Details Table (Session specific metrics and foreign keys)
        String createWorkoutDetailsTable = "CREATE TABLE " + TABLE_WORKOUT_DETAILS + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "session_id INTEGER, "
                + "exercise_type_id INTEGER, "
                + "volume REAL, "
                + "duration INTEGER, "
                + "distance REAL DEFAULT 0, "      // Distance for cardio (in km)
                + "calories REAL DEFAULT 0, "      // Calories burned
                + "avg_speed REAL DEFAULT 0, "     // Average speed (in km/h)
                + "FOREIGN KEY(session_id) REFERENCES " + TABLE_WORKOUT_SESSIONS + "(id), "
                + "FOREIGN KEY(exercise_type_id) REFERENCES " + TABLE_EXERCISE_TYPES + "(id))";
        db.execSQL(createWorkoutDetailsTable);

        // Automatically insert default exercise types upon initial database creation
        insertInitialExerciseTypes(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // Migrate from version 1 to 2: Retain existing data while adding new metric columns
            db.execSQL("ALTER TABLE " + TABLE_WORKOUT_DETAILS + " ADD COLUMN distance REAL DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE_WORKOUT_DETAILS + " ADD COLUMN calories REAL DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE_WORKOUT_DETAILS + " ADD COLUMN avg_speed REAL DEFAULT 0");
        }
    }

    /**
     * Inserts the foundational exercise types and their respective units into the system.
     */
    private void insertInitialExerciseTypes(SQLiteDatabase db) {
        ContentValues values = new ContentValues();

        values.put("name", "Sparring");
        values.put("unit", "rounds");
        db.insert(TABLE_EXERCISE_TYPES, null, values);

        values.put("name", "Heavy Bag");
        values.put("unit", "rounds");
        db.insert(TABLE_EXERCISE_TYPES, null, values);

        values.put("name", "Jump Rope");
        values.put("unit", "minutes");
        db.insert(TABLE_EXERCISE_TYPES, null, values);

        values.put("name", "Running");
        values.put("unit", "km");
        db.insert(TABLE_EXERCISE_TYPES, null, values);
    }

    /**
     * Purges old database records, retaining only the specified number of recent workouts.
     * Enforces referential integrity during the deletion process.
     */
    public void keepOnlyRecentWorkouts(int limit) {
        SQLiteDatabase db = this.getWritableDatabase();

        // Identify IDs of obsolete records that fall outside the specified limit.
        // Order by descending ID and use OFFSET to skip the records we want to keep.
        String query = "SELECT id FROM " + TABLE_WORKOUT_SESSIONS + " ORDER BY id DESC LIMIT -1 OFFSET " + limit;
        Cursor cursor = db.rawQuery(query, null);

        if (cursor.moveToFirst()) {
            do {
                int obsoleteSessionId = cursor.getInt(0);

                // Enforce referential integrity: Delete child records (WorkoutDetails) first
                // to prevent orphan data and potential database constraints violations.
                db.delete(TABLE_WORKOUT_DETAILS, "session_id = ?", new String[]{String.valueOf(obsoleteSessionId)});

                // Delete the parent record (WorkoutSessions) after child dependencies are cleared.
                db.delete(TABLE_WORKOUT_SESSIONS, "id = ?", new String[]{String.valueOf(obsoleteSessionId)});

            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
    }
}