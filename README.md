# Cathode Browser

An ultra-lean, bloat-free web browser built specifically for Android TV and Google TV with leanback remotes.

<p align="center">
  <img src="res/drawable/banner.png" alt="Cathode Banner" width="480"/>
</p>

## Highlights

- **Ultra-lightweight**: APK footprint is under 150 KB with zero external runtime dependencies.
- **Back-to-Navbar Architecture**: Pressing `BACK` while browsing brings up the navigation bar instead of abruptly quitting the app.
- **History Navigation**: Pressing `BACK` while on the navigation bar seamlessly navigates back in browser history (`webView.goBack()`).
- **One-Touch Bar Dismissal**: Pressing D-Pad `UP` or `DOWN` on the navigation bar exits directly back to web browsing.
- **Intelligent Modal & Popup Edge Scrolling**: When the cursor enters any floating popup, modal dialog, dropdown menu, or iframe (such as Google's 9-dot app drawer, cookie consent sheets, or nested containers), edge scrolling dynamically binds to the boundaries of that modal. Pushing against the top or bottom of the modal smoothly scrolls the container content without requiring the cursor to travel to the distant TV screen edges.
- **Two-Stage Scroll Detent & Unified Glass Reticles**: Reaching any scrollable edge keeps the standard circular reticle intact. The cursor only morphs into the directional scroll reticle (`▲` / `▼`) on the first extra push in that direction, featuring matching 3dp drop shadows and glass ring styling that ensures crisp readability over white, black, or colorful backgrounds.
- **Fluid 60fps Smooth Scrolling**: Chromium GPU-composited smooth scrolling (`behavior: 'smooth'`) for pages and containers, paired with targeted touch gestures for isolated cross-origin iframes.
- **Material Blue Page Progress**: Clean Electric Blue (`#2979FF`) loading bar at the top of the screen instead of OEM default yellow.
- **Three-Layer Universal Scrolling**:
  - **Layer 1 (Native Generic Motion)**: Hardware mouse-wheel `ACTION_SCROLL` events (`AXIS_VSCROLL`) routed directly to the element under the cursor.
  - **Layer 2 (Targeted Touch Flick)**: Physics-tuned touch gestures at `(cursorX, cursorY)` that seamlessly scroll `<iframe>` widgets and cross-origin popups where DOM access is restricted.
  - **Layer 3 (Universal DOM)**: Deep multi-container scroll covering `window` and any scrollable container with overflow.
- **Dedicated App Exit Flow**: A clear Exit button `[->]` on the far left of the bottom bar with an Android TV confirmation dialog (`[Cancel]` / `[Exit]`) to prevent accidental quits.
- **Direct D-Pad Tabbing**: Full focusable support for TV remotes with high-contrast outlines (2.5dp pure white border on the URL input when focused).
- **Instant Search with Clear Button**: One-click `[ ✕ ]` button embedded directly in the search bar to clear input or start a new search.
- **Color Remote Shortcuts**:
  - 🔴 **Red**: Toggle Bookmarks Drawer
  - 🟢 **Green**: Reload Page
  - 🔵 **Blue**: Quick Search Mode
  - 📋 **Menu / Window**: Toggle Navigation Bar

---

## Remote Control Guide

| Remote Key | Context: Web Browsing | Context: Navigation Bar |
| :--- | :--- | :--- |
| **D-Pad Directional Keys** | Moves virtual cursor across page | Tabs focus between navigation buttons |
| **D-Pad Center (OK)** | Simulates left-click at cursor position | Activates focused button / opens URL |
| **D-Pad Up / Down** | Edge scroll (when cursor is at screen edge) | Exits navigation bar back to web content |
| **BACK** | Opens bottom navigation bar | Navigates back in browser history |
| **Ch+ / Ch- / PgUp / PgDn** | Scrolls page up / down | Scrolls page up / down |
| **Red Button** | Toggles Bookmarks Drawer | Toggles Bookmarks Drawer |
| **Green Button** | Reloads current page | Reloads current page |
| **Blue Button** | Focuses search bar at top | Focuses search bar at top |
| **Exit Button (on bar)** | — | Opens confirmation modal to exit |

---

## Memory & System Hygiene

- **Automatic Keyboard Dismissal**: Soft keyboard automatically dismisses when returning to webview, pausing, or closing dialogs.
- **System Memory Trimming**: Responds to `onTrimMemory(TRIM_MEMORY_MODERATE)` to release WebView caches when the TV is under memory pressure.
- **Clean Destruction**: Stops all ongoing network requests, clears history, loads `about:blank`, unregisters handler callbacks, and destroys the WebView when exiting.

---

## Building from Source

### Prerequisites
- JDK 17+ (or Amazon Corretto 21)
- Android SDK Platform `android-30`
- Android Build-Tools `35.0.0`

### Build Script
```bash
chmod +x build.sh
./build.sh
```
The compiled, aligned, and signed APK will be output to `build/Cathode.apk`.

---

## Testing & Installation

### Installing on Android TV (Physical Device)
1. Enable **Developer Options** and **ADB Debugging** on your Android TV.
2. Connect over local Wi-Fi:
   ```bash
   adb connect <tv-ip-address>:5555
   ```
3. Install the APK:
   ```bash
   adb install -r build/Cathode.apk
   ```

### Testing on Android TV Emulator
You can test Cathode on any Android TV or Google TV Virtual Device (AVD):
1. Open **Android Studio** -> **Device Manager** -> **Create Device**.
2. Select **TV** category (e.g. *Android TV (1080p)* or *Google TV (1080p)*).
3. Select system image with API 30+ (e.g., Android 11+ x86_64).
4. Launch the emulator.
5. In your terminal, run:
   ```bash
   adb install -r build/Cathode.apk
   adb shell am start -n com.cathode.browser/.MainActivity
   ```
6. Use keyboard arrow keys for D-Pad, Enter for OK, and Escape/Backspace for TV Remote `BACK`.

---

## License
MIT License
