package solutions.onz.toolbox.gruntface.create;

import java.util.List;

public final class ConventionalInputs {
    public static final String PURPOSE = "purpose";
    public static final String LOCATION_SHORT = "location_short";
    public static final String ENVIRONMENT = "environment";
    public static final String PROJECT_NAME_SHORT = "project_name_short";

    /** Required keys; {@link #PROJECT_NAME_SHORT} is optional and not in this list. */
    public static final List<String> REQUIRED = List.of(PURPOSE, LOCATION_SHORT, ENVIRONMENT);

    public static final List<String> ALL = List.of(PURPOSE, LOCATION_SHORT, ENVIRONMENT, PROJECT_NAME_SHORT);

    private ConventionalInputs() {}
}
