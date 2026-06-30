locals {
  base_source_url = "github.com/terraform-modules.git//root-modules/key-vault?ref=v1"
  # Expression-only locals — harvested as references, NOT literals, so they are
  # skipped by the _common harvest. This reproduces the reported bug.
  location_short = local.location_vars.locals.location_short
  environment    = local.environment_vars.locals.environment
}
