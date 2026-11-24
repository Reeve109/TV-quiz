#!/usr/bin/env bash
# Manual build without Gradle (useful when Gradle/AGP download is blocked).
# Requires: JDK 17, Android SDK platform 34 + build-tools 34.0.0
# Set JAVA_HOME and ANDROID_HOME, or edit the defaults below.
set -e
cd "$(dirname "$0")"
JAVA_HOME="${JAVA_HOME:-$HOME/workspace/tools/jdk}"
ANDROID_HOME="${ANDROID_HOME:-$HOME/workspace/tools/sdk}"
BT="$ANDROID_HOME/build-tools/34.0.0"
AJAR="$ANDROID_HOME/platforms/android-34/android.jar"
export PATH="$JAVA_HOME/bin:$PATH"

OUT=app/build/manual
rm -rf "$OUT"; mkdir -p "$OUT/compiled" "$OUT/gen" "$OUT/classes" "$OUT/dex" app/build/outputs/apk/debug

echo "[1/6] aapt2 compile"
find app/src/main/res -type f | xargs "$BT/aapt2" compile -o "$OUT/compiled"

echo "[2/6] aapt2 link (produces unsigned apk + R.java)"
# aapt2 (unlike Gradle/AGP) requires package/version/uses-sdk in the manifest; inject them into a copy.
python3 - "$OUT/AndroidManifest.xml" <<'PY'
import pathlib, sys
src = pathlib.Path("app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
src = src.replace(
 '<manifest xmlns:android="http://schemas.android.com/apk/res/android">',
 '<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.sunzhe.tvquiz" android:versionCode="4" android:versionName="1.3">\n    <uses-sdk android:minSdkVersion="24" android:targetSdkVersion="33" />'
)
pathlib.Path(sys.argv[1]).write_text(src, encoding="utf-8")
PY
"$BT/aapt2" link -o "$OUT/unsigned.apk" -I "$AJAR" \
  --manifest "$OUT/AndroidManifest.xml" \
  --java "$OUT/gen" --auto-add-overlay "$OUT"/compiled/*.flat

echo "[3/6] javac"
find app/src/main/java "$OUT/gen" -name "*.java" > "$OUT/sources.txt"
javac -encoding UTF-8 -source 17 -target 17 -cp "$AJAR" -d "$OUT/classes" @"$OUT/sources.txt"

echo "[4/6] d8"
"$BT/d8" --lib "$AJAR" --output "$OUT/dex" $(find "$OUT/classes" -name "*.class")

echo "[5/6] add dex, align"
cp "$OUT/unsigned.apk" "$OUT/aligned.apk"
(cd "$OUT/dex" && zip -q -X "../../../../$OUT/aligned.apk" classes.dex)
"$BT/zipalign" -f 4 "$OUT/aligned.apk" "$OUT/final.apk"

echo "[6/6] sign (debug key)"
if [ ! -f "$OUT/debug.keystore" ]; then
  keytool -genkeypair -keystore "$OUT/debug.keystore" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" -noprompt
fi
"$BT/apksigner" sign --ks "$OUT/debug.keystore" --ks-pass pass:android --key-pass pass:android \
  --out app/build/outputs/apk/debug/app-debug.apk "$OUT/final.apk" || {
    mkdir -p app/build/outputs/apk/debug
    "$BT/apksigner" sign --ks "$OUT/debug.keystore" --ks-pass pass:android --key-pass pass:android \
      --out app/build/outputs/apk/debug/app-debug.apk "$OUT/final.apk"
  }
cp app/build/outputs/apk/debug/app-debug.apk TVQuiz.apk
echo "Done: TVQuiz.apk"
"$BT/apksigner" verify --print-certs TVQuiz.apk | head -5
ls -lh TVQuiz.apk
