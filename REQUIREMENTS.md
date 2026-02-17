## Scope
TagDay is an Android application for tagging calendar days, like a diary but with tags instead of notes.

## UX principles
- Interface should be minimalistic and simple.
- Prefer concise, focused screens over dense detail views.
- Day view must not include a separate `Entries` section; tags are presented through aggregated capsules/chips only.

Primary scope for this iteration:
- Add, view, and edit tags for a selected day (default: today)
- Browse data in day/week/month/year views
- Manage global tags
- Configure app preferences

Planned scope (next iteration):
- Local-first backup and restore with Google Drive integration

## Domain model
### Tag format
A tag entry consists of:
- `name`
- `color`
- `hidden` flag
- optional `:` + `value` or `rating`

Examples:
- Basic tag: `dinner-with-family`
- Tag with value: `watching-movie:tron`
- Tag with rating: `workout:***`

### Naming and validation rules
Tag name rules:
- Must start with a letter
- May contain words separated by `-`
- Valid: `dinner-with-family`, `fast-food`, `vacation`
- Invalid: `-a-tag`, `a-tag-`, `a--tag`

Value rules:
- Similar to tag-name shape but may contain digits
- May start with a digit
- Valid: `terminator-2`, `1-2-3`, `die-666`

Rating rules:
- One to five stars only: `*`, `**`, `***`, `****`, `*****`

### Same-day aggregation and display
When multiple entries of the same tag name exist on one day:
- Basic tag: show count, for example `dinner-with-family (3)`
- Tag with value: show merged values, for example `watching-movie (tron, dune)`
- Tag with rating: show average rating and entry count, for example `workout:** (3)` where `(3)` is the number of entries used for averaging

### Tag visual presentation
- Tags should be displayed as capsule/chip UI elements (similar to GitLab labels), not plain text lines.
- Capsule background/border should reflect the tag color, while maintaining readable text contrast.
- Basic tag capsule text example: `dinner-with-family (3)`.
- Value tag capsule text example: `watching-movie (tron, dune)`.
- Rating tags should render stars visually as a fixed 5-star control:
  - filled rating stars in yellow
  - remaining stars as blank/outline gray
  - example: `***` is shown as 3 yellow stars + 2 gray stars
- If rating is aggregated to a noninteger value, display rounded value in stars and keep entry count visible (for example `★★★☆☆ (4)`).

### Tag color rules
- Each global tag has an associated display color used across day/week/month/year views.
- On tag creation, assign a default color automatically from a curated palette (avoid low-contrast or hard-to-read colors).
- User can change a tag color later from tag management UI.
- Color changes must update all existing occurrences of that tag in historical views.
- If no explicit user-selected color exists, use the auto-assigned default color.

### Tag visibility rules
- Each global tag can be marked as hidden.
- Hidden tags are excluded from day/week/month/year views by default.
- User can explicitly enable a `Show hidden tags` option to include hidden tags in screens.
- Hidden state is a global tag property and applies to all historical and future occurrences.

## Functional requirements
### Day tagging
- User can add one or more tags to a specific day
- User can view and edit tags of the currently selected day through chip-based summaries and global tag management controls
- User can remove a tag from the selected day directly from its chip action, with confirmation before deletion
- Initial selection is today

### Global tag management
Global tags are the canonical list of known tags.
- User can list globally used tags
- Renaming a global tag updates all existing occurrences in historical day data
- Deleting a global tag removes it from the global list only; existing day entries remain unchanged
- User can update color of a global tag
- User can mark/unmark a global tag as hidden

### Calendar views
- Day view: full tag details for one day in a minimal chip-based presentation (no separate raw-entry list/section)
- Week view: table layout (Monday at top, Sunday at bottom) with minimized tags as needed
- Month view: summary only (for example, one or two selected tags plus count of additional tags)
- Year view: summary only (same approach as month view)

### Settings
- Settings screen allows modifying preferences
- Settings entry point is a gear icon in top-right corner, always visible
- Settings include a `Show hidden tags` toggle (default: off)

### Project documentation
- Maintain a root `README.md` as the primary onboarding and development starting point
- `README.md` should describe project purpose, architecture overview, setup steps, build/test commands, and current feature status
- Update `README.md` in the same change set whenever behavior, setup, or developer workflow changes

### Backup & restore (planned)
Backup follows a local-first model similar to common messaging-app backups:
- Local database is always the source of truth; app must work fully offline
- Backup is optional and asynchronous; it must never block normal app usage

Backup modes:
- Manual backup from Settings (on-demand)
- Periodic backup (daily or weekly), configurable by user
- Periodic backup should run only under allowed device constraints (for example network availability)

Backup content:
- Include tags, day-tag mappings, global tags, and relevant app preferences
- Exclude transient cache, logs, and temporary device-specific data

Google Drive behavior:
- Store backup in app-specific Google Drive location
- Keep latest backup and optionally a small version history
- Show last successful backup timestamp and latest error status

Restore behavior:
- Restore is explicit user action from Settings
- User chooses one mode: `Replace local data` or `Merge with local data`
- Show backup metadata before restore (timestamp, record counts, app data version)

Merge/conflict resolution:
- Use deterministic conflict rule (record with newest `updatedAt` wins)
- Restore/merge must be idempotent (re-running does not duplicate records)

Security and integrity:
- Encrypt backup payload before upload
- Do not include auth tokens, secrets, or sensitive runtime credentials
- Validate checksum and data version before applying restore

## Navigation requirements
- Day view:
  - Swipe left: one day forward
  - Swipe right: one day back
  - Swipe up: jump to today
  - Swipe down: Week view
- Week view:
  - Tap day: Day view
  - Swipe left: one week back
  - Swipe right: one week forward
  - Swipe up: Day view
  - Swipe down: Month view
- Month view:
  - Swipe left: one month back
  - Swipe right: one month forward
  - Swipe up: Week view
  - Swipe down: Year view
- Year view:
  - Swipe left: one year back
  - Swipe right: one year forward
  - Swipe up: Month view
