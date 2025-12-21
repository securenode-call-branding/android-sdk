# Building the Android APK

## Prerequisites

1. **Android Studio** installed
2. **Android SDK** installed (usually comes with Android Studio)
3. **Java JDK** 11 or higher

## Step 1: Set Android SDK Location

The build needs to know where your Android SDK is located. You have two options:

### Option A: Create local.properties file (Recommended)

Create a file `android/local.properties` with:

```properties
sdk.dir=C\:\\Users\\YOUR_USERNAME\\AppData\\Local\\Android\\Sdk
```

Replace `YOUR_USERNAME` with your Windows username. The path might also be:
- `C:\Android\Sdk` (if installed elsewhere)
- Check Android Studio → Settings → Appearance & Behavior → System Settings → Android SDK for the exact path

### Option B: Set Environment Variable

Set the `ANDROID_HOME` environment variable:
- Windows: `setx ANDROID_HOME "C:\Users\YOUR_USERNAME\AppData\Local\Android\Sdk"`
- Then restart your terminal

## Step 2: Build the APK

### Using Gradle (Command Line)

```bash
cd android
.\gradlew assembleDebug
```

The APK will be created at:
`android/app/build/outputs/apk/debug/app-debug.apk`

### Using Android Studio

1. Open Android Studio
2. File → Open → Select the `android` folder
3. Wait for Gradle sync to complete
4. Build → Build Bundle(s) / APK(s) → Build APK(s)
5. The APK will be in `app/build/outputs/apk/debug/app-debug.apk`

## Step 3: Install on Device

### Via ADB (if device connected)

```bash
adb install android/app/build/outputs/apk/debug/app-debug.apk
```

### Via File Transfer

1. Copy `app-debug.apk` to your Android device
2. Enable "Install from Unknown Sources" in device settings
3. Open the APK file on your device and install

## Building Release APK (For Production)

For a release APK, you need to:

1. Create a keystore for signing:
```bash
keytool -genkey -v -keystore securenode-release.keystore -alias securenode -keyalg RSA -keysize 2048 -validity 10000
```

2. Create `android/key.properties`:
```properties
storePassword=YOUR_STORE_PASSWORD
keyPassword=YOUR_KEY_PASSWORD
keyAlias=securenode
storeFile=../securenode-release.keystore
```

3. Update `android/app/build.gradle` to use the keystore (see Android signing documentation)

4. Build release APK:
```bash
cd android
.\gradlew assembleRelease
```

## Troubleshooting

### "SDK location not found"
- Make sure `android/local.properties` exists with correct `sdk.dir` path
- Or set `ANDROID_HOME` environment variable

### "Gradle sync failed"
- Open Android Studio and let it sync the project
- Check that all required SDK components are installed

### "Build failed"
- Check that Java JDK 11+ is installed
- Verify Android SDK is properly configured
- Try cleaning the build: `.\gradlew clean`

