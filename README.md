# OldButGOLD

A clean Android/Kotlin project for the MU Immortal yellow-boss automation prototype.

## Current implementation

- Separate app identity; no FarmBotInGen source project copied wholesale.
- Gold-themed configuration screen.
- ATK and DEF thresholds saved locally.
- Buff enable/disable setting.
- Touch-point selection for opening chat, closing chat, and the buff destination.
- Draggable floating bubble that opens an in-game control panel.
- Panel can be closed independently; the floating bubble can also be hidden and restored from the app settings screen.
- Accessibility-service screenshot capture and ML Kit text recognition diagnostics.
- Buff monitor triggers navigation only when both ATK and DEF are below their configured minimums.
- Buff navigation taps the configured chat-open point, buff coordinate, then chat-close point.
- While waiting at the buff point, OCR checks both values and returns to monitoring only when both meet their minimums.

## Not yet implemented / not validated in game

- Yellow skull detection and repeated confirmation of the pale-gray death state.
- Finding the nearest living skull within a configurable pixel radius.
- Random-button scanning loop.
- Player-marker/target overlap detection before closing the map.
- Starting auto-attack after reaching a boss and continuing until the boss dies.
- End-to-end validation on the user's device and actual game screen.

The floating panel and buff/OCR pieces are being compiled and should be tested on-device. A successful APK build does not prove the OCR labels or game-screen interactions work correctly.

## Build

GitHub Actions builds a debug APK and uploads it as the OldButGOLD-debug-apk artifact.
