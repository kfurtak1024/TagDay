# Repository Guidelines

## Requirements Source
Product behavior and scope are defined in `REQUIREMENTS.md`. Treat that file as the source of truth for feature decisions.

## Project Structure & Module Organization
This repository is in bootstrap state. Use standard Android layout:
- `app/src/main/` production code (`kotlin/`), resources (`res/`), `AndroidManifest.xml`
- `app/src/test/` JVM unit tests
- `app/src/androidTest/` instrumentation/UI tests
- `gradle/`, `build.gradle.kts`, `settings.gradle.kts` build configuration

When adding modules, use feature-first names (for example `feature-tagging`, `feature-calendar`) and shared modules as `core-*` (for example `core-ui`, `core-data`).

## Modern Android Engineering Standards
- Use Kotlin as the default language.
- Prefer Jetpack Compose for new UI unless interoperability requires Views.
- Use MVVM + repository pattern with unidirectional state flow.
- Use coroutines + `Flow` for async/state streams; avoid blocking calls on main thread.
- Use dependency injection (Hilt recommended) and constructor injection by default.
- Keep classes focused and small; prefer immutable models and explicit state types.

## Build, Test, and Quality Commands
Run from repository root:
- `./gradlew assembleDebug` build debug APK
- `./gradlew testDebugUnitTest` run unit tests
- `./gradlew connectedDebugAndroidTest` run instrumentation tests
- `./gradlew lint` run Android lint
- `./gradlew clean` clean build outputs

Before PR: run `./gradlew lint testDebugUnitTest` at minimum.

## Coding Style & Naming
- 4-space indentation, no wildcard imports.
- `PascalCase` for types, `camelCase` for functions/variables, `UPPER_SNAKE_CASE` for constants.
- Package names are lowercase and domain-based (for example `com.example.tagged.feature.day`).
- Resource names use `snake_case` (for example `ic_tag_add`, `screen_day.xml`).
- Document non-obvious decisions in short KDoc/comments.

## Testing Guidelines
- Co-locate tests with the feature they verify.
- Test class naming: `SubjectTest` and `SubjectInstrumentedTest`.
- Test names should describe behavior (for example `addTag_whenInputValid_persistsEntry`).
- Add regression tests for every bug fix.

## Security & Privacy Requirements
- Never commit secrets, signing keys, tokens, or PII test dumps.
- Keep machine-local configuration in `local.properties`.
- Validate and sanitize all user input before persistence.
- Do not log sensitive data; use redaction where logging is required.
- Prefer encrypted storage for sensitive local data and HTTPS-only network traffic.

## Commit & Pull Request Guidelines
- Commit format: `type(scope): summary` (for example `feat(day): add tag input validation`).
- Keep commits atomic, reviewable, and buildable.
- PRs must include: purpose, key changes, test evidence, and screenshots for UI updates.
