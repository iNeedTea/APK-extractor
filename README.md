# Region APK Extractor

A small Android app for your own phone. Aurora Store downloads a region-locked app (set up here for the **Japanese IDOLiSH7 / アイドリッシュセブン**). This app then copies the installed app's APK files to a file you can keep or install on another device.

## How it works

1. **Download.** The "Open IDOLiSH7 (JP) in Aurora Store" button opens Aurora Store and searches for アイドリッシュセブン. Aurora is an open-source Google Play client. It handles the Japan region, so this app doesn't have to talk to Google Play itself.
2. **Extract.** The app lists installed apps, filtered to IDOLiSH7 by default. Tap one, choose where to save, and it writes either:
   - a plain `.apk`, if the app is a single APK, or
   - a `.apks` file (a zip of `base.apk` + `split_config.*.apk`), if the app ships as an App Bundle, which most games do.

It needs no root and no storage permission. It saves through Android's normal "Save as" file picker.

## One-time setup on the phone

1. Install **Aurora Store** from F-Droid or auroraoss.com.
2. Get a **Japan-region Google account**. The usual way: connect to a VPN server in Japan, create a new throwaway Google account, and don't add a non-Japanese payment method. In Aurora, sign in with that account.
   - Aurora's anonymous mode while on a Japan VPN sometimes works too. Whether it does depends on which shared account you're given.
   - Don't use your main Google account or your work account. Using an unofficial client breaks Google Play's terms of service, and Google sometimes flags those accounts.
3. Install this app. See "Build" below.

## Using it

1. Open Region APK Extractor and tap **Open IDOLiSH7 (JP) in Aurora Store**, then install the game.
2. Return to the app. The game shows up under "Show IDOLiSH7 only".
3. Tap it, then **Choose where to save**, and pick a folder (for example Downloads).
4. To install the `.apks` on another phone, use **SAI (Split APKs Installer)**. From a PC, unzip the file and run:
   ```
   adb install-multiple base.apk split_config.*.apk
   ```

## Things the export doesn't cover

- **Game data.** IDOLiSH7 downloads several GB of assets from the publisher's servers on first launch. Those files aren't in the APK. The download may also check that you're connecting from Japan, so keep the VPN on for the first launch.
- **The phone's CPU architecture.** The splits match the phone you extracted from (usually arm64). A `.apks` taken from an arm64 phone won't install on an x86 emulator.
- **Signatures.** The files are the original Google Play-signed APKs, unchanged. Updates through Aurora or Play install over them normally.

## Build

Open this folder in **Android Studio** (it installs the Android SDK and Gradle for you), then **Build > Build App Bundle(s) / APK(s) > Build APK(s)**. The APK lands in `app/build/outputs/apk/debug/`. Copy it to the phone and install it, allowing "install unknown apps" when asked.

Requirements: Android 8.0 (API 26) or newer. Built with compileSdk/targetSdk 35, AGP 8.7, Kotlin 2.0, Gradle 8.9.
