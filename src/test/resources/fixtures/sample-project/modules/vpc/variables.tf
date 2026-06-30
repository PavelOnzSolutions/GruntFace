variable "name" {
  type        = string
  description = "Network name"
}

variable "cidr_block" {
  type        = string
  default     = "10.0.0.0/16"
  description = "CIDR for the VNet"
}

variable "enable_dns" {
  type    = bool
  default = true
}
