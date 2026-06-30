locals {
  root_path        = dirname(find_in_parent_folders("root.hcl"))
  base_source_url = "git@github.com/PavelOnz/azure-terraform-modules.git//root-modules/key-vault"
  some_function   = read_terragrunt_config(find_in_parent_folders("env.hcl"))
}

inputs = {
  tenant_id = local.shared_vars.locals.tenant_id
}
