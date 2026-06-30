terraform {
  source = "git::https://example.com/modules/kv.git?ref=v1.2.3"
}

inputs = {
  name = "kv-secrets"
}
