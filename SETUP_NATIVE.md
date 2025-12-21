# Native Call Integration Setup

This guide will help you set up the app to intercept real incoming calls on iOS and Android.

## Prerequisites

- **iOS**: Xcode 14+, CocoaPods, physical iOS device (CallKit doesn't work in simulator)
- **Android**: Android Studio, Android SDK, physical device or emulator
- API key from Portal → Settings → API Keys

## Step 1: Build Web App

```bash
npm run build
```

## Step 2: Sync Capacitor

This creates the iOS and Android native projects:

```bash
npx cap sync
```

## Step 3: iOS Setup

### 3.1 Open iOS Project

```bash
npx cap open ios
```

### 3.2 Add iOS SDK

1. In Xcode, go to File → Add Packages...
2. If the SDK is local, add it as a local package:
   - Click "Add Local..."
   - Navigate to `../ios-sdk`
   - Click "Add Package"
3. Or add from GitHub if published

### 3.3 Copy Native Files

Copy these files to your iOS project:

- `ios-native/CallKitManager.swift` → `ios/App/App/CallKitManager.swift`
- `ios-native/CallBrandingPlugin.swift` → `ios/App/App/CallBrandingPlugin.swift`

### 3.4 Update AppDelegate

In `ios/App/App/AppDelegate.swift`, add:

```swift
import CallKitManager

func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
    // Initialize if API key exists
    if let apiKey = UserDefaults.standard.string(forKey: "securenode_api_key"), !apiKey.isEmpty {
        CallKitManager.shared.initialize(apiKey: apiKey)
    }
    return true
}
```

### 3.5 Configure Info.plist

Add to `ios/App/App/Info.plist`:

```xml
<key>UIBackgroundModes</key>
<array>
    <string>voip</string>
    <string>remote-notification</string>
</array>
```

### 3.6 Enable Capabilities

1. Select your app target in Xcode
2. Go to "Signing & Capabilities"
3. Add "Push Notifications"
4. Add "Background Modes" → Enable "Voice over IP"

### 3.7 Register Plugin

In `ios/App/App/AppDelegate.swift`, register the plugin:

```swift
import Capacitor

// In didFinishLaunchingWithOptions, after creating the bridge:
bridge?.registerPlugin(CallBrandingPlugin.self)
```

## Step 4: Android Setup

### 4.1 Open Android Project

```bash
npx cap open android
```

### 4.2 Add Android SDK

In `android/settings.gradle`, add:

```gradle
include ':securenode-android-sdk'
project(':securenode-android-sdk').projectDir = new File('../android-sdk')
```

In `android/app/build.gradle`, add:

```gradle
dependencies {
    implementation project(':securenode-android-sdk')
    // ... other dependencies
}
```

### 4.3 Copy Native Files

Copy these files:

- `android-native/SecureNodeConnectionService.kt` → `android/app/src/main/java/io/securenode/nodekit/SecureNodeConnectionService.kt`
- `android-native/MainActivity.kt` → `android/app/src/main/java/io/securenode/nodekit/MainActivity.kt`
- `android-native/CallBrandingPlugin.java` → `android/app/src/main/java/io/securenode/nodekit/CallBrandingPlugin.java`

### 4.4 Update AndroidManifest.xml

Add to `android/app/src/main/AndroidManifest.xml`:

```xml
<manifest>
    <!-- Permissions -->
    <uses-permission android:name="android.permission.READ_PHONE_STATE" />
    <uses-permission android:name="android.permission.CALL_PHONE" />
    <uses-permission android:name="android.permission.ANSWER_PHONE_CALLS" />
    
    <application>
        <!-- ConnectionService -->
        <service
            android:name=".SecureNodeConnectionService"
            android:permission="android.permission.BIND_TELECOM_CONNECTION_SERVICE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.telecom.ConnectionService" />
            </intent-filter>
        </service>
    </application>
</manifest>
```

### 4.5 Register Plugin

In `android/app/src/main/java/io/securenode/nodekit/MainActivity.java` (or Kotlin), ensure the plugin is registered in Capacitor's plugin registry.

## Step 5: Testing

### iOS Testing

1. Build and run on a **physical device** (simulator doesn't support CallKit)
2. Enter your API key in the app
3. The SDK will sync branding data automatically
4. To test, you'll need to:
   - Send a VoIP push notification with a phone number, OR
   - Use a test call service that triggers CallKit

### Android Testing

1. Build and run on a device
2. Enter your API key in the app
3. Grant phone permissions when prompted
4. On Android 10+, make the app the default phone app:
   - Settings → Apps → Default apps → Phone app
   - Select "SecureNode Test"
5. Place a test call to a number with branding configured
6. The call screen should show the branding

## Troubleshooting

### iOS

- **CallKit not working**: Must use physical device, not simulator
- **No calls intercepted**: Ensure VoIP push notifications are configured
- **SDK not found**: Check that the iOS SDK package is properly added

### Android

- **Calls not intercepted**: Ensure app is set as default phone app (Android 10+)
- **Permissions denied**: Check that all phone permissions are granted
- **SDK errors**: Verify Android SDK is properly added to Gradle

## Next Steps

Once set up, the app will:
1. Automatically sync branding data when initialized
2. Intercept incoming calls on both platforms
3. Display branded caller information (name, logo, call reason)
4. Record imprints for billing

The branding test page in the app allows you to:
- Configure your API key
- Test branding lookups
- Preview how calls will appear
- Sync branding data manually

