# Gemini Omni Studio (Android)

A Kotlin + Jetpack Compose agent app powered by the Gemini API with function calling. It can act on your phone and in an in-app workspace.

## Features
- ~50 tools: alarms, timers, calendar, email, SMS, WhatsApp, apps, flashlight, maps, music
- Screen control via AccessibilityService (read, tap, type, scroll) when toggled on
- Workspace: read/write/edit/search files, shell commands, HTTP requests, GitHub reads
- Generates PDFs, Word-compatible docs, spreadsheets, slide decks, meeting notes
- Persistent memory, tasks, subagents, and a remote MCP client
- Multiple API keys with failover, selectable model, Fast/Plan/Max modes
- Approval dialog before sensitive actions (shell, SMS, WhatsApp, file delete, non-GET HTTP, MCP tools)

## Setup
1. Open this folder in Android Studio (JDK 17) and let Gradle sync.
2. Get a key at https://aistudio.google.com/apikey.
3. Paste it in the app under Settings, or copy `local.properties.example` to `local.properties` and set `GEMINI_API_KEY`.

## Security notes
- Keys entered in the app are stored in app-private SharedPreferences (backup disabled). Never commit `local.properties`.
- A key set in `local.properties` is compiled into the APK; do not distribute such builds.
- Screen control and shell access are powerful. Web content can try to steer the model, so review approval prompts.
- For Play Store distribution, use Firebase AI Logic with App Check instead of client-held keys, and review Google's AccessibilityService policy.

## License
MIT

## Build without Android Studio
Push to GitHub and the **Build APK** workflow runs automatically (or run it from the Actions tab). Download `GeminiOmniStudio-debug-apk` from the run's Artifacts and install the APK on your phone.
