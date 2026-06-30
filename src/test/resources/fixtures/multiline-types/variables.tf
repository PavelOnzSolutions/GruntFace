variable "simple" {
  type = string
}

variable "config" {
  type = object({
    name = string
    age  = number
  })
}

variable "tags" {
  type = map(object({
    key   = string
    value = string
  }))
  default = {
    env = {
      key   = "env"
      value = "prod"
    }
  }
}
