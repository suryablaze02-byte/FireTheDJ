# Installing FireTheDJ

FireTheDJ isn't on the Play Store yet, so you install it from an APK on the
[**Releases**](https://github.com/suryablaze02-byte/FireTheDJ/releases/latest) page. There are two apps:

| APK | Install it on |
|---|---|
| `FireTheDJ-vX.Y.Z.apk` | Android phones and tablets |
| `FireTheDJ-TV-vX.Y.Z.apk` | Android TV and Google TV (TCL, Sony, Chromecast with Google TV, Mi Box, Nvidia Shield, …) |

Both need **Android 8.0 or newer**. Casting the mix to a Chromecast needs Android 10+, and YouTube Music streaming needs Android 13+.

- [Phone or tablet](#phone-or-tablet)
- [Android TV and Google TV](#android-tv-and-google-tv)
- [First launch](#first-launch)
- [Updating](#updating)
- [Troubleshooting](#troubleshooting)

---

## Phone or tablet

1. On your phone, open the [latest release](https://github.com/suryablaze02-byte/FireTheDJ/releases/latest) and tap **FireTheDJ-vX.Y.Z.apk** under *Assets* to download it.
2. Open the downloaded file from your browser's downloads or the **Files** app.
3. If Android says it can't install apps from this source, tap **Settings**, turn on **Allow from this source**, and go back.
4. Tap **Install**, then **Open**.

> [!NOTE]
> Google Play Protect may warn that it doesn't recognise the app. That's because FireTheDJ isn't installed from the Play Store. Tap **More details › Install anyway**. The full source is in this repository if you'd like to check it, or [build it yourself](../README.md#build).

## Android TV and Google TV

TVs don't come with a browser that can download APKs, so get the file onto the TV one of these ways.

### Option A: Downloader app (easiest, no computer)

1. On the TV, install **Downloader** by AFTVnews from the Play Store.
2. Open Downloader, type the code **`9758344`** in the URL box and press **Go**.
   It always downloads the latest FireTheDJ TV. (The code is a short link to
   `https://github.com/suryablaze02-byte/FireTheDJ/releases/latest/download/FireTheDJ-TV.apk`;
   you can type that instead, or `aftv.news/9758344` in a browser.)
3. When asked, allow Downloader to install unknown apps (on Google TV: **Settings › System › About**, click **Android TV OS build** seven times to turn on developer options first, then **Settings › Apps › Security & restrictions › Unknown sources › Downloader**).
4. Tap **Install**. Afterwards you can delete the APK to free space.

### Option B: Send Files to TV

1. Install **Send Files to TV** on both your phone and your TV from the Play Store.
2. Download **FireTheDJ-TV-vX.Y.Z.apk** on your phone and send it to the TV.
3. On the TV, open the received file and install it (allow unknown apps for Send Files to TV when asked).

### Option C: USB drive

1. Copy **FireTheDJ-TV-vX.Y.Z.apk** to a USB drive on your computer.
2. Plug it into the TV and open it with a file manager (for example **File Commander** or **X-plore** from the Play Store).
3. Install it, allowing unknown apps for that file manager when asked.

### Option D: adb (for developers)

With **Network debugging** / **USB debugging** turned on in the TV's developer options:

```bash
adb connect <tv-ip-address>:5555
adb install -r FireTheDJ-TV-vX.Y.Z.apk
```

Once installed, **FireTheDJ TV** shows up in the TV's app list. On Google TV it's under **Apps** or **Your apps**. You can move it to the front with **Reorder**.

---

## First launch

A short guided tour shows you around. Then add some music:

- **Phone**: on the **Music** tab, add a folder from your phone, or paste a YT Music, Audius, Jamendo, Internet Archive or M3U link.
- **TV**: on Home, choose **This TV & USB** to scan the TV's storage and USB drives, or **YT Music** for playlists by mood and genre.

FireTheDJ analyses every track on the device first (tempo, key, phrases, drops). The count at the top shows how many are still analysing, and genre crates fill in as it goes. Pick a crate and the set starts.

Permissions FireTheDJ may ask for:

| Permission | Why |
|---|---|
| Music and audio / storage | to find songs on the device (TV), or the folders you choose (phone) |
| Notifications | media controls while the set plays in the background |
| Screen capture and microphone | only when you stream the mix to a Chromecast: Android captures the app's own sound through this; nothing is recorded from the mic |
| Install unknown apps | only when you install an update from inside the app |

## Updating

From version 1.2.0 on, FireTheDJ checks GitHub once a day. When a new version is out you'll see a banner; open **Settings › Updates**, read what's new and tap **Install**. Android asks once to let FireTheDJ install apps.

You can also update by hand: install the newer APK over the old one, the same way as above. Your library, crates and settings are kept.

Updating from 1.0 or 1.1? Install the new APK by hand once; in-app updates work from then on.

## Troubleshooting

**"App not installed" or "package conflicts with an existing package"**
You have a copy signed with a different key, for example one you built yourself. Uninstall it first, then install the release APK. This also clears your library.

**"There was a problem parsing the package"**
The download didn't finish, or the device is older than Android 8.0. Download it again.

**I can't find the TV app after installing**
Look in **Apps › Your apps** (Google TV) or the full app list. If it isn't there, check you installed the **TV** APK; the phone APK doesn't appear on a TV's home screen.

**Songs stay on "analysing" for a long time**
Analysis runs on the device, so a big library on a slower TV can take a while the first time. You can start a set from a crate as soon as it has songs; new tracks join the set as they finish.

**YT Music doesn't work**
It needs Android 13+ and an internet connection. YouTube support uses unofficial interfaces and can break when YouTube changes things; look for a newer release, or [open an issue](https://github.com/suryablaze02-byte/FireTheDJ/issues).

Still stuck? [Open an issue](https://github.com/suryablaze02-byte/FireTheDJ/issues) with your device, Android version and FireTheDJ version (shown in **Settings**).
