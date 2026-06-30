include "root" {
  path = find_in_parent_folders("root.hcl")
}

terraform {
  source = "../../../../../modules/key-vault"
}

dependency "uami" {
  config_path  = "../managed-identity-apps"
  mock_outputs = {}
}

inputs = {
  name              = "kv-secrets"
  uami_principal_id = dependency.uami.outputs.principal_id
}
