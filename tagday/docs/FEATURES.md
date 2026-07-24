# TagDay — Features

## Concept

TagDay is a calendar where days are annotated with **tags** instead of free-text notes.
A day can carry any number of tags, including the same tag more than once — and even the
same tag used in different ways on different occasions. Tags themselves live in a shared
**tag repository**, identified by a stable internal id (with a unique, renamable display
name), and are reused across many days.

## Tag types

A tag's **type is not part of the tag definition** — it's a property of each individual
*instance* (each time a tag is added to a day, that specific addition has a type):

| Type | Shape | Example |
|---|---|---|
| **Simple** | name only (presence/absence) | `reading`, `field-trip`, `fast-food`, `meditation` |
| **Rated** | name + a 1–5 star rating | `work: ★★★`, `sleep: ★`, `freediving: ★★★★` |
| **Valued** | name + a free-text value | `movie: dune`, `reading: blade-runner`, `playing-game: death-stranding-2` |

Because type lives on the instance rather than the tag, the **same tag name can be used
with different types at different times** — even on the same day. For example, the tag
`movie` could have a Simple instance (just noting a movie was watched), a Rated instance
(`movie: ★★★`, rating it), and a Valued instance (`movie: dune`, naming it) all coexisting.
Each addition of a tag to a day requires picking which type applies *for that instance*.
A Rated instance's star value can be added or changed at any time — there's no requirement
to set it the moment the instance is created.

### Multiple instances & type grouping

A tag can be added to the same day more than once, in the same or different types. On
the Day view, instances of the same tag name are **grouped by type** for display, and
each group is aggregated using the same rules as before:

- **Simple group**: `name (count)` — count shown only when >1, e.g. `movie (2)`.
- **Rated group**: `name: ★★★ (count)` — average rating across the group, count shown
  only when >1, e.g. `movie: ★ (3)`.
- **Valued group**: `name: [value (count), value, ...]` — every value in the group listed,
  with a per-value count when that specific value repeats, e.g. `movie: [dune (2), terminator]`.

A day where `movie` was used all three ways would show all three groups together:
`movie (2), movie: ★ (3), movie: [dune (2), terminator]`.

## Tag repository

- Tags are identified internally by a stable **id**; the display **name** must be unique
  (case-insensitive) but can be changed after creation without breaking existing references.
- A tag definition holds: id, name, color, and creation date — **no type**. Type is chosen
  per instance when the tag is added to a day (see above), not stored on the tag itself.
- **Color**: chosen from a fixed palette, with the option to pick a custom color too;
  stored as a single 32-bit (ARGB) integer regardless of source.
- Tags are created inline where they're first used (from the day-tagging flow) or from
  the Tags view — creating a tag itself doesn't require picking a type; that choice happens
  per addition.
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
  - **Day**: full detail — tags assigned to that day, shown grouped by tag name and then
    by type (see grouping rules above); add a tag (pick existing from repository or create
    new — repeatable, even for a tag already on that day, and with a type chosen per
    addition), remove an instance, edit an instance's rating/value.
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
- **Instance type mutability**: an instance's type can be changed after the fact (e.g.
  Simple → Rated) directly, rather than requiring delete-and-recreate.
- **Managing individual instances**: tapping a type group (e.g. the Valued group
  `movie: [dune (2), terminator]`) shows an editable list of the individual instances —
  each instance can be edited or removed independently. Removing the last instance in a
  group simply removes that instance, and the group disappears from the day's display
  once empty (no separate "clear group" action needed).
