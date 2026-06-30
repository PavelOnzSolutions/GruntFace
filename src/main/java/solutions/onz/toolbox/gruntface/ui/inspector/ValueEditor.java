package solutions.onz.toolbox.gruntface.ui.inspector;

import javafx.scene.Node;
import solutions.onz.toolbox.gruntface.model.InputValue;
import solutions.onz.toolbox.gruntface.model.Variable;

/**
 * A per-input editor abstraction: knows how to render itself and how to read
 * its current state back as an {@link InputValue}.
 */
public interface ValueEditor {

    /** The JavaFX node to add to the form. */
    Node node();

    /**
     * Read the editor's current state and convert to a typed {@link InputValue}.
     * Implementations may return {@code previous} unchanged when the editor cannot
     * produce a meaningful value (e.g. raw fallback failed).
     */
    InputValue read(Variable v, InputValue previous);
}
