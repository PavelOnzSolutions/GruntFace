terraform {
  source = "../../../modules/aks"
}

dependency "vpc" {
  config_path = "../vpc"
}

dependency "subnets" {
  config_path = "../subnets"
}

inputs = {
  cluster_name = "dev-aks"
  node_count   = 3
  enabled      = true
}
