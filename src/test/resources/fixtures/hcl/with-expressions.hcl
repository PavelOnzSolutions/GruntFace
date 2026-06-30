terraform {
  source = "../../../modules/subnets"
}

inputs = {
  name      = "subnets"
  vpc_id    = dependency.vpc.outputs.id
  cidrs     = ["10.0.1.0/24", "10.0.2.0/24"]
  tags      = local.common_tags
}
