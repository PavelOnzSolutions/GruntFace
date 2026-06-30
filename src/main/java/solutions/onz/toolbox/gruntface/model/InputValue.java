package solutions.onz.toolbox.gruntface.model;

public sealed interface InputValue
        permits InputValue.StringValue, InputValue.NumberValue,
                InputValue.BoolValue, InputValue.RawHcl {

    record StringValue(String value) implements InputValue {}
    record NumberValue(String literal) implements InputValue {}
    record BoolValue(boolean value) implements InputValue {}
    record RawHcl(String hcl) implements InputValue {}
}
