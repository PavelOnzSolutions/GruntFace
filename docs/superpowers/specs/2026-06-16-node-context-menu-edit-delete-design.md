# GruntFace — Node Context Menu: Edit Raw HCL & Delete (design)

**Date:** 2026-06-16
**Status:** Approved (brainstorming)
**Related:**
- `2026-06-04-terragrunt-visualizer-design.md` (read/edit MVP)
- `2026-06-12-diagram-renderer-design.md` (pure-JavaFX diagram)
- `2026-06-15-add-resource-include-design.md` (Create wizard)
- `2026-06-16-dependency-resources-design.md` (Dependencies in wizard + inspector)

## 1. Purpose

The diagram surfaces Resource nodes (`u::` Units) and Include nodes (`c::` CommonHcl). Today, right-clicking only offers creation entry points. Users have no way to open the underlying file for free-form edits beyond the Inspector's `inputs`-only editor, and no way to delete a resource without leaving the app.

This change adds two right-click actions on `u::` and `c::` nodes:

- **Edit raw HCL** — open the file in an in-app modal editor with syntax highlighting; save through the existing diff-preview / atomic-write / reparse pipeline.
- **Delete** — confirm and remove the file; for Includes, refuse if any Unit still includes the file (block, don't cascade).

Goal: stay consistent with the existing edit flow (DiffDialog → atomic move → reparse → in-place patch), reuse the already-wired context-menu hook on `DiagramView`, and avoid full re-discovery whenever in-place patching is correct.

## 2. Scope

### In scope

- Two new menu items on the `u::` and `c::` branches of `MainController.showCanvasOrNodeContextMenu`.
- A modal `HclEditDialog` (RichTextFX `CodeArea`) with HCL syntax highlighting, line numbers, monospace font, `Ctrl+S` to save, `Esc` to cancel, dirty-prompt on window-close.
- A regex-based `HclSyntaxHighlighter` (~6 token classes; produces `StyleSpans<Collection<String>>`).
- A new `InfraGraphPatcher.replaceUnit(InfraGraph, Unit, Unit)` that handles full edge re-resolution when a Unit's file is rewritten (existing `MainController.replaceUnit` for inputs-only saves stays as-is).
- New `InfraGraphPatcher.removeUnit(InfraGraph, Unit)` and `removeCommon(InfraGraph, CommonHcl)`.
- Reference safety: deleting a `CommonHcl` is blocked when any `IncludesEdge` still points at it.
- A full `loadAsync()` after a Common raw-edit save (correctness over UX preservation — see §3).

### Out of scope

- Editing Module (`m::`) or External (`x::`) nodes. Modules are filesystem folders containing `.tf` files (Terraform, not Terragrunt); Externals have no local files.
- Renaming files from the menu.
- Multi-file selection / bulk delete.
- Undo. Delete is permanent; the user gets one confirmation.
- Cascading delete (deleting referencing units when their `Include` target goes away).
- Auto-format on save, find/replace, multi-tab, HCL-aware completions, persisting editor window size.
- Validating the HCL on save before writing (we trust the user; parse failures surface as `ParseIssue`s on reload).
- A symmetric reference check before deleting a Unit. Deleting a Unit that is the target of `dependency` blocks in other units is allowed; those units will surface parse issues on next discovery, which is the correct signal.

## 3. Architecture

### Package layout (delta only)

Under `solutions.onz.toolbox.gruntface`:

```
create
├── InfraGraphPatcher              // EXTENDED: + replaceUnit, removeUnit, removeCommon

ui
├── MainController                  // EXTENDED: menu items + edit/delete handlers
└── (rest unchanged)

ui.edit                             // NEW package
├── HclEditDialog.java              // modal editor
└── HclSyntaxHighlighter.java       // regex-based highlighter for CodeArea
```

New resource:

```
src/main/resources/solutions/onz/toolbox/gruntface/ui/edit/
└── hcl-editor.css                  // CodeArea token color classes
```

### Boundaries

- `HclEditDialog` knows nothing about `Unit` / `CommonHcl` / `InfraGraph`. It edits text and reports either "user saved this text" or "user cancelled".
- `HclSyntaxHighlighter` is a pure function `String → StyleSpans<Collection<String>>`. No FX state.
- `MainController` orchestrates: read file → open dialog → on save, compare-and-write atomically → reparse → patch graph → rerender.
- `InfraGraphPatcher` mutates the immutable `InfraGraph` record; no I/O, no FX.

### Data flow

**Edit (Unit):**

```
right-click u::file
        │
        ▼
MainController.editUnitRawHcl(unit)
        │ read file from disk (drift check)
        ▼
HclEditDialog.show(window, file, text, onSave)
        │ user edits + Ctrl+S
        ▼
onSave: re-read disk, compare to text we showed; refuse if changed
        │
        ▼
DiffDialog.show(window, "Save changes — <file>", original, new)
        │ OK
        ▼
write tmp → ATOMIC_MOVE
        │
        ▼
Unit newU = discovery.linkSingleUnit(file, currentGraph)
        │
        ▼
currentGraph = InfraGraphPatcher.replaceUnit(currentGraph, oldU, newU)
        │
        ▼
rerender; reselect u::file; statusLabel "Saved <file>"
```

**Edit (Common):**

```
right-click c::file
        │
        ▼
MainController.editCommonRawHcl(common)
        │ read file from disk (drift check)
        ▼
HclEditDialog.show(...)
        │ Ctrl+S
        ▼
onSave: re-read disk, drift check, DiffDialog, atomic write
        │
        ▼
loadAsync()                          // full re-discovery
        │
        ▼
onLoaded: try to reselect c::<oldPath> if still present
```

Why full reload: a Common's `baseSourceUrl` and `locals` are interpolated into every Unit that includes it (`${include.common.locals.base_source_url}` → unit `terraform { source }`). Editing those values can re-resolve unit→module edges across the project. Patching that precisely would require reparsing every including Unit and recomputing `usesEdges`. Common edits are rare; the cost is acceptable.

**Delete (Unit):**

```
right-click u::file → "Delete…"
        │
        ▼
confirm: "Delete <filename>? This cannot be undone."  [Cancel | OK]
        │ OK
        ▼
Files.delete(unit.file())
        │
        ▼
currentGraph = InfraGraphPatcher.removeUnit(currentGraph, unit)
        │
        ▼
rerender; clearSelection; inspector.showEmpty(); statusLabel "Deleted <file>"
```

**Delete (Common) with reference block:**

```
right-click c::file → "Delete…"
        │
        ▼
collect List<Unit> refs from currentGraph.includesEdges() where to.file == common.file
        │
        ├─ non-empty: Alert.INFO "<file> is included by N unit(s): a, b, c. Remove those includes first."  [OK]
        │             (terminate — no destructive action)
        │
        └─ empty:
                ▼
        confirm: "Delete <filename>? This cannot be undone."
                ▼
        Files.delete(common.file())
                ▼
        currentGraph = InfraGraphPatcher.removeCommon(currentGraph, common)
                ▼
        rerender; clearSelection; inspector.showEmpty(); statusLabel "Deleted <file>"
```

## 4. Menu wiring

`MainController.showCanvasOrNodeContextMenu(double, double, String nodeId)` already runs on the FX thread (called via `Platform.runLater` from the diagram). Extended branches:

```java
} else if (nodeId.startsWith("c::")) {
    CommonHcl include = findCommonByNodeId(nodeId);
    if (include != null) {
        addItem(menu, "Create resource from this…", ...existing...);
        addItem(menu, "Edit raw HCL…",  () -> editCommonRawHcl(include));
        addItem(menu, "Delete…",        () -> deleteCommon(include));
    }
} else if (nodeId.startsWith("u::")) {
    Unit unit = findUnitByNodeId(nodeId);                 // NEW finder
    if (unit != null) {
        addItem(menu, "Edit raw HCL…", () -> editUnitRawHcl(unit));
        addItem(menu, "Delete…",       () -> deleteUnit(unit));
    }
}
```

The existing `u::` branch is absent today (Units have no menu actions). Adding it preserves the canvas-default and other-node behaviours unchanged.

Helper additions in `MainController`:

```java
private Unit findUnitByNodeId(String nodeId);
private void editUnitRawHcl(Unit unit);
private void editCommonRawHcl(CommonHcl common);
private void deleteUnit(Unit unit);
private void deleteCommon(CommonHcl common);
private void saveUnitRawFile(Unit unit, String originalText, String newText);
private void saveCommonRawFile(CommonHcl common, String originalText, String newText);
```

Naming chosen so `saveUnitRawFile` / `saveCommonRawFile` are clearly distinct from the existing `saveUnit` (inputs-only structured) and `saveUnitFreeText` (inputs-block raw text) flows.

## 5. `HclEditDialog`

### API

```java
public final class HclEditDialog {
    public static void show(
        javafx.stage.Window owner,
        java.nio.file.Path file,
        String initialText,
        java.util.function.Predicate<String> onSave  // returns true → close dialog; false → keep open
    );
}
```

The save callback returns a boolean so the host (`MainController`) can keep the dialog open when the save attempt fails (drift conflict, atomic-write IOException, reparse error). On `true` the dialog closes; on `false` it stays open with the user's edits intact and an error already surfaced by the host via `Alert`.

- Modal `Stage`, `Modality.WINDOW_MODAL`, owner = main window.
- Title: `"Edit — " + file.getFileName()`.
- Layout: `BorderPane`. Center = `org.fxmisc.flowless.VirtualizedScrollPane<CodeArea>`. Bottom = `HBox` (right-aligned) with `Save` (default button) and `Cancel` (cancel button).
- `CodeArea`:
  - Monospace font (`-fx-font-family: "Consolas", "Menlo", monospace`).
  - Line numbers via `LineNumberFactory.get(codeArea)`.
  - `setWrapText(false)`.
  - Tab inserts two spaces (consume `KeyCode.TAB` and `replaceSelection("  ")`).
- Keybindings: `Ctrl+S` → Save action; `Esc` → Cancel action (goes through the same dirty prompt as the window-close path).
- Dirty tracking: `boolean dirty` flips to `true` on the first `textProperty` change. On window-close request *or* Cancel action, if `dirty`, show `Alert.CONFIRMATION` "Discard unsaved changes?"; on Cancel, consume the event so the dialog stays open.
- Default size: 900×700, resizable. Size is **not** persisted across launches (out of scope).
- Save handler: `boolean closeNow = onSave.test(codeArea.getText()); if (closeNow) stage.close();`. Dialog stays open if the host returns `false`.
- Cancel handler: dirty prompt (if applicable) → `stage.close()`.

### CSS (`hcl-editor.css`)

Six token color classes, light + dark variants:

```css
.hcl-editor .code-area .hcl-comment   { -fx-fill: -color-fg-muted; -fx-font-style: italic; }
.hcl-editor .code-area .hcl-string    { -fx-fill: -color-success-fg; }
.hcl-editor .code-area .hcl-number    { -fx-fill: -color-warning-fg; }
.hcl-editor .code-area .hcl-keyword   { -fx-fill: -color-accent-fg; -fx-font-weight: bold; }
.hcl-editor .code-area .hcl-block     { -fx-fill: -color-accent-fg; }
.hcl-editor .code-area .hcl-attr      { -fx-fill: -color-fg-default; }
```

AtlantaFX exposes `-color-*` tokens that re-resolve under `.dark` automatically, so a single rule set covers both themes.

### Threading

Dialog runs on FX thread. Highlighting runs on FX thread inside a `successionEnds(Duration.ofMillis(150))` debounce subscriber so typing isn't blocked.

## 6. `HclSyntaxHighlighter`

```java
public final class HclSyntaxHighlighter {
    public static StyleSpans<Collection<String>> computeHighlighting(String text);
}
```

Single compiled `Pattern` with named groups, in this precedence order:

| Group     | Regex                                              | Class         |
| --------- | -------------------------------------------------- | ------------- |
| `COMMENT` | `#[^\n]*` ∪ `//[^\n]*` ∪ `/\*[\s\S]*?\*/`          | `hcl-comment` |
| `STRING`  | `"(?:\\.\|[^"\\])*"`                               | `hcl-string`  |
| `HEREDOC` | `<<-?(\w+)[\s\S]*?\n\1`                            | `hcl-string`  |
| `NUMBER`  | `\b\d+(?:\.\d+)?\b`                                | `hcl-number`  |
| `KEYWORD` | `\b(?:true\|false\|null\|for\|in\|if\|else\|endif\|endfor)\b` | `hcl-keyword` |
| `BLOCK`   | `\b(?:terraform\|inputs\|locals\|include\|dependency\|dependencies\|generate\|remote_state\|skip\|prevent_destroy\|iam_role\|download_dir)\b` | `hcl-block` |
| `ATTR`    | `\b[a-zA-Z_][a-zA-Z0-9_]*\b(?=\s*=)`               | `hcl-attr`    |

Implementation:

```java
Matcher m = PATTERN.matcher(text);
StyleSpansBuilder<Collection<String>> b = new StyleSpansBuilder<>();
int last = 0;
while (m.find()) {
    String cls = ... // pick matched group name → class
    b.add(Collections.emptyList(), m.start() - last);
    b.add(Collections.singleton(cls), m.end() - m.start());
    last = m.end();
}
b.add(Collections.emptyList(), text.length() - last);
return b.create();
```

Edge case: empty text → return `StyleSpans` with one zero-length empty span (RichTextFX requires a non-null result).

## 7. `InfraGraphPatcher` additions

### `replaceUnit(InfraGraph base, Unit oldU, Unit newU)`

Used by the **raw-file** edit flow only (not the existing inputs-only `MainController.replaceUnit`, which stays). Handles the case where the new file declares different `dependency`, `include`, or `terraform { source }` blocks than the old one.

Steps:

1. **Units list**: replace `oldU` with `newU` (matched by `file()` equality).
2. **`UsesEdge`**: drop any edge with `from == oldU` (by file). Add fresh outgoing edges from `newU` using the same resolution as `addUnit`:
   - If `newU.sourceLocalPath()` present: find matching `Module` by canonical-path equality; add `UsesEdge(newU, module)`.
   - Else if `newU.sourceRef()` present: find matching `ExternalModule` by `sourceRef()`; add `UsesEdge(newU, external)`.
3. **`IncludesEdge`**: drop any edge with `from == oldU`. For each `IncludeBlock` in `newU.includes()` with a `resolvedPath`, find matching `CommonHcl` and add `IncludesEdge(newU, common)`.
4. **`DependsOnEdge`**:
   - Drop any edge with `from == oldU` (outgoing — will rebuild).
   - For edges with `to == oldU` (incoming from other units), **rebind** `to` to `newU` (other units still depend on this file path).
   - Re-add outgoing edges: for each `Dependency` in `newU.dependencies()` with a `resolvedUnitPath`, find the target unit by canonical-dir match against the updated units list, add `DependsOnEdge(newU, target)`.
5. **Cycles**: recompute via `CycleDetector.findCycles` over the new `dependsEdges`.
6. Return a new `InfraGraph` record.

The pattern mirrors `InfraGraphPatcher.addUnit` but operates as remove-then-add for the single unit.

### `removeUnit(InfraGraph base, Unit unit)`

1. Drop `unit` from units list.
2. Drop every `UsesEdge` whose `from.file() == unit.file()`.
3. Drop every `IncludesEdge` whose `from.file() == unit.file()`.
4. Drop every `DependsOnEdge` whose `from.file() == unit.file()` OR `to.file() == unit.file()`.
5. Recompute cycles.
6. Return a new `InfraGraph` record.

### `removeCommon(InfraGraph base, CommonHcl common)`

1. Drop `common` from commons list.
2. Drop every `IncludesEdge` whose `to.file() == common.file()` *(should be empty by precondition, but defensive)*.
3. Return a new `InfraGraph` record. `UsesEdge` / `DependsOnEdge` / cycles untouched.

## 8. Edit save flow detail

### `saveUnitRawFile(Unit oldU, String originalText, String newText)`

1. `String onDisk = Files.readString(oldU.file());`
2. If `!onDisk.equals(originalText)` → `Alert.WARNING "File changed on disk — reload before saving."`; return without writing.
3. If `originalText.equals(newText)` → no-op (statusLabel "No changes"); return.
4. `boolean go = DiffDialog.show(window, "Save changes — " + file.getFileName(), originalText, newText);` — return on Cancel.
5. Atomic write: tmp → `Files.move(tmp, file, ATOMIC_MOVE, REPLACE_EXISTING)`.
6. `Unit newU = discovery.linkSingleUnit(oldU.file(), currentGraph);`
7. `currentGraph = InfraGraphPatcher.replaceUnit(currentGraph, oldU, newU);`
8. `rerender();`
9. `graphView.select("u::" + newU.file());`
10. `inspector.showUnit(newU, findUsedModule(newU), findUsedExternal(newU), findUsedCommon(newU));`
11. `statusLabel.setText("Saved " + file.getFileName());`

### `saveCommonRawFile(CommonHcl oldC, String originalText, String newText)`

1–5. Same drift check, no-op check, diff dialog, atomic write.
6. `this.postLoadSelectId = Optional.of("c::" + oldC.file());` — a new `Optional<String>` field on `MainController`.
7. Call `loadAsync()`.
8. Extend `MainController.onLoaded(...)` to, at the end, consume `postLoadSelectId` if present: call `graphView.select(id)` and clear the field. If the id no longer resolves (file gone from the new graph), the selector silently no-ops — the existing `DiagramSkin.highlightSelection(null/missing)` already handles that.

## 9. Delete flow detail

### `deleteUnit(Unit unit)`

1. `Alert.CONFIRMATION` with header "Delete resource", body `"Delete " + unit.file().getFileName() + "? This cannot be undone."`, OK + Cancel buttons. Default = Cancel.
2. On OK: `Files.delete(unit.file());` (IOException → `Alert.ERROR`, return).
3. `currentGraph = InfraGraphPatcher.removeUnit(currentGraph, unit);`
4. `rerender(); graphView.clearSelection(); inspector.showEmpty();`
5. `statusLabel.setText("Deleted " + unit.file().getFileName());`

### `deleteCommon(CommonHcl common)`

1. Compute `List<Unit> refs = currentGraph.includesEdges().stream().filter(e -> e.to().file().equals(common.file())).map(e -> e.from()).distinct().toList();`
2. If `!refs.isEmpty()`:
   - Build a comma-separated list of `unit.file().getFileName()` strings (first ~5; "and N more" if longer).
   - Show `Alert.INFORMATION` with header "Include is in use", body `"<filename> is included by N unit(s): a, b, c. Remove those includes first."` — **OK only**, no destructive option.
   - Return.
3. `Alert.CONFIRMATION` "Delete include", body `"Delete " + filename + "? This cannot be undone."`.
4. On OK: `Files.delete(common.file());` (IOException → `Alert.ERROR`, return).
5. `currentGraph = InfraGraphPatcher.removeCommon(currentGraph, common);`
6. `rerender(); graphView.clearSelection(); inspector.showEmpty();`
7. `statusLabel.setText("Deleted " + filename);`

## 10. Error handling

| Condition | Behaviour |
| --- | --- |
| `Files.readString` fails when opening Edit | `Alert.ERROR` with message; dialog not opened. |
| File changed on disk between dialog-open and save | `Alert.WARNING` "File changed on disk — reload before saving."; no write. |
| Save clicked with no actual changes | No-op; statusLabel "No changes". Dialog closes. |
| `Files.move` (atomic write) fails | `Alert.ERROR`; graph untouched; dialog stays open so the user can copy their text out. |
| Reparse via `discovery.linkSingleUnit` throws | `Alert.ERROR`; file is already written; fall back to a full `loadAsync()` so the graph isn't left in an inconsistent state. |
| `Files.delete` fails | `Alert.ERROR`; graph untouched. |
| Common is referenced at delete time | Blocking `Alert.INFORMATION` listing referencing unit filenames; no destructive action. |
| User cancels confirm / diff dialog / dirty prompt | Silent no-op. |
| `loadAsync()` after Common edit fails | Existing `onLoadFailed` path applies (`Alert.ERROR`, statusLabel). |

## 11. Module-info / build

`build.gradle.kts`:

```
implementation("org.fxmisc.richtext:richtextfx:0.11.5")
```

This transitively pulls `org.fxmisc.flowless`, `org.fxmisc.undo`, `org.fxmisc.wellbehaved`, `org.reactfx`. All are automatic modules under JPMS.

`module-info.java`:

```
requires org.fxmisc.richtext;
requires org.fxmisc.flowless;
requires org.reactfx;
```

No `exports` / `opens` for `ui.edit` (no FXML; constructed in Java).

GraalVM native-image reachability: RichTextFX uses `Region.setSnapshotParameters` and `WritableImage`, both already on the JavaFX path the diagram uses. No new reflection registrations expected; if `nativeCompile` surfaces missing reflection metadata, add a `reflect-config.json` entry under `META-INF/native-image/org.fxmisc.richtext/`. Out of scope until / unless it fails.

## 12. Testing

### Headless JUnit 5 (under `src/test/java`)

- **`InfraGraphPatcherRemoveTest`**
  - `removeUnit_dropsOutgoingUsesIncludesDependsEdges`
  - `removeUnit_dropsIncomingDependsEdges`
  - `removeUnit_recomputesCyclesAfterRemoval`
  - `removeCommon_dropsIncludesEdgesToCommon` (defensive case — caller blocks first)
  - `removeCommon_leavesUnrelatedEdgesIntact`

- **`InfraGraphPatcherReplaceUnitTest`**
  - `replaceUnit_swapsUnitInList`
  - `replaceUnit_addsNewOutgoingDependsEdges` (old unit had no deps; new declares two)
  - `replaceUnit_dropsRemovedOutgoingDependsEdges` (old had two; new has one)
  - `replaceUnit_rebindsIncomingDependsEdgesToNewInstance` (another unit depended on this file)
  - `replaceUnit_switchesUsesEdgeWhenSourceChanges` (local module → external)
  - `replaceUnit_recomputesCyclesWhenDepsChange`

- **`HclSyntaxHighlighterTest`**
  - `tokens_simpleInputsBlock` — assert ranges for strings, attrs, blocks
  - `tokens_lineCommentAndBlockComment`
  - `tokens_heredocSurvives` (uses `<<-EOT … EOT` form, matching existing fixtures)
  - `tokens_emptyText` returns a single empty span
  - `tokens_pureWhitespace` produces no styled spans

### Manual smoke

1. Right-click a Unit → Edit raw HCL → change a comment → Save → DiffDialog shows the diff → confirm → file updated on disk → diagram redraws with the unit reselected.
2. Edit raw HCL on a Unit → add a new `dependency "x" { config_path = "../sibling" mock_outputs = {} }` block → Save → graph shows a new `dependsOn` edge to the sibling.
3. Edit raw HCL on a Unit → change `terraform { source }` from a local module to an external `git::...?ref=...` ref → Save → `UsesEdge` switches target.
4. Edit raw HCL on a Common → change a `locals` value → Save → full reload occurs → diagram reselects the same common node.
5. Right-click an orphan Common (no including units) → Delete → confirm → file gone → node gone.
6. Right-click a referenced Common → Delete → blocking dialog lists units; no destructive action available.
7. Right-click a Unit → Delete → confirm → file gone → all incoming `dependsOn` edges from other units gone from the diagram.
8. Open Edit dialog, type changes, close window via X → "Discard unsaved changes?" prompt → Cancel keeps dialog open.
9. Open Edit dialog on a file, then externally modify the file, then click Save → "File changed on disk" warning.
10. Toggle theme between Light and Dark with the editor open → token colors re-resolve via `-color-*` tokens.

## 13. Implementation sequencing (preview)

Final sequencing is the writing-plans skill's job. Natural decomposition:

1. `InfraGraphPatcher.removeUnit` + `removeCommon` + `replaceUnit` with `InfraGraphPatcherRemoveTest` and `InfraGraphPatcherReplaceUnitTest`.
2. `build.gradle.kts` + `module-info.java`: add RichTextFX. Verify the project still compiles and `jlink` succeeds.
3. `ui.edit.HclSyntaxHighlighter` + `HclSyntaxHighlighterTest`.
4. `ui.edit.HclEditDialog` + `hcl-editor.css`.
5. `MainController`: `findUnitByNodeId`, `editUnitRawHcl`, `editCommonRawHcl`, `saveUnitRawFile`, `saveCommonRawFile`, plus menu wiring for `u::` and the two new `c::` items.
6. `MainController`: `deleteUnit`, `deleteCommon`, plus menu wiring.
7. Optional `Optional<String> postLoadSelectId` plumbing in `MainController.onLoaded` for the Common-edit reselect.
8. Manual smoke pass 1–10. Polish copy on alerts.

## 14. Open questions

None blocking. Items deliberately deferred (see Out of scope):

- Editing Module / External nodes.
- Renaming files.
- Cascading delete and undo.
- Bulk operations.
- Persisting editor window size and scroll position.
