# TODO

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
