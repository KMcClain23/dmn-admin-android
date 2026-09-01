# Release notes

Newest first. One section per bundle that was actually built. The version line
records what was read out of the merged manifest and out of the AAB itself, not
what the build was expected to produce.

---

## 0.3.0 — versionCode 54

Built 2026-08-31. Read back from
`app/build/intermediates/merged_manifest/release/.../AndroidManifest.xml` and,
independently, from `base/manifest/AndroidManifest.xml` inside the AAB: both say
`versionCode 54`, `versionName 0.3.0`.

Not yet uploaded. `version.properties` still records 49 as the published floor,
and stays that way until an upload actually succeeds.

### What Marizete gets

- **An Editing tab.** Books assigned to her, opened to a pane that holds the
  editing progress and the pickups for that book — the only place either is
  written, on any screen.
- **Pickups on Today**, so the agenda is the whole job rather than the
  recording half of it.
- **Settings**, gated by capability rather than by role name, so an editor sees
  the settings an editor may change and nothing else.
- **The in-app update prompt**, deferred from 0.2.0.

### Interim: how a typed chapter count is stored

`chapters_edited` had two writers with different meanings. The phone writes a
COUNT (`set_editing_progress`); the website records a SET, one row per chapter
in `chapter_progress`, and a trigger derives the count from it. The phone's
write used to overwrite that derived number, and the next web toggle recomputed
it from the rows and silently threw the phone's number away.

The function's signature has not changed — versionCode 49 and 54 both call it
with the same three arguments and keep working. What changed is behaviour:

- On a book with no chapter rows, the typed number is stored as before. The
  count IS the record there.
- On a book the website tracks per chapter, a typed number is CONVERTED into
  the rows it implies, so a later web toggle builds on it instead of erasing it.
- When converting would destroy information — chapters marked done out of
  order, where no single number can express the set — the write is REFUSED,
  naming the chapter that does not fit and pointing at the website.

**This is interim, not the design.** Converting a count into a set is a guess
that happens to be safe when the set is contiguous; it is not a second way of
saying the same thing. The intended end state is one representation — the set —
with the phone editing chapters directly, the way the website's grid does. Until
then the refusal is doing the work a proper model would do by construction, and
the fact that a valid state can be unwritable from the phone is a symptom of the
split, not a feature.

### Also

- A refusal now appears in the editing section, next to the field that was
  refused, instead of under the pickups list further down the screen.
- Failure messages are reduced to the sentence a person should read.
  supabase-kt puts the whole request diagnostic in `Throwable.message` — the
  SQLSTATE, the URL, the method, and the request headers, which include an
  `Authorization: Bearer ey…` prefix. Every screen that surfaced a raw message
  was putting part of a credential on screen. See `domain/ErrorMessage.kt`.

### Known, and deliberately not fixed here

- Settings reads "Not set in site_settings" where it should read the value.
- The book pane's editing section shows "Not started" regardless of progress:
  `card_detail_for_editor` does not return `chapters_edited`.
