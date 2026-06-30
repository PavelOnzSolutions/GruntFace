# Creating resources

Use the **+ New…** button on the toolbar (or **File → New …**) to launch the
creation wizard in one of three modes:

- **New Resource (extend Include)** — create a new unit that includes an
  existing shared HCL file. Best when your project already has a base
  configuration you want to reuse.
- **New Resource (extend Module)** — create a new unit that points directly
  at a Terraform module (local or external).
- **New Include** — create a new shared HCL file from a Terraform module.

You can also right-click a node and start the wizard pre-filled from it:

- Right-click an **include** to create a new unit that uses that include.
- Right-click a **module** to create a new unit or a new include from that
  module.

The wizard walks you through:

1. Picking the template (include or module).
2. Choosing the target location and name.
3. Wiring dependencies on other units.
4. Filling in inputs.

Before any file is written, you see a **create preview** showing each file
GruntFace will create. If you cancel, nothing is written.
