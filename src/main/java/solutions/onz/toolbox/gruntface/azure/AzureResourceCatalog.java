package solutions.onz.toolbox.gruntface.azure;

import java.util.ArrayList;
import java.util.List;

public final class AzureResourceCatalog {

    private static final List<AzureResource> ALL = buildCatalog();
    private static final AzureResource GENERIC = findGenericOrThrow();

    private AzureResourceCatalog() {}

    public static List<AzureResource> all() { return ALL; }

    public static AzureResource generic() { return GENERIC; }

    private static List<AzureResource> buildCatalog() {
        List<AzureResource> raw = new ArrayList<>();
        add(raw, "resource_group",      "Resource Group",            "resourcegroup", "rg");
        add(raw, "virtual_network",     "Virtual Network",           "virtual-network", "vnet", "network");
        add(raw, "subnet",              "Subnet",                    "subnet");
        add(raw, "nsg",                 "Network Security Group",    "network-security-group", "nsg");
        add(raw, "public_ip",           "Public IP",                 "public-ip", "publicip", "pip");
        add(raw, "private_endpoint",    "Private Endpoint",          "private-endpoint", "private-link");
        add(raw, "application_gateway", "Application Gateway",       "application-gateway", "app-gateway", "appgw");
        add(raw, "load_balancer",       "Load Balancer",             "load-balancer", "lb");
        add(raw, "firewall",            "Azure Firewall",            "firewall", "azure-firewall", "afw");
        add(raw, "front_door",          "Front Door",                "front-door", "frontdoor", "afd", "cdn");
        add(raw, "dns_zone",            "DNS Zone",                  "dns-zone", "dns");
        add(raw, "traffic_manager",     "Traffic Manager",           "traffic-manager");
        add(raw, "bastion",             "Bastion",                   "bastion");
        add(raw, "vm",                  "Virtual Machine",           "virtual-machine", "vm");
        add(raw, "vmss",                "VM Scale Set",              "vmss", "scale-set");
        add(raw, "aks",                 "Kubernetes Service",        "kubernetes", "aks", "k8s");
        add(raw, "app_service",         "App Service",               "app-service", "webapp", "web-app");
        add(raw, "app_service_plan",    "App Service Plan",          "app-service-plan", "asp");
        add(raw, "function_app",        "Function App",              "function-app", "functions", "func");
        add(raw, "container_app",       "Container App",             "container-app", "containerapp", "aca");
        add(raw, "container_instance",  "Container Instance",        "container-instance", "aci");
        add(raw, "container_registry",  "Container Registry",        "container-registry", "acr");
        add(raw, "storage_account",     "Storage Account",           "storage-account", "storage", "sa");
        add(raw, "file_share",          "File Share",                "file-share", "fileshare", "files");
        add(raw, "recovery_vault",      "Recovery Services Vault",   "recovery-services", "recovery", "backup", "rsv");
        add(raw, "sql_database",        "SQL Database",              "sql-database", "sqldb", "mssql");
        add(raw, "sql_server",          "SQL Server",                "sql-server", "sqlserver");
        add(raw, "postgres",            "PostgreSQL",                "postgres", "postgresql", "psql");
        add(raw, "mysql",               "MySQL",                     "mysql");
        add(raw, "cosmos_db",           "Cosmos DB",                 "cosmos", "cosmosdb", "cosmos-db");
        add(raw, "synapse",             "Synapse",                   "synapse");
        add(raw, "redis",               "Redis Cache",               "redis", "cache");
        add(raw, "data_factory",        "Data Factory",              "data-factory", "adf");
        add(raw, "key_vault",           "Key Vault",                 "key-vault", "keyvault", "kv");
        add(raw, "managed_identity",    "Managed Identity",          "managed-identity", "identity", "msi", "uami");
        add(raw, "event_hub",           "Event Hub",                 "event-hub", "eventhub", "eh");
        add(raw, "service_bus",         "Service Bus",               "service-bus", "servicebus", "sb");
        add(raw, "event_grid",          "Event Grid",                "event-grid", "eventgrid", "eg");
        add(raw, "logic_app",           "Logic App",                 "logic-app", "logicapp");
        add(raw, "api_management",      "API Management",            "api-management", "apim");
        add(raw, "log_analytics",       "Log Analytics",             "log-analytics", "loganalytics", "law");
        add(raw, "app_insights",        "Application Insights",      "application-insights", "appinsights", "ai");
        add(raw, "monitor",             "Monitor",                   "monitor", "alerts");
        add(raw, "cognitive_services",  "Cognitive Services",        "cognitive-services", "cognitive");
        add(raw, "openai",              "Azure OpenAI",              "openai", "aoai", "azure-openai");
        add(raw, "search",              "AI Search",                 "search", "aisearch", "cognitive-search");
        add(raw, "policy",              "Policy",                    "policy");
        add(raw, "virtual_gateway",     "Virtual Network Gateway",   "network-gateway", "vnet-gateway", "vngw", "virtual-gateway");
        add(raw, "subscription",        "Subscription",              "subscription", "sub", "azure-subscription", "subscriptions");
        addNoKeywords(raw, "resource",  "Resource");

        return validateAndFilter(raw);
    }

    private static void add(List<AzureResource> list, String id, String displayName, String... keywords) {
        list.add(new AzureResource(id, displayName, "azure-icons/" + id + ".svg", List.of(keywords)));
    }

    private static void addNoKeywords(List<AzureResource> list, String id, String displayName) {
        list.add(new AzureResource(id, displayName, "azure-icons/" + id + ".svg", List.of()));
    }

    private static List<AzureResource> validateAndFilter(List<AzureResource> raw) {
        List<AzureResource> kept = new ArrayList<>(raw.size());
        for (AzureResource r : raw) {
            String classpath = "/solutions/onz/toolbox/gruntface/ui/graph/" + r.iconResourcePath();
            if (AzureResourceCatalog.class.getResource(classpath) == null) {
                System.err.println("[AzureResourceCatalog] dropping " + r.id()
                    + " — missing icon resource " + classpath);
                continue;
            }
            kept.add(r);
        }
        return List.copyOf(kept);
    }

    private static AzureResource findGenericOrThrow() {
        for (AzureResource r : ALL) if (r.id().equals("resource")) return r;
        throw new IllegalStateException("catalog missing required 'resource' fallback entry");
    }
}
