package solutions.onz.toolbox.gruntface.create;

import solutions.onz.toolbox.gruntface.model.CommonHcl;
import solutions.onz.toolbox.gruntface.model.ExternalModule;
import solutions.onz.toolbox.gruntface.model.Module;

public sealed interface ResourceTemplate {
    /** Stable identifier for matching across rebuilds (e.g. when re-deriving schemas). */
    String id();
    /** Human-readable name shown in the picker. */
    String displayName();

    record IncludeTemplate(CommonHcl include) implements ResourceTemplate {
        @Override public String id() { return "i::" + include.file(); }
        @Override public String displayName() { return include.name(); }
    }

    record LocalModuleTemplate(Module module) implements ResourceTemplate {
        @Override public String id() { return "lm::" + module.dir(); }
        @Override public String displayName() { return module.name(); }
    }

    record ExternalModuleTemplate(ExternalModule external) implements ResourceTemplate {
        @Override public String id() { return "xm::" + external.sourceRef(); }
        @Override public String displayName() { return external.sourceRef(); }
    }
}
