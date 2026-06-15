# 🥊 FightLog - Smart Interval Timer & Cardio Tracker

FightLog is a professional Android application specifically engineered for combat sports athletes and boxers. It combines robust interval round management with dynamic cardio tracking (Running & Jump Rope).

The project is built upon industry-standard engineering practices, featuring a **State Machine** architecture, **SQLite Database Migration** handling, modern **Runtime Permission** flows, and real-time **GPS/Location Services** integration.

---

## 📸 Screenshots
<img width="296" height="666" alt="Ekran görüntüsü 2026-06-15 214103" src="https://github.com/user-attachments/assets/d881f7b3-ea5d-4c11-9067-f2e5f4afbfed" />
<img width="295" height="665" alt="Ekran görüntüsü 2026-06-15 214111" src="https://github.com/user-attachments/assets/b3eec707-f259-419e-b58a-2fb942272cd7" />
<img width="301" height="666" alt="Ekran görüntüsü 2026-06-15 214124" src="https://github.com/user-attachments/assets/02e4a3a6-6cdb-4c47-9fe9-d694227dc11e" />
<img width="298" height="665" alt="Ekran görüntüsü 2026-06-15 214132" src="https://github.com/user-attachments/assets/a60c6cff-dff6-445e-aacf-008f0094a449" />



## 🚀 Key Features

### 1. Dual-Mode Timer Engine
* **Boxing Mode (Count-down):** Fully customizable interval timer for Sparring or Heavy Bag sessions. Adjust round count, work duration, and rest duration. Features audible warnings (`SoundPool` API) during the last 10 seconds and automated Gong sounds for transitions.
* **Cardio Mode (Count-up):** An open-ended, serbest zamanlayıcı (free timer) structure for Jump Rope and Running. Utilizing dynamic UI manipulation (`getParent()`), irrelevant UI elements are automatically hidden based on the selected activity for a clean experience.

### 2. Advanced Metric & Location Tracking
* **Real-time GPS Tracking:** The Running mode utilizes a robust `LocationListener` architecture that communicates directly with satellites. It accurately calculates distance in kilometers using the Haversine formula and displays real-time speed (km/h).
* **Scientific Calorie Engine:** Calories burned are calculated every second using the gold standard **MET (Metabolic Equivalent of Task)** formulation from sports science.
    * *Jump Rope MET: 10*
    * *Running MET: 9.8*

### 3. Integrated History & SQL Data Engine
* **SQL Aggregate Dashboard:** The History screen provides a high-level summary by scanning the database using SQL analytical functions (`SUM()` and `COUNT()`). It displays cumulative statistics for Total Sessions, Total Volume (Rounds), and Total Duration.
* **SQLite Data Optimization:** An automated cleanup mechanism, `keepOnlyRecentWorkouts(30)`, is implemented to keep the local storage efficient, retaining only the 30 most recent workout records.

---

## 🛠️ Tech Stack & Architecture

* **Language:** Java (Android SDK)
* **Database:** SQLite (Advanced Relational handling with Migration)
* **UI/UX:** XML (Custom Dark Theme, Dynamic View States)
* **Hardware Integration:** GPS & Location Services (Fine/Coarse Location Management)
* **Multimedia:** SoundPool API (Low-latency audio triggering)

---

## 📐 Engineering Highlights

* **State Machine Management:** The timer flow is orchestrated using a strict State Machine pattern (`READY`, `WORK`, `REST`, `PAUSED`, `FINISHED`) to ensure data consistency and predictable UI behavior.
* **Database Schema Migration (V1 -> V2):** Handled application updates gracefully. The `onUpgrade` logic implements `ALTER TABLE` commands to inject `distance`, `calories`, and `avg_speed` columns into the existing schema without data loss.
* **Runtime Permission Flow:** Adhering to Android 6.0+ security protocols, the application implements a dynamic permission request flow. The GPS permission is only requested at the precise moment the user selects "Running" mode.

---

## 📦 Installation & Setup

### Building from Source
1. Clone the repository:
   ```bash
   git clone [https://github.com/USERNAME/FightLog.git](https://github.com/USERNAME/FightLog.git)
