terraform {
  source = "../../../modules/vpc"
}

inputs = {
  name       = "dev-vpc"
  cidr_block = "10.0.0.0/16"
  enable_dns = true
}
