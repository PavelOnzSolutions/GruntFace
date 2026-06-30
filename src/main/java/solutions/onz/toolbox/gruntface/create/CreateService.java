package solutions.onz.toolbox.gruntface.create;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

public class CreateService {

    public CreatePlan plan(CreateRequest req, Path terragruntRoot) {
        Path target = resolveTargetFile(req);
        String body = HclEmitter.emit(req, terragruntRoot);
        Optional<Path> conflict = Files.exists(target) ? Optional.of(target) : Optional.empty();
        return new CreatePlan(target, body, conflict);
    }

    public void commit(CreatePlan plan) throws IOException {
        if (Files.exists(plan.targetFile())) {
            throw new FileAlreadyExistsException(plan.targetFile().toString());
        }
        Files.createDirectories(plan.targetFile().getParent());
        Path tmp = plan.targetFile().resolveSibling(
            plan.targetFile().getFileName() + ".gruntface.tmp");
        Files.writeString(tmp, plan.contentToWrite());
        try {
            Files.move(tmp, plan.targetFile(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            // Fall back to non-atomic move (still safer than a direct write).
            Files.move(tmp, plan.targetFile(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path resolveTargetFile(CreateRequest req) {
        if (req.mode() instanceof WizardMode.IncludeFromModule) {
            return req.parentDir().resolve(req.folderName() + ".hcl");
        }
        return req.parentDir().resolve(req.folderName()).resolve("terragrunt.hcl");
    }
}
