# Reading the graph

The graph shows four kinds of nodes:

- **Unit** — a `terragrunt.hcl` file in your project.
- **Include** — a shared HCL file that other units pull in via `include`.
- **Module** — a local Terraform module discovered in your modules folder.
- **External module** — a remote module reference (`git::`, registry, etc.).
  External modules have no local variable schema, so units that use them get
  a free-text input editor.

Edges:

- A unit **uses** a module (or external module).
- A unit **includes** a shared HCL file.
- A unit **depends on** another unit (`dependency "<name>" { … }`).

Use **Fit** (toolbar or **View → Fit to Window**) to recenter the graph.

The **Show modules** and **Show includes** toggles hide or show those node
types so you can focus on units while still keeping the relationships available
when you need them.

If your project has dependency cycles, GruntFace highlights the involved nodes
so you can spot the loop.
