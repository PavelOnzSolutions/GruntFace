terraform {
  source = "../../../modules/things"
}

inputs = {
  name = "thing"
  config = {
    timeout = 30
    retries = 3
    nested = {
      inner = local.something
    }
  }
  many = {
    a = {
      b = "c"
    }
    d = ["e", "f"]
  }
  trailing = "ok"
}
