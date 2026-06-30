terraform {
  source = "../../../modules/subnets"
}

dependency "vpc" {
  config_path = "../vpc"
}

inputs = {
  name   = "dev-subnets"
  vpc_id = dependency.vpc.outputs.id
  cidrs  = ["10.0.1.0/24"]
}
