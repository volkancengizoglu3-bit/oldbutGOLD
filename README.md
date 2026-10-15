# OldButGOLD

A clean Android project for a configurable MU Immortal yellow-boss automation prototype.

## Requirements captured

- Independently created project; no inherited FarmBotInGen implementation.
- Manually configured minimum ATK and DEF values, with OCR intended to read current values.
- Buff route triggers only when **both** current ATK and DEF are below their configured minimums.
- Buff can be enabled or disabled.
- Buff navigation uses the configured chat coordinate.
- Use the minimap Random button to relocate and search for living yellow skull markers within a configurable pixel radius.
- Prefer the nearest living boss.
- Navigate by tapping the minimap and detect when the player marker overlaps the target before closing the map and enabling auto-attack.
- Keep fighting until the minimap skull changes from yellow to pale gray; confirm the gray state repeatedly before moving to the next boss.
- If no eligible boss is found, tap Random and scan again.

## Current status

This first commit is only a clean Android project foundation and settings-screen scaffold. It does **not** implement the game automation yet. The OCR, minimap marker recognition, player/target overlap detection, buff navigation, and automation state machine must be implemented and tested before the APK can be considered functional.

## Build

The GitHub Actions workflow builds a debug APK and uploads it as an artifact.
