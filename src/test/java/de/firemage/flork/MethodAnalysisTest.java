package de.firemage.flork;

import de.firemage.flork.flow.FlowAnalysis;
import org.junit.jupiter.api.Test;
import spoon.Launcher;
import spoon.compiler.ModelBuildingException;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtMethod;
import spoon.support.compiler.FileSystemFile;

class MethodAnalysisTest {
    @Test
    public void testFlow() throws ModelBuildingException {
        Launcher launcher = new Launcher();
        launcher.addInputResource(new FileSystemFile(this.getClass().getResource("Test.java").getFile()));
        launcher.getEnvironment().setShouldCompile(false);
        launcher.getEnvironment().setSourceClasspath(new String[] {this.getClass().getResource("./").getFile()});
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setComplianceLevel(17);

        CtModel model = launcher.buildModel();

        var analysis = new FlowAnalysis(model);
        var method = (CtMethod<?>) model.getElements(CtMethod.class::isInstance).get(0);
        System.out.println(method);
        var engine = analysis.analyzeMethod(method);

        System.out.println(engine);
    }
}
