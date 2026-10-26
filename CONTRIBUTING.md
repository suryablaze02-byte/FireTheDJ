# Contributing to FireTheDJ

Thanks for wanting to help. Bug reports, ideas and pull requests are all welcome.

## Reporting a bug

Open an [issue](https://github.com/suryablaze02-byte/FireTheDJ/issues/new/choose) with:

- your phone, Android version and FireTheDJ version;
- what you did, what you expected and what happened instead;
- for anything about a mix: the track names and the moment. FireTheDJ logs every analysed track
  and every planned transition under the `AutoDJ` tag: `adb logcat -s AutoDJ:I`.

## Suggesting a feature

Open an issue describing what you'd like the DJ to do and why. A new transition recipe or
visualizer is easiest to discuss with a short description of how it should sound or look.

## Pull requests

1. Fork the repository and branch off `main`.
2. Keep each pull request to one change, and match the style of the code around it: names that
   say what things are, comments that explain *why*, small focused functions.
3. Add or update unit tests for logic you change (analysis, planning, DSP and parsers are tested
   in `app/src/test`).
4. Make sure everything passes:

   ```bash
   ./gradlew testDebugUnitTest assembleDebug
   ```

5. Describe what changed and how you tested it, on a phone if it touches playback.

Never commit `local.properties`, `keystore.properties`, keystores, APKs or API keys.

## Where things live

| Area | Folder |
|---|---|
| Track analysis (tempo, key, genre, structure) | `app/src/main/java/com/autodj/analysis` |
| Set planning, transitions, effects, playback | `app/src/main/java/com/autodj/dj` |
| Streaming sources (YT Music, Audius, Jamendo, …) | `app/src/main/java/com/autodj/remote` |
| Casting (Miracast, Chromecast) | `app/src/main/java/com/autodj/cast`, `receiver/` |
| Screens and visualizers | `app/src/main/java/com/autodj/ui` |

[docs/HOW_IT_WORKS.md](docs/HOW_IT_WORKS.md) walks through the whole pipeline.

## Licence

By contributing you agree that your contributions are licensed under the project's
[GPL-3.0 licence](LICENSE).
