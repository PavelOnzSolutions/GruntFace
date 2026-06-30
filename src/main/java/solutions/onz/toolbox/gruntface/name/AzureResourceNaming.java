package solutions.onz.toolbox.gruntface.name;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Maps {@code AzureResource.id()} (the kebab-case identifier used in
 * {@code AzureResourceCatalog}) to the conventional Naming resource-name prefix.
 *
 * Entries are intentionally minimal — only the resources where we have a confident
 * convention. {@link #prefixFor(String)} returns empty for unmapped ids; the caller
 * then falls through to a generic strategy.
 */
public final class AzureResourceNaming {

    private static final Map<String, String> PREFIX = buildPrefixMap();

    private AzureResourceNaming() {}

    public static Optional<String> prefixFor(String resourceId) {
        if (resourceId == null) return Optional.empty();
        String p = PREFIX.get(resourceId);
        return p == null ? Optional.empty() : Optional.of(p);
    }

    private static Map<String, String> buildPrefixMap() {
        Map<String, String> m = new LinkedHashMap<>();
        // Verbatim entries from spec (kebab-case)
        m.put("resource-group", "rg");
        m.put("storage-account", "st");
        m.put("key-vault", "kv");
        m.put("virtual-machine", "vm");
        m.put("vm", "vm");
        m.put("vmss", "vmss");
        m.put("log-analytics", "log");
        m.put("log-analytics-workspace", "log");
        m.put("network-watcher", "nw");
        m.put("private-dns-zone", "pdns");
        m.put("dns-zone", "dns");
        m.put("public-ip", "pip");
        m.put("virtual-network", "vnet");
        m.put("subnet", "snet");
        m.put("nsg", "nsg");
        m.put("application-gateway", "agw");
        m.put("front-door", "afd");
        m.put("firewall", "afw");
        m.put("api-management", "apim");
        m.put("app-service", "app");
        m.put("app-service-plan", "asp");
        m.put("function-app", "func");
        m.put("logic-app", "logic");
        m.put("container-app", "ca");
        m.put("container-registry", "acr");
        m.put("container-instance", "ci");
        m.put("aks", "aks");
        m.put("cosmos-db", "cosmos");
        m.put("sql-server", "sql");
        m.put("sql-database", "sqldb");
        m.put("postgres", "psql");
        m.put("mysql", "mysql");
        m.put("redis", "redis");
        m.put("event-hub", "evh");
        m.put("event-grid", "evgt");
        m.put("service-bus", "sb");
        m.put("data-factory", "adf");
        m.put("synapse", "syn");
        m.put("openai", "oai");
        m.put("cognitive-services", "cog");
        m.put("search", "srch");
        m.put("app-insights", "appi");
        m.put("monitor", "amon");
        m.put("policy", "policy");
        m.put("managed-identity", "id");
        m.put("recovery-vault", "rsv");
        m.put("file-share", "share");
        m.put("private-endpoint", "pe");
        m.put("bastion", "bas");
        m.put("load-balancer", "lb");
        m.put("traffic-manager", "tm");

        // Defensive snake_case duplicates (AzureResourceCatalog uses snake_case ids)
        m.put("resource_group", "rg");
        m.put("storage_account", "st");
        m.put("key_vault", "kv");
        m.put("virtual_machine", "vm");
        m.put("log_analytics", "log");
        m.put("network_watcher", "nw");
        m.put("private_dns_zone", "pdns");
        m.put("dns_zone", "dns");
        m.put("public_ip", "pip");
        m.put("virtual_network", "vnet");
        m.put("application_gateway", "agw");
        m.put("front_door", "afd");
        m.put("api_management", "apim");
        m.put("app_service", "app");
        m.put("app_service_plan", "asp");
        m.put("function_app", "func");
        m.put("logic_app", "logic");
        m.put("container_app", "ca");
        m.put("container_registry", "acr");
        m.put("container_instance", "ci");
        m.put("cosmos_db", "cosmos");
        m.put("sql_server", "sql");
        m.put("sql_database", "sqldb");
        m.put("event_hub", "evh");
        m.put("event_grid", "evgt");
        m.put("service_bus", "sb");
        m.put("data_factory", "adf");
        m.put("cognitive_services", "cog");
        m.put("app_insights", "appi");
        m.put("managed_identity", "id");
        m.put("recovery_vault", "rsv");
        m.put("file_share", "share");
        m.put("private_endpoint", "pe");
        m.put("traffic_manager", "tm");

        return Map.copyOf(m);
    }
}
