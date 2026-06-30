# GruntFace — Pure-JavaFX Diagram Renderer (design)

**Date:** 2026-06-12
**Status:** Approved (brainstorming)
**Supersedes (in part):** §6 "Graph rendering (WebView + Cytoscape.js)" of `2026-06-04-terragrunt-visualizer-design.md`

## 1. Purpose

Replace the WebView + Cytoscape.js diagram with a **pure-JavaFX renderer** that shows the Terragrunt `InfraGraph` as nested swimlanes (region → env → resources) of expandable Azure resource cards, with descriptive resolved resource names and orthogonally routed dependency edges.

The motivation is **richer node UI** (the current rectangle-with-icon cards cannot host inline content) and **performance & UX polish** (no JS bridge, native fonts, AtlantaFX-themed cards). Stability and GraalVM native-image support are not goals of this change but are not regressed either.

## 2. Scope

### In scope

- A new `ui.graph.DiagramView` JavaFX `Region` that fully replaces `ui.graph.GraphView`.
- Compound layout (region → env → resources) computed by **Eclipse Layout Kernel (ELK)** with the `layered` algorithm.
- Expandable `UnitCard`s with header (Azure icon, unit name, resource display name, resolved resource name) and an expand caret revealing dependency port rows.
- Plain header-only `ModuleCard`, `ExternalCard`, `CommonCard`.
- `RegionContainer` / `EnvContainer` styled panels with a header label and unit-count badge.
- Best-effort **resource-name resolution** that evaluates the small subset of HCL expressions used in Fidoo Terragrunt projects to produce names like `vm-fid-jumphost-gwc-prod`.
- Pan/zoom viewport with IntelliJ-style shortcuts.
- Removal of `javafx.web`, all Cytoscape / dagre JS, the `graph.html` page, and the `JSObject` bridge.

### Out of scope

- Editing the graph topology by dragging in the diagram (no add/remove nodes from the diagram itself; inputs editing stays in the right-hand `InspectorController`).
- Per-output edge anchors (one synthetic east-side "out" port per unit; multiple outgoing edges share it).
- Multi-include locals merging (only the **first** common include's locals are consulted for name evaluation).
- Evaluation of `read_terragrunt_config`, `merge(...)`, `dependency.<name>.outputs.<x>`, arithmetic, conditionals, or collection expressions.
- GraalVM native-image support for the new renderer (no `reflect-config.json` added in this change).
- JavaFX UI tests; the diagram is exercised manually for MVP.
- Per-project override of the Fidoo prefix table for resource-name synthesis.

## 3. Library choice

### Decision

**ELK (`org.eclipse.elk:org.eclipse.elk.core:0.11.0`, EPL-2.0)** for layout + **hand-rolled JavaFX renderer** for visuals.

### Rejected alternatives

- **`eckig/graph-editor` (EPL-1.0)** — JavaFX node-graph rendering, actively released (multiple releases/month), small (~5k LOC). Rejected because it has **no first-class compound/group nodes**: nested region/env containers would have to be modelled as oversized background "nodes" with cards stacked above them via z-order — a real ergonomic tax for our most-wanted layout. Kept on the shelf as a fallback if hand-rolled rendering balloons past ~1500 LOC.
- **Custom layered layout (no ELK)** — would force us to reimplement edge routing around containers and port-side optimisation. ELK has eaten ten years of these edge cases; reimplementing is wrong work for a side feature.
- **`brunomnsilva/JavaFXSmartGraph`** — MIT, maintained, but vertices are predefined shapes only; no arbitrary JavaFX content per node. Doesn't support cards or ports.
- **`mihosoft/VWorkflows-FX`**, **`FXDiagram`**, **`tesis-dynaware/graph-editor`**, **`JGraphX`** — all unmaintained or archived.
- **`nidi3/graphviz-java`** — Apache-2.0 but last commit early 2022; embeds JS engine to run dot; native-image hostile.
- **3D / WebGPU** — striking but no informational value over 2D for ~300 nodes.
- **JNI / Panama wrappers around native libs** — disproportionate complexity for a JavaFX MVP.

## 4. Architecture

### Package layout

Everything new sits under `solutions.onz.toolbox.gruntface`:

```
ui.graph
├── DiagramView           // JavaFX Region; the public API used by MainController
├── DiagramSkin           // builds & holds card/edge/container Nodes from a DiagramLayout
├── DiagramViewport       // pan/zoom Group wrapper
├── card
│   ├── UnitCard          // expandable card for a Unit
│   ├── ModuleCard
│   ├── ExternalCard
│   └── CommonCard
├── container
│   ├── RegionContainer   // outer compound container (gwc, weu, …)
│   └── EnvContainer      // inner compound container (prod, preprod, …)
├── edge
│   ├── EdgeNode          // javafx.scene.shape.Path subclass
│   └── EdgeRouter        // ELK ElkEdgeSection → JavaFX Path geometry
└── layout
    ├── ElkLayoutService  // pure: InfraGraph → DiagramLayout
    ├── DiagramLayout     // record: nodes-by-id, edges, container hierarchy
    └── HierarchyBuilder  // groups units by region/env from filesystem paths

name
├── InputEvaluator        // pure: (HCL expression AST, EvaluationContext) → Optional<String>
├── EvaluationContext     // immutable: common-include locals + the unit's literal inputs
├── ResourceNameSynth     // assembles "rg-fid-monitor-alerts-gwc-prod" from evaluated inputs
├── ResolvedName          // record: text + confidence (LITERAL, EVALUATED, FALLBACK)
└── AzureResourceNaming   // static map: AzureResource.id → conventional prefix ("st", "kv", …)
```

The `model` package gains one field: `Unit.resolvedName: Optional<ResolvedName>`. All other model records are unchanged.

### Data flow

```
InfraGraph
   │
   ├─→ HierarchyBuilder ──→ ContainerTree
   │
   ├─→ DiscoveryService (extended) ──→ ResolvedName per Unit
   │
   ▼
ContainerTree + InfraGraph
   │
   ▼
ElkLayoutService ──→ DiagramLayout (plain Java record)
   │
   ▼  (FX thread)
DiagramView.render(DiagramLayout)
   │
   ▼
DiagramSkin builds: containers, UnitCards, EdgeNodes
```

ELK types (`ElkNode`, `ElkPort`, `ElkEdge`) live entirely inside `ElkLayoutService`. The `DiagramLayout` record that crosses to the FX thread contains only plain Java values: node ids, coordinates, sizes, port positions, and edge bend-point lists.

### Threading

- `HierarchyBuilder`, `ElkLayoutService`, `InputEvaluator`, `ResourceNameSynth` are pure and run on the existing background `ExecutorService` from `DiscoveryService`.
- `DiagramView.render(DiagramLayout)` runs on the JavaFX Application thread.
- `Unit.resolvedName` is populated during discovery, before the `InfraGraph` reaches the UI.

## 5. Layout — ELK integration

### Compound graph construction

`ElkLayoutService` builds one root `ElkNode`. Children:

- One child `ElkNode` per **region** (e.g. `gwc`, `weu`) — name from the directory segment.
- Each region node contains one child `ElkNode` per **env** (`prod`, `preprod`, …).
- Each env node contains one child `ElkNode` per **unit**. Each unit node:
    - has fixed size matching the collapsed `UnitCard` (`240 × 96` px nominal),
    - has one `ElkPort` per `Dependency` on the WEST side, `portConstraints = FIXED_SIDE`, ordered by appearance in the source HCL,
    - has one synthetic `ElkPort` on the EAST side used as the source anchor for all outgoing `dependsOn` edges,
    - is labelled with the unit name.
- Units that don't fit the `<region>/<env>` pattern (e.g. files in `_common/` or above the region directory) live in a top-level "uncategorised" `ElkNode` sibling to the region nodes.
- `Module`, `ExternalModule`, and `CommonHcl` nodes — when shown — live at the root level alongside regions. They have no container; they're standalone reference nodes.

### ELK options (set on the root)

```
algorithm                  = layered
elk.direction              = DOWN
elk.layered.layering.strategy = NETWORK_SIMPLEX
elk.edgeRouting            = ORTHOGONAL
elk.hierarchyHandling      = INCLUDE_CHILDREN   // route edges across container boundaries
elk.spacing.nodeNode       = 32
elk.spacing.edgeNode       = 16
elk.padding                = "[top=28, left=12, bottom=12, right=12]"  // container padding
```

`elk.hierarchyHandling = INCLUDE_CHILDREN` is the option that makes ELK route a `dependsOn` edge from one env's unit to another env's unit cleanly around both containers. Without it, cross-container edges go through walls.

### Output

`RecursiveGraphLayoutEngine.layout(root, new BasicProgressMonitor())` mutates the `ElkNode` tree in place with absolute-coordinate geometry. `ElkLayoutService` then walks the tree once and emits:

```
record DiagramLayout(
    List<ContainerBox> containers,   // id, kind=REGION|ENV|UNCATEGORISED, label, bounds, parentId
    List<NodeBox>      nodes,        // id, kind=UNIT|MODULE|EXTERNAL|COMMON, bounds, ports, parentId
    List<EdgeShape>    edges         // id, kind=USES|DEPENDS_ON|INCLUDES, bendPoints, sourceId, targetId
) {}

record PortAnchor(String side /* W|E|N|S */, double x, double y) {}
```

Coordinates in `DiagramLayout` are in a single scene coordinate space — already flattened from ELK's per-parent local space. The FX side never sees ELK's hierarchical coords.

## 6. Rendering — JavaFX

### Cards

`UnitCard extends javafx.scene.layout.Region`, fixed width 240 px, height varies by `expanded` state.

```
┌──────────────────────────────────────┐
│ [icon]  vm-jumphost            [▶]   │
│         Virtual Machine               │
│         vm-fid-jumphost-gwc-prod      │
├──────────────────────────────────────┤   ← divider, only when expanded
│ ● dependency: subnet-mgmt            │
│ ● dependency: keyvault-secrets       │
│ ○ output: id                          │
└──────────────────────────────────────┘
```

- **Header (always visible):** Azure icon (32×32), unit folder name (bold, 13 px), `AzureResource.displayName` (regular, 11 px), resolved name (mono, 11 px, dim).
- **Expand caret:** a small triangle in the top-right. Mouse click or `Space`/`Enter` on the focused card toggles `expanded`. Caret rotates 90° via JavaFX `RotateTransition` (120 ms).
- **Port rows (visible only when expanded):** one row per `Dependency` (west, "in") and one row per `dependsOn` outgoing target (east, "out"). Clicking a port row pans the viewport to the unit on the other end of that edge and selects it.
- **Whole-card click** (anywhere outside the caret) → `selectionProperty.set(this)`. `MainController` already listens on the equivalent event today; we keep the contract.

CSS pseudo-classes set on the card root:

| Class | Effect |
| --- | --- |
| `:selected` | accent border, 2 px |
| `:cycle` | orange border, 2 px |
| `:error` | red border, 2 px |
| `:guessed` | opacity 0.85 |
| `:unknown` | opacity 0.70 |
| `:highlighted` | accent border on edges connected to the current selection |
| `:dimmed` | opacity 0.30 (other cards while one is selected) |

`ModuleCard`, `ExternalCard`, `CommonCard` are header-only and not expandable. They reuse the existing hexagon/tag visual language from the current `graph.css` palette, ported to AtlantaFX tokens.

### Icons

`UnitCard` loads its icon by name from `classpath:.../ui/graph/azure-icons/<name>.svg`. Two implementation options, decision deferred to the implementation plan:

1. **Option A — `ImageView` on the SVG bytes via `javafx.scene.image.Image`.** Works for the current icon set if JavaFX 25 reads them; if it doesn't, fall back to B.
2. **Option B — `SvgLoader` utility:** parse out the `<path d="…"/>` element(s) and instantiate `SVGPath` nodes. ~20 LOC. Robust to any SVG renderer quirks.

The icons themselves are unchanged from the existing `azure-icons/` directory.

### Edges

`EdgeNode extends javafx.scene.shape.Path`. Its `getElements()` is rebuilt from the `EdgeShape.bendPoints` list: one `MoveTo` to the first point, then `LineTo` for the rest. Arrowheads are rendered as a small second `Path` placed at the last segment, rotated to match its angle.

Styles per `EdgeShape.kind`, mapped to AtlantaFX CSS variables (the renderer reads `-color-fg-*` / `-color-accent-*` tokens, which AtlantaFX swaps automatically per active theme — no separate JSON palette like the current `graph.html` carries):

| Kind | Stroke (CSS variable) | Width | Dash | Arrow |
| --- | --- | --- | --- | --- |
| `USES` | `-color-fg-muted` | 1 px | `4 4` | none |
| `DEPENDS_ON` | `-color-accent-emphasis` | 2 px | solid | triangle, 8 px |
| `INCLUDES` | `-color-warning-emphasis` | 1 px | `2 2` | none |

If a target token doesn't exist in the active AtlantaFX theme (custom themes), the renderer falls back to `-fx-text-fill` / `-fx-accent` and logs a `WARNING` once per theme change.

### Containers

`RegionContainer` and `EnvContainer` are `Region` subclasses rendered as rounded panels. Geometry comes from the corresponding `ContainerBox`. A `Label` is pinned 8 px from the top-left showing the container name plus a `(<n> units)` badge. Containers are non-interactive (no selection, no expansion); their only purpose is visual grouping.

### Viewport / pan & zoom

`DiagramViewport` wraps the scene `Group` with a `Scale` and `Translate` transform.

| Input | Action |
| --- | --- |
| Drag on empty canvas | Pan |
| Plain scroll | Vertical pan (Shift = horizontal) |
| `Ctrl + scroll` | Zoom toward cursor, clamped `[0.25, 4.0]` |
| `Ctrl + 0` | Fit to viewport |
| `Ctrl + =` / `Ctrl + -` | Zoom in/out by 1.2× |
| Click empty canvas | Clear selection |

## 7. Resource-name resolution

### Goal

Show on every unit card a single human-readable resource name such as `kv-fid-mgmt-gwc-prod`, derived from the unit's literal inputs and the locals of the common include it references.

### `InputEvaluator` grammar (deliberately small)

Walks the hcl4j AST of an HCL expression. Returns `Optional<String>`.

1. **String literal** → its value.
2. **Local references** — `local.<name>` and `include.<include-name>.locals.<name>` → look up in `EvaluationContext.locals`. Empty if absent.
3. **Variable references** — `var.<name>` → look up in the unit's own `inputs` map. If the input itself is a literal, return its value. If it's another expression, recurse with a cycle-guard set.
4. **String interpolation** — `"prefix-${expr1}-${expr2}-suffix"` → split into segments, evaluate each interpolation segment, concatenate. If any segment is empty, the whole result is empty.
5. **Anything else** (`merge(...)`, ternary, arithmetic, lists, objects, `dependency.x.outputs.y`, …) → empty.

Hcl4j is expected to expose a parsed expression node. If — discovered during implementation — it flattens expressions to raw strings instead, the implementation plan adds a thin tokeniser fallback that handles cases 1, 2, 3, 4 only. This contingency is acknowledged here; the precise tokeniser design is deferred to the implementation plan.

### `ResourceNameSynth`

Two passes per `Unit`:

**Pass 1 — explicit `name` input.** Check the unit's `inputs` map for the first key, in this order, that exists: `name`, then `resource_name`, then `<kind>_name` where `<kind>` is the snake-case form of the inferred `AzureResource.id` (e.g. `storage_account_name`, `key_vault_name`). The `<kind>_name` lookup is skipped when `AzureResourceInferrer` returned `Confidence.UNKNOWN`. Evaluate the chosen expression with `InputEvaluator`. Success → `ResolvedName(text=…, confidence=LITERAL)` for plain string literals or `confidence=EVALUATED` for evaluated expressions. Empty evaluation → fall through to Pass 2.

**Pass 2 — Fidoo convention.** If `AzureResourceInferrer` matched the unit (`Confidence.MATCHED`), look up the conventional prefix in `AzureResourceNaming`:

```
storage-account      → "st"
key-vault            → "kv"
virtual-machine      → "vm"
log-analytics-…      → "log"
network-watcher      → "nw"
resource-group       → "rg"
private-dns-zone     → "pdns"
public-ip            → "pip"
virtual-network      → "vnet"
…
```

Then build `<prefix>[-<project_name_short>]-<purpose>-<location_short>-<environment>` from evaluated `inputs.purpose`, `inputs.location_short`, `inputs.environment`, and optionally `inputs.project_name_short`. Locals fall in via `include.common.locals.*` lookups. If any required piece evaluates to empty, the result is **null** and we fall through to:

**Pass 3 — fallback.** `ResolvedName(text=<unit folder name>, confidence=FALLBACK)`.

### Display

The resolved name appears as the third line of the `UnitCard` header.

- `LITERAL` / `EVALUATED` → plain mono, dim.
- `FALLBACK` → italic mono, prefixed with `?`.
- Hover → tooltip explains the source: `"from inputs.name"`, `"synthesised from inputs.purpose + location_short + environment"`, or `"fallback: dynamic expression"`.

### Multi-include policy

Only the **first** `include "<name>" { … }` block (in document order) with a resolved `CommonHcl` contributes locals to `EvaluationContext`. Multi-include merging is out of scope. If the user's repo relies on this, we add it later; for the current Fidoo layout it is not needed.

## 8. Removal plan

Single commit, no dual-stack period:

| Delete | Notes |
| --- | --- |
| `src/main/java/.../ui/graph/GraphView.java` | replaced by `DiagramView` |
| `src/main/java/.../ui/graph/GraphBridge.java` | replaced by direct callbacks on `DiagramView` |
| `src/main/resources/.../ui/graph/graph.html` | — |
| `src/main/resources/.../ui/graph/graph.css` | merged into `application.css` as `.diagram-*` selectors |
| `src/main/resources/.../ui/graph/cytoscape.min.js` | — |
| `src/main/resources/.../ui/graph/cytoscape-dagre.min.js` | — |
| `src/main/resources/.../ui/graph/dagre.min.js` | — |

In `MainController`: every `engine.executeScript(...)` call and every `JSObject` cast is replaced by a direct method call on `DiagramView` (`render(graph, options)`, `fit()`, `setOnSelect(handler)`, `setOnDeselect(handler)`).

The existing `GraphView.RenderOptions(boolean showModules, boolean showIncludes)` record carries over to `DiagramView.RenderOptions` with identical fields and semantics: when `showModules` is false, `Module` and `ExternalModule` nodes plus their `uses` edges are excluded from the `DiagramLayout` before ELK runs (not just hidden after — exclusion gives ELK fewer nodes to lay out, which keeps the resource grid compact). When `showIncludes` is false, `CommonHcl` nodes and `includes` edges are excluded similarly.

`azure-icons/*.svg` is unchanged — the icons stay where they are and are now loaded by `UnitCard` instead of being base64-embedded by `GraphView`.

## 9. Build / dependencies

`build.gradle.kts`:

```kotlin
dependencies {
    implementation("org.eclipse.elk:org.eclipse.elk.core:0.11.0")
    implementation("org.eclipse.elk:org.eclipse.elk.alg.layered:0.11.0")
    // existing dependencies unchanged
}

javafx {
    version = "25.0.2"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.swing")  // drop javafx.web
}
```

`module-info.java`:

```java
requires org.eclipse.elk.core;
requires org.eclipse.elk.alg.layered;
// remove: requires javafx.web;
```

EMF (`org.eclipse.emf.ecore.xmi`) comes in transitively with ELK. It's pure-Java, runs headlessly, adds ~3 MB to the distribution. No GraalVM native-image reflection config in this change.

## 10. Testing

Headless JUnit 5 tests under `src/test/java`:

- `layout.HierarchyBuilderTest` — feed unit paths like `platform/management/gwc/prod/key-vault/terragrunt.hcl`; assert region=`gwc`, env=`prod`. Edge case: `_common/` and root-level units → `uncategorised`.
- `layout.ElkLayoutServiceTest` — feed a hand-built `InfraGraph` (no IO); assert `DiagramLayout` contains the expected container hierarchy and that node bounding boxes do not overlap.
- `name.InputEvaluatorTest` — literal-only inputs, locals + interpolation, unsupported expression fallback.
- `name.ResourceNameSynthTest` — `Pass 1`/`Pass 2`/`Pass 3` cases with confidence assertions.
- `edge.EdgeRouterTest` — synthetic `EdgeShape` with known bend points; assert the produced `Path` elements.

UI classes (`DiagramView`, `UnitCard`, `DiagramViewport`) stay manual-test-only, consistent with the existing MVP testing posture (no TestFX dependency added).

## 11. Implementation sequencing (preview)

Final sequencing is the writing-plans skill's job. The natural decomposition is:

1. `HierarchyBuilder` + `ElkLayoutService` + `DiagramLayout` (headless, parallel-startable).
2. `InputEvaluator` + `ResourceNameSynth` + `Unit.resolvedName` (headless, parallel-startable).
3. `DiagramViewport` + `EdgeNode` + `EdgeRouter` against a fixture layout. Visual check.
4. `UnitCard` / `ModuleCard` / `ExternalCard` / `CommonCard` (static, no expansion). Visual check.
5. `DiagramView` ties everything together; cut over `MainController`. Visible swap-in.
6. Expansion + interaction (caret, selection, dimming, port-row navigation).
7. Polish: theme tokens, fit/zoom shortcuts, container labels, empty state.
8. Cleanup: delete old WebView files; drop `javafx.web` module; update `2026-06-04-terragrunt-visualizer-design.md` cross-reference.

## 12. Open questions

None blocking. Items deliberately deferred:

- Hcl4j expression-AST availability — if it flattens to strings, the implementation plan adds a tiny tokeniser. The plan must verify this in stage 2.
- SVG icon loading path (`ImageView` vs. small `SvgLoader`) — decided in the implementation plan, both options are ~20 LOC.
- Per-output edge anchors (currently one synthetic east port per unit).
- Multi-include locals merging.
- GraalVM native-image reflect-config for EMF.
- A per-project override file for the Fidoo prefix table in `AzureResourceNaming`.
