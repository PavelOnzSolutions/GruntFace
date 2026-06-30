# GruntFace — Dependency Resources in Create Wizard + Inspector (design)

**Date:** 2026-06-16
**Status:** Approved (brainstorming)
**Related:**
- `2026-06-15-add-resource-include-design.md` (Create wizard MVP — parent spec; this extends it)
- `2026-06-04-terragrunt-visualizer-design.md` (read/edit MVP)

## 1. Purpose

The Create wizard currently emits `include`, `terraform`, and `inputs` blocks only. Real Terragrunt units routinely declare `dependency "<name>" { config_path = "..." mock_outputs = {...} }` blocks and reference their outputs from `inputs` (e.g. `resource_group_name = dependency.cae.outputs.resource_group_name`). Today users would have to hand-edit the file after creation to add either side of this.

This change lets users:

- Select sibling units as dependencies when creating a new resource.
- Reference any declared dependency's outputs from an input field via a `Value | Ref` toggle.
- See the same `Value | Ref` toggle on input rows in the Inspector when editing existing units.

Goal: keep the change small. No model surgery, no parser changes, no rewrite of existing `dependency` blocks on disk. The on-disk representation of a reference is plain `RawHcl` (`dependency.<name>.<suffix>`), which the existing renderer already emits correctly.

## 2. Scope

### In scope

- A new **Step 3 "Dependencies"** in `NewResourceWizard` for the two Resource flows (`ResourceFromInclude`, `ResourceFromModule`). Skipped for `IncludeFromModule`.
- `Step3Inputs` becomes `Step4Inputs`; each input row gains a `Value | Ref` segmented toggle.
- A `DependencyReference` helper (regex parse / build) so references round-trip between `RawHcl` strings and a structured `(depName, outputsPath, wrappedInList)` triple.
- `HclEmitter` extension: emit one `dependency "<name>" { config_path = "..." mock_outputs = {} }` block per selected dep, inserted between the `terraform { source = "..." }` block and the `inputs = { ... }` block.
- Inspector's input editor (`InputsEditor.makeRow`) wrapped in the same `Value | Ref` toggle, so existing units with `dependency.<x>.outputs.<y>` inputs are rendered in `Ref` mode automatically.
- Shared widget `ui.inspector.DependencyRefRow` used by both the wizard's Step 4 and the Inspector.

### Out of scope

- Editing, adding, removing, or renaming `dependency` blocks from the Inspector. The Inspector's existing read-only Dependencies list stays as-is.
- Round-tripping user-authored `mock_outputs` content. We always emit `mock_outputs = {}` on create; we never rewrite existing dep blocks, so files with rich mocks remain untouched.
- User-directory override for dependency candidates. Sibling-only for now; cross-env / nested deps deferred.
- Output-key autocomplete in the `outputs.<suffix>` field.
- Showing the Dependencies step for `IncludeFromModule`. Includes are unit-agnostic; per-include dep blocks would be unusual.
- Model changes. `Dependency` record stays `(name, configPath, resolvedUnitPath)`. `InputValue` sealed hierarchy is unchanged.

## 3. Architecture

### Package layout (delta only)

Under `solutions.onz.toolbox.gruntface`:

```
create
├── DependencyDecl              // NEW record (name, configPath)
├── DependencyReference         // NEW: parse(InputValue) / build(name, suffix, wrap)
├── CreateRequest               // EXTENDED: + LinkedHashMap<String,DependencyDecl> dependencies
├── HclEmitter                  // EXTENDED: emit dependency blocks for Resource flows
└── (rest unchanged)

ui.create
├── Step3Dependencies           // NEW FXML + controller
├── Step4Inputs                 // RENAMED from Step3Inputs; rows wrapped in Value|Ref toggle
└── NewResourceWizard           // EXTENDED: 4 steps; new cache for step3 selection

ui.inspector
├── DependencyRefRow            // NEW shared widget; implements ValueEditor
└── (rest unchanged)
```

### Data flow

```
Wizard Step 3 Dependencies
        │
        │ user picks siblings + names
        ▼
LinkedHashMap<String,DependencyDecl>  ─────────┐
                                                │
Wizard Step 4 Inputs                            │
        │                                       │
        │ for each Ref-mode row:                │
        │   DependencyReference.build(name,    │
        │     outputsPath, wrap)                │
        │   → InputValue.RawHcl(...)           │
        ▼                                       │
LinkedHashMap<String,InputValue>                │
        │                                       │
        ▼                                       │
   CreateRequest ◄────────────────────────────  ┘
        │
        │ HclEmitter.emit
        ▼
   File body: include + terraform + dependency blocks + inputs
```

On Inspector load:
```
Unit.inputs() → for each entry:
    DependencyReference.parse(value)
        ├ present  → render as Ref row (pre-filled)
        └ absent   → render with existing ValueEditor
```

## 4. Model (no changes)

The on-disk representation of a dependency reference is `InputValue.RawHcl`. Examples:
- `dependency.cae.outputs.resource_group_name`
- `[dependency.uami.outputs.user_assigned_identity.id]` (list-wrapped)

`InputsRenderer` already emits `RawHcl` unquoted, so no rendering changes are needed at the inputs layer.

`DependencyReference.parse` operates only on `InputValue.RawHcl`. `StringValue("dependency.x.outputs.y")` is treated as a literal quoted string and **not** classified as a reference — this matches what the user gets from the `Value` editor.

### `DependencyDecl`

```java
public record DependencyDecl(String name, String configPath) {}
```

- `name`: HCL identifier (`[A-Za-z_]\w*`), unique within the request.
- `configPath`: relative path from the new resource's directory to the dep's directory (e.g. `"../container-app-environment"`). Pre-computed at wizard time.

### `DependencyReference`

```java
public final class DependencyReference {
    public record Match(String depName, String outputsPath, boolean wrappedInList) {}

    public static Optional<Match> parse(InputValue v) { ... }
    public static InputValue.RawHcl build(String depName, String outputsPath, boolean wrap) { ... }
}
```

Regex (anchored, applied to the trimmed `RawHcl.hcl()`):

```
^(\s*\[\s*)?dependency\.([A-Za-z_]\w*)\.([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*)(\s*\]\s*)?$
```

The suffix group is constrained to chained HCL identifiers (`outputs.id`, `outputs.user_assigned_identity.id`) — this prevents accidental matches when the input is `dependency.cae.outputs.id]` (a literal trailing `]` would otherwise be swallowed by a loose `(.+?)`).

A match's `wrappedInList` is `true` iff both the leading `[` and trailing `]` groups are present. A leading `[` with no trailing `]` (or vice versa) is treated as **not** a reference (parser returns empty) — defensive against half-formed text. Implementation: `if (leftBracket.present() != rightBracket.present()) return Optional.empty();`.

`build(name, "outputs.id", false)` → `RawHcl("dependency.{name}.outputs.id")`.
`build(name, "outputs.id", true)` → `RawHcl("[dependency.{name}.outputs.id]")`.

### `CreateRequest`

```java
public record CreateRequest(
    WizardMode mode,
    ResourceTemplate template,
    Path parentDir,
    String folderName,
    LinkedHashMap<String, DependencyDecl> dependencies,   // NEW; empty for IncludeFromModule
    LinkedHashMap<String, InputValue> inputs
) {
    public CreateRequest {
        dependencies = new LinkedHashMap<>(dependencies);
        inputs = new LinkedHashMap<>(inputs);
    }
    // defensive copies on read, same as inputs()
}
```

The map key is the dep name (matches the HCL block label). LinkedHashMap preserves emission order.

## 5. Wizard

### Step header

```
┌─ Template ──── Location ──── Dependencies ──── Inputs ─┐
```

For `IncludeFromModule`, the third cell is dimmed and skipped: `Next` from Location goes straight to Inputs.

### Step 3 — Dependencies (Resource flows only)

```
┌─ Available sibling units in <parentDir-of-target> ──────────────┐
│ Filter: [_______________]                                       │
│                                                                 │
│ ┌─────────────────────────────────────────────────────────────┐ │
│ │ ☑ container-app-environment   name: [container_app_env____] │ │
│ │ ☑ managed-identity-apps       name: [managed_identity_apps] │ │
│ │ ☑ key-vault                   name: [key_vault____________] │ │
│ │ ☐ storage-account             name: (disabled until ticked) │ │
│ │ ☐ application-insights        name: (disabled until ticked) │ │
│ └─────────────────────────────────────────────────────────────┘ │
│                                                                 │
│ Status: 3 selected · all names unique                           │
└─────────────────────────────────────────────────────────────────┘
```

**Population:** walk `graph.units()`, keep those whose `unit.file().getParent().toAbsolutePath().normalize()` equals `request.parentDir.toAbsolutePath().normalize()`. Exclude the future location of the unit being created (the resolved target's parent is `request.parentDir`, and there is no unit at `<parentDir>/<folderName>` yet). Sort by folder name.

**Default name:** sibling folder name with `-` replaced by `_`, lowercased, stripped of any character outside `[A-Za-z0-9_]`. Folder names are unique within a directory by filesystem invariant, so default names are collision-free.

**Validation:**
- Name field per ticked row: matches `[A-Za-z_]\w*`. Invalid → red outline, status-bar message, `Next` disabled.
- Name uniqueness across all ticked rows. Duplicate names → both rows red, status-bar message ("Duplicate dependency name: `key_vault`"), `Next` disabled.
- Selecting zero dependencies is valid (deps are optional). `Next` always enabled when ticked rows have valid + unique names.

**Empty state** (no siblings found): pane shows "No sibling units yet — skip this step and add dependencies after creating peers." `Next` is enabled; selection is empty.

**Re-entering via Back:** ticked set and edited names are cached in a `step3depsCache` field on `NewResourceWizard` controller, parallel to the existing `step3Cache` (renamed to `step4Cache`).

### Step 4 — Inputs (was Step 3)

Each row gains a `Value | Ref` segmented control on the right of the row label:

```
resource_group_name        ( Value | ● Ref )   dep [container_app_env ▾]  outputs.[resource_group_name________]   ☐ wrap in [ ]
user_assigned_identity_ids ( Value | ● Ref )   dep [managed_identity_apps ▾]  outputs.[user_assigned_identity.id]   ☑ wrap in [ ]
purpose                    ( ● Value | Ref )   [powerauth__________]
```

- **`Value` mode**: existing wizard widget (TextField / TextArea / CheckBox, as built by `Step3Inputs.buildEditor` today). No behavioural change.
- **`Ref` mode**: dep dropdown + non-editable `outputs.` prefix + suffix TextField + `wrap in [ ]` checkbox.
- **Dep dropdown options**: dep names selected in Step 3, in selection order. If Step 3's selection is empty, the `Ref` toggle is disabled with tooltip "No dependencies declared in Step 3 — go back and add some."
- **Suffix validation**: non-empty, matches `[A-Za-z_]\w*(\.[A-Za-z_]\w*)*` (e.g. `outputs.id`, `outputs.user_assigned_identity.id`). Invalid → red outline, row invalid, `Next` disabled.
- **`wrap in [ ]`** is a single checkbox; multi-element lists are out of scope for this toggle (user uses `Value` mode + TextArea raw HCL for that).

**Re-entering Step 4 via Back:** for each row whose cached value is a `RawHcl` matching `DependencyReference.parse`, the row starts in `Ref` mode pre-filled with the parsed `(depName, outputsPath, wrappedInList)`. Otherwise it starts in `Value` mode (unchanged behaviour).

**Schema interaction**: the existing `SchemaDeriver` and conventional-inputs handling is unaffected. The `Value | Ref` toggle is a UI affordance on top of any row, regardless of its schema group.

### Wizard controller wiring (`NewResourceWizard`)

- New field: `Step3Dependencies step3deps` + `Parent step3depsRoot`.
- Existing `step3` field becomes `step4` (rename `Step3Inputs` → `Step4Inputs`).
- `step3Cache` becomes `step4Cache`. New `step3depsCache : SelectionState` field.
- `currentStep` ranges over 1–4. Header active styling extended.
- `Next` from Step 2 routes to Step 3 (deps) for Resource flows, or Step 4 (inputs) for `IncludeFromModule`. Symmetric `Back` from Step 4.
- `onNext` for step 3 deps: validates, stashes `LinkedHashMap<String,DependencyDecl>` to a controller field, then calls `step4.init(... , dependencies, ...)`.
- `CreateRequest` constructor call in step 4's `onNext` passes the dep map; empty map for `IncludeFromModule`.

## 6. Entry points

Unchanged. `WizardPrefill` is unchanged. The new step appears in-line for the two Resource flows.

## 7. HCL emission

### Insertion point

Dependency blocks are emitted **after** the `terraform { source = "..." }` block and **before** the `inputs = { ... }` block. One blank line between each dep block and the next, and one blank line between the last dep block and `inputs`.

### Helper

```java
private static void appendDependencies(StringBuilder sb,
                                        LinkedHashMap<String, DependencyDecl> deps) {
    for (DependencyDecl d : deps.values()) {
        sb.append("dependency \"").append(d.name()).append("\" {\n");
        sb.append("  config_path  = \"").append(escapeForDoubleQuotedHcl(d.configPath())).append("\"\n");
        sb.append("  mock_outputs = {}\n");
        sb.append("}\n\n");
    }
}
```

Two-space indent. `config_path` and `mock_outputs` keys aligned via fixed padding (key column width = 12). Order is the request's `LinkedHashMap` order.

### Resource from Include (example)

```hcl
include "root" {
  path = find_in_parent_folders("root.hcl")
}

include "common" {
  path   = "${dirname(find_in_parent_folders("root.hcl"))}/_common/container-app.hcl"
  expose = true
}

terraform {
  source = include.common.locals.base_source_url
}

dependency "container_app_env" {
  config_path  = "../container-app-environment"
  mock_outputs = {}
}

dependency "managed_identity_apps" {
  config_path  = "../managed-identity-apps"
  mock_outputs = {}
}

inputs = {
  purpose                      = "powerauth"
  resource_group_name          = dependency.container_app_env.outputs.resource_group_name
  user_assigned_identity_ids   = [dependency.managed_identity_apps.outputs.user_assigned_identity.id]
}
```

### Resource from Module (local / external)

Same insertion point: after `terraform { source = "..." }`, before `inputs = { ... }`. `IncludeFromModule` is unchanged (`dependencies` is always empty for that mode; emitter asserts).

### Empty dependencies map

If `dependencies` is empty, no `dependency` blocks are emitted and output is byte-identical to current behaviour (regression protected by existing emitter fixtures).

### Edge cases

- **Duplicate dep names**: prevented by Step 3 validation. Emitter defensively asserts `dependencies.keySet().size() == dependencies.size()` (LinkedHashMap by construction does, but the check guards against future refactors).
- **Empty `configPath`**: not possible by construction (sibling rows always resolve to `../<folder>`). Emitter throws `IllegalArgumentException` if it sees one.

## 8. Inspector

### Dependencies section

Unchanged. Existing read-only `ListView` of `name → config_path` stays exactly as it is (`InspectorController.showUnit` lines 171–182). Out of scope: editing.

### Inputs section

`InspectorController.InputsEditor.makeRow` wraps each row's `ValueEditor` in a `DependencyRefRow` composite that owns the `Value | Ref` toggle.

**First-render detection:**
```java
Optional<DependencyReference.Match> ref = DependencyReference.parse(value);
ValueEditor editor;
if (ref.isPresent() && !unit.dependencies().isEmpty()) {
    editor = new DependencyRefRow(unit.dependencies(), ref.get());
} else {
    editor = <existing widget chain>;
}
```

If the value parses as a reference but the unit has no `dependency` blocks declared (orphan reference — possible in malformed files), fall back to the existing `RawHcl` text-area editor and prepend a status hint "Orphan dependency reference (no matching `dependency` block in this file)."

**Dep dropdown options (Inspector):** the unit's existing `dependencies` list, in declaration order. The Inspector does **not** offer to add new deps — switching to `Ref` is disabled with tooltip "This unit has no `dependency` blocks — add one in your editor and reload." if the list is empty.

**Save flow:** unchanged. The composite editor's `read(Variable, InputValue)` delegates to whichever sub-editor is active. `Ref` mode returns `DependencyReference.build(name, suffix, wrap)`; `Value` mode returns what the existing widget produced. `InputsRenderer` emits both correctly.

### Shared widget `ui.inspector.DependencyRefRow`

Implements `ValueEditor`. Owns:

- A small toggle row at the top (segmented control: `Value | Ref`).
- Two child editor regions:
  - `valueEditor: ValueEditor` (delegate; the standard widget the row would otherwise have used).
  - `refRegion: HBox` with dep dropdown + `outputs.` label + suffix TextField + wrap checkbox.
- A `read(Variable, InputValue)` implementation that returns from whichever region is currently active.

Used by:
- Wizard Step 4 row builder (constructs `DependencyRefRow(decls, /*initialMatch*/ null)` for new rows; passes the parsed `Match` when re-entering from Back).
- Inspector's `InputsEditor.makeRow`.

## 9. Error handling

| Condition | Behaviour |
| --- | --- |
| Duplicate dependency name in Step 3 | Both rows red-outlined; status-bar message; `Next` disabled. |
| Empty / invalid dependency name | Row red-outlined; status-bar message; `Next` disabled. |
| Empty `outputs.<suffix>` in a `Ref` row | Row invalid; `Next` disabled. |
| `Ref` toggle clicked when Step 3 selection is empty | Toggle disabled with tooltip. |
| Wizard's chosen dep folder disappears between Step 3 and commit | `CreateService.commit` proceeds (we don't re-validate dep targets); resulting file references a non-existent `config_path` — Terragrunt will surface this at `plan` time. Same posture as existing edit-flow. |
| Inspector loads a unit with `dependency.<x>.outputs.<y>` where `<x>` isn't in `unit.dependencies()` | Falls back to raw `RawHcl` text-area editor with a status hint (see "Inputs section"). |
| Inspector loads a unit with no `dependency` blocks at all | `Ref` toggle disabled with tooltip on every row. Existing units load and save as today. |
| Existing dep blocks with `mock_outputs` body | Not touched. We never rewrite dep blocks; the existing save flow rewrites `inputs` only. |

## 10. Testing

Headless JUnit 5 under `src/test/java`:

- **`DependencyReferenceTest`**
  - `parse("dependency.cae.outputs.id")` → `Match("cae", "outputs.id", false)`.
  - `parse("[dependency.uami.outputs.user_assigned_identity.id]")` → `Match("uami", "outputs.user_assigned_identity.id", true)`.
  - `parse("dependency.cae.")` → empty.
  - `parse("dependency.cae outputs.id")` → empty.
  - `parse("[dependency.cae.outputs.id")` → empty (mismatched bracket).
  - `parse("dependency.cae.outputs.id]")` → empty.
  - `parse(new StringValue("dependency.cae.outputs.id"))` → empty (only `RawHcl` matches).
  - `build("cae", "outputs.id", false).hcl()` → `"dependency.cae.outputs.id"`.
  - `build("cae", "outputs.id", true).hcl()` → `"[dependency.cae.outputs.id]"`.
  - Round-trip matrix: `parse(build(name, suffix, wrap))` equals the original triple.

- **`HclEmitterTest`** — two new fixtures:
  - `resource-from-include-with-deps`: three dep blocks + three references in inputs (one wrapped in `[]`). Asserts byte-exact equality with a fixture file.
  - `resource-from-module-with-deps`: two dep blocks + one reference. Byte-exact.
  - Existing five fixtures unchanged.
  - Empty `dependencies` map: output identical to current (regression).

- **`CreateServiceTest`** — extend happy path with two `DependencyDecl`s; write + parse round-trip + `InfraGraphPatcher.addUnit` produces the expected `dependsOn` edges.

- **`InfraGraphPatcherTest`** — small fixture: base graph with three sibling units, new unit with two `dependency` blocks referencing two of them; assert `dependsOn` edges count and direction.

- **`Step3DependenciesTest`** — pure-logic test for sibling discovery + default-name sanitisation + duplicate detection. UI part is manual.

**Visual checks (manual):**

1. Wizard from toolbar → New Resource (extend Include) → `container-app` → location `applications/payments/gwc/prod/container-app-powerauth/`. Step 3 lists siblings. Tick three; verify default names. Step 4: switch two rows to `Ref`. Preview matches the example in Section 7. Commit. New node selected on diagram with `dependsOn` edges.
2. Inspector on the just-created file: Dependencies section shows the three deps. `resource_group_name` row opens in `Ref` mode pre-filled. Toggle to `Value`, edit, save, reload — value persisted as a quoted string.
3. Inspector on a hand-written unit with a `dependency.x.outputs.y` input: opens in `Ref` mode without a wizard round-trip.
4. Empty-siblings case: create resource in a directory with no peer units. Step 3 shows skip hint; Step 4 disables `Ref` toggle with tooltip.

## 11. Build / dependencies

No new external dependencies. `module-info.java` is unchanged.

## 12. Implementation sequencing (preview)

Final sequencing is the writing-plans skill's job. Natural decomposition:

1. `create.DependencyDecl` + `create.DependencyReference` + `DependencyReferenceTest`.
2. `create.CreateRequest.dependencies` field + `create.HclEmitter` extension + two new emitter fixtures + emitter tests.
3. `create.CreateService` regression test (deps) + `InfraGraphPatcher` regression test.
4. `ui.inspector.DependencyRefRow` shared widget.
5. Wizard rename `Step3Inputs` → `Step4Inputs`; new `Step3Dependencies` controller + FXML; update `NewResourceWizard` to 4-step flow with new cache field.
6. Step 4 rows wrap existing editors in `DependencyRefRow`.
7. `InspectorController.InputsEditor.makeRow` wraps existing editors in `DependencyRefRow`.
8. Visual passes 1–4. Polish copy.

## 13. Open questions

None blocking. Items deliberately deferred (see Out of scope):

- Editing / adding / removing dep blocks from the Inspector.
- Round-tripping user-authored `mock_outputs`.
- Cross-environment / non-sibling dep candidates (user-dir override).
- Output-key autocomplete.
- Multi-element list references (Ref toggle wraps a single element only).
