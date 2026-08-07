# TagDay — Build & Release

Branching, CI, signing, and the path to a Play Store release. See ADR-013 in
`DECISIONS.md` for the reasoning behind the choices here.

## Branching strategy

Trunk-based, not GitFlow: `main` is always releasable. Short-lived feature branches,
merged via PR. No `develop`/`release/*`/`hotfix/*` branches — that model solves
parallel-release-train coordination for multiple contributors, a problem this
solo-developer project doesn't have yet. Revisit only if a second regular contributor
joins.

Releases are marked with SemVer git tags directly on `main` (`v1.0.0`, `v1.1.0`, ...) —
the tag *is* the release record, no separate release branch needed.

## Continuous integration

`.github/workflows/ci.yml` runs on every push to `main` and every PR targeting `main`:

- JDK 21 via `actions/setup-java` (matches `gradle/gradle-daemon-jvm.properties`'
  `toolchainVersion=21`).
- `gradle/actions/setup-gradle` for dependency/build caching and Gradle wrapper
  checksum validation.
- `./gradlew testDebugUnitTest assembleDebug` — the same two commands used throughout
  local development to verify changes; nothing CI-specific.
- `./gradlew lintDebug` — Android Lint, with `warningsAsErrors = true` in
  `app/build.gradle.kts`, so a new warning fails the build rather than accumulating unread.
  Three "a newer version of X exists" checks are disabled there (`OldTargetApi`,
  `NewerVersionAvailable`, `AndroidGradlePluginVersion`): they fire on their own schedule with
  no code change, so as errors they'd break a build nobody touched. The gate covers **Android
  Lint only** — Kotlin compiler warnings are a separate stream and are not gated, so a
  deprecated API still surfaces as a `w:` line rather than a failure.
- Unit test HTML reports are uploaded as a workflow artifact if the run fails, for
  quick debugging from the Actions tab without re-running locally.

**Manual follow-up, can't be done from a committed file**: once this workflow has run
at least once on GitHub, enable branch protection on `main` (repo Settings → Branches →
Add rule) requiring the `build-and-test` check to pass before merging. A workflow file
alone doesn't block anything — the branch-protection rule is what does.

Signing, release builds, and any Play Store upload are **deliberately not** part of CI
yet — see § Open notes.

## Signing & versioning (current state)

- No release `signingConfig` exists yet — `assembleRelease` currently produces an
  **unsigned** APK. This is the first gap to close before shipping (see § Release
  process below).
- `versionCode`/`versionName` are hardcoded in `app/build.gradle.kts`, bumped manually
  per release. Deliberately not automated (e.g. deriving `versionCode` from commit
  count) — one more moving part than a solo-dev release cadence needs.
- Release `optimization { enable = false }` (R8/shrinking off) was a deliberate
  early-development choice (see ADR-010/ADR-011's reasoning about avoiding
  minification-related surprises while iterating fast). Revisit before the first real
  release — see § Open notes.

## Release process (once ready to ship)

1. Generate a release keystore (`keytool -genkeypair ...`) if one doesn't exist yet.
   **Back it up somewhere durable outside git** (password manager, encrypted cloud
   storage) — losing it means the app can never be updated under that Play Store
   listing again, with no recovery path. Never commit it (already covered by
   `.gitignore`'s `*.jks`/`*.keystore` rules).
2. Add a `signingConfig` to `app/build.gradle.kts`'s `release` build type, reading
   keystore path/passwords from a local, gitignored `keystore.properties` — never
   hardcoded in the build file.
3. Bump `versionCode`/`versionName`, commit, tag (`git tag v1.0.0`).
4. `./gradlew bundleRelease` locally to produce the signed `.aab` (Play Store requires
   an Android App Bundle, not a bare APK, for new apps).
5. Play Console: create the app listing. The privacy policy is already hosted at
   `https://tagday.krfu.dev/privacy` (`docs/CNAME` + `docs/privacy.md` at the repo
   root, served via GitHub Pages) — reuse that URL. Fill out the Data Safety form
   accurately: the app is fully offline and collects nothing, matching the existing
   privacy policy.
6. Upload the `.aab` to the **Internal testing** track first (fast, no review);
   install and smoke-test on a real device end-to-end.
7. Promote to Production once verified. Note: new personal Play Console accounts may
   be required to run a closed test with a minimum tester count for some period before
   Production unlocks — check the Console for the current policy, since it affects
   timeline and isn't something CI or this repo can control.

## Open notes for later

- **Signing should become part of the automated build process** — once the manual
  release process above has been done by hand at least once and is well understood,
  wire the release keystore into CI as a GitHub Actions secret so a tagged push
  produces a signed release `.aab` automatically as a workflow artifact (the deferred
  middle option from ADR-013's alternatives — still short of automating the Play
  Console upload itself).
- Whether to also automate the Play Console upload (via the Play Developer API /
  Gradle Play Publisher or fastlane) is a separate, later decision — not committed to.
- R8/shrinking re-enablement for release builds needs an explicit decision plus a full
  regression pass (Room/Hilt/Compose all ship consumer ProGuard rules, so risk should
  be low, but it needs verifying, not assuming) before the first real Play Store
  release.
