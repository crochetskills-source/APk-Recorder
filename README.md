# 🎬 Macro Recorder for Android

A native Android macro automation application built in **Kotlin**. It allows you to record any sequence of actions across your device (taps, clicks, text typing, app switching, scrolling, and hardware buttons), save the recorded session with a custom name, and replay them on demand.

---

## ✨ Features

- **System-Wide Action Capture**:
  - Taps and clicks on buttons, icons, and menus
  - Long presses and holds
  - Text typing into search bars, input fields, chat apps
  - Opening apps and switching between applications
  - Directional scrolling (up, down, left, right)
  - System navigation (Back, Home, Recents, Notifications)
- **Automatic Coordinate Fallback**: Uses accessibility node hierarchy first, and falls back to exact screen `(X, Y)` tap coordinates for games, WebViews, or custom canvases.
- **Save with Custom Name & Description**: Name your automation guides (e.g., `"Open WhatsApp & Send Message"`, `"Login Flow"`).
- **Interactive Floating Overlay**: Draggable floating panel that sits on top of any app with **Record**, **Stop**, and **Open App** buttons so you can record without returning to the Macro Recorder app.
- **Accurate Playback Timing**: Replays all actions with the exact timestamp intervals recorded.
- **Persistent Storage**: Saved as JSON macro guides in internal app storage.

---

## 📱 How to Build the Installable APK

You have **3 easy options** to generate the `.apk`:

### Option 1: Using Android Studio (Recommended & Easiest)
1. Download and open [Android Studio](https://developer.android.com/studio) (free).
2. Click **Open** and select the folder `/Volumes/Data/Test/MacroRecorder`.
3. Wait for Gradle sync to complete (it will automatically download SDK & dependencies).
4. In the top menu, click **Build** > **Build Bundle(s) / APK(s)** > **Build APK(s)**.
5. Once finished, click the **"locate"** link in the popup balloon. Your installable `app-debug.apk` is ready!

### Option 2: Using GitHub Actions (Zero Local Setup)
1. Push this `MacroRecorder` folder to a GitHub repository.
2. The included GitHub Actions workflow (`.github/workflows/build.yml`) will automatically trigger.
3. Go to the **Actions** tab on your repository, select the latest run, and download the **MacroRecorder-debug-apk** artifact!

### Option 3: Command Line (if JDK 17+ and Android SDK are installed)
```bash
cd /Volumes/Data/Test/MacroRecorder
./gradlew assembleDebug
```
The output APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📲 How to Install and Set Up on Your Android Device

### Step 1: Install the APK
- Transfer `app-debug.apk` to your phone and tap it to install (allow "Install from Unknown Sources" if prompted).
- Or install via ADB from your computer:
  ```bash
  adb install app-debug.apk
  ```

### Step 2: Grant Permissions (Required by Android)
1. **Accessibility Service**:
   - Open **Macro Recorder**.
   - Tap **"Enable Now"** on the yellow warning banner (or go to `Settings > Accessibility > Installed apps / Downloaded services`).
   - Select **Macro Recorder Service** and toggle it **ON**. Tap **Allow**.
   *(Android requires this system permission to allow any app to monitor and replay touches/typing)*.
2. **Display Over Other Apps (Floating Controls)**:
   - Tap **"Enable Overlay"** to grant permission to display floating buttons over other apps.

---

## 🚀 How to Use

### 1. Recording Actions
1. Open **Macro Recorder** and tap **"🎯 Start Floating Controls"**.
2. A small draggable widget will appear on your screen.
3. Switch to any app you want to automate.
4. Tap the **Red Circle (● REC)** button on the floating widget to begin recording.
5. Perform all the actions you want recorded (type messages, open apps, tap buttons, scroll, etc.).
6. When finished, tap the **Stop (■)** button.
7. Enter a **Name** for your macro (e.g. `Login and Refresh Feed`) and optional notes, then tap **Save**.

### 2. Running / Replaying Actions
1. Open **Macro Recorder**.
2. Find your macro in the **Saved Macros** list.
3. Tap **▶ Run**.
4. The app will replay all captured taps, typing, app launches, and gestures in the exact sequence!
5. To cancel a running playback at any time, tap the **Stop** button on screen or in the floating controls.
