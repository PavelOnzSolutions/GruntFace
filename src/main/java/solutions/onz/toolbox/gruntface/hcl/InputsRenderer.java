package solutions.onz.toolbox.gruntface.hcl;

import solutions.onz.toolbox.gruntface.model.InputValue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InputsRenderer {

    private InputsRenderer() {}

    public static String render(Map<String, InputValue> inputs, List<String> declaredOrder) {
        LinkedHashMap<String, InputValue> ordered = new LinkedHashMap<>();
        for (String k : declaredOrder) {
            if (inputs.containsKey(k)) ordered.put(k, inputs.get(k));
        }
        for (Map.Entry<String, InputValue> e : inputs.entrySet()) {
            if (!ordered.containsKey(e.getKey())) ordered.put(e.getKey(), e.getValue());
        }

        int width = 0;
        for (String k : ordered.keySet()) width = Math.max(width, k.length());

        StringBuilder sb = new StringBuilder("inputs = {\n");
        for (Map.Entry<String, InputValue> e : ordered.entrySet()) {
            sb.append("  ");
            sb.append(pad(e.getKey(), width));
            sb.append(" = ");
            sb.append(renderValue(e.getValue()));
            sb.append('\n');
        }
        sb.append('}');
        return sb.toString();
    }

    private static String pad(String s, int width) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) sb.append(' ');
        return sb.toString();
    }

    private static String renderValue(InputValue v) {
        if (v instanceof InputValue.StringValue(String value)) return quote(value);
        if (v instanceof InputValue.BoolValue(boolean value)) return Boolean.toString(value);
        if (v instanceof InputValue.NumberValue(String literal)) return literal;
        if (v instanceof InputValue.RawHcl(String hcl)) return hcl;
        throw new IllegalStateException("unknown variant: " + v);
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"'  -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}
