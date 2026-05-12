# BUGFIXES.md

Bugfix-Pass nach der Source-Code-Durchsicht. Insgesamt neun Befunde,
fünf davon echte Bugs, der Rest UX-Lücken und Code-Hygiene. Die Reihenfolge
entspricht der ursprünglichen Befund-Sortierung (Schwere absteigend).

> **Build-Verifikation:** Konnte ich in dieser Session nicht ausführen
> (kein Android-SDK in der Umgebung verfügbar, nur JDK 21). Alle
> Änderungen sind händisch durchgegangen, neue Tests folgen strikt
> `TESTING_CONVENTIONS.md`. Vor dem Merge bitte einmal
> `./gradlew :app:testDebugUnitTest` laufen lassen.

---

## 1. `redetectKeyer` ließ den Preview-Cache stehen

**Datei:** `viewmodel/EditorViewModel.kt` (vorher Z. 216–225)

`redetectKeyer` setzte `_overlayBitmap.value = null`, fasste
`_previewBitmap` aber nicht an. Wenn 🎯 nach dem ersten Auto-Detect
eine geringfügig andere Farbe lieferte (z.B. nach einer Override durch
Picker zwischendurch), zeigte ein anschließendes 🖼 das alte Preview
gegen die alte Target-Farbe — sichtbar falsch.

**Fix:** Der Detect-Job ruft am Ende `invalidateAndMaybeRecompute()`
auf, der zentrale Helper, der beide Caches dropt und — falls der User
gerade ANALYSIS oder PREVIEW sieht — sofort neu rechnet.

**Test:** `redetectKeyer invalidates both overlay and preview caches`
in `EditorViewModelTest.kt`.

---

## 2. Live-Update von Analyse/Preview war kaputt

**Datei:** `viewmodel/EditorViewModel.kt` (Setter `setThreshold`,
`setTargetColor`, `setOutputMode`)

Die Setter invalidierten die Caches auf `null`, lösten aber keine
Neuberechnung aus. Über `ImageCanvas.kt:107–110` (`overlay ?: source`,
`preview ?: source`) klappte die Canvas-Anzeige während eines Slider-
Drags oder Mode-Wechsels still auf das nackte Source-Bild zurück,
obwohl `state.viewMode` noch ANALYSIS bzw. PREVIEW war. Erst ein
manueller Toggle off+on rechnete neu.

**Fix:** Neue private Methode `invalidateAndMaybeRecompute()` ruft je
nach aktuellem `viewMode` `runAnalysis()` oder `runPreview()` auf.
Aufgerufen von `setThreshold`, `setTargetColor` und `redetectKeyer`.
`setOutputMode` triggert nur das Preview neu (Overlay ist Maske-only,
unabhängig vom Output-Modus).

Damit rapide Slider-Drags nicht Berechnungen aufstauen, cancelt jede
neue Berechnung den vorhergehenden Job — siehe Fix 7.

**Tests:** Fünf neue in `EditorViewModelTest.kt`:
- `setThreshold in ANALYSIS view re-runs analysis`
- `setThreshold in PREVIEW view re-runs preview`
- `setThreshold in SOURCE view does not trigger any recompute`
- `setTargetColor in PREVIEW view re-runs preview`
- `setOutputMode in PREVIEW view re-runs preview with the new mode`

---

## 3. `canvasToBitmapPixel` akzeptierte Sub-Pixel-Taps links/oberhalb

**Datei:** `imaging/ImageGeometry.kt` (Z. 89–90)

```kotlin
val bx = ((canvasX - placement.originX) / fit).toInt()
val by = ((canvasY - placement.originY) / fit).toInt()
```

`.toInt()` trunkiert Richtung 0, d.h. `(-0.5f).toInt() == 0`. Taps bis
zu `fit − 1` Canvas-Pixel **außerhalb** des linken/oberen Bitmap-Randes
landeten fälschlich auf Pixel `(0, 0)` statt rejected zu werden. Bei
`zoom = 20` war die fehlerhafte Zone bis zu 20 Canvas-Pixel breit.

**Fix:** `floor()` statt `.toInt()`. Negative Resultate werden korrekt
zu `-1` (oder kleiner) und vom `bx < 0`-Check verworfen.

**Tests:** Drei neue in `ImageGeometryTest.kt`:
- `canvasToBitmapPixel rejects sub-pixel taps just left of the bitmap`
- `canvasToBitmapPixel rejects sub-pixel taps just above the bitmap`
- `canvasToBitmapPixel at high zoom rejects sub-pixel taps over multiple canvas pixels`

---

## 4. `MiniFab` ignorierte `enabled` visuell

**Datei:** `ui/components/EditorFab.kt` (Z. 125–139)

Der `enabled`-Parameter wurde nur im `onClick`-Lambda geprüft. Die
SmallFloatingActionButton selbst bekam keine Disabled-Optik —
vollwertiger Ripple, keine TalkBack-Indikation. Vor dem Laden eines
Bildes wirkten 🎯/🖼/👁 wie aktive Buttons, machten aber nichts.

**Fix:** `Modifier.alpha(0.38f)` (Material 3 Standard für disabled) +
`semantics { disabled() }` für TalkBack. Layout bleibt stabil
(Mini-FABs verschwinden nicht), Optik signalisiert klar den Zustand.

---

## 5. Filename-Mismatch + toter Fallback-Code

**Datei:** `ui/screens/EditorScreen.kt` (Z. 140–144) +
`res/values/strings.xml` + `res/values-de/strings.xml`

Das `.ifBlank { default }` am Ende von `generateFilename` konnte nie
feuern — `SimpleDateFormat` und `OutputMode.name` liefern garantiert
nicht-leere Strings. Damit waren auch die String-Ressourcen
`export_default_filename_amoled` / `_transparent` (en + de) toter Code.
Die README versprach außerdem das Format `greenwall_yyyymmdd_hhmmss.png`,
der Code produzierte aber `..._amoled.png` / `..._transparent.png`.

**Fix:**
- `generateFilename` vereinfacht: nur noch `outputMode`-Parameter,
  kein `.ifBlank`, kein Fallback-Argument. KDoc mit dem tatsächlichen
  Format hinzugefügt.
- Beide ungenutzten String-Ressourcen aus `values/strings.xml` und
  `values-de/strings.xml` entfernt.
- README-Abschnitt zum Speicher-Pfad angepasst — das Suffix `_amoled`
  /`_transparent` ist jetzt dokumentiertes Verhalten (sinnvoll, weil
  zwei Klicks im selben Sekundenfenster sonst denselben Filename
  ergäben).

---

## 6. Picker bleibt im PREVIEW-/ANALYSIS-View

**Datei:** `viewmodel/EditorViewModel.kt` (`enablePicker`)

Aus PREVIEW oder ANALYSIS heraus auf "💧 Tap aufs Bild" tippen
aktivierte den Picker, ließ aber den aktuellen View-Modus stehen. Der
User sah das despill'd Preview bzw. das komplementär eingefärbte
Overlay, getappt wurde aber auf den darunterliegenden Source-Pixel
(`pickColorAt` liest aus `_sourceBitmap`). Visuell ≠ Resultat.

**Fix:** `enablePicker()` setzt `viewMode = ViewMode.SOURCE`, sodass
WYSIWYG gilt.

**Test:** `enablePicker forces viewMode back to SOURCE` in
`EditorViewModelTest.kt`.

---

## 7. In-flight Job-Races bei wiederholten Aktionen

**Datei:** `viewmodel/EditorViewModel.kt`

`loadSource`, `runAnalysis`, `runPreview`, `redetectKeyer` machten alle
ein nacktes `viewModelScope.launch { ... }` ohne den vorherigen Job zu
canceln. Bei schnellen User-Aktionen (zwei Bilder schnell hintereinander
laden, Slider-Drag während Preview-Rechnung läuft) konnten ältere
Resultate die neueren in `_sourceBitmap` / `_overlayBitmap` /
`_previewBitmap` überschreiben.

Auf einem schnellen Test-Dispatcher fällt das nicht auf — alles ist
deterministisch single-threaded. Auf realem `Dispatchers.IO` mit
Worker-Pool und unterschiedlich teurem IO ist es ein klassischer Race.

**Fix:** Vier private `Job?`-Felder (`loadJob`, `detectJob`,
`analysisJob`, `previewJob`). Jede Operation cancelt ihren eigenen
Vorgänger; `loadSource` cancelt alle vier (alter Source bedeutet alle
laufenden Berechnungen sind obsolet).

`saveResult` wurde **nicht** mit Job-Tracking versehen — Save ist eine
terminale User-Aktion, doppelter Klick könnte zwei Speicherungen
auslösen, aber das ist via `state.isExporting`-Disable auf dem Button
abgefangen.

---

## 8. `CancellationException` in `saveResult.runCatching` verschluckt

**Datei:** `viewmodel/EditorViewModel.kt` (`saveResult`, ehemals
Z. 240–247)

```kotlin
}.getOrElse { e -> ExportMessage.Error(e.message) }
```

`runCatching` fängt `Throwable`, also auch `CancellationException`.
Bei VM-Clear während eines laufenden Saves wurde das in
`ExportMessage.Error("Job was cancelled")` umgewandelt — semantisch
falsch, und strukturelle Concurrency wird verletzt.

**Fix:**

```kotlin
}.getOrElse { e ->
    if (e is CancellationException) throw e
    ExportMessage.Error(e.message)
}
```

---

## 9. Unerreichbarer `else`-Zweig in `despillPixel`

**Datei:** `imaging/ColorMatchTransform.kt` (Z. 88–96)

Der frühe Return `if (!rDom && !gDom && !bDom) return argb` deckt den
"keine Dominanz"-Fall bereits ab. Das `else -> return argb` im
darunter liegenden `when` war damit toter Code; die drei
`{r,g,b}Dom`-Bedingungen können auch nicht alle gleichzeitig wahr sein
(mathematisch unmöglich bei integer mean).

**Fix:** `else -> error("unreachable: ...")` mit erklärendem Kommentar.
Wenn die Annahme je gebrochen wird (z.B. falsche Refactor-Aktion am
mean-Vergleich), schlägt der Test sofort an, statt still einen
falschen Pixel-Wert zurückzugeben.

---

## Geänderte Dateien (Übersicht)

```
app/src/main/java/com/github/reygnn/greenwall/
    imaging/ColorMatchTransform.kt          [Fix 9]
    imaging/ImageGeometry.kt                [Fix 3]
    ui/components/EditorFab.kt              [Fix 4]
    ui/screens/EditorScreen.kt              [Fix 5]
    viewmodel/EditorViewModel.kt            [Fix 1, 2, 6, 7, 8]
app/src/main/res/values/strings.xml         [Fix 5]
app/src/main/res/values-de/strings.xml      [Fix 5]
app/src/test/java/com/github/reygnn/greenwall/
    imaging/ImageGeometryTest.kt            [+3 Tests für Fix 3]
    viewmodel/EditorViewModelTest.kt        [+8 Tests für Fix 1, 2, 6]
README.md                                   [Filename-Pattern]
```

Bestehende Tests wurden nicht modifiziert; ich habe sie händisch
durchgegangen und gegen das neue VM-Verhalten geprüft (alle bestehenden
Assertions bleiben gültig, weil `bothCachesWarmed` auf
`ViewMode.SOURCE` endet → die neue Auto-Recompute-Logik feuert dort
nicht).

---

## Was NICHT geändert wurde

- **`saveResult` Job-Cancellation** — siehe Fix 7, bewusst weggelassen.
- **Pan/Zoom-Clamping** — der User kann das Bild beliebig weit aus dem
  Canvas heraus pannen, ohne Reset-Knopf. Ist UX-fragwürdig, aber kein
  Bug; ein zusätzliches "Reset View" wäre ein eigenständiges Feature.
- **`ImageProcessing.copiedWith` Allokation** — `Bitmap.copy()` legt
  eine vollständige Kopie an, deren Pixel sofort danach komplett
  überschrieben werden. Ein `Bitmap.createBitmap(w, h, ARGB_8888)`
  würde die Allokation halbieren. Aber: messbar-relevante Slider-Drag-
  Performance gewinnen wir erst durch Job-Cancellation (Fix 7), und
  das ist erledigt.
- **`Bitmap` Recycling** — die alten Overlay/Preview-Bitmaps werden
  beim Cache-Drop nicht `recycle()`'d. Auf modernen Android-Versionen
  (Android 16) ist das praktisch egal, weil der GC die Native-Allocation
  über den `NativeAllocationRegistry` verwaltet. Trotzdem wert, irgendwann
  mal anzuschauen.

---

## Follow-up nach Review

Nach einer Code-Review wurden zwei Nachzieher in denselben Branch
übernommen:

### Picker-Restore-Verhalten (Erweiterung Fix 6)

`enablePicker` merkt sich jetzt den vorherigen `ViewMode` in einem
privaten `preservedViewMode: ViewMode?`-Feld. `disablePicker` und der
Erfolgs- wie auch Out-of-Bounds-Pfad von `pickColorAt` stellen diesen
Modus wieder her, statt den User auf SOURCE sitzen zu lassen. Wenn der
restaurierte Modus ANALYSIS oder PREVIEW ist und seine Cache während
des Picks (durch `setTargetColor`) invalidiert wurde, wird die
entsprechende Compute direkt angestoßen, damit das Canvas nicht
kurzzeitig auf Source kollabiert.

`loadSource` resettet `preservedViewMode` defensiv auf `null`, damit
ein Bild-Reload mitten in einer Picker-Session nicht später einen
stale ViewMode wiederherstellt.

Fünf neue Regressionstests in `EditorViewModelTest.kt` (ein Test
mehr als die Review eigentlich verlangt hat — die symmetrische
PREVIEW-Recompute-Variante neben der ANALYSIS-Variante kam dazu, weil
beide Pfade von der Restore-Logik berührt werden):
- `disablePicker restores the pre-picker ANALYSIS view without recompute when target is unchanged`
- `pickColorAt restores ANALYSIS and recomputes the overlay when target changed`
- `pickColorAt restores PREVIEW and recomputes the preview when target changed`
- `pickColorAt out-of-bounds restores PREVIEW without recompute`
- `loadSource clears any preserved view mode from a prior picker session`

### Regressionstest für Fix 8

Die Review hat zurecht angemerkt, dass für Fix 8
(`CancellationException` muss propagieren) kein Test existiert. Neu:
`saveResult lets CancellationException propagate and does not emit Error`
in `EditorViewModelTest.kt`. Der Test injiziert einen `CountingExporter`
der eine `CancellationException` wirft, ruft `saveResult` auf und
verifiziert, dass `exportMessage` `null` bleibt — also gerade KEIN
`ExportMessage.Error("Job was cancelled")` entsteht.

Dokumentierte Nebenwirkung: `isExporting` bleibt nach diesem Pfad auf
`true` stecken, weil das `_state.update { isExporting = false, ... }`
nach `withContext` durch das Re-throwing unerreichbar wird. In der
Praxis tritt dieser Pfad nur bei VM-Destroy auf (`viewModelScope`
wird gecancelt), wo der State sowieso verworfen wird. Der Test pinnt
das explizit als gewolltes Verhalten via `assertTrue(vm.state.value.isExporting)`,
damit es nicht versehentlich als Regression interpretiert wird.

### Bewusst nicht angefasst

- **Debounce für Recompute-Trigger.** Die Review wies zurecht darauf
  hin, dass die nicht-suspendierenden Pixel-Kernel sich nicht
  kooperativ canceln lassen — ein laufender Compute auf
  `Dispatchers.IO` läuft bis zum Ende durch, nur das Ergebnis wird
  verworfen. Bei großem Bild + schnellem Slider-Drag rödeln also
  mehrere Worker parallel mit Wegwerf-Output. Korrektheit ist fein,
  Akku/Wärme weniger. Saubere Lösung wäre ein separater
  `MutableSharedFlow<Unit>` für Recompute-Trigger mit `.debounce(...)`
  und `.collectLatest`. Ist als eigener Refactor in `TODO.md` notiert.

- **`loadSource`-Ordering.** Cache-Nullen (`_overlayBitmap`/`_previewBitmap`)
  passieren nach dem suspendierenden `loader.load(...)`. Theoretisch
  zeigt das Canvas in dem Fenster den alten Overlay über der alten
  Source. In der Praxis sind die beiden anschließenden State-Writes
  (`_xxxBitmap.value = null` → `_sourceBitmap.value = bitmap`) nicht
  durch Suspensionspoints getrennt; Compose recompose't da nicht
  dazwischen. Nicht-Blocker, nicht angefasst.
