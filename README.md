# Tagged

Tagged is an Android app for tagging calendar days. It works like a diary with structured tags instead of free-form notes.

## Current status
This repository now includes the first implementation slice:
- Day view with selected date (default: today)
- Add tag entries with validation (`name`, `name:value`, `name:***`)
- Same-day aggregation in capsule/chip UI
- Minimal day interface (no separate raw `Entries` section)
- Rating visualization as 5-star display
- Global tag list and editing (rename, color, hidden flag, delete from global list)
- Settings action in top-right with `Show hidden tags` toggle
- Room-backed local persistence and MVVM state flow
- Unit tests for tag parsing/validation and day aggregation

Not yet implemented:
- Week/month/year screens and gesture navigation
- Backup and restore

## Architecture overview
- UI: Jetpack Compose (`MainActivity`, tabbed day/global-tag screens)
- State: `MainViewModel` with unidirectional UI state (`StateFlow`)
- Data: `RoomTaggedRepository` over Room database/DAO
- Domain: parsing/validation and aggregation in `domain/`

Main packages:
- `dev.krfu.tagged.ui`
- `dev.krfu.tagged.data`
- `dev.krfu.tagged.domain`
- `dev.krfu.tagged.model`

## Setup
1. Ensure Android Studio with Android SDK configured.
2. Open this project root.
3. Sync Gradle.

## Build and test
From repository root:
- `./gradlew assembleDebug`
- `./gradlew testDebugUnitTest`
- `./gradlew lint`

Minimum pre-PR verification:
- `./gradlew lint testDebugUnitTest`

## Product requirements
Behavior and scope are defined in `REQUIREMENTS.md`.
