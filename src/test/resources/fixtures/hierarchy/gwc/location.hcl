locals {
    location       = "germanywestcentral"
    location_short = basename(get_terragrunt_dir())
    tags = {
        Region = upper(local.location_short)
    }
}
