package solutions.onz.toolbox.gruntface.hcl;

import solutions.onz.toolbox.gruntface.model.CommonHcl;
import solutions.onz.toolbox.gruntface.model.InputValue;
import solutions.onz.toolbox.gruntface.model.Module;
import solutions.onz.toolbox.gruntface.model.Unit;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface HclService {
    Unit parseUnit(Path terragruntHcl) throws IOException;
    Module parseModule(Path moduleDir) throws IOException;
    CommonHcl parseCommon(Path commonHclFile) throws IOException;
    String renderInputsBlock(Map<String, InputValue> inputs, List<String> declaredOrder);
    Map<String, String> parseHierarchyLocals(Path hclFile) throws IOException;
}
