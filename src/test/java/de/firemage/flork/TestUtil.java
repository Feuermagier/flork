package de.firemage.flork;

import de.firemage.flork.flow.FlowContext;
import de.firemage.flork.flow.analysis.MethodAnalysis;
import de.firemage.flork.flow.value.ValueSet;
import spoon.Launcher;
import spoon.compiler.SpoonResource;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtMethod;
import spoon.support.compiler.FileSystemFile;
import spoon.support.compiler.FileSystemFolder;
import spoon.support.compiler.VirtualFile;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestUtil {
    public static FlowContext getFlowContext(Path path, boolean closedWorld) {
        return getFlowContext(new FileSystemFile(path.toFile()), closedWorld);
    }

    public static FlowContext getFlowContext(String fileContent, boolean closedWorld) {
        return getFlowContext(new VirtualFile(fileContent), closedWorld);
    }

    public static FlowContext getFlowContext(SpoonResource resource, boolean closedWorld) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(resource);
        launcher.addInputResource(new FileSystemFolder("jdk-minified"));
        launcher.getEnvironment().setShouldCompile(false);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setComplianceLevel(17);

        CtModel model = launcher.buildModel();
        return new FlowContext(launcher.getFactory(), closedWorld);
    }

    public static CtMethod<?> getMethod(String type, String method, FlowContext context) {
        return (CtMethod<?>) context.getModel().getUnnamedModule()
                .getElements(CtMethod.class::isInstance)
                .stream()
                .filter(e -> e instanceof CtMethod<?> m && m.getSimpleName().equals("foo") && m.getDeclaringType().getSimpleName().equals("Foo"))
                .findFirst().get();
    }

    public static void canReturn(ValueSet value, MethodAnalysis analysis) {
        boolean ok = analysis.getReturnStates().stream().anyMatch(r -> r.value().isSupersetOf(value));
        assertTrue(ok);
    }

    public static void cannotReturn(ValueSet value, MethodAnalysis analysis) {
        boolean ok = analysis.getReturnStates().stream().noneMatch(r -> r.value().isSupersetOf(value));
        assertTrue(ok);
    }
}
