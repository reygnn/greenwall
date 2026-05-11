# TESTING_CONVENTIONS.md

Testing conventions for **greenwall**. Read this before writing a test.

> **Scope note.** As of the v0.1 scaffold, greenwall has zero test files —
> only `testing/MainDispatcherRule.kt`. The conventions below match what
> coverup and chiaroscuro have validated in production. Expect three test
> classes once the feature branches land:
> `ColorMatchTransformTest` + `KeyerDetectionTest` (pure JVM),
> `EditorViewModelTest` (JVM, MockK + Turbine),
> `ImageProcessingRobolectricTest` (Robolectric NATIVE, pins hasAlpha + PNG).

---

## 1. Dispatcher rule — non-negotiable

Every coroutine-touching test uses **one** `TestDispatcher`, installed via
`MainDispatcherRule` as `@get:Rule`, and passed to both `runTest` and the
ViewModel's `ioDispatcher`:

```kotlin
@get:Rule val mainRule = MainDispatcherRule()

@Test fun example() = runTest(mainRule.testDispatcher) {
    val vm = EditorViewModel(
        transformer = FakeTransformer(),
        loader = FakeLoader(...),
        exporter = CountingExporter(),
        ioDispatcher = mainRule.testDispatcher,
    )
    // ...
    advanceUntilIdle()
}
```

**Why it matters:** `MainDispatcherRule` installs the test dispatcher as
`Dispatchers.Main`. If the VM's `withContext(Dispatchers.IO)` runs on the
real IO pool, the test body and the production code are on different
schedulers; `advanceUntilIdle()` only drains one of them. Symptoms are
silent and nondeterministic. Inject the same dispatcher everywhere.

**Anti-patterns** (will break tests silently):
- `val testDispatcher = StandardTestDispatcher()` as a second dispatcher
- `Dispatchers.setMain(...)` manually in `@Before` (conflicts with the rule)
- `testScope.runTest { }` instead of `runTest(rule.testDispatcher) { }`

---

## 2. ViewModel construction inside `runTest`, not as a field

Do NOT promote ViewModels (or their collaborators) to test-class fields.
JUnit's `@Rule` setup runs **after** field initializers, so a field-built VM
captures the real `Dispatchers.Main` in its `viewModelScope`. Every
`launch` / `stateIn` / etc. then dispatches into a scheduler the test
cannot drain — setters appear to do nothing, `advanceUntilIdle()` has no
effect, assertions see the initial state.

Always construct VMs and their fakes inside the `runTest` block:

```kotlin
@Test fun example() = runTest(mainRule.testDispatcher) {
    val loader = FakeLoader(returns = mockk<Bitmap>(relaxed = true))
    val vm = EditorViewModel(loader = loader, ioDispatcher = mainRule.testDispatcher)
    // ...
}
```

A helper factory (`newViewModel(...)`) is fine, as long as it's called
from inside `runTest`.

---

## 3. Robolectric — targeted exception, not the default

JVM unit tests are the default. They run with
`unitTests.isReturnDefaultValues = true`, so `android.graphics.Bitmap`,
`Canvas`, `Color`, etc. return defaults (`0` / `null` / `false`). Any
assertion that depends on those values is meaningless.

Pure-Kotlin kernels (`ColorMatchTransform`, `KeyerDetection`) cover the
pixel-math logic on the JVM without any Android imports. **Always extract
there first.**

Robolectric is added only when:
1. Pure-Kotlin extraction won't work (the test genuinely needs the framework
   to draw, decode, or compress), **and**
2. The risk of silent regression justifies the cost (Robolectric cold-start
   adds several seconds per test class).

The expected case for greenwall: `ImageProcessingRobolectricTest` pins the
`Bitmap.hasAlpha` + PNG-IHDR invariant for `applyTransparent` — a direct
port of chiaroscuro's test (see CLAUDE.md hard rule #4).

### NATIVE graphics mode is mandatory for pixel assertions

```kotlin
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageProcessingRobolectricTest { … }
```

Without `@GraphicsMode(NATIVE)`, Robolectric uses legacy shadows where
`Canvas.drawColor()` **does not actually paint pixels** — `Bitmap.getPixel()`
returns `0x00000000` for everything. coverup hit this on its first
Robolectric run: five tests failed identically with `expected #FF0000FF
but was #00000000`. NATIVE mode uses real native graphics libraries and
renders bitmaps as a device would.

### SDK pin matches the app

Robolectric 4.16.1 supports SDK 36; the project's `minSdk = compileSdk =
targetSdk = 36`, so the test SDK matches. JDK 21 is the test-time runtime
(see `CLAUDE.md` for the build command).

---

## 4. Faking Bitmaps in JVM tests

`EditorViewModel` will hold `MutableStateFlow<Bitmap?>` for the source and
the analysis overlay. On the JVM, `Bitmap.createBitmap(...)` returns
`null`. Solution: inject a `BitmapLoader` fake whose `load()` returns a
`mockk<Bitmap>(relaxed = true)`.

The VM stores the mock; you assert state transitions, not bitmap contents.
Pixel correctness goes in a Robolectric test (per §3) or in a pure-Kotlin
test against the kernel.

```kotlin
val sourceBmp = mockk<Bitmap>(relaxed = true)
val loader = FakeLoader(returns = sourceBmp)
val vm = EditorViewModel(loader = loader, ioDispatcher = mainRule.testDispatcher)

vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
advanceUntilIdle()

assertTrue(vm.state.value.sourceLoaded)
assertEquals(sourceBmp, vm.sourceBitmap.value)
```

---

## 5. Flow assertions: Turbine

Use Turbine for `StateFlow` / `Flow` assertions; it makes ordering explicit:

```kotlin
vm.state.test {
    assertEquals(0xFF00FF00.toInt(), awaitItem().targetColor)
    vm.setTargetColor(0xFF0000FF.toInt())
    assertEquals(0xFF0000FF.toInt(), awaitItem().targetColor)
    cancelAndIgnoreRemainingEvents()
}
```

Avoid collecting into a local mutable list unless Turbine genuinely doesn't
fit. Manual collection is easy to write wrong.

---

## 6. MockK conventions

### Variable naming — no `mock` prefix

The type and the `mockk(...)` call already say it's a mock. Name after what
it represents:

```kotlin
// ✅
private val sourceBmp: Bitmap = mockk(relaxed = true)

// ❌
private val mockSourceBmp: Bitmap = mockk(relaxed = true)
```

### Default to `relaxed = true`

`relaxed = true` makes unstubbed calls return defaults; it matches the
ergonomics we want for collaborators we only care about a few methods on.
Don't use `relaxUnitFun = true` — it's a footgun for suspend functions
returning `Unit`, which **look** stubbed but throw `MockKException` at
runtime.

### Suspend vs. non-suspend

- Suspend functions → `coEvery { ... }` / `coVerify { ... }`
- Non-suspend functions (including properties of `Flow` type) →
  `every { ... }` / `verify { ... }`

Getting this wrong produces confusing runtime errors, not compile errors.

---

## 7. Assertions: JUnit, not kotlin.test

Use `org.junit.Assert.*`. The project doesn't depend on `kotlin-test`.

```kotlin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
```

---

## 8. Virtual time: pin temporal properties

If/when production code uses `delay(...)`, `withTimeout(...)`,
`SharingStarted.WhileSubscribed(timeout)`, or other temporal primitives,
at least one test should assert on virtual time — not just the final
emission.

Two idioms:

**Relative timing** (compare two durations):
```kotlin
val firstStart = testScheduler.currentTime
useCase().test { awaitItem(); cancelAndIgnoreRemainingEvents() }
val firstDuration = testScheduler.currentTime - firstStart
// run again, compare
```

**Boundary** (proving behavior just before/after a timeout):
```kotlin
advanceTimeBy(TIMEOUT_MS - 1000)
// still alive
advanceTimeBy(2000)
// timed out
```

greenwall doesn't use temporal primitives today; this is preemptive.

### `advanceUntilIdle()` vs. `runCurrent()`

`advanceUntilIdle()` runs every queued task to completion, regardless of
delays. If a flow uses `WhileSubscribed`, that means **running past the
timeout** and dropping the upstream. In that case, use
`testScheduler.runCurrent()` to advance only the immediately-runnable
work, or attach an active subscriber via Turbine.

---

## 9. What we explicitly don't test on the JVM

- **Bitmap pixel values** — per §3, JVM defaults are 0/null. Use
  Robolectric NATIVE mode or test against the pure-Kotlin kernel via its
  `IntArray` API.
- **`MediaStore` IO** — straightforward plumbing; manual device
  verification covers it.
- **Compose UI tree shape** — if/when needed, add Compose UI tests
  (`createComposeRule`). None today.
- **`BitmapFactory.decodeStream`** — covered by the `BitmapLoader`
  interface + fake, so the loading path itself isn't unit-tested. The
  interface exists precisely to keep this out of JVM tests.
