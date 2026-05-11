# greenwall

A small Android tool for stripping the colored backdrop from AI-generated
tattoo motifs. AI image generators (Gemini, Imagen, …) typically render
motifs on a flat green / blue / pink "keyer" backdrop. greenwall detects
that backdrop, replaces it with either AMOLED-black (`#000000`) or full
transparency, and cleans the residual color spill at the antialiased
motif edges.

> **Personal app.** Built for the maintainer's own device. Aggressive
> choices (Android 16 only, dark theme only, debug-signed release
> builds) are deliberate. Not on the Play Store and not planned to be.

---

## What it does

Given a tattoo-motif PNG on a single-color backdrop, four steps:

1. **Auto-detect the keyer color** by taking the per-channel median RGB
   of an 8-pixel border ring around the image. The median is robust
   against the slight per-pixel variation AI generators introduce —
   Gemini's "pure green", for example, sits around `(R<32, G≈200,
   B<32)`, not at `(0, 255, 0)`.

2. **Identify keyer pixels** by Chebyshev distance in RGB space: a
   pixel matches when every R, G, B channel differs from the target by
   at most the chosen *threshold*. Threshold default is 24, slider goes
   0..255.

3. **Replace matching pixels** with either:
   - **AMOLED-black** (`#000000`) — the AMOLED display turns those
     pixels off, saving battery on dark wallpapers.
   - **Transparent** (`alpha = 0`) — produces a cutout that layers
     cleanly elsewhere.

4. **Despill the non-matched pixels** — for every pixel that wasn't
   replaced, the dominant channels of the target color are capped at
   the maximum of the pixel's other channels. This kills the residual
   green/blue/pink tint at motif edges without changing which pixels
   are kept. A typical edge pixel `(10, 214, 10)` against a green
   target becomes `(10, 10, 10)` — dark grey, indistinguishable from
   AMOLED-black, clean against transparency.

The result is saved as PNG into `Pictures/greenwall/`.

---

## How to use

The 🛠 FAB lives in the bottom-right corner. Drag it anywhere; the
speed-dial follows.

1. **🛠 → ☰** opens the commands panel. Tap **📂 Pick image** to load a
   PNG/JPG. Auto-detection runs immediately and stores the detected
   keyer color in the swatch.

2. **(Optional) Tune the threshold** with the slider. The colored
   swatch next to it shows the current target. Wider threshold catches
   more keyer-near pixels — useful if the backdrop varies — but risks
   eating into dark motif regions.

3. **(Optional) Override the auto-detected color.** If the motif
   touched the image edge and dragged the median off, tap **💧 Tap on
   image** in the panel. The panel closes and the next canvas tap sets
   the target color to that pixel; tap outside the image to cancel.
   Alternatively, **🛠 → 🎯** re-runs auto-detection.

4. **(Optional) Check what's selected.** **🛠 → 👁** toggles the
   analysis overlay — matching pixels are recolored to the
   complementary color of the target (magenta against green, yellow
   against blue, dark cyan against pink). Honest match preview — no
   despill, no edge cleanup, just the raw mask.

5. **Switch between AMOLED and Transparent** output via the segmented
   control in the commands panel.

6. **🛠 → 🖼 Preview** renders the final result (AMOLED or transparent
   applied, with despill) full-canvas — exactly what the saved PNG
   will look like.

7. **Pan and zoom** on the canvas: 1-finger drag pans, 2-finger pinch
   zooms (1× to 20×). Works in all view modes — useful for inspecting
   pixel-level edges in preview mode. Loading a new image resets
   pan/zoom.

8. **🛠 → ☰ → 💾 Save PNG.** The result lands in `Pictures/greenwall/`
   under `greenwall_yyyymmdd_hhmmss.png`.

---

## Requirements

- **Android 16** (API 36) or newer.
- **JDK 21** to build — greenwall pins `jvmTarget = JVM_21` because
  Robolectric 4.16.1 needs JDK 21 to run tests against SDK 36. The
  bundled JBR that ships with Android Studio is JDK 21.

---

## Building

```bash
git clone git@github.com:reygnn/greenwall.git
cd greenwall
JAVA_HOME=/snap/android-studio/current/jbr ./gradlew :app:testDebugUnitTest
JAVA_HOME=/snap/android-studio/current/jbr ./gradlew :app:assembleRelease
```

On a system whose default JDK is 17 or older, `JAVA_HOME` must point at
a JDK 21 install — either Android Studio's bundled JBR (the snap path
above on Ubuntu) or a system-installed `openjdk-21-jdk`. Without it the
Kotlin compiler refuses (`Unknown JVM target version: 21`) or
Robolectric won't start.

Prebuilt installable APKs are published as GitHub
[Releases](https://github.com/reygnn/greenwall/releases) under
`prerelease-build-N` tags. They are signed with the debug keystore.

---

## Tests

90 unit tests across seven files:

- `ColorMatchTransformTest` (23) — Chebyshev-distance matching,
  AMOLED / transparent replacement, despill math, idempotence,
  alpha handling.
- `KeyerDetectionTest` (8) — median-border detection, robustness
  against motif intrusion + simulated AI drift, fallback for
  degenerate inputs.
- `ColorsTest` (5) — complementary-RGB math (used for the analysis
  overlay color).
- `ImageGeometryTest` (14) — fit-center math, pan/zoom-aware
  canvas-to-bitmap pixel mapping.
- `EditorViewModelTest` (35) — state transitions, viewMode toggles,
  preview/analysis cache invalidation, picker flow, auto-detect,
  save success/error paths.
- `ImageProcessingRobolectricTest` (2) — `Bitmap.setHasAlpha` +
  PNG IHDR invariant for transparent output, under Robolectric
  NATIVE graphics.
- `DespillRobolectricTest` (3) — real-image verification against a
  representative AI-generated source (`femdroid.png` in the test
  resources): detect identifies green; after apply, `G ≤ max(R, B)`
  holds on every pixel.

Run them all:

```bash
JAVA_HOME=/snap/android-studio/current/jbr ./gradlew :app:testDebugUnitTest
```

Detailed conventions live in
[`TESTING_CONVENTIONS.md`](TESTING_CONVENTIONS.md).

---

## Tech stack

- Kotlin 2.2.21, Jetpack Compose, Material 3 (dark theme only).
- Single Gradle module — no DI framework, no persistence layer yet.
- Plain `ViewModel` + `StateFlow`. `IO`-dispatcher, `BitmapLoader`,
  `BitmapTransformer`, and `BitmapExporter` are constructor-injected
  so the whole VM is testable on the JVM without touching the
  framework.
- Pixel kernels (`ColorMatchTransform`, `KeyerDetection`,
  `ImageGeometry`, `complementaryRgb`) are pure Kotlin over packed
  ARGB `IntArray`s — no `android.graphics` imports, JVM-testable
  without Robolectric.
- Tests: JUnit 4, MockK, Turbine, kotlinx-coroutines-test,
  Robolectric (NATIVE graphics mode for pixel assertions).

Architecture sketch and the seven hard rules are in
[`CLAUDE.md`](CLAUDE.md).

---

## Privacy

No network, no analytics, no permissions beyond what the Android Photo
Picker grants for the image you load. The saved PNG is written to
`Pictures/greenwall/` via `MediaStore`; nothing leaves the device.

---

## Languages

English and Deutsch. The app follows your system locale.

---

## Project documents

- [`CLAUDE.md`](CLAUDE.md) — architectural rules and project
  conventions (read first if you touch code).
- [`TESTING_CONVENTIONS.md`](TESTING_CONVENTIONS.md) — how tests are
  written in this project, including the Robolectric NATIVE graphics
  gotcha and the `setHasAlpha` + PNG-IHDR invariant.
- [`TODO.md`](TODO.md) — open work items and history.

---

## License

Personal project. No public license granted; if you want to fork or
reuse the code, open an issue and ask.
