# Getting started

Open a Terragrunt project from **File → Open Terragrunt Project…**, or use
**Open Project…** on the toolbar. Choose the root directory of your Terragrunt
project.

GruntFace tries to auto-detect your Terraform modules folder and any shared
`commons` HCL files. If auto-detection picks the wrong folder, override it:

- **View → Set modules location…** — point at the folder that contains your
  Terraform modules (subdirectories with `*.tf` files).
- **View → Set commons location…** — point at the folder that contains shared
  HCL files included by your units.
- **View → Clear location overrides** — go back to auto-detect.

Use **Reload** (toolbar or **File → Reload**) to re-scan the project after
changing files outside GruntFace.

GruntFace remembers the last project you opened and your location overrides
between sessions.
