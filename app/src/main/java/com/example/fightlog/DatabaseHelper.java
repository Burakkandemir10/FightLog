package com.example.fightlog; // Kendi paket adının yazdığından emin ol

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * FightLog veritabanı yöneticisi.
 * 3NF mimarisine uygun, ilişkisel (relational) tablo yapılarını içerir.
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "FightLog.db";
    private static final int DATABASE_VERSION = 2;

    // Tablo İsimleri
    public static final String TABLE_EXERCISE_TYPES = "ExerciseTypes";
    public static final String TABLE_WORKOUT_SESSIONS = "WorkoutSessions";
    public static final String TABLE_WORKOUT_DETAILS = "WorkoutDetails";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // 1. Egzersiz Türleri Tablosu (Sabit veriler ve ölçü birimleri)
        String createExerciseTypesTable = "CREATE TABLE " + TABLE_EXERCISE_TYPES + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "name TEXT NOT NULL, "
                + "unit TEXT NOT NULL)";
        db.execSQL(createExerciseTypesTable);

        // 2. Antrenman Oturumları Tablosu (Genel tarih ve toplam süre)
        String createWorkoutSessionsTable = "CREATE TABLE " + TABLE_WORKOUT_SESSIONS + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "date TEXT DEFAULT CURRENT_TIMESTAMP, "
                + "total_duration INTEGER)"; // dakika cinsinden
        db.execSQL(createWorkoutSessionsTable);

        String createWorkoutDetailsTable = "CREATE TABLE " + TABLE_WORKOUT_DETAILS + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "session_id INTEGER, "
                + "exercise_type_id INTEGER, "
                + "volume REAL, "
                + "duration INTEGER, "
                + "distance REAL DEFAULT 0, "      // YENİ: Koşu için kilometre
                + "calories REAL DEFAULT 0, "      // YENİ: Yakılan kalori
                + "avg_speed REAL DEFAULT 0, "     // YENİ: Ortalama hız (km/s)
                + "FOREIGN KEY(session_id) REFERENCES " + TABLE_WORKOUT_SESSIONS + "(id), "
                + "FOREIGN KEY(exercise_type_id) REFERENCES " + TABLE_EXERCISE_TYPES + "(id))";
        db.execSQL(createWorkoutDetailsTable);

        // Veritabanı ilk oluştuğunda sabit idman türlerini (Sparring, Torba vs.) otomatik ekleyelim
        insertInitialExerciseTypes(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // Sürüm 1'den 2'ye geçiş yapıyorsak eski verileri koruyup yeni kolonları ekliyoruz
            db.execSQL("ALTER TABLE " + TABLE_WORKOUT_DETAILS + " ADD COLUMN distance REAL DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE_WORKOUT_DETAILS + " ADD COLUMN calories REAL DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE_WORKOUT_DETAILS + " ADD COLUMN avg_speed REAL DEFAULT 0");
        }
    }

    /**
     * Uygulama ilk kez açıldığında ölçü birimleriyle birlikte temel idmanları sisteme kaydeder.
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
     * Veritabanını temizler ve sadece belirtilen sayıdaki en yeni kaydı tutar.
     */
    public void keepOnlyRecentWorkouts(int limit) {
        SQLiteDatabase db = this.getWritableDatabase();

        // Belirlediğimiz limitin (örneğin 30) DIŞINDA kalan eski kayıtların ID'lerini buluyoruz.
        // DESC (Yeniden eskiye) sırala, ilk 30'u atla (OFFSET), kalanları getir.
        String query = "SELECT id FROM " + TABLE_WORKOUT_SESSIONS + " ORDER BY id DESC LIMIT -1 OFFSET " + limit;
        Cursor cursor = db.rawQuery(query, null);

        if (cursor.moveToFirst()) {
            do {
                int obsoleteSessionId = cursor.getInt(0);

                // DOBRA MÜHENDİSLİK DETAYI:
                // Önce alt tabloyu (WorkoutDetails) temizlemek zorundayız.
                // Eğer direkt ana oturumu silersek, detay tablosundaki kayıtlar "Yetim Kayıt" (Orphan Data) olarak kalır ve sistemi çökertir.
                db.delete(TABLE_WORKOUT_DETAILS, "session_id = ?", new String[]{String.valueOf(obsoleteSessionId)});

                // Detaylar silindikten sonra ana oturumu (WorkoutSessions) siliyoruz.
                db.delete(TABLE_WORKOUT_SESSIONS, "id = ?", new String[]{String.valueOf(obsoleteSessionId)});

            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
    }

}