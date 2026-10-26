# Casting to a TV

The cast button (Library and Now Playing headers) offers two kinds of TV.

## Smart TV screen (Miracast, Smart View, Screen Share)

Pick **Screen cast** in the cast dialog and choose the TV in Android's own screen-cast list.
Once connected, the TV becomes a second screen: FireTheDJ shows the visualizer you've picked full
screen in the TV's landscape, with the track, artist and BPM, while the phone keeps the controls.
Android carries the audio to the TV with the picture. Nothing to set up.

## Chromecast, Google TV, "Chromecast built-in"

Chromecasts are listed in the dialog directly. When one connects, FireTheDJ asks to capture its
own audio (Android shows the microphone permission and a "start casting?" prompt; choose
*entire screen* if asked: only FireTheDJ's audio is taken, the TV never sees your screen). The
mix is encoded and streamed over the Wi-Fi to the TV; the phone's media volume is turned down
while the TV plays, so you don't hear both a few seconds apart. Pause stops the TV at once.

Needs Android 10 or newer, and the phone and TV on the same network.

### Visualizers on the Chromecast

Out of the box the TV runs Google's **Default Media Receiver**, which plays the mix and shows the
track's artwork only. To get the visualizers on the TV, register FireTheDJ's own receiver page,
[`receiver/index.html`](../receiver/index.html). It plays the stream and draws the visualizers
from the audio it is playing, so they stay in time with the TV's sound.

1. **Register** at the [Google Cast SDK Developer Console](https://cast.google.com/publish)
   (one-time US$5 fee).
2. **Host the page on HTTPS.** With GitHub Pages: repository *Settings → Pages*, source
   *Deploy from a branch*, branch `main`, folder `/ (root)`. The page is then at
   `https://<user>.github.io/FireTheDJ/receiver/`.
3. **Add a Custom Receiver** in the console with that URL. You get an 8-character **App ID**.
4. **Register your TV for testing** (*Add new device*, using the TV's or Chromecast's serial
   number), then reboot it. Unpublished receivers only run on registered devices; publish the
   receiver in the console when it's ready for everyone.
5. Add the App ID to `local.properties` and rebuild:

   ```properties
   cast.appId=A1B2C3D4
   ```

### A TV doesn't show up?

Cast devices are found through multicast (mDNS) on the Wi-Fi. If a TV with Chromecast built-in is
missing from FireTheDJ *and* from YouTube's cast button, the network is losing those messages:

- restart the router;
- in the router's Wi-Fi settings, turn off *Multicast to unicast / Multicast enhancement* and
  *AP / client isolation*;
- try IGMP snooping off (check that any IPTV service still works afterwards).

Miracast doesn't depend on this, so **Screen cast** is the fallback.
