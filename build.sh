#!/usr/bin/env bash
set -e

export JAVA_HOME="/tmp/jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
BUILD_TOOLS="/Users/midnight/Library/Android/sdk/build-tools/35.0.0"
PLATFORM="/Users/midnight/Library/Android/sdk/platforms/android-30/android.jar"

cd "$(dirname "$0")"

rm -rf build bin gen
mkdir -p build bin gen

echo "==> 1. Compiling resources..."
"$BUILD_TOOLS/aapt2" compile --dir res -o build/compiled_res.zip

echo "==> 2. Linking APK & generating R.java..."
"$BUILD_TOOLS/aapt2" link build/compiled_res.zip \
  -I "$PLATFORM" \
  --manifest AndroidManifest.xml \
  --no-xml-namespaces \
  --enable-compact-entries \
  --java gen \
  -o build/Cathode.unaligned.apk

echo "==> 3. Compiling Java sources..."
javac -d bin -cp "$PLATFORM" gen/com/cathode/browser/R.java src/com/cathode/browser/*.java

echo "==> 4. Dexing bytecode..."
"$BUILD_TOOLS/d8" --release --min-api 26 --lib "$PLATFORM" --output build $(find bin -name "*.class")

echo "==> 5. Packaging classes.dex..."
cd build
zip -9 -u Cathode.unaligned.apk classes.dex
cd ..

echo "==> 6. Zipalign..."
"$BUILD_TOOLS/zipalign" -f -p 4 build/Cathode.unaligned.apk build/Cathode.aligned.apk

echo "==> 7. Signing APK..."
"$BUILD_TOOLS/apksigner" sign --ks debug.keystore --ks-pass pass:android --out build/Cathode.apk build/Cathode.aligned.apk

echo "==> Build finished: build/Cathode.apk"
ls -lh build/Cathode.apk
