terraform {
  source = "git::https://example.com/aks.git//mod?ref=v1.0"
}

dependency "subnets" {
  config_path = "../subnets"
}

inputs = {
  name       = "dev-aks"
  node_count = 3
}
