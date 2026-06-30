resource "azurerm_subnet" "this" {
  count                = length(var.cidrs)
  name                 = "${var.name}-${count.index}"
  virtual_network_name = var.vpc_id
  address_prefixes     = [var.cidrs[count.index]]
}
