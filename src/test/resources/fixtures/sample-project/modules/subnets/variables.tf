variable "name" {
  type = string
}

variable "vpc_id" {
  type        = string
  description = "Parent VNet id"
}

variable "cidrs" {
  type    = list(string)
  default = []
}
