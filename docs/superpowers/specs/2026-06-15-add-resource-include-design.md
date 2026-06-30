# GruntFace — Add Resource / Add Include (design)

**Date:** 2026-06-15
**Status:** Approved (brainstorming)
**Related:**
- `2026-06-04-terragrunt-visualizer-design.md` (read/edit MVP)
- `2026-06-12-diagram-renderer-design.md` (pure-JavaFX diagram)

## 1. Purpose

Today GruntFace is read-and-edit: it discovers existing Terragrunt units and Includes, shows them on a diagram, and lets the user edit each unit's `inputs` block. There is no way to *create* new files from inside the app.

This change adds two creation flows:

- **New Resource** — a new `terragrunt.hcl` (a `Unit`) that either extends an existing `_common/*.hcl` (an Include) or points directly at a Terraform module (local or external).
- **New Include** — a new `_common/<name>.hcl` (a `CommonHcl`) that wraps a Terraform module so multiple resources can extend it.

Goal: keep the create flow consistent with the existing edit flow — schema-driven form, diff preview before write, atomic-move commit, in-place graph patch (no full reload).

## 2. Scope

### In scope

- A modal three-step wizard `NewResourceWizard` reachable from four entry points (toolbar, File menu, right-click on diagram canvas, right-click on a Module/Include/External node, "Use as template…" button in the Inspector).
- A new `create` package holding the headless template/schema/generator logic (unit-testable without a display).
- A new `ui.create` package holding the wizard FXML/controllers.
- An `HclEmitter` that produces canonical, byte-stable file content for the three modes (Resource-from-Include, Resource-from-Module, Include-from-Module).
- A `CreateService` that plans + commits new files via the same `tmp + ATOMIC_MOVE` pattern the save flow uses.
- A `parseSingleUnit` / `parseSingleCommon` extraction from `DiscoveryService` so post-create updates patch the graph rather than re-walking the project.
- A diff preview before write, reusing the existing `DiffDialog` rendering with an empty "before" side.

### Out of scope

- Multi-file create transactions (e.g. "create an Include and three Resources extending it" in one go). Each create is one file.
- Editing the just-created file in the wizard *after* it's been written — once committed, further changes go through the existing Inspector flow.
- Deleting or renaming existing Resources / Includes. (Future work — same package will likely host it.)
- Cross-project import of templates from outside the loaded Terragrunt root.
- Schema introspection of remote modules. External modules contribute only their `sourceRef`; users fill the inputs schema by hand when extending them.
- TestFX coverage of the wizard. Wizard is manual-test-only, consistent with current UI testing posture.
- Per-project override of the Fidoo conventional-input list. The four conventional inputs (`purpose`, `location_short`, `environment`, optional `project_name_short`) are hard-coded for now, mirroring `ResourceNameSynth`.

## 3. Architecture

### Package layout

Under the existing `solutions.onz.toolbox.gruntface` module:

```
create
├── ResourceTemplate          // sealed: IncludeTemplate | LocalModuleTemplate | ExternalModuleTemplate
├── TemplateCatalog           // pure: InfraGraph → List<ResourceTemplate>, filtered per WizardMode
├── TemplateSchema            // record: name, type, description, default, required, group (CONVENTIONAL|TEMPLATE|EXTRA)
├── SchemaDeriver             // ResourceTemplate + WizardMode → List<TemplateSchema>
├── LocationSuggester         // (Path target, Path tgRoot) → Optional<{region, env, purpose}>
├── HclEmitter                // (CreateRequest, ResolvedLocation) → String  (file body)
├── CreateRequest             // immutable: mode, template, parentDir, folderName, inputs (LinkedHashMap)
├── CreatePlan                // immutable: targetFile, contentToWrite, mkdirs:List<Path>, conflict:Optional<Path>
└── CreateService             // CreateRequest → CreatePlan; commits a plan atomically

ui.create
├── NewResourceWizard         // Stage subclass, owns the 3-step pane stack + Next/Back/Cancel/Create
├── Step1TemplatePicker       // mode radio, searchable list, preview pane
├── Step2Location             // parent-dir TreeView, folder-name TextField, live preview labels
├── Step3Inputs               // schema-driven form (reuses ValueEditor widgets from ui.inspector)
└── CreatePreviewDialog       // wraps DiffDialog with an empty "before" side
```

### Why a new `create` package

`discovery` reads the world; `hcl` (the existing `Hcl4jHclService` + `InputsRenderer`) parses and surgically rewrites existing files. Creating new files is a third concern: directory creation, conflict checks, file-naming rules, post-write graph patching. Bundling those into either of the existing packages would muddy them. The new package depends on `model`, `hcl`, `discovery`, and `name`; it is *not* depended on by any of them.

### Data flow

```
NewResourceWizard
        │ collects
        ▼
   CreateRequest ─────────────┐
        │                     │
        │ CreateService.plan()│ (pure, no IO)
        ▼                     │
   CreatePlan                 │
        │                     │
        │ CreatePreviewDialog │
        ▼                     │
   user confirms ─────────────┘
        │
        │ CreateService.commit()  (FX io executor)
        ▼
   write file → DiscoveryService.parseSingleUnit / parseSingleCommon
        │
        ▼
   InfraGraphPatcher.add(...)  →  MainController.rerender() + select new node
```

### Threading

- `TemplateCatalog`, `SchemaDeriver`, `LocationSuggester`, `HclEmitter`, `CreateService.plan` are pure and run on the FX thread (they are fast — milliseconds — and called from interactive controls).
- `CreateService.commit` runs on the existing `MainController.io` single-thread executor so a slow disk does not freeze the UI.
- Wizard widgets live on the FX Application thread, same as the rest of the app.

## 4. Wizard modes

```
sealed interface WizardMode {
    record ResourceFromInclude() implements WizardMode {}
    record ResourceFromModule()  implements WizardMode {}
    record IncludeFromModule()   implements WizardMode {}
}
```

The mode determines which template kinds appear in Step 1 and which file shape `HclEmitter` produces:

| Mode | Template list | Output file | Output kind |
| --- | --- | --- | --- |
| `ResourceFromInclude` | All `CommonHcl` instances | `<parent>/<folder>/terragrunt.hcl` | `Unit` |
| `ResourceFromModule` | Local `Module`s + `ExternalModule`s | `<parent>/<folder>/terragrunt.hcl` | `Unit` |
| `IncludeFromModule` | Local `Module`s + `ExternalModule`s | `<parent>/<folder>.hcl` | `CommonHcl` |

## 5. Entry points

All four entry points construct a `WizardPrefill` and call `NewResourceWizard.open(prefill)`:

```
record WizardPrefill(
    WizardMode mode,                         // chosen up front (toolbar/menu/inspector) or null (canvas right-click)
    Optional<ResourceTemplate> template,     // pre-selected when entry was on a node
    Optional<Path> parentDir                 // pre-filled when entry was inside a container
) {}
```

| Entry | Mode | Template | Parent dir |
| --- | --- | --- | --- |
| Toolbar `+ New…` split-button → `New Resource (extend Include)` | `ResourceFromInclude` | empty | Terragrunt root |
| Toolbar → `New Resource (extend Module)` | `ResourceFromModule` | empty | Terragrunt root |
| Toolbar → `New Include` | `IncludeFromModule` | empty | `<root>/_common` if it exists else Terragrunt root |
| `File` menu — three matching items | same as toolbar | empty | same |
| Right-click empty canvas → "New Resource here (extend Include)…" / "…(extend Module)…" / "New Include here…" | chosen item | empty | the container the cursor was over (region or env), else Terragrunt root |
| Right-click `Include` node → "Create resource from this…" | `ResourceFromInclude` | the include | Terragrunt root |
| Right-click `Module` / `External` node → "Create resource from this…" | `ResourceFromModule` | the module | Terragrunt root |
| Right-click `Module` / `External` node → "Create Include from this…" | `IncludeFromModule` | the module | `<root>/_common` if it exists else Terragrunt root |
| Inspector header button "Use as template…" (visible when an Include or Module is selected) | inferred from selected node kind (Module/External → Resource-from-Module; Include → Resource-from-Include) | the selected node | Terragrunt root |

The toolbar split-button sits left of the existing controls. The `File` menu mirrors the same three primary actions.

## 6. Wizard UX

### Chrome

Modal `Stage`, 720 × 560, owner = main window, centred. Header strip across the top shows three step labels (`Template` · `Location` · `Inputs`) with the active one highlighted using AtlantaFX accent tokens. Footer: `Cancel` on the left; `Back`, then `Next` (or `Preview & Create` on the last step) on the right.

Keyboard:

| Key | Action |
| --- | --- |
| `ESC` | Cancel |
| `Enter` | Next (on the last step, equivalent to Preview & Create) |
| `Ctrl+Enter` | Preview & Create (only on the last step) |
| `Tab` / `Shift+Tab` | Standard focus traversal |

`Next` is disabled until the current step validates. The footer left-side has a small status label that explains *why* `Next` is disabled when it is (e.g. "Choose a template", "Folder name contains invalid characters").

### Step 1 — Template

Top: mode radio group (three options, the first two are Resource flavours, the third is Include). Switching the mode rebuilds the list. If the wizard was opened with a pre-selected mode (from menu/right-click/Inspector), the radios are still editable — the user can change their mind without leaving the wizard.

Body: a split pane.

```
┌─ Mode ─────────────────────────────────────────────┐
│ ◉ Resource from Include   ◯ Resource from Module   │
│ ◯ Include from Module                              │
└────────────────────────────────────────────────────┘
┌─ Filter: [____________________]  ─────────────────────┐
│ ┌─ Includes (8) ───────┐ ┌─ Preview ─────────────────┐│
│ │ • key-vault   [kv]   │ │ Name: key-vault.hcl       ││
│ │ • storage-account[st]│ │ File: _common/key-vault.hcl│
│ │ • virtual-machine[vm]│ │ Source: git@bitbucket…/kv │
│ │ • …                  │ │ Locals: base_source_url,…│
│ └──────────────────────┘ │ Conventional inputs:      ││
│                          │   purpose, location_short,││
│                          │   environment             ││
│                          └───────────────────────────┘│
└──────────────────────────────────────────────────────┘
```

- The list is grouped (Includes / Local Modules / External Modules) — only groups relevant to the active mode appear. Each row shows the inferred Azure-resource tag in square brackets, sourced from the existing `AzureResourceInferrer` (e.g. `[kv]`, `[st]`).
- The filter does case-insensitive substring matching against name + tag.
- Preview pane is read-only:
  - For Includes: file path, `base_source_url`, listed locals, observed conventional inputs (derived from existing units that already extend it), and a "Used by *N* existing units" footer.
  - For local Modules: directory path, declared variables (name, type, required/optional), and "Used by *N* existing units".
  - For external Modules: raw `sourceRef`, and "Used by *N* existing units" — no variables (we cannot introspect).
- Validates when one row is selected.

### Step 2 — Location

```
Parent directory  ┌──────────────────────────────┐
                  │ TreeView, dirs only, rooted   │
                  │ at the Terragrunt root        │
                  │ • applications/               │
                  │   • payments/                 │
                  │     • gwc/                    │
                  │       • prod/        ← chosen │
                  └──────────────────────────────┘
Folder name       [ key-vault-secrets______ ]

Resolved path     applications/payments/gwc/prod/key-vault-secrets/
                  └─ terragrunt.hcl
Resolved name     kv-fid-secrets-gwc-prod    (from path: location=gwc, env=prod)
```

- The `TreeView` shows directories only. Built once when the wizard opens by walking the Terragrunt root one level at a time and lazy-expanding (cheap for large trees).
- The folder-name field defaults to a sanitised slug of the chosen template's name (`Key Vault` → `key-vault`); the user is free to edit.
- Resolved-path label updates live; the `terragrunt.hcl` or `<folder>.hcl` suffix is shown so the user can see exactly what file will be created.
- Resolved-name label updates live by feeding the speculative `CreateRequest` through the existing `ResourceNameSynth`, so the user sees the same name that will appear on the diagram card.
- Validation:
  - Parent directory must be selected.
  - Folder name must be non-empty and match `[a-zA-Z0-9._-]+`.
  - The resolved target path must not already exist.
  - For `IncludeFromModule`, the resolved target's parent must be under (or equal to) the Terragrunt root. Includes outside the project root are not supported.

Invalid state → red outline on the offending field, inline error label below, `Next` disabled.

### Step 3 — Inputs

The form is built from `SchemaDeriver.derive(template, mode)` which returns `TemplateSchema` rows in three groups, in this top-to-bottom order:

1. **Conventional** — only for `ResourceFromInclude`. Shows four rows (`purpose`, `location_short`, `environment`, optional `project_name_short`). Pre-filled:
   - `location_short` ← Step 2's parent-dir region segment if one was inferred (e.g. `gwc`); else blank.
   - `environment` ← Step 2's parent-dir env segment if one was inferred (e.g. `prod`); else blank.
   - `purpose` ← Step 2's folder name, minus any leading inferred resource prefix.
   - `project_name_short` is left blank by default.
   - Each row is editable; required rows show a red "•" prefix.
2. **Template** —
   - For `ResourceFromModule` and `IncludeFromModule`: one row per `Variable` from the module, in declaration order. Widget choice mirrors the Inspector: TextField for `string`, numeric TextField for `number`, CheckBox for `bool`, TextArea for `list(...)` / `map(...)` / `object({...})`, with the existing `MapObjectTableEditor` reused for tabular object types. Default values from `Variable.default` pre-fill the widget; required rows (no default) show the red "•" prefix and start empty.
   - For `ResourceFromInclude`: one row per *peer-derived* input — any non-conventional input key the `SchemaDeriver` observed across existing units that already extend the chosen include. These rows are always optional (no red "•"); widget type is inferred from the observed values' shape (string / bool / numeric / text-area fallback). Pre-filled empty so the user explicitly opts in. Switching the include re-derives this list.
3. **Extra** — empty by default; a single `+ Add input` row at the bottom lets the user add ad-hoc key/value pairs (free-text key + `ValueEditor`). Useful for inputs the module schema doesn't declare (rare but real).

Switching the template via Back→Step 1 re-derives the schema but keeps user-entered values where the key names match. Entries that no longer have a matching schema row are demoted to the Extra group rather than discarded.

Validation: every row marked required must have a non-empty value. `Next` (now "Preview & Create") is disabled otherwise.

### Commit step (after Preview & Create)

1. `HclEmitter.emit(request, location)` produces the file body string.
2. `CreatePreviewDialog` shows the diff — the existing `DiffDialog` with an empty "before" side; the entire generated content appears as additions.
3. On `Confirm`, the wizard is closed and `CreateService.commit(plan)` is dispatched to the `io` executor.
4. After successful commit, `MainController` patches the graph and selects + scrolls-into-view the new node.

`Cancel` from the preview returns to Step 3 with the wizard state intact. `Cancel` from any step closes the wizard with no side effects.

## 7. File emission

The emitter produces three canonical shapes. All three reuse `InputsRenderer` (2-space indent, `=` aligned to the longest key) so generated files look identical to what the existing Save flow would write.

### Resource from Include

```hcl
include "root" {
  path = find_in_parent_folders("root.hcl")
}

include "common" {
  path   = "${dirname(find_in_parent_folders("root.hcl"))}/<relative-path-to-include>"
  expose = true
}

terraform {
  source = "${include.common.locals.base_source_url}"
}

inputs = {
  purpose         = "secrets"
  location_short  = "gwc"
  environment     = "prod"
  # … any Extra inputs the user entered, in their entered order
}
```

- `<relative-path-to-include>` is the include file's path relative to the Terragrunt root, computed by `Path.relativize`. If `root.hcl` is not reachable as `find_in_parent_folders("root.hcl")` from the new file's location (i.e. there is no `root.hcl` walking up the directory tree), the emitter falls back to a directly relative `path = "../../_common/<name>.hcl"` and the wizard records a warning ribbon in Step 2 (warning, not blocker).
- The `include "common"` block's name is derived from the include's file name minus the `.hcl` suffix.
- The `terraform { source = … }` block always references `include.common.locals.base_source_url`. If the chosen include does not declare `base_source_url`, the wizard surfaces this as a Step 1 validation error: "This include has no `base_source_url` local — pick another or create an Include from a Module first."

### Resource from Module (local)

```hcl
include "root" {
  path = find_in_parent_folders("root.hcl")
}

terraform {
  source = "../../../../modules/key-vault"
}

inputs = {
  # one line per filled module variable, in declaration order
}
```

- `terraform.source` is the local module directory's path relative to the new file. Computed via `Path.relativize`. If the relativised path would escape the user's filesystem (different roots on Windows), the emitter falls back to an absolute path with a warning.

### Resource from Module (external)

```hcl
terraform {
  source = "<raw external sourceRef>"
}

inputs = {
  # one line per filled module variable
}
```

- No `include "root"` block — external modules are stand-alone by convention.
- The raw `sourceRef` is preserved verbatim from the `ExternalModule`.

### Include from Module (local)

```hcl
locals {
  base_source_url = "../../modules/key-vault"   # relative path; user typically replaces with a remote git URL
}

inputs = {
  # any shared inputs the user entered
}
```

The wizard shows a warning in Step 1: "Include shared sources are usually remote URLs. The generated local path will work but is unusual." Proceeding is allowed.

### Include from Module (external)

```hcl
locals {
  base_source_url = "git@bitbucket.org:directpojistovna/fid-terraform-modules.git//root-modules/key-vault"
}

inputs = {
  # any shared inputs
}
```

The raw `sourceRef` is copied verbatim.

## 8. Commit

`CreateService.commit(CreatePlan)` runs on the `io` executor:

1. `Files.createDirectories(plan.targetFile.parent)` — mkdirs.
2. Conflict re-check: if `Files.exists(plan.targetFile)`, abort with `FileAlreadyExistsException`. The wizard catches this, surfaces it in the Step 2 error label, and re-opens at Step 2.
3. Write to `<targetFile>.gruntface.tmp`, then `Files.move(tmp, targetFile, ATOMIC_MOVE, REPLACE_EXISTING)`.
4. `DiscoveryService.parseSingleUnit(targetFile)` / `parseSingleCommon(targetFile)` — extracted from the existing walk, returns a fresh `Unit` or `CommonHcl`.
5. `InfraGraphPatcher.add(graph, newNode)` (mirror of the existing `replaceUnit` helper) appends the node, derives its `uses` / `includes` / `dependsOn` edges from the parse, and re-runs Tarjan over the updated edge list to recompute cycles. Cheap — the graph is small.
6. `MainController.onLoaded(newGraph)` re-renders and selects the new node.

If step 4 throws (parse error on the file we just emitted — would indicate an `HclEmitter` bug), the file stays on disk and the status bar shows the parser error with a "Reload" hint. No auto-revert: the user can open the file in their editor.

If step 5 throws (graph patcher bug), same behaviour: file on disk, status-bar error, "Reload" hint.

## 9. Error handling

| Condition | Behaviour |
| --- | --- |
| Folder name contains invalid characters | Inline red label on the field; `Next` disabled. |
| Target path already exists at Step 2 | Inline red label; `Next` disabled. |
| Target path appeared between Step 2 validation and commit (race) | Commit aborts with `FileAlreadyExistsException`; wizard re-opens at Step 2 with the error. |
| Parent directory not writable | Caught at commit; modal error showing the OS message; wizard re-opens at Step 2. |
| `root.hcl` not reachable from chosen Resource-from-Include location | Warning ribbon in Step 2 ("Extending the include from this location may not resolve at runtime — falling back to a direct relative path"). Not blocking. |
| Chosen Include has no `base_source_url` local (Resource-from-Include) | Step 1 validation error ("Pick an include with `base_source_url`, or create an Include from a Module first"). Blocks `Next`. |
| Chosen template was deleted or moved between wizard open and commit | Re-stat at commit; modal "Template no longer available — please pick again"; wizard returns to Step 1. |
| No templates exist for the chosen mode (e.g. project has no `_common/*.hcl` and the user picked Resource-from-Include) | Step 1 list shows an empty-state hint: "No includes found in this project. Create one first via *New Include*." with an in-place button that switches the wizard's mode to `IncludeFromModule`. `Next` disabled. |
| Required input left empty (Step 3) | `Next` / `Preview & Create` disabled; offending row gets a red "•" highlight. |
| Post-write parse fails | File stays on disk; status-bar error with "Reload" hint. |
| Post-write graph patch fails | Same as above. |

## 10. Testing

Headless JUnit 5 tests under `src/test/java`:

- `TemplateCatalogTest` — given a fixture `InfraGraph`, asserts the right list of templates per `WizardMode`. Covers: include-only fixture, module-only fixture, mixed fixture.
- `SchemaDeriverTest` —
  - Resource-from-Include: returns the four conventional inputs plus any non-conventional input keys observed across existing units that already extend the chosen include. Sample fixture has two existing units extending `key-vault.hcl` with `purpose` and `extra_tag` set; deriver returns `purpose`, `location_short`, `environment`, `project_name_short`, `extra_tag`.
  - Resource-from-Module: returns module's declared variables in declaration order, with required/optional set from the presence of a default.
  - Include-from-Module: same as above.
- `LocationSuggesterTest` — parses `applications/payments/gwc/prod/foo` → `{location_short=gwc, environment=prod, purpose=foo}`. Edge cases: path with only one segment, root-level path, path with a non-matching pattern (returns empty). Inference is best-effort: it returns the two-letter region segment if it matches a known region list, and the env segment if it matches a known env list (`prod`, `preprod`, `dev`, `test`).
- `HclEmitterTest` — five fixtures, one per shape (`resource-from-include`, `resource-from-local-module`, `resource-from-external-module`, `include-from-local-module`, `include-from-external-module`). Each fixture has an input `CreateRequest` + expected file content; assert byte-exact equality.
- `CreateServiceTest` — uses a `@TempDir`:
  - Happy path: emit + commit + read back + parse → equal to expected.
  - Conflict path: pre-create the target file; commit throws `FileAlreadyExistsException`.
  - Mkdirs path: parent directory does not exist before commit; commit creates it.
  - Atomic-move path: pre-create the `.gruntface.tmp` file; commit overwrites it (move with `REPLACE_EXISTING`).
- `InfraGraphPatcherTest` — synthetic graph + parsed `Unit`; assert nodes appended, edges derived, cycles recomputed.

Wizard UI is manual-test-only, consistent with the existing MVP testing posture. No `TestFX` dependency added.

## 11. Build / dependencies

No new external dependencies. Everything is internal to the existing module.

`module-info.java` is unchanged.

## 12. Implementation sequencing (preview)

Final sequencing is the writing-plans skill's job. The natural decomposition:

1. `create.ResourceTemplate` + `TemplateCatalog` + `TemplateSchema` + `SchemaDeriver` — headless, fully unit-tested.
2. `create.LocationSuggester` + `HclEmitter` + `CreateRequest` + `CreatePlan` — headless, fully unit-tested.
3. `DiscoveryService.parseSingleUnit` / `parseSingleCommon` extraction + `InfraGraphPatcher` — refactor + unit tests.
4. `create.CreateService` + commit integration test against a `@TempDir`.
5. `ui.create.Step1TemplatePicker` — list + preview pane, no wizard chrome yet. Visual check.
6. `ui.create.Step2Location` — tree picker + folder-name + live previews. Visual check.
7. `ui.create.Step3Inputs` — schema-driven form (reuses `ValueEditor`). Visual check.
8. `ui.create.NewResourceWizard` — chrome, step navigation, validation, prefill. Toolbar + File menu entries. Visual check end-to-end.
9. Right-click context menus on `DiagramView` (canvas + node). Inspector "Use as template…" button.
10. `CreatePreviewDialog` + commit wiring + post-commit graph patch + select-new-node. Final visible E2E.
11. Polish: keyboard shortcuts, error messages, validation copy, status-bar messages.

## 13. Open questions

None blocking. Items deliberately deferred:

- Bulk create (one Include + N Resources extending it in a single transaction).
- Delete / rename flows for existing Resources and Includes. Same package will host them when needed.
- Schema introspection of remote modules (would require git clone + `*.tf` parse). The Extra-inputs group lets the user fill external-module inputs by hand for now.
- Per-project override of the Fidoo conventional-input list.
- Inspector-side "duplicate this Resource" shortcut (would be a thin wrapper over the wizard prefill).
