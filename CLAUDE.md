# CLAUDE.md

Project conventions for **greenwall** — a small Compose-based Android tool
that takes a tattoo motif rendered on a colored "keyer" backdrop (the green /
blue / pink backgrounds typical of AI image generators) and replaces the
backdrop with either AMOLED-black (`#000000`) or fully transparent pixels.
Claude Code reads this file automatically at session start. Keep it short
and actionable.

> **Personal app.** Built for the maintainer's own device, not Play-Store
> distribution. Aggressive choices (minSdk = compileSdk = targetSdk = 36,
> dark theme only, en/de locales only, release signed with the debug keystore)
> are deliberate — do not flag them as compatibility or "best-practice" issues.

For test details, see `TESTING_CONVENTIONS.md`. greenwall is a sibling to
**coverup** (structural template) and **chiaroscuro** (pixel-kernel /
`setHasAlpha` reference); look there for patterns rather than re-inventing.

---

## Stack

- Kotlin 2.2.21, Jetpack Compose + Material 3, Compose BOM 2026.03.01
- Min/target/compile SDK 36 — **Android 16 only**, no compatibility shims
- JDK 21 at build/test time (Robolectric 4.16.1 needs it for SDK 36)
- Single Gradle module (`:app`)
- Plain `ViewModel` + `StateFlow` (no DI framework, no persistence layer yet)
- Tests: JUnit 4, MockK, Turbine, kotlinx-coroutines-test, Robolectric

## Build & test

```bash
JAVA_HOME=/snap/android-studio/current/jbr ./gradlew :app:compileDebugKotlin
JAVA_HOME=/snap/android-studio/current/jbr ./gradlew :app:testDebugUnitTest
JAVA_HOME=/snap/android-studio/current/jbr ./gradlew :app:assembleRelease
```

JDK 21 lives inside Android Studio's bundled JBR. The system default is 17
on this machine — building without `JAVA_HOME` fails (`jvmTarget = JVM_21`)
or Robolectric refuses to start (SDK 36 needs JDK 21).

---

## Architecture

```
app/src/main/java/com/github/reygnn/greenwall/
  GreenwallApplication.kt    Minimal Application — composition root if/when
                             a DataStore or DI framework is added.
  MainActivity.kt            ComponentActivity, edge-to-edge, hosts EditorScreen.
  ui/theme/Theme.kt          Material3 dark-only theme.
  ui/screens/EditorScreen.kt Placeholder for v0.1; expands as features land.
```

Planned packages, filled in by the feature branches that follow the v0.1
scaffold:

- `imaging/` — pure-Kotlin pixel kernels (`ColorMatchTransform`,
  `KeyerDetection`) over ARGB IntArrays, plus a thin Android adapter
  (`ImageProcessing`) that drives them via `Bitmap.getPixels` /
  `Bitmap.setPixels`.
- `model/` — `EditorState` (data class), `OutputMode` (AMOLED /
  TRANSPARENT), `ExportMessage` (sealed).
- `viewmodel/EditorViewModel.kt` — StateFlow holders for source bitmap,
  target color, threshold, output mode, analysis overlay; gesture combiner;
  exporter. Loader, transformer, exporter, and IO dispatcher are all
  constructor-injected so JVM tests can fake them.
- `ui/components/` — `ImageCanvas`, `EditorFab` (draggable + speed-dial),
  `CommandsPanel` (presets, threshold slider, output mode, save).

Pixel selection rule: **Chebyshev distance** in RGB. A pixel is "near the
target keyer color" iff every channel differs by at most `threshold`. This
matches chiaroscuro's per-channel pattern and is faster + simpler to reason
about in a slider than Euclidean distance.

---

## Hard rules

1. **Pixel math lives in pure-Kotlin kernels.** `ColorMatchTransform` and
   `KeyerDetection` take primitive `Int`/`IntArray` parameters, return
   plain data, have no Android or Compose imports. JVM unit tests use
   `unitTests.isReturnDefaultValues = true`; any call into
   `android.graphics.*` returns `0`/`null`, so assertions on those values
   are meaningless. Move logic out before reaching for Robolectric.

2. **Robolectric tests for `android.graphics` MUST set
   `@GraphicsMode(GraphicsMode.Mode.NATIVE)`.** Without it, Robolectric uses
   legacy shadows and `Canvas.drawColor()` etc. don't actually paint pixels
   — `Bitmap.getPixel()` returns `0x00000000` and every color assertion
   fails. Pair with `@Config(sdk = [36])`. coverup and chiaroscuro both
   document this; the working pattern lives in
   `ImageProcessingRobolectricTest`.

3. **ViewModels take `ioDispatcher` as a constructor parameter.** Default is
   `Dispatchers.IO`; tests pass `mainRule.testDispatcher` so the test body
   and `withContext(ioDispatcher)` share one scheduler and
   `advanceUntilIdle()` actually drains the work. Same idea for
   `BitmapLoader`, `BitmapTransformer`, `BitmapExporter` — all interfaces
   with an Android default impl and a fake test impl.

4. **Transparent output requires `Bitmap.setHasAlpha(true)`.** When
   `ImageProcessing.applyTransparent(...)` (or any future method that
   introduces `0x00000000` pixels) returns, the result must have its
   `hasAlpha` flag flipped on. `Bitmap.compress(PNG, ...)` reads that flag
   to decide whether to emit an alpha channel; a `false` flag silently
   produces an opaque PNG even when the pixel array is fully transparent.
   This is the same gotcha that bit chiaroscuro on day 1 — its
   `ImageProcessingRobolectricTest` pins the invariant (hasAlpha=true + PNG
   IHDR color-type byte = 6). greenwall needs an equivalent test.

5. **No DataStore/DI yet — when added, follow chiaroscuro, not Kolibri.**
   If persistence becomes necessary (last-used threshold, output mode,
   filename counter, …), introduce a `PreferencesRepository` interface, a
   `DataStorePreferencesRepository` impl, and a hand-rolled in-memory fake
   for tests — all wired through `GreenwallApplication`. greenwall's scope
   does not justify Hilt; the sibling `Kolibri_Launcher` project does it
   bigger by design, but copying that footprint here is overengineering.

6. **English in new comments and KDoc.** Existing files are already
   English; keep them that way. UI strings live in `res/values/strings.xml`
   (default English) and `res/values-de/strings.xml` (German). When you
   change UI, update both.

7. **Release builds sign with the debug keystore** (`signingConfig =
   signingConfigs.getByName("debug")` on the `release` buildType). This is
   intentional — greenwall is personal-device-only and a real release
   keystore would be infrastructure with no payoff. Not a bug, not a TODO.

---

## Test conventions (short)

The full reference lives in `TESTING_CONVENTIONS.md`. The non-negotiable
point:

```kotlin
@get:Rule val mainRule = MainDispatcherRule()

@Test fun whatever() = runTest(mainRule.testDispatcher) {
    val vm = EditorViewModel(
        transformer = FakeTransformer(),
        loader = FakeLoader(...),
        exporter = CountingExporter(),
        ioDispatcher = mainRule.testDispatcher,
    )
    // ...
}
```

Plain `runTest { }` creates its own scheduler, so `advanceUntilIdle()` will
not drive coroutines that run on `Dispatchers.Main`. Tests become flaky.

---

## Versioning

`versionName` / `versionCode` live in `app/build.gradle.kts`. Release
artifacts uploaded to GitHub use tags like `prerelease-build-N`; greenwall
is not under semver and the tag has no semantic load — it just exists
because GitHub releases require a tag.

---

## Git workflow

Larger changes — bigger bugfixes, refactorings, new features, anything that
touches multiple files or could plausibly be reverted as a unit — go on a
dedicated branch, never directly on `main`. Trivial edits (typo fix, single-
line tweak, doc nit) can stay on the current branch.

When in doubt, **stop and ask before starting**. Confirming is cheap;
realising mid-implementation that the work is on the wrong branch is not.

Branch prefixes: `feature/`, `fix/`, `refactor/`, `chore/`, `test/`.

After a fast-forward merge into `main`: switch back to `main` and ask before
deleting the merged branch (locally and on the remote). Even after a merge,
an open PR or historical reference may still hang on the branch.

Commit messages: short subject in German is fine. Trailer:
`Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>`.

---

## What this file is NOT

- Not a description of the project — the package names and filenames are
  the description.
- Not the full testing reference — see `TESTING_CONVENTIONS.md`.
- Not the place for transient refactor notes — those belong in commit
  messages on short-lived feature branches.

Update this file when an architectural rule changes or a hard-won lesson
deserves to be future-proofed. Do not bloat it with details that are
obvious from reading the code.
