# Fix SecurityException: Unknown calling package name 'com.google.android.gms'

The application is failing with a `SecurityException` when interacting with Google Play Services (via Firebase). This is typically caused by missing package visibility declarations or incompatible SDK versions.

## Proposed Changes

### [app](file:///C:/Users/3036264/StudioProjects/WitsPath/app)

#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/3036264/StudioProjects/WitsPath/app/src/main/AndroidManifest.xml)
- Add `INTERNET` and `ACCESS_NETWORK_STATE` permissions required for Firebase.
- Add `<queries>` block for `com.google.android.gms` to ensure package visibility on Android 11+.

#### [MODIFY] [build.gradle.kts](file:///C:/Users/3036264/StudioProjects/WitsPath/app/build.gradle.kts)
- Revert `compileSdk` and `targetSdk` from `37` to `35` (Android 15) to ensure compatibility with current Google Play Services versions.
- Correct the syntax for `compileSdk`.

## Verification Plan

### Automated Tests
- Run `gradlew assembleDebug` to ensure the project builds correctly.

### Manual Verification
- Deploy the app to a device/emulator and verify that Firebase Auth/Firestore calls no longer trigger the `SecurityException`.
