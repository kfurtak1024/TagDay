# TagDay — Features

## Concept

TagDay is a calendar where days are annotated with **tags** instead of free-text notes.
A day can carry any number of tags, including the same tag more than once. Tags themselves
live in a shared **tag repository**, identified by a stable internal id (with a unique,
renamable display name), and are reused across many days.

## Tag types

Every tag has exactly one type, fixed at creation and **immutable afterward** — to use a
tag concept differently, create a new tag with a different name rather than changing an
existing tag's type. This keeps historical data unambiguous: a Rated tag's instances are
always ratings, a Valued tag's instances are always values, with no risk of a type change
orphaning past data.

| Type | Shape | Example |
|---|---|---|
| **Simple** | name only (presence/absence) | `reading`, `field-trip`, `fast-food`, `meditation` |
| **Rated** | name + a 1–5 star rating, set per day | `work: ★★★`, `sleep: ★`, `freediving: ★★★★` |
| **Valued** | name + a free-text value, set per day | `movie: dune`, `reading: blade-runner`, `playing-game: death-stranding-2` |

A Rated tag's star value can be added or changed at any time — there's no requirement to
set it the moment an instance is created.

### Multiple instances per day

A tag can be added to the same day more than once. Each type displays this differently:

| Type | Two instances on the same day | Display |
|---|---|---|
| **Simple** | added twice | `walk (2)` — just a count |
| **Rated** | `★★★★` and `★★` | `freediving: ★★★ (2)` — average rating, with count |
| **Valued** | `dune` and `terminator` | `movie: [dune, terminator]` — all values listed, with a per-value count when a specific value repeats |

## Tag repository

- Tags are identified internally by a stable **id**; the display **name** must be unique
  (case-insensitive) but can be changed after creation without breaking existing references.
- A tag definition holds: id, name, **type**, color, and creation date. Type is fixed at
  creation and immutable — see § Tag types.
- **Color**: a single 32-bit (ARGB) integer. Tags created inline from the day-tagging
  flow get one auto-assigned from the fixed palette (no picker in that flow — see
  `UI_UX.md` § Quick-entry tag bar); manual choice from the fixed palette, plus a custom
  color option, belongs to the Tags view (M3).
- Tags are created inline where they're first used, from the day-tagging flow only
  (type either inferred from a `name:***`/`name:text` shorthand or chosen explicitly
  when ambiguous) — type is chosen once, at creation. The Tags view is management-only
  (list/filter/rename/recolor/delete existing tags); it has no "create" entry.
- **Renaming** a tag (Tags view) updates the display name only — all existing day
  associations (identified by id) are unaffected.
- **Deleting** a tag cascade-deletes all of its instances across every day, after a
  confirmation dialog warning the user how many instances will be removed.

## Main views (v1)

TagDay has two primary views/screens, reachable via bottom navigation or similar:

### 1. Calendar view

The core, default view of the app.

- **Zoom levels**: Day, Week, Month, Year — one continuous calendar, viewed at different
  granularities rather than four separate screens.
  - **Day** is the default zoom level on app launch.
- **Navigation gestures**:
  - **Swipe up/down** — change zoom level (Day ↔ Week ↔ Month ↔ Year).
  - **Swipe left/right** — move backward/forward in time, one unit of the current zoom
    level at a time (previous/next day, week, month, or year).
- **Per-zoom-level content**:
  - **Day**: full detail — tags assigned to that day, shown grouped by tag name (see
    grouping rules above); add a tag (pick existing from repository or create new —
    repeatable, even for a tag already on that day), remove an instance, edit an
    instance's rating/value.
  - **Week**: zoomed-out overview using multi-tag chips/dots per day — enough room at
    this density to show several tags per day at a glance.
  - **Month / Year**: switch to a **single-tag heatmap** — a tag picker/dropdown at the
    top of the view selects which tag is focused, and each day is shaded by that tag's
    presence/intensity for the period. Showing one tag at a time keeps the view legible
    even at Year zoom, where a full multi-tag chip display wouldn't fit.
- Tapping a day at any zoom level above Day jumps to that day at Day zoom.

### 2. Tags view

Management screen for the tag repository.

- Lists all tags currently defined.
- **Filter by name** — search/filter box to quickly find a tag in a large repository.
- **Rename** a tag (display name only; id and existing day associations are unaffected).
- **Edit color** for a tag.
- **Remove** a tag from the repository.

## Additional features (v1)

3. **Backup / restore (Google Drive)**
   - Manual export of the local database/tag data to the user's Google Drive.
   - Manual restore/import from a previously exported backup.
   - No live sync in v1 — this is a backup mechanism, not multi-device sync.

## Non-goals for v1

- Multi-device live sync / real-time cloud storage.
- Tag categories or hierarchies (e.g. grouping `reading`, `meditation` under "Wellness").
- Notifications / reminders to log a day.
- Sharing days or tags with other users.
- Widgets or wearable companion.
- Dedicated stats/insights screen (tag frequency, rating trends) — likely v1.1, once the
  two main views are solid.
- Filtering/searching *days* by tag (as opposed to filtering the *tag list* by name in the
  Tags view) — likely v1.1.

## Resolved decisions

- **Tag deletion**: cascades to all of that tag's instances across every day, gated by a
  confirmation dialog.
- **Color**: fixed palette + custom color option, stored as a 32-bit ARGB integer.
- **Rated instances**: rating can be set or changed at any time, not just at creation.
- **Month/Year zoom**: single-tag heatmap, tag selected via a picker/dropdown at the top
  of the view; Week zoom keeps the multi-tag chip/dot style.
- **Instance type mutability**: not applicable — type lives on the tag, fixed at creation
  and immutable. To use a tag concept differently, create a new tag with a different name.
- **Managing individual instances**: tapping a tag's group (e.g. the Valued group
  `movie: [dune, terminator]`) shows an editable list of the individual instances —
  each instance can be edited or removed independently. Removing the last instance in a
  group simply removes that instance, and the group disappears from the day's display
  once empty.
- **Quick-removing a whole group**: each group's capsule also has an inline "x" that
  removes *all* of that tag's instances for the day in one tap, regardless of count
  (`walk (2)` → tap → gone entirely) — a fast path for "I didn't mean to tag this today
  at all," distinct from the instance list's per-instance editing/removal above.
