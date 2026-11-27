# CART3R RL AI — Android visual coaching prototype

This is a native Kotlin Android starter project. It captures the screen only after the Android system's MediaProjection consent dialog and performs a deliberately simple orange-pixel heuristic. It does **not** control Rocket League Sideswipe, inject touches, read game memory, bypass anti-cheat, or guarantee wins.

## Requirements
- Android Studio Ladybug or newer
- JDK 17
- Android SDK 35
- Android device running Android 8.0 (API 26) or newer

## Open and build
1. Extract the ZIP.
2. In Android Studio choose **Open** and select the `CART3R_RL_AI` folder.
3. Allow Gradle sync to finish. Install SDK 35 if prompted.
4. Connect an Android phone with USB debugging enabled, or create an emulator.
5. Select the `app` run configuration and press **Run**.
6. To create an APK: **Build > Build Bundle(s) / APK(s) > Build APK(s)**. The debug APK is normally under `app/build/outputs/apk/debug/`.

## Use
1. Launch CART3R RL AI.
2. Tap **START SCREEN ANALYSIS**.
3. Accept Android's screen-capture prompt.
4. Switch to Sideswipe. Android may show a persistent screen-sharing indicator/notification.
5. Return to the app to view the latest candidate detection and coaching text.
6. Tap **STOP ANALYSIS** to end capture.
