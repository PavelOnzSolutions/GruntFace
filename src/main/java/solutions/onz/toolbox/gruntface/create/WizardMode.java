package solutions.onz.toolbox.gruntface.create;

public sealed interface WizardMode {
    record ResourceFromInclude() implements WizardMode {}
    record ResourceFromModule()  implements WizardMode {}
    record IncludeFromModule()   implements WizardMode {}
}
