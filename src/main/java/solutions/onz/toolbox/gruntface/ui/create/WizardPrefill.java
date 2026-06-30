package solutions.onz.toolbox.gruntface.ui.create;

import solutions.onz.toolbox.gruntface.create.ResourceTemplate;
import solutions.onz.toolbox.gruntface.create.WizardMode;

import java.nio.file.Path;
import java.util.Optional;

public record WizardPrefill(
    Optional<WizardMode> mode,
    Optional<ResourceTemplate> template,
    Optional<Path> parentDir
) {
    public static WizardPrefill empty() { return new WizardPrefill(Optional.empty(), Optional.empty(), Optional.empty()); }
}
