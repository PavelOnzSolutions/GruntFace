include "root" {
  path = find_in_parent_folders("root.hcl")
}

include "common" {
  path   = "${dirname(find_in_parent_folders("root.hcl"))}/_common/key-vault.hcl"
  expose = true
}

terraform {
  source = "${include.common.locals.base_source_url}"
}

inputs = {
  purpose = "dbx"
}
