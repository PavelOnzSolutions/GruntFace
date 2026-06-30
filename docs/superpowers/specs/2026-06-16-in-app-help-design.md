# In-App Help — design

**Date:** 2026-06-16
**Status:** Approved (brainstorming)

## 1. Purpose

GruntFace currently exposes only an `About` item under the `Help` menu. New users open the app and have no in-application explanation of what the nodes mean, how to edit a resource, or how to drive the creation wizards.

This change adds an **in-app User Guide**: a multi-topic help window opened from the `Help` menu that describes the tool and walks the user through its main flows. Content is authored as Markdown files shipped in the JAR and rendered to native JavaFX nodes (no WebView), so it inherits the app's theming automatically.

## 2. Scope

### In scope

- New `Help → User Guide…` menu item that opens a resizable, modeless `Stage`.
- A topic list (left) + content pane (right) split layout, with topics driven by a static registry.
- Six initial topics: Overview, Getting started, Reading the graph, Editing resources, Creating resources, Preferences.
- A small Markdown-to-JavaFX renderer built on `commonmark-java`, supporting: H1/H2/H3, paragraphs, ordered + unordered lists, **bold**, *italic*, `inline code`, fenced code blocks, and links (opened via `HostServices`).
- Theming via CSS classes added to `style.css` so light/dark/auto continue to work.
- Unit tests for the renderer and for the shipped Markdown content.

### Out of scope

- Search across topics.
- Images or screenshots in help content (text-only for v1).
- Context-sensitive help ("show help for the inspector").
- Per-topic deep links from elsewhere in the UI.
- A separate toolbar button — the menu entry is the only entry point.
- Internationalization — content ships in English only.

## 3. Architecture

### Package layout

A new package, sitting alongside the existing UI packages:

```
solutions.onz.toolbox.gruntface.ui.help
  ├── HelpDialog.java         // the Stage + SplitPane wiring
  ├── HelpTopics.java         // static registry; loads .md resources
  ├── HelpTopic.java          // record (id, resourcePath, title, markdown)
  └── MarkdownRenderer.java   // commonmark Node tree -> javafx.scene.Node
```

Resources:

```
src/main/resources/solutions/onz/toolbox/gruntface/ui/help/
  ├── 01-overview.md
  ├── 02-getting-started.md
  ├── 03-reading-the-graph.md
  ├── 04-editing-resources.md
  ├── 05-creating-resources.md
  └── 06-preferences.md
```

Each unit has one clear purpose:

- `HelpTopic` — a value object: the topic's id, its classpath resource path, its display title (parsed from the first `# heading`), and the raw Markdown body. No behavior.
- `HelpTopics` — knows the ordered list of topic resources and how to load them. Pure I/O of classpath resources; no JavaFX.
- `MarkdownRenderer` — given a parsed `org.commonmark.node.Node`, produces a `javafx.scene.Node` (a `VBox` of per-block children). Pure construction; no scene-graph mutation outside the returned subtree.
- `HelpDialog` — builds the `Stage`, the `SplitPane`, the topic `ListView`, and the content `ScrollPane`. Invokes `HelpTopics` to load, invokes `MarkdownRenderer` to render. The only class here that touches JavaFX containers and the application thread.

### Threading

The help window is constructed on the JavaFX Application Thread. Loading `.md` resources from the classpath is synchronous but trivially fast (six small files), so no background executor is needed — the dialog opens immediately. If a topic ever grows large enough to stutter the UI, we can move parsing off-thread; this is not necessary for v1.

### Build

`build.gradle.kts` gains one dependency:

```kotlin
implementation("org.commonmark:commonmark:0.22.0")
```

`commonmark-java` 0.22.0 has no transitive dependencies and ships with an automatic module name (`org.commonmark`), so the `module-info.java` only needs:

```java
requires org.commonmark;
```

## 4. UI integration

### Menu

In `main-view.fxml` `Help` menu, **above** the existing `About`:

```xml
<Menu text="Help">
    <MenuItem text="User Guide…" onAction="#onShowUserGuide"/>
    <SeparatorMenuItem/>
    <MenuItem text="About" onAction="#onAbout"/>
</Menu>
```

A new `@FXML void onShowUserGuide()` on `MainController` delegates to `HelpDialog.show(graphHost.getScene().getWindow())`. No toolbar button.

### Window

- `Stage` with `Modality.NONE` and `initOwner(owner)` — modeless so users can keep the help open while working.
- Default size 900×600. Position and size persisted via new `Prefs` helpers (`helpWindowSize(w, h)`, `helpWindowPosition(x, y)`, paired getters with default fallbacks) added to the existing `Prefs` class. Keys: `help_window_w`, `help_window_h`, `help_window_x`, `help_window_y`. These are new — the current `Prefs` only persists the main window's size.
- Title: "GruntFace — User Guide".
- Stylesheets: inherits from the owner's scene, plus the new help-specific classes added to `style.css`.

### Layout

```
┌─ Stage ─────────────────────────────────────────┐
│ SplitPane (dividerPositions = 0.25)             │
│ ┌──────────────┬──────────────────────────────┐ │
│ │ ListView     │  ScrollPane                  │ │
│ │  Overview    │  ┌──────────────────────────┐│ │
│ │  Getting…    │  │ VBox (rendered content)  ││ │
│ │  Reading…    │  │  ─ H1 (topic title)      ││ │
│ │  Editing…    │  │  ─ paragraph             ││ │
│ │  Creating…   │  │  ─ list                  ││ │
│ │  Preferences │  │  …                       ││ │
│ │              │  └──────────────────────────┘│ │
│ └──────────────┴──────────────────────────────┘ │
└─────────────────────────────────────────────────┘
```

- The ListView's selection change handler swaps the content of the right-hand `ScrollPane`.
- First topic is selected automatically on open.
- The content pane resets scroll-to-top on each topic change.

## 5. Markdown rendering

### Supported constructs

| Markdown                  | JavaFX result                                                |
|---------------------------|--------------------------------------------------------------|
| `# H1`                    | `Label` with style class `help-h1`                           |
| `## H2`                   | `Label` with style class `help-h2`                           |
| `### H3`                  | `Label` with style class `help-h3`                           |
| paragraph                 | `TextFlow` of inline runs                                    |
| `**bold**`                | `Text` with style class `help-bold`                          |
| `*italic*`                | `Text` with style class `help-italic`                        |
| `` `inline code` ``       | `Text` with style class `help-code-inline`                   |
| ` ```fenced``` `          | `TextFlow` with style class `help-code-block` inside a `Region` background |
| `- item` / `1. item`      | `VBox` of `HBox` rows: bullet/number `Label` + `TextFlow`    |
| `[label](url)`            | `Hyperlink` that calls `application.getHostServices().showDocument(url)` |
| hard line break (` \n`)   | `\n` inside the surrounding `TextFlow`                       |

Anything not in this table is rendered as plain text (no failure). The renderer never throws on unknown constructs.

### Renderer interface

```java
public final class MarkdownRenderer {
    private final HostServices hostServices;  // for link clicks; nullable in tests

    public MarkdownRenderer(HostServices hostServices) { … }

    /** Parses the given Markdown and returns a VBox containing the rendered blocks. */
    public VBox render(String markdown) { … }
}
```

The renderer parses with `Parser.builder().build().parse(markdown)`, then walks the resulting `Node` tree with a `Visitor`. Visitor implementation is a private inner class so the public surface stays minimal.

### Styling

New CSS rules in `src/main/resources/solutions/onz/toolbox/gruntface/ui/style.css`, with light + dark variants (the existing `.theme-dark` selector convention is already used in the codebase). Every help-related selector is namespaced with `help-` to avoid clashes.

## 6. Content

The six topics, with the substantive points each must cover:

1. **Overview** — what GruntFace is (Terragrunt + Terraform module visualizer), the desktop-only positioning, what it does *not* do (no `plan`/`apply`, no remote module schema fetch, no Terragrunt function evaluation).
2. **Getting started** — `File → Open Terragrunt Project…`; optional `Set commons location…` / `Set modules location…` overrides and when to use them; the auto-detect default; `Reload`.
3. **Reading the graph** — node types (Unit, Include, Module, External module) and what colors/shapes mean; edge types (uses, includes, dependsOn); the cycle indicator; `Fit`; `Show modules` / `Show includes` toggles.
4. **Editing resources** — selecting a node opens the inspector; structured inputs editor (driven by the module's variable schema); free-text input editor for externals; `Edit raw HCL…` via right-click; the diff-preview save flow; file-drift warning.
5. **Creating resources** — the three wizard modes (Resource-from-Include, Resource-from-Module, Include-from-Module); how the right-click context menu pre-fills the wizard from the clicked node; the create-preview dialog.
6. **Preferences** — theme (Light / Dark / Auto); commons/modules location overrides and how to clear them; what GruntFace persists between sessions.

Content is drafted from the design specs in `docs/superpowers/specs/` (paraphrased for an end-user audience — no internal package or class names).

## 7. Testing

- `MarkdownRendererTest` — one test per supported construct, asserting the shape of the produced JavaFX subtree (e.g., a `- a\n- b` bullet list produces a `VBox` containing two `HBox` rows, each starting with a bullet `Label` and ending with a `TextFlow` whose text equals the item's content). Assertions inspect the returned node tree directly (instanceof, child counts, `Text.getText()`) — *constructing* `Label` / `TextFlow` / `VBox` does not require a running JavaFX toolkit, so no `@BeforeAll` init is needed. If a future test needs CSS resolution, a one-time `new JFXPanel()` call can be added then.
- `HelpTopicsTest` — iterates the registered topics and asserts each one: resource exists on the classpath, body is non-empty, the first non-blank line starts with `# `, the title returned by `HelpTopics.load(topic)` matches the H1 text.
- No dialog-level UI test for `HelpDialog` — consistent with `DiffDialog` and `HclEditDialog`, which are exercised manually.

## 8. Risks and mitigations

- **commonmark-java doesn't expose itself as a named module in older Gradle metadata.** Verified: 0.22.0 publishes `Automatic-Module-Name: org.commonmark`. The `requires org.commonmark;` line in `module-info.java` is sufficient.
- **Selectable / copyable text.** `TextFlow` is not user-selectable by default. For v1 the content pane is read-only and non-selectable; we can add selection support later if users ask. This is called out as a known limitation rather than designed around now.
- **Window state restoration crossing monitors.** If a user moves the help window to a monitor that's no longer connected on next launch, `Prefs`-restored coordinates can place the window off-screen. `HelpDialog` checks that the restored bounds intersect a `Screen.getScreens()` bound and falls back to centered-on-owner if not.
- **Markdown content drift from feature changes.** A future PR that renames `Set commons location…` will leave the help file stale. Acceptable for v1; periodic review during planning sessions is the mitigation. Not worth automation here.

## 9. Definition of done

- `Help → User Guide…` opens a window matching the layout in §4.
- All six topics render correctly in both light and dark themes.
- `MarkdownRendererTest` and `HelpTopicsTest` pass.
- Window size and position persist across app restarts via `Prefs`.
- Existing `About` menu item still works.
- No new warnings on `./gradlew build`.
