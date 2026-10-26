# Signing release builds

Every APK you publish has to be signed with **your own release key**. Android only installs an
update over an existing install when both are signed with the same key, so this key is what lets
people update FireTheDJ without uninstalling. Lose it and you can never ship an update to those
installs; leak it and someone else can. Keep it, and its passwords, out of git and backed up.

## 1. Create the key (once)

Run this in the project folder. `keytool` comes with the JDK (Android Studio bundles one in
`jbr\bin`).

```bash
keytool -genkeypair -v -keystore firethedj-release.jks -alias firethedj -keyalg RSA -keysize 4096 -validity 10000
```

It asks for a keystore password, a key password (you can use the same one) and a name for the
certificate ("Surya", "FireTheDJ"…). Choose strong passwords and save them in a password manager.

`*.jks` is already in `.gitignore`, so the key never reaches GitHub. **Back up
`firethedj-release.jks` somewhere safe** (a password manager attachment, an encrypted drive).

## 2. Point the build at it

Copy `keystore.properties.example` to `keystore.properties` (also git-ignored) and fill it in:

```properties
storeFile=firethedj-release.jks
storePassword=…
keyAlias=firethedj
keyPassword=…
```

From then on `./gradlew assemblePhoneRelease assembleTvRelease` signs with your key. Without the file, release builds
fall back to the local debug key, which is fine for your own phone and nothing else.

## 3. Build and check

```bash
./gradlew testPhoneDebugUnitTest assemblePhoneRelease assembleTvRelease
```

That builds both apps:

| App | APK |
|---|---|
| FireTheDJ (phones) | `app/build/outputs/apk/phone/release/app-phone-release.apk` |
| FireTheDJ TV (Android TV) | `app/build/outputs/apk/tv/release/app-tv-release.apk` |

Check who signed them (`apksigner` is in the Android SDK's `build-tools`):

```bash
apksigner verify --print-certs app/build/outputs/apk/phone/release/app-phone-release.apk
```

The certificate should show the name you gave in step 1.

## 4. Publish on GitHub

1. Raise `versionCode` (by one) and `versionName` in `app/build.gradle.kts`, commit and push.
   Both apps share the version.
2. On GitHub: **Releases → Draft a new release**, tag `v1.1.0` (matching `versionName`), add notes.
3. Rename the APKs to `FireTheDJ-v1.1.0.apk` and `FireTheDJ-TV-v1.1.0.apk`, attach both, publish.

## Switching from the debug key

Installs made with the debug-signed builds can't update to the release-signed one: Android sees
a different signer. Uninstall the old build once, then install the release APK. (FireTheDJ's
analysis cache and library are app data, so they go with the uninstall.)
