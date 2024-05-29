package de.firemage.flork;

import de.firemage.flork.flow.FlowContext;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtMethod;
import spoon.support.compiler.FileSystemFile;
import spoon.support.compiler.FileSystemFolder;

public class TestUtil {
    public static FlowContext getFlowContext(String file, boolean closedWorld) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(new FileSystemFile(TestUtil.class.getResource("Test.java").getFile()));
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
}
