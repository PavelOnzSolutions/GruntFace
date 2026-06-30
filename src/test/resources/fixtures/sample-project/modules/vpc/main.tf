resource "azurerm_virtual_network" "this" {
  name          = var.name
  address_space = [var.cidr_block]
}
