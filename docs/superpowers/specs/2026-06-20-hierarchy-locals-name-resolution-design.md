# Hierarchy-locals name resolution

**Date:** 2026-06-20
**Status:** Approved, pre-implementation

## Problem

Resource nodes created through GruntFace's add-resource flow show a correctly
synthesised name (e.g. `ca-container-app-gwc-nonprod`), but hand-written units
loaded from an existing Terragrunt tree show the `FALLBACK` form — the folder
name prefixed with `? ` (e.g. `? container-app-environment`, `? spoke-network`,
`? application-insights`). See `scr3.png`.

The `? ` prefix is rendered by `UnitCard` when a unit's `ResolvedName.confidence`
is `FALLBACK` (`UnitCard.java:79-82`). The fallback text is `u.name()`, the unit's
folder name, which in this project coincides with the resource kind — hence the
report of "? <resource-kind>".

### Root cause

`ResourceNameSynth.pass2Convention` assembles
`<prefix>-[<project_name_short>-]<purpose>-<location_short>-<environment>` and
needs `purpose`, `location_short`, and `environment`. It reads them from the
unit's own literal `inputs`, or from `EvaluationContext.locals`. Two independent
gaps starve it of those values for hand-written units:

1. **Hierarchy values are never read.** `location_short`, `environment`, and
   `project_name_short` come from `location.hcl` / `env.hcl` / `project.hcl`,
   discovered via `find_in_parent_folders` and pulled into the unit through
   `_common/<kind>.hcl`. GruntFace does not read those hierarchy files. Even the
   `_common` file expresses them as references
   (`location_short = local.location_vars.locals.location_short`), not literals,
   so the existing `_common`-locals harvest (`CommonHcl`, string-literals only)
   skips them too.

   The real values are produced by the Fidoo convention:
   - `gwc/location.hcl`: `location_short = basename(get_terragrunt_dir())` → `gwc`
   - `nonprod/env.hcl`:   `environment    = basename(get_terragrunt_dir())` → `nonprod`
   - `powerauth/project.hcl`: `project_name_short = "wpa"` (literal)

2. **The unit's own `locals` block is never read.** `container-app-environment`
   and `spoke-network` set `purpose = local.purpose`, with the literal
   `purpose = "cae"` / `"network"` defined in the unit's *own* `locals {}` block.
   `DiscoveryService.buildEvaluationContext` only harvests `_common` locals, never
   the unit's own, so `local.purpose` resolves to nothing.

   (`application-insights` sets `purpose = "wpa"` as a literal input, so it is
   affected only by gap 1.)

GruntFace-created units avoid both gaps because the create flow writes literal
`purpose` / `location_short` / `environment` directly into the unit's `inputs`.

Both gaps converge on `EvaluationContext.locals`; one change addresses both.

## Approach

Teach discovery to read the Terragrunt hierarchy files and the unit's own locals,
and feed the evaluated values into `EvaluationContext.locals`. Two well-known HCL
idioms are supported; everything else is skipped (consistent with the existing
"literals only" stance of `CommonHcl`).

### Components

**`HclService.parseHierarchyLocals(Path hclFile) → Map<String,String>`**
New interface method, implemented in `Hcl4jHclService`. Parses the first
top-level `locals { ... }` block and returns the entries it can evaluate:

- Quoted string literal (`key = "value"`) → `value`. Reuses the existing
  `extractStringLocals` scan.
- `key = basename(get_terragrunt_dir())` → `basename(<directory containing hclFile>)`.

All other right-hand sides (function calls, references, merges, lists, objects)
are omitted. Returns an empty map when there is no `locals` block.

**Modeling assumption.** Inside a hierarchy file read via `read_terragrunt_config`,
`get_terragrunt_dir()` is modeled as that file's own directory. This reproduces the
observed Fidoo values (`location.hcl` in `gwc/` → `gwc`, `env.hcl` in `nonprod/` →
`nonprod`, `project.hcl` in `powerauth/` → `powerauth`) and is the only function
idiom the evaluator supports.

**`HierarchyLocalsResolver`** (new, `discovery` package)
Given a unit file, walks from the unit's directory upward. At each directory level
it calls `hcl.parseHierarchyLocals` on every `*.hcl` file in that directory —
including the unit's own `terragrunt.hcl` (this covers gap 2) — and merges the
results **nearest-wins** (`putIfAbsent` while ascending). The walk stops after
processing the directory that contains `root.hcl` (the Terragrunt anchor used by
`find_in_parent_folders`), and is also bounded by the filesystem root as a safety
net. Returns a flat `Map<String,String>`.

Stateless and pure apart from filesystem reads. Files are small; per-unit
re-reading of shared ancestor files (e.g. one `location.hcl` per region) is
acceptable for current tree sizes. A per-discovery directory cache is a possible
future optimization, explicitly out of scope here.

**`DiscoveryService.buildEvaluationContext`** (modified)
After harvesting `_common` locals exactly as today, fill remaining keys from the
resolver using `putIfAbsent`:

```
flatLocals = {}
for each include's CommonHcl:           // existing behavior, unchanged
    putIfAbsent each literal local
for each (k, v) in resolver.resolve(unit.file()):   // new, additive
    putIfAbsent(k, v)
```

The change is purely additive: keys already provided by `_common` keep their
current values, so units that already resolve are unaffected; only
previously-missing keys (`location_short`, `environment`, `purpose`,
`project_name_short`, …) get filled. `includeLocals` and `inputs` are unchanged.

Because `buildEvaluationContext` now needs `hcl`, it becomes an instance method
(or `hcl` is threaded through). Both discovery code paths — full `load` and
single-unit add via `linkSingleUnit` → `linkOne` — call it, so both benefit.

### Data flow

```
unit terragrunt.hcl ──► HierarchyLocalsResolver.resolve(file)
                          │  walk up to (and including) root.hcl dir
                          │  parseHierarchyLocals(each *.hcl), nearest-wins
                          ▼
                        { purpose, location_short, environment,
                          project_name_short, location, ... }
                          │
buildEvaluationContext ───┤ merge under _common locals (putIfAbsent)
                          ▼
                   EvaluationContext.locals ──► ResourceNameSynth.pass2
                                                  ▼
                                       ca-wpa-cae-gwc-nonprod  (EVALUATED)
```

## Expected results (powerauth/gwc/nonprod)

| Unit | match prefix | purpose | result | source of fix |
|------|--------------|---------|--------|---------------|
| container-app-environment | `ca` | `cae` (unit local) | `ca-wpa-cae-gwc-nonprod` | gaps 1 + 2 |
| spoke-network | `vnet` | `network` (unit local) | `vnet-wpa-network-gwc-nonprod` | gaps 1 + 2 |
| application-insights | `appi` | `wpa` (literal input) | `appi-wpa-wpa-gwc-nonprod` | gap 1 |

(Exact prefixes depend on `AzureResourceInferrer` matches and `AzureResourceNaming`;
the table shows the shape. The point is `EVALUATED` confidence, not `FALLBACK`.)

### Accepted consequence

`project_name_short = "wpa"` becomes available to every unit in the project, and
pass2 appends it. The GruntFace-created `container-app` therefore changes from
`ca-container-app-gwc-nonprod` to `ca-wpa-container-app-gwc-nonprod`. This is
deliberate: it is consistent with hand-written units and closer to the real Fidoo
resource name. Approved during brainstorming.

## Out of scope

- The create-flow live preview (`Step2Location`) builds its own
  `EvaluationContext` and is not changed here; its preview may differ from the
  post-discovery diagram name. Tracked as a possible follow-up.
- Caching of hierarchy-file parses across units.
- Any HCL evaluation beyond string literals and `basename(get_terragrunt_dir())`.

## Testing

- **`parseHierarchyLocals`** (Hcl4j service test): string literal extraction;
  `basename(get_terragrunt_dir())` → directory basename; no `locals` block → empty;
  non-literal/unsupported entries skipped.
- **`HierarchyLocalsResolver`** (temp-dir tree): assembles a fixture
  `root.hcl` / `project.hcl` / `location.hcl` / `env.hcl` / unit `terragrunt.hcl`,
  asserts merged map and nearest-wins, and that the walk stops at the `root.hcl`
  directory.
- **`DiscoveryService`** (fixture resembling powerauth): a hand-written unit
  resolves to `EVALUATED` (not `FALLBACK`) with the expected text; an
  already-resolving unit is unchanged (no regression).
- Existing `ResourceNameSynthTest` / `InputEvaluatorTest` continue to pass.
