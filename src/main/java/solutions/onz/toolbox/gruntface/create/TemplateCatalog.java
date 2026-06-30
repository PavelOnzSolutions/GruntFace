package solutions.onz.toolbox.gruntface.create;

import solutions.onz.toolbox.gruntface.model.InfraGraph;

import java.util.ArrayList;
import java.util.List;

public final class TemplateCatalog {
    private TemplateCatalog() {}

    public static List<ResourceTemplate> forMode(InfraGraph graph, WizardMode mode) {
        List<ResourceTemplate> out = new ArrayList<>();
        switch (mode) {
            case WizardMode.ResourceFromInclude rfi ->
                graph.commons().forEach(c -> out.add(new ResourceTemplate.IncludeTemplate(c)));
            case WizardMode.ResourceFromModule rfm -> addModulesAndExternals(graph, out);
            case WizardMode.IncludeFromModule ifm -> addModulesAndExternals(graph, out);
        }
        return List.copyOf(out);
    }

    private static void addModulesAndExternals(InfraGraph graph, List<ResourceTemplate> out) {
        graph.modules().forEach(m -> out.add(new ResourceTemplate.LocalModuleTemplate(m)));
        graph.externals().forEach(x -> out.add(new ResourceTemplate.ExternalModuleTemplate(x)));
    }
}
