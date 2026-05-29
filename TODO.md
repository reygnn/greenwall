# TODO

- [x] Preview-Bild vor dem Speichern. Umgesetzt in `feature/preview`:
  neuer `ViewMode`-Enum (SOURCE / ANALYSIS / PREVIEW) ersetzt
  `analysisVisible`. Im FAB ein neuer 🖼-Knopf schaltet zwischen
  SOURCE und PREVIEW. Das Preview-Bitmap kommt durch den gleichen
  `applyAmoled` / `applyTransparent`-Pfad wie das Speichern — also
  1:1 das, was beim Save rauskäme. Cache wird bei Änderungen an
  `targetColor`, `threshold`, `outputMode` oder beim Bildladen
  invalidiert. Pan + Zoom (1.0..20.0, Pinch um den Centroid) gilt
  für alle drei View-Modes und respektiert die Picker-Mathe.

- [x] Color-Picker / Pipette für die Keyer-Hintergrundfarbe als Fallback,
  wenn die Auto-Erkennung nicht eindeutig anschlägt. Umgesetzt in
  `feature/pipette` als Tap-aufs-Canvas: ein Knopf 💧 im CommandsPanel
  aktiviert den Picker-Modus, der nächste Canvas-Tap setzt `targetColor`
  auf den Pixel-Wert; Tap außerhalb des Bildes bricht ab. Reine-Kotlin-
  Kernel `ImageGeometry.canvasToBitmapPixel(...)` macht die Canvas →
  Bitmap-Mapping-Arithmetik testbar.

- [x] Die fixen Preset-Buttons Grün / Blau / Pink aus dem CommandsPanel
  entfernen. Erledigt im gleichen Branch — der ganze `KeyerPreset`-Enum
  ist raus. Default-Target ist jetzt `EditorState.DEFAULT_TARGET_COLOR`
  (hartkodiert auf `0xFF00FF00`).

- [x] Debounce für Recompute-Trigger in `EditorViewModel`. Umgesetzt in
  `refactor/debounce-recompute`: neuer `MutableSharedFlow<Unit>`
  (`recomputeTrigger`), den `setThreshold` per `tryEmit` füttert, mit
  einem `.debounce(50.milliseconds).collectLatest { recomputeCurrentView() }`-
  Collector im `init`. Nur der kontinuierliche Slider läuft über den
  Trigger; die diskreten Setter (`setTargetColor`, `redetectKeyer`)
  bleiben auf dem sofortigen `invalidateAndMaybeRecompute`-Pfad. Slider-
  State + Cache-Invalidierung passieren weiter synchron in `setThreshold`
  — nur die teure Berechnung wird debounced, sodass ein flotter Drag in
  eine einzige `analyze`/`apply`-Berechnung kollabiert statt pro Tick
  einen Wegwerf-Worker auf `Dispatchers.IO` zu starten.
