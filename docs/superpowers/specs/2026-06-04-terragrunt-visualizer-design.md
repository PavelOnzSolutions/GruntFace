# GruntFace — Terragrunt IaC Visualizer (MVP design)

**Date:** 2026-06-04
**Status:** Approved (brainstorming)

## 1. Purpose

GruntFace is a desktop application that opens a Terragrunt project directory and a Terraform modules directory, discovers their relationship, and shows an interactive infrastructure graph. The user can click a node and edit that unit's `inputs = { ... }` block, with a diff-preview save back to the original `terragrunt.hcl` file.

The MVP target is a tool useful enough to understand and adjust an existing Terragrunt project without leaving the application — not a full Terragrunt IDE.

## 2. Scope

### In scope (MVP)

- Loading two directories: a Terragrunt root and a Terraform modules root.
- Discovering all `terragrunt.hcl` files under the Terragrunt root as **units**.
- Discovering all directories under the modules root that contain `*.tf` files with `variable` blocks as **modules**.
- Parsing each unit's `terraform { source = … }`, `dependency "<name>" { … }`, and `inputs = { … }` blocks (literal HCL — no Terragrunt function evaluation).
- Building an `InfraGraph` with unit nodes, module nodes, `uses` edges (unit → module), and `dependsOn` edges (unit → unit).
- Rendering the graph in a JavaFX `WebView` using Cytoscape.js with the dagre hierarchical layout.
- An inspector panel that, on node selection, shows a structured editor for the unit's inputs (driven by the source module's variable schema) or a read-only view of the module.
- A diff-preview save flow that surgically rewrites the `inputs` block in-place, preserving everything else in the file byte-for-byte.
- Detecting and rendering dependency cycles with a warning ribbon.
- Persisting the last-loaded Terragrunt and modules paths in user preferences.

### Out of scope (MVP)

- Evaluating Terragrunt functions (`find_in_parent_folders`, `read_terragrunt_config`, `get_env`, etc.). Expressions we cannot resolve are stored as raw text and shown read-only.
- Resolving `include` / `dependency` outputs into a merged unit configuration. We read the literal `inputs` block.
- Fetching schemas for remote modules (`git::`, `tfr://`, registry refs). Such units link to an `ExternalModule` placeholder and use a free-text input editor.
- Running `terragrunt plan` / `terragrunt apply` from the UI.
- Multi-project workspaces, theming/dark mode, multi-window, refactoring across units.
- JavaFX-based UI tests. UI is exercised manually for MVP.

## 3. Architecture

### Tech stack

- Java 25 (existing toolchain).
- JavaFX 25 controls / fxml / web (already on the module path).
- HCL4j (`com.bertramlabs.plugins:hcl4j`) for HCL2 parsing.
- Cytoscape.js + cytoscape-dagre for graph rendering, vendored into resources (no runtime network access).
- JUnit 5 (already configured) for tests.

### Package layout

All packages sit under the existing Java module `onz.solutions.toolbox.gruntface.gruntface`.

- `app` — `GruntFaceApplication` (renamed from `HelloApplication`), `Launcher`, top-level wiring.
- `model` — pure POJOs: `Unit`, `Module`, `Variable`, `Dependency`, `InfraGraph`, `ExternalModule`. No JavaFX, no IO.
- `hcl` — `HclService` interface; `Hcl4jHclService` implementation. Parsing + surgical write.
- `discovery` — `DiscoveryService`. Walks the two roots, links sources to modules, returns an `InfraGraph`.
- `ui` — JavaFX views: `MainController`, `InspectorController`, `DiffDialog`, FXML files.
- `ui.graph` — `GraphView` (the WebView host), `GraphBridge` (the JS↔Java callback object), `graph.html` (resource).

Each unit has one clear purpose, talks to the next layer through an interface, and (for everything outside `ui*`) is unit-testable without a display.

### Threading

- Discovery and HCL parse run on a background `ExecutorService` (`Executors.newSingleThreadExecutor`).
- Results return to the JavaFX Application thread via `Platform.runLater`.
- The `InfraGraph` is only mutated on the JavaFX thread.
- `HclService` is stateless — no locks, easy to test concurrently.

## 4. Data model

```
Unit {
    Path file;                 // .../terragrunt.hcl
    String name;               // parent dir name
    String sourceRef;          // raw "source" string, or null
    Optional<Path> sourceLocalPath;   // resolved local module path, or empty
    Map<String, InputValue> inputs;   // parsed literal values; expressions kept as RawHcl
    ByteRange inputsRange;     // location of "inputs = { ... }" in the file
    List<Dependency> dependencies;
    List<ParseIssue> issues;
}

Module {
    Path dir;
    String name;
    List<Variable> variables;  // ordered as encountered in the .tf files
}

Variable {
    String name;
    String typeExpr;           // "string", "list(string)", "object({...})", etc.
    String description;        // may be empty
    Optional<String> defaultLiteral;
}

Dependency {
    String name;               // dependency block label
    String configPath;         // raw config_path string
    Optional<Path> resolvedUnitPath;
}

InputValue = StringValue | NumberValue | BoolValue | RawHcl
             // RawHcl is the fallback for collections, expressions, or anything
             // we cannot map to a primitive editor.

InfraGraph {
    List<Unit> units;
    List<Module> modules;
    List<ExternalModule> externals;
    List<UsesEdge> usesEdges;
    List<DependsOnEdge> dependsEdges;
    List<List<Unit>> cycles;   // non-empty if dependency cycles exist
}
```

## 5. Discovery & parsing

**Walk passes.** Two `Files.walk` traversals — one over the Terragrunt root, one over the modules root.

- Terragrunt walk: every file named `terragrunt.hcl` becomes a `Unit`. The unit's `name` defaults to the parent directory name.
- Modules walk: every directory containing at least one `*.tf` file with a `variable` block becomes a `Module`. The module's `name` defaults to the directory name.

**Per-unit parse.** Using HCL4j on each `terragrunt.hcl`:

1. `terraform { source = "..." }` → `sourceRef`.
2. All `dependency "<name>" { config_path = "..." }` blocks → `Dependency` list.
3. `inputs = { ... }` → flat map of input name to `InputValue`, plus the byte range of the block (start offset, end offset) in the original file content.

We keep the **original file bytes** in memory alongside the parsed model — they're the source of truth for the surgical write.

**Per-module parse.** For each `*.tf` file in the module directory, collect `variable "<name>" { type, default, description }` blocks. Merge into `Module.variables`, ordered by file then by source position.

**Linking source → module.** Given a unit's `sourceRef`:

- Starts with `./` or `../` → resolve relative to the unit's directory. If the resolved path matches a discovered module → record `sourceLocalPath`. Otherwise → record a `ParseIssue` ("Source module not found at <path>"), no local link.
- Starts with an absolute path that happens to be inside the modules root → same as above.
- Otherwise (`git::`, `tfr://`, `registry.terraform.io/…`, absolute outside modules root) → create or reuse an `ExternalModule` keyed by the raw `sourceRef`.

**Cycle detection.** Tarjan's SCC algorithm on the `dependsOn` edges. SCCs with size > 1 become entries in `InfraGraph.cycles`.

## 6. Graph rendering (WebView + Cytoscape.js)

> **Superseded by `2026-06-12-diagram-renderer-design.md`.**

`graph.html` is a small static page that loads `cytoscape.min.js` and `cytoscape-dagre.min.js` from the same resources directory. It exposes two JS functions:

- `renderGraph(elementsJson)` — clears the canvas and lays out the new elements via the dagre layout.
- `fit()` — fits the graph to the viewport.

The Java side calls into JS via `webEngine.executeScript(...)`. The JS side calls back into Java by invoking methods on a `bridge` object that Java sets on the `window`:

```java
JSObject window = (JSObject) webEngine.executeScript("window");
window.setMember("bridge", graphBridge);
```

`GraphBridge` exposes `onSelect(String nodeId)` and `onDeselect()`. These methods fire JavaFX events that `MainController` listens to, marshalling onto the FX thread.

**Node styling.**
- Unit node: rounded rectangle, label = unit name, small badge with input count.
- Module node: hexagon, label = module name, badge with variable count.
- External module node: hexagon with grey fill and dashed border.
- `uses` edge: thin, dashed, no arrowhead.
- `dependsOn` edge: solid, arrowhead.
- Selected node: thick blue outline. Cycle member: orange outline. Parse error: red outline.

## 7. UI

### Main window

`BorderPane` layout:
- Top: `MenuBar` (File → Open Terragrunt Root, Open Modules Root, Reload, Quit; View → Fit, Show Modules; Help → About) and a `ToolBar` underneath with the same primary actions as buttons plus a "Show modules" checkbox. "Show modules" toggles the visibility of module and external-module nodes plus their `uses` edges — the dependency graph between units remains visible either way.
- Center: `SplitPane`, horizontal, 60/40, draggable. Left pane = `GraphView`. Right pane = `Inspector`.
- Bottom: status `Label` (counts: units, modules, externals, warnings).

Window size and the two loaded paths are persisted in `java.util.prefs.Preferences`.

### Empty state

On first launch, the graph pane shows a centered prompt: "Open a Terragrunt root to begin", with a button that opens the directory picker. After the Terragrunt root is selected, the modules picker opens immediately (defaulting to a sibling-named "modules" directory if one exists). Loading runs on the background executor with a `ProgressIndicator` over the graph pane.

### Inspector panel

The right side of the `SplitPane`, an FXML-loaded `VBox` with three states:

**Nothing selected.** Centered hint text: "Click a node to inspect".

**Unit selected.**
- Header: unit name (bold), file path (monospaced, with a "Reveal" link that opens the platform file manager via `Desktop.getDesktop().open(file.getParentFile())`).
- Source section: module name (clickable — selecting it re-selects the linked module node on the graph) or "External: <sourceRef>" or "Module not found".
- Inputs section (the editor — see below).
- Dependencies section: read-only `ListView` of dependency name → resolved unit (clickable).
- Save bar: a single `Save…` button at the bottom, disabled until edits exist.

**Module selected.**
- Header: module name, directory path.
- Variables section: read-only `TableView` (name, type, default, description).
- Used by section: clickable list of units that reference this module.

### Inputs editor

One row per `Variable` in the source module — the module's variable order is the canonical order. Each row:
- `string` → `TextField`.
- `number` → numeric `TextField` with input validation.
- `bool` → `CheckBox`.
- `list(...)`, `map(...)`, `object({...})`, or anything else → `TextArea` containing the value's HCL representation (no full type validation; we write back what the user types as HCL).
- A "default" hint text from the variable's `default` (if any) shows when the field is empty.
- Description appears as a tooltip on the label.

After the declared variables, an **Extra inputs** group shows any keys present in the unit's `inputs` block that aren't declared in the module's variables. Each is editable but marked with a yellow "not declared" indicator.

Inputs whose current value is a `RawHcl` expression we couldn't reduce to a primitive are shown as a read-only `TextField` with a small "expression" tag — editing requires Terragrunt evaluation, which is out of scope.

## 8. Save flow

1. User edits inputs in the inspector → the `Save…` button enables.
2. Click `Save…` → `HclService.renderInputsBlock(values, variableOrder)` produces a deterministically formatted block:
   - 2-space indentation, keys aligned to the longest key's `=`, primitives written as HCL literals, raw HCL fragments inserted verbatim.
   - Order: declared variables in module order first, then extra inputs in their original order.
3. Splice into the original file content: `text[0..start] + newBlock + text[end..]`. Compute a unified diff against the on-disk content.
4. Open `DiffDialog` — a modal `Stage` with a monospaced `TextFlow` rendering the diff (red `-`, green `+`, grey context), and `Cancel` / `Confirm & Save` buttons.
5. On confirm:
   - Re-stat the file's `lastModifiedTime`. If it has changed since we parsed it, abort with a modal: "File changed on disk — reload?". The edits remain in the inspector for the user to copy out.
   - Write the new content to `<file>.gruntface.tmp` in the same directory.
   - `Files.move(tmp, file, ATOMIC_MOVE, REPLACE_EXISTING)`.
   - Re-parse just that one unit and patch it in the `InfraGraph`. The graph view is updated in place — no full reload.
6. The `Save…` button disables again; the inspector reflects the now-saved values.

## 9. Error handling

| Condition | Behaviour |
|---|---|
| Parse error in a `terragrunt.hcl` | Unit still created; outlined red on the graph; inspector shows the parser error instead of the inputs form. Other units load normally. |
| Source module not found at the resolved local path | Unit gets a red badge; inspector shows "Source module not found at <path>" as text. No `uses` edge. |
| External `sourceRef` | Unit links to an `ExternalModule` placeholder node (grey, dashed). Inspector falls back to free-text mode: a single `TextArea` editing the literal `inputs = { ... }` HCL. |
| Dependency `config_path` doesn't resolve to a discovered unit | `Dependency.resolvedUnitPath` is empty; the dependency row in the inspector shows "unresolved"; no `dependsOn` edge. |
| Dependency cycle | Members outlined orange; a warning ribbon at the top of the graph pane lists the cycle. Graph still renders. |
| Write failure (permission denied, IO error) | Modal error dialog with the message. Inspector keeps edits intact. |
| File changed on disk between parse and save | Abort with "File changed on disk — reload?". Edits preserved. |
| HCL re-parse after a successful write fails | Log in status bar with a "Reload" suggestion. File is not reverted automatically. |

## 10. Testing

**Unit tests (JUnit 5)** under `src/test/java`:
- `model` — straightforward POJO/equality tests where useful.
- `hcl` — parse a set of canonical `terragrunt.hcl` fixtures from `src/test/resources/fixtures/hcl/`. Round-trip test: parse, immediately re-render the same `inputs` block, splice back, assert byte-identical content (the no-op write must be a true no-op).
- `discovery` — a tree of fixture files under `src/test/resources/fixtures/sample-project/` containing 3 units, 2 local modules, 1 dependency edge, 1 external source. Assert the produced `InfraGraph` matches expected structure.
- Cycle detection: a fixture with a 3-unit cycle; assert it appears in `InfraGraph.cycles`.

**Integration test** — one end-to-end test that loads the sample project, applies an edit to one unit's inputs via `HclService`, writes through `discovery`'s atomic-rename path, re-parses, and asserts the new value is present and the rest of the file is byte-identical to the pre-edit content outside the `inputs` block range.

**UI** — manual for MVP. No `TestFX` dependency added.

## 11. Build / dependencies

`build.gradle.kts` additions:

```kotlin
dependencies {
    implementation("com.bertramlabs.plugins:hcl4j:0.9.+") // pin exact version when wiring
    // existing dependencies unchanged
}
```

`module-info.java` additions:

```java
requires com.bertramlabs.plugins.hcl4j; // confirm exact module name from the jar
```

The bundled Cytoscape JS files (`cytoscape.min.js`, `cytoscape-dagre.min.js`, `graph.html`, `graph.css`) live under `src/main/resources/onz/solutions/toolbox/gruntface/gruntface/ui/graph/` and are loaded from the classpath at runtime.

## 12. Open questions

None blocking. Items deliberately deferred to post-MVP:
- Whether to evaluate simple Terragrunt functions (`find_in_parent_folders`, `read_terragrunt_config`) to give richer module schemas.
- Remote module schema resolution (clone-and-cache).
- A `terragrunt plan` / `apply` runner pane.
- Saving the loaded project as a named workspace, with per-workspace UI state.
