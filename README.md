# Calculator

A fast, fully offline calculator for Android, built with Kotlin and Jetpack Compose. Design inspired by the Apple, Google and Samsung calculators.

## Features
- 100% offline: the app requests **no permissions**, not even internet
- Haptic feedback on every key, with distinct feedback for equals, errors and long-press
- Exact decimal math (`0.1 + 0.2 = 0.3`)
- Live result preview while typing
- Parentheses, percent (`50 + 10% = 55`), implicit multiplication (`2(3+4)`)
- Calculation history saved on the device
- Swipe the display to delete, long-press the result to copy
- Material You dynamic colors, dark/light theme, TalkBack support, landscape layout

## Install
Download the latest APK from the [Releases](../../releases) page and open it on your phone (Android 8.0+). You may need to allow installs from your browser or file manager.

## Build from source
```
gradle assembleRelease
```
Or push to GitHub: the workflow in `.github/workflows/build.yml` builds the APK automatically.

## Releasing a new version
Create and push a tag such as `v1.0.0`. The workflow builds the APK and attaches it to a GitHub Release.

## Tech
Kotlin · Jetpack Compose · Material 3 · BigDecimal expression parser · min SDK 26

## License
MIT. See [LICENSE](LICENSE).
