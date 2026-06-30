include "root" {
  path = find_in_parent_folders("root.hcl")
}

terraform {
  source = "../../../../../modules/key-vault"
}

inputs = {
  name     = "kv-secrets"
  location = "westeurope"
}
