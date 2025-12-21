# Native Integration Guide

This guide explains how to integrate the SecureNode SDKs into the iOS and Android native projects for real call interception.

## Prerequisites

1. Build the web app first:
   ```bash
   npm run build
   ```

2. Sync Capacitor to create native projects:
   ```bash
   npx cap sync
   ```

## iOS Integration

### 1. Add iOS SDK to Xcode Project

1. Open the iOS project in Xcode:
   ```bash
   npx cap open ios
   ```

2. Add the SecureNode iOS SDK:
   - File → Add Packages...
   - Enter: `https://github.com/SecureNode-Call-Identidy-SDK/apple-ios-sdk.git`
   - Or add the local SDK from `../ios-sdk` as a local package

### 2. Copy Native Files

Copy the following file to your iOS project:
- `ios-native/CallKitManager.swift` → `ios/App/App/CallKitManager.swift`

### 3. Update AppDelegate

In `ios/App/App/AppDelegate.swift`, add:

```swift
import UIKit
import Capacitor

@UIApplicationMain
class AppDelegate: UIResponder, UIApplicationDelegate {
    
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        // Initialize CallKit manager
        // API key should be retrieved from UserDefaults or Keychain
        if let apiKey = UserDefaults.standard.string(forKey: "securenode_api_key"), !apiKey.isEmpty {
            CallKitManager.shared.initialize(apiKey: apiKey)
        }
        
        return true
    }
    
    // Handle VoIP push notifications
    func application(_ application: UIApplication, didReceiveRemoteNotification userInfo: [AnyHashable : Any], fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void) {
        // Extract call information from push notification
        if let phoneNumber = userInfo["phone_number"] as? String {
            let uuid = UUID()
            CallKitManager.shared.handleIncomingCall(uuid: uuid, phoneNumber: phoneNumber)
        }
        completionHandler(.newData)
    }
}
```

### 4. Configure Info.plist

Add to `ios/App/App/Info.plist`:

```xml
<key>UIBackgroundModes</key>
<array>
    <string>voip</string>
    <string>remote-notification</string>
</array>
```

### 5. Enable Capabilities

In Xcode:
1. Select your app target
2. Go to Signing & Capabilities
3. Add "Push Notifications" capability
4. Add "Background Modes" → Enable "Voice over IP"

## Android Integration

### 1. Add Android SDK

In `android/app/build.gradle`, add:

```gradle
dependencies {
    // SecureNode Android SDK
    implementation project(':securenode-android-sdk')
    // Or if using Maven:
    // implementation 'com.securenode:android-sdk:1.0.0'
}
```

If using local SDK, add to `android/settings.gradle`:

```gradle
include ':securenode-android-sdk'
project(':securenode-android-sdk').projectDir = new File('../android-sdk')
```

### 2. Copy Native Files

Copy the following files:
- `android-native/SecureNodeConnectionService.kt` → `android/app/src/main/java/io/securenode/nodekit/SecureNodeConnectionService.kt`
- `android-native/MainActivity.kt` → `android/app/src/main/java/io/securenode/nodekit/MainActivity.kt`

### 3. Update AndroidManifest.xml

Add to `android/app/src/main/AndroidManifest.xml`:

```xml
<manifest>
    <!-- Permissions -->
    <uses-permission android:name="android.permission.READ_PHONE_STATE" />
    <uses-permission android:name="android.permission.CALL_PHONE" />
    <uses-permission android:name="android.permission.ANSWER_PHONE_CALLS" />
    
    <application>
        <!-- ConnectionService for call handling -->
        <service
            android:name=".SecureNodeConnectionService"
            android:permission="android.permission.BIND_TELECOM_CONNECTION_SERVICE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.telecom.ConnectionService" />
            </intent-filter>
        </service>
        
        <!-- Main Activity -->
        <activity
            android:name=".MainActivity"
            android:exported="true"
            ...>
        </activity>
    </application>
</manifest>
```

### 4. Register Phone Account

The app needs to register as a phone account handler. This is done in MainActivity when permissions are granted.

## Testing

### iOS Testing

1. Build and run on a physical device (CallKit doesn't work in simulator)
2. Send a test VoIP push notification with phone number
3. The call should appear with branding applied

### Android Testing

1. Build and run on a device
2. Make the app the default phone app (Android 10+)
3. Place a test call to a number with branding
4. The call screen should show branding

## API Key Configuration

The API key should be set from the Vue app and stored securely:

- **iOS**: Store in Keychain or UserDefaults
- **Android**: Store in SharedPreferences or EncryptedSharedPreferences

You can add a Capacitor plugin to bridge the API key from JavaScript to native code.

## Notes

- iOS CallKit requires a physical device - simulators don't support it
- Android requires the app to be set as default phone app (Android 10+)
- Both platforms require proper permissions to be granted
- VoIP push notifications are needed for iOS to intercept calls
- Android ConnectionService intercepts calls at the system level

