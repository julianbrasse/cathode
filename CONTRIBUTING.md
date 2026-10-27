# Contributing to Cathode

Thank you for your interest in contributing to Cathode! Cathode is an ultra-lean, bloat-free web browser engineered specifically for Android TV and Google TV. To ensure the browser remains blisteringly fast, rock-solid, and lightweight, all contributions must strictly adhere to the development rules and architectural guidelines outlined below.

---

## 1. Core Architectural Principles

- **Zero External Runtime Dependencies**: Cathode has zero third-party libraries, zero analytics SDKs, and zero heavy frameworks. Everything is written using pure Android SDK APIs (API 30+) and native WebKit/Chromium primitives.
- **Strict Size Budget**: The final release APK must remain under 200 KB (currently ~163 KB). PRs introducing bloat, external JARs, or multi-megabyte assets will not be merged.
- **Microscopic Memory Footprint**: Baseline memory consumption must remain between 3 MB and 10 MB RAM so low-spec smart TVs never suffer low-memory kills or frame drops.
- **Pure Command-Line Build Toolchain**: The project builds directly via `build.sh` using AAPT2, javac, d8, zipalign, and apksigner -- no 500 MB Gradle daemon or external Gradle wrappers required.

---

## 2. The 60px / 20px Unified Grid Standard

Cathode's user experience is anchored by a deterministic, mathematically aligned coordinate grid. Every contributor must maintain these geometric invariants:

1. **60px Cursor Dimension**:
   - The virtual reticle has an exact diameter of 60 pixels (`CURSOR_SIZE_PX = 60f`).
   - The visual click center is strictly located at the exact mathematical center of the reticle (`CURSOR_HALF_PX = 30f`).
2. **20px Step Increment**:
   - Every single D-Pad click advances the cursor by exactly 20 pixels (`CURSOR_STEP_PX = 20f`).
   - Rapid D-Pad repeat advances by 40 pixels (`CURSOR_REPEAT_STEP_PX = 40f`, an exact $2 \times 20$ multiple).
3. **Zero Coordinate Drift Guarantee**:
   - `cursorX` and `cursorY` must always be exact multiples of 20 (`cursorX % 20 == 0`, `cursorY % 20 == 0`).
   - Every coordinate assignment must pass through `Math.round(coord / 20f) * 20f`.
   - Never allow unrounded DOM coordinates (`getBoundingClientRect()`) or arbitrary floats to set cursor position.
4. **40px Safe Screen Edge Margins**:
   - Cursor positions are clamped to `[40, screenWidth - 40]` and `[40, screenHeight - 40]`.
   - This accommodates CRT and modern TV overscan safety zones and guarantees the cursor is never obscured by TV bezels.
5. **Modal & Container Grid Snapping**:
   - All detected popup boxes, modals, iframes, and dropdowns must have their bounding boxes clamped to the 20px grid using `Math.floor(val / 20) * 20` for top/left and `Math.ceil(val / 20) * 20` for bottom/right.

---

## 3. TV D-Pad UX & Interaction Rules

- **Back-to-Navbar Architecture**: Pressing `BACK` during web browsing must reveal the navigation bar instead of abruptly terminating the application. Only a second `BACK` on root history triggers the exit confirmation dialog.
- **Two-Stage Scroll Detents**: Moving the cursor to a screen or container edge keeps the standard circular reticle. Only upon pressing further in that direction does the cursor morph into the directional scroll indicator (UP / DOWN).
- **Dedicated Remote Shortcuts**: Standard TV remote color keys must be preserved:
  - **Red**: Bookmarks shelf toggle.
  - **Green**: Page reload.
  - **Yellow**: Add current page to bookmarks.
  - **Blue**: Instant URL / search bar focus.
- **Uncluttered Canvas**: The navigation bar must automatically hide during active content browsing to maximize viewing area on 1080p and 4K displays.

---

## 4. Visual Design & Shadow Standards

- **Centered Shadows Only**: Never use directional X/Y offsets for drop shadows or glow halos (`box-shadow: 0 0 <blur> <color>`). All shadows must remain concentric and centered with blur rather than artificial angle offsets.
- **Modular 20dp Radius**: In accordance with the 60px/20px grid system, rounded card surfaces, modal dialogs, and HUD elements must use a 20dp radius (`res/drawable/debug_card_background.xml`).
- **Dark Glass Palette**:
  - Background: `#0A0A0C`
  - Glass Surface: `#101116` / `#1A1B22`
  - Electric Blue Accent: `#2979FF`
  - CRT Cyan Glow: `#00E5FF`
  - Subtle Border: `rgba(255, 255, 255, 0.10)`
  - Status Success: `#00E676`

---

## 5. Viewport & Auto-Fit Clamping

- **Minimum Zoom Limit**: Automatic viewport zoom injection must never scale below 0.70 (`Math.max(0.70, factor)`).
- **Quantized Zoom Steps**: Calculated zoom factors must strictly snap to multiples of 0.05 (`Math.round(factor / 0.05) * 0.05`) to prevent WebKit font blur and subpixel raster distortion.

---

## 6. Code Quality & Repository Hygiene

- **Strict 7-Bit ASCII Mandate**: All source files (Java, XML, HTML, CSS, SVG, Bash, Markdown) must consist exclusively of standard 7-bit ASCII characters (values 0 to 127). No curly smart quotes, em-dashes, or non-ASCII unicode symbols.
- **Zero Compiler Warnings**: All Java code must compile with `javac -Xlint:all` with zero warnings.
- **Zero Dead Code or Bloat**: Keep helper classes lean. Do not leave commented-out experimental code, unused imports, or debug logging loops in pull requests.
- **Strict Privacy**: Zero telemetry, zero analytics tracking, zero user identifier transmission, and zero PII.

---

## 7. Submitting a Pull Request

Before opening a pull request, run the following verification checklist:

```bash
# 1. Clean build verification
./build.sh

# 2. Compiler warning check
export JAVA_HOME="/tmp/jdk/Contents/Home"
javac -Xlint:all -d bin -cp "/Users/midnight/Library/Android/sdk/platforms/android-30/android.jar" gen/com/cathode/browser/R.java src/com/cathode/browser/*.java

# 3. 7-bit ASCII check
python3 -c "
import os
for root, _, files in os.walk('.'):
    if '.git' in root: continue
    for f in files:
        if f.endswith(('.java', '.xml', '.html', '.css', '.svg', '.sh', '.md')):
            p = os.path.join(root, f)
            with open(p, 'rb') as fp:
                for idx, b in enumerate(fp.read(), 1):
                    if b > 127: raise SystemExit(f'Non-ASCII byte in {p} at byte {idx}')
print('All repository files are 100% clean ASCII!')
"
```
