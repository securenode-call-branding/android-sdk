# PowerShell script to build Android APK
# This script helps set up and build the APK

Write-Host "Building SecureNode Test APK..." -ForegroundColor Cyan

# Check if Android SDK path exists
$sdkPath = $env:ANDROID_HOME
if (-not $sdkPath) {
    # Try common locations
    $commonPaths = @(
        "$env:LOCALAPPDATA\Android\Sdk",
        "C:\Android\Sdk",
        "$env:USERPROFILE\AppData\Local\Android\Sdk"
    )
    
    foreach ($path in $commonPaths) {
        if (Test-Path $path) {
            $sdkPath = $path
            Write-Host "Found Android SDK at: $sdkPath" -ForegroundColor Green
            break
        }
    }
}

if (-not $sdkPath) {
    Write-Host "Android SDK not found!" -ForegroundColor Red
    Write-Host "Please either:" -ForegroundColor Yellow
    Write-Host "1. Set ANDROID_HOME environment variable, OR" -ForegroundColor Yellow
    Write-Host "2. Create android/local.properties with: sdk.dir=C\:\\Users\\YOUR_USERNAME\\AppData\\Local\\Android\\Sdk" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "You can find your SDK path in Android Studio:" -ForegroundColor Yellow
    Write-Host "Settings → Appearance & Behavior → System Settings → Android SDK" -ForegroundColor Yellow
    exit 1
}

# Create local.properties if it doesn't exist
$localPropsPath = "android\local.properties"
if (-not (Test-Path $localPropsPath)) {
    Write-Host "Creating local.properties..." -ForegroundColor Yellow
    $sdkDir = $sdkPath -replace '\\', '\\'
    "sdk.dir=$sdkDir" | Out-File -FilePath $localPropsPath -Encoding ASCII
    Write-Host "Created $localPropsPath" -ForegroundColor Green
}

# Build the APK
Write-Host "Building APK..." -ForegroundColor Cyan
Set-Location android
.\gradlew assembleDebug

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "✓ APK built successfully!" -ForegroundColor Green
    Write-Host "APK location: android\app\build\outputs\apk\debug\app-debug.apk" -ForegroundColor Cyan
} else {
    Write-Host ""
    Write-Host "✗ Build failed. Check the error messages above." -ForegroundColor Red
    exit 1
}

Set-Location ..

