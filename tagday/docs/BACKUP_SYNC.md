# TagDay — Backup / Sync (Google Drive)

Scope: M5b (`MILESTONES.md`). WhatsApp-inspired backup — periodic snapshot with a manual
override, not multi-device live sync, which stays an explicit non-goal (`FEATURES.md`
§ Non-goals). See ADR-015 in `DECISIONS.md` for why this shape over the alternatives.

> **Sequenced behind M5a, and conditional on it** (ADR-032). The JSON document described
> under § Backup format is defined and shipped first as *local* export/import, with no
> account or network; this doc covers the transport that later carries the same document.
> Everything below still stands as the intended shape — but two corrections apply from
> ADR-032: an automatic backup must never overwrite an existing remote backup when local data
> is empty, and the automatic path should be a WorkManager one-shot rather than a bare
> launch-time coroutine (which also answers the retry/backoff open question at the bottom).

## Mechanism

- **Drive REST API**, `drive.appdata` scope — an app-managed backup file living in the
  user's hidden Drive app-data folder, not a file they browse or see in "My Drive".
- **Auth**: Sign in with Google via Credential Manager (Google Identity Services),
  requesting `drive.appdata` alongside basic profile scope. Sign-in only happens when
  the user first engages with backup (opens the backup entry point, or responds to the
  fresh-install restore prompt) — not forced at first app launch.

## Backup format

- A single JSON document, versioned (`schemaVersion` field for forward compatibility),
  containing the full `Tag` and `TagInstance` tables as defined in `DATA_MODEL.md`.
- **One rolling backup per account** — each successful backup overwrites the previous
  one (standard Drive `appDataFolder` pattern: query by filename first, `files.update`
  if found, `files.create` if not). No retained history of past backups; this is a
  safety net, not a version-history feature.

## Trigger

- **Manual** — a "Back up now" action (exact entry point TBD at implementation time;
  likely near Tags view or a small settings-style surface, per `UI_UX.md`'s open note
  on where Drive backup UI might live).
- **Automatic** — checked on app foreground/launch: if the last successful backup is
  more than 24h old and the user has previously signed in, kick off a backup in the
  background, non-blocking. No user-configurable frequency in v1 (unlike WhatsApp's
  daily/weekly/monthly picker) — fixed 24h staleness threshold, revisit only if
  actually requested.

## Restore

- On fresh install, if local `Tag`/`TagInstance` data is empty and a Drive backup
  exists for the signed-in Google account, prompt "Restore from backup?" — no file
  picker, no manual file selection.
- If local data is **not** empty (e.g. the user already added a few tags before
  signing in), the auto-prompt is suppressed — restoring over existing data is only
  ever a deliberate action reachable from the manual entry point, never something
  offered automatically once there's something to lose.
- Restore fully **replaces** local `Tag`/`TagInstance` data with the backup's contents
  — no merge, matching M5's original "identical dataset" done-criteria.

## Explicitly out of scope for v1

- Media/attachments — not applicable; tags carry no media in this app's data model.
- Backup encryption passphrase (unlike WhatsApp's end-to-end-encrypted backups) — tag
  data is low-sensitivity personal habit tracking, not message content; Drive's own
  transport/at-rest security is sufficient.
- User-configurable backup frequency.
- Retained backup history / multiple versions — single rolling backup only.
- Multi-device live/real-time sync — see `FEATURES.md` § Non-goals; this remains a
  one-directional backup mechanism, not a sync engine.

## Open questions (TBD at implementation time)

- Exact UI entry point and any backup-status indicator (last backup time, in-progress
  state, failure surfacing).
- Whether sign-in is offered proactively on first launch or only lazily when backup is
  first engaged with.
- Retry/backoff behavior for a failed automatic backup (e.g. offline, quota, auth
  expired) — likely just "try again next time staleness triggers it," but not decided.
