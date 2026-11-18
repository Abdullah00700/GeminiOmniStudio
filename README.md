# Gemini Dev Agent (Android)

An Android app built with **Kotlin** and **Jetpack Compose (Material 3)** that acts as a personal task assistant powered by the **Gemini API** (`v1beta/models/{model}:generateContent`) using **Function Calling**.

## What It Can Do
Ask Gemini in natural language, and it will automatically choose and execute the right action on your phone or in your in-app task list:
- **Manage In-App Tasks**: Add, list, complete, and delete tasks with priority (`LOW`, `MEDIUM`, `HIGH`) and due labels.
- **Set Alarms & Timers**: Triggers Android's native Clock app (`AlarmClock.ACTION_SET_ALARM` / `ACTION_SET_TIMER`).
- **Create Calendar Events**: Opens your Calendar app pre-filled with title, description, and location (`CalendarContract.Events`).
- **Compose Emails**: Opens your Email app pre-filled with recipient, subject, and body (`ACTION_SENDTO`).
- **Web Search**: Opens a Google search in your browser (`ACTION_VIEW`).

## Quick Start in Android Studio
1. Open the `GeminiDevAgent` folder in **Android Studio** (Ladybug / Meerkat or newer, JDK 17).
2. Let Gradle sync.
3. Get a free API key from [Google AI Studio](https://aistudio.google.com/apikey).
4. **Option A (In-App — Easiest)**: Run the app on an emulator or device, tap the **Settings (gear)** icon in the top bar, and paste your Gemini API key.
5. **Option B (BuildConfig)**: Copy `local.properties.example` to `local.properties` and set:
   ```properties
   GEMINI_API_KEY=your_api_key_here
   ```
6. Default model is `gemini-flash-latest` (configurable in Settings).

## Production Security Note
For production apps distributed on Google Play, Google recommends using [Firebase AI Logic](https://firebase.google.com/docs/ai-logic/get-started) (`com.google.firebase:firebase-ai`) with Firebase App Check instead of shipping a raw Gemini API key in the client APK. This project uses direct REST calls with user-supplied keys so you can test and run it immediately without setting up a Firebase project.
