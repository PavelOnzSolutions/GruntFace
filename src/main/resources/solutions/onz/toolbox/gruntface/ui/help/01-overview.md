# Overview

GruntFace is a desktop visualizer for Terragrunt projects. Point it at a Terragrunt
root and a Terraform modules directory and it draws the relationships between your
units, includes, and modules as an interactive graph.

You can:

- Browse units, includes, and modules visually.
- Inspect a unit's inputs against its source module's variable schema.
- Edit inputs through a structured form, or edit the raw HCL file directly.
- Create new resources and includes via guided wizards.

What GruntFace does **not** do:

- It does not run `terragrunt plan` or `terragrunt apply`.
- It does not evaluate Terragrunt functions like `find_in_parent_folders` or
  `read_terragrunt_config`. Expressions are shown as raw text.
- It does not fetch schemas for remote modules. Units that point at a remote
  source get a free-text input editor.
