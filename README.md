# Cathode Browser

An ultra-lean, bloat-free web browser built specifically for Android TV and leanback remotes.

<p align="center">
  <img src="res/drawable/banner.png" alt="Cathode Banner" width="480"/>
</p>

## Highlights

- **Ultra-lightweight**: APK footprint is under 60 KB.
- **Pure Virtual Cursor**: Smooth, physics-tuned pointer navigation with DPAD controls and click animation.
- **TV-Optimized Layout**: Centered, auto-hiding bottom navigation bar with high-contrast, fully rounded circular and pill buttons.
- **Instant Search with Clear Button**: One-click `[ ✕ ]` button embedded directly in the search pill to quickly clear and enter new URLs or search queries.
- **Dual Navigation Architecture**: Seamlessly switch between D-Pad direct tabbing for app controls & bookmarks, and smooth virtual cursor for browsing web links.
- **Universal TV Support**: Works out-of-the-box on all Android TV and Google TV devices with any remote.
- **Modern Web Engine**: Modern desktop User-Agent ensures rich modern web layouts without mobile or legacy fallback CSS degradations.
- **Hardware Remote Color Shortcuts**:
  - 🔴 **Red**: Toggle Bookmarks Drawer
  - 🟢 **Green**: Reload Page
  - 🔵 **Blue**: Focus Search / URL bar (glides to top with keyboard)

## Building from Source

### Prerequisites
- JDK 17+ (or Corretto 21)
- Android SDK Platform `android-30`
- Android Build-Tools `35.0.0`

### Build Script
```bash
chmod +x build.sh
./build.sh
```
The compiled, aligned, and signed APK will be output to `build/Cathode.apk`.

### Installing to Android TV
```bash
adb connect <tv-ip-address>:5555
adb install -r build/Cathode.apk
```

## License
MIT License
