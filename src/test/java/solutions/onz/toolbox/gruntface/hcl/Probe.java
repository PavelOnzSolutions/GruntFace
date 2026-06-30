package solutions.onz.toolbox.gruntface.hcl;

import solutions.onz.toolbox.gruntface.model.Unit;
import java.nio.file.Path;

public class Probe {
    public static void main(String[] args) throws Exception {
        HclService svc = new Hcl4jHclService();
        Unit u = svc.parseUnit(Path.of("src/test/resources/fixtures/include-project/applications/x/y/key-vault-dbx/terragrunt.hcl"));
        System.out.println("sourceRef: " + u.sourceRef());
        System.out.println("includes: " + u.includes());
    }
}
