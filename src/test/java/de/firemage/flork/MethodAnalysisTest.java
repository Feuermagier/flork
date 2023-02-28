package de.firemage.flork;

import de.firemage.flork.flow.FlowContext;
import org.junit.jupiter.api.Test;
import spoon.Launcher;
import spoon.compiler.ModelBuildingException;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.compiler.FileSystemFile;
import spoon.support.compiler.FileSystemFolder;

class MethodAnalysisTest {
    @Test
    public void testFlow() throws ModelBuildingException {
        Launcher launcher = new Launcher();
        launcher.addInputResource(new FileSystemFile(this.getClass().getResource("Test.java").getFile()));
        launcher.addInputResource(new FileSystemFolder("jdk-minified"));
        launcher.getEnvironment().setShouldCompile(false);
        //launcher.getEnvironment().setSourceClasspath(new String[] {this.getClass().getResource("./").getFile()});
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setComplianceLevel(17);

        CtModel model = launcher.buildModel();

        var context = new FlowContext(launcher.getFactory(), true);
        var method = (CtMethod<?>) model.getUnnamedModule()
            .getElements(CtMethod.class::isInstance)
            .stream()
            .filter(m -> ((CtMethod<?>) m).getSimpleName().equals("foo"))
            .findFirst().get();
        System.out.println(method);
        var result = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();
    }
}
