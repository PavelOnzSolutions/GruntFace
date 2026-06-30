include "root" {
  path = find_in_parent_folders("root.hcl")
}

include "common" {
  path   = "${dirname(find_in_parent_folders("root.hcl"))}/_common/container-app.hcl"
  expose = true
}

terraform {
  source = include.common.locals.base_source_url
}

dependency "container_app_env" {
  config_path  = "../container-app-environment"
  mock_outputs = {}
}

dependency "managed_identity_apps" {
  config_path  = "../managed-identity-apps"
  mock_outputs = {}
}

inputs = {
  purpose                    = "powerauth"
  resource_group_name        = dependency.container_app_env.outputs.resource_group_name
  user_assigned_identity_ids = [dependency.managed_identity_apps.outputs.user_assigned_identity.id]
}
