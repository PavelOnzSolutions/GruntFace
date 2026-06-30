package solutions.onz.toolbox.gruntface.create;

import solutions.onz.toolbox.gruntface.model.CommonHcl;
import solutions.onz.toolbox.gruntface.model.InfraGraph;
import solutions.onz.toolbox.gruntface.model.InputValue;
import solutions.onz.toolbox.gruntface.model.Module;
import solutions.onz.toolbox.gruntface.model.Unit;
import solutions.onz.toolbox.gruntface.model.Variable;

import java.util.*;

public final class SchemaDeriver {
    private SchemaDeriver() {}

    public static List<TemplateSchema> derive(ResourceTemplate template, WizardMode mode, InfraGraph graph) {
        return switch (mode) {
            case WizardMode.ResourceFromInclude rfi -> {
                if (!(template instanceof ResourceTemplate.IncludeTemplate it)) {
                    throw new IllegalArgumentException(
                        "ResourceFromInclude requires an IncludeTemplate, got " + template.getClass().getSimpleName());
                }
                yield deriveForResourceFromInclude(it, graph);
            }
            case WizardMode.ResourceFromModule rfm -> deriveForModule(template);
            case WizardMode.IncludeFromModule ifm -> deriveForModule(template);
        };
    }

    private static List<TemplateSchema> deriveForResourceFromInclude(
            ResourceTemplate.IncludeTemplate t, InfraGraph graph) {
        List<TemplateSchema> out = new ArrayList<>();
        out.add(new TemplateSchema(ConventionalInputs.PURPOSE, "string", "Resource purpose / short slug",
            true, SchemaGroup.CONVENTIONAL, Optional.empty()));
        out.add(new TemplateSchema(ConventionalInputs.LOCATION_SHORT, "string", "Region short code (e.g. gwc)",
            true, SchemaGroup.CONVENTIONAL, Optional.empty()));
        out.add(new TemplateSchema(ConventionalInputs.ENVIRONMENT, "string", "Environment (e.g. prod, preprod)",
            true, SchemaGroup.CONVENTIONAL, Optional.empty()));
        out.add(new TemplateSchema(ConventionalInputs.PROJECT_NAME_SHORT, "string", "Project name short code (optional)",
            false, SchemaGroup.CONVENTIONAL, Optional.empty()));

        // Peer-observed keys: union of input keys used by existing units that include the same CommonHcl,
        // minus the conventional names. Preserve first-seen order.
        Set<String> conventional = new HashSet<>(ConventionalInputs.ALL);
        LinkedHashSet<String> peerKeys = new LinkedHashSet<>();
        Map<String, String> peerTypes = new LinkedHashMap<>();
        // If multiple peer units observe the same key with different value types, the first wins.
        for (InfraGraph.IncludesEdge e : graph.includesEdges()) {
            if (!sameFile(e.to(), t.include())) continue;
            for (Map.Entry<String, InputValue> ie : e.from().inputs().entrySet()) {
                if (conventional.contains(ie.getKey())) continue;
                peerKeys.add(ie.getKey());
                peerTypes.putIfAbsent(ie.getKey(), inferType(ie.getValue()));
            }
        }
        for (String key : peerKeys) {
            out.add(new TemplateSchema(key, peerTypes.get(key), "", false,
                SchemaGroup.TEMPLATE, Optional.empty()));
        }
        return List.copyOf(out);
    }

    private static List<TemplateSchema> deriveForModule(ResourceTemplate template) {
        if (template instanceof ResourceTemplate.LocalModuleTemplate lm) {
            Module m = lm.module();
            List<TemplateSchema> out = new ArrayList<>();
            for (Variable v : m.variables()) {
                out.add(new TemplateSchema(
                    v.name(),
                    v.typeExpr() == null ? "" : v.typeExpr(),
                    v.description() == null ? "" : v.description(),
                    v.defaultLiteral().isEmpty(),
                    SchemaGroup.TEMPLATE,
                    v.defaultLiteral()
                ));
            }
            return List.copyOf(out);
        }
        // External modules: no introspectable variables.
        return List.of();
    }

    private static boolean sameFile(CommonHcl a, CommonHcl b) {
        return a.file().toAbsolutePath().normalize().equals(b.file().toAbsolutePath().normalize());
    }

    private static String inferType(InputValue v) {
        if (v instanceof InputValue.StringValue) return "string";
        if (v instanceof InputValue.BoolValue) return "bool";
        if (v instanceof InputValue.NumberValue) return "number";
        return "";  // RawHcl: unknown
    }
}
