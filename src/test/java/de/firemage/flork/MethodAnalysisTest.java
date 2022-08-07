package de.firemage.flork;

import de.firemage.flork.flow.BooleanValueSet;
import de.firemage.flork.flow.FlowAnalysis;
import de.firemage.flork.flow.IntValueSet;
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

        var analysis = new FlowAnalysis();
        var method = (CtMethod<?>) model.getElements(CtMethod.class::isInstance).get(0);
        System.out.println(method);
        var result = analysis.analyzeMethod(method);

        System.out.println(result.getReturnStates().size() + " return states: " + result.getReturnStates());

        System.out.println(IntValueSet.ofIntSingle(0).merge(IntValueSet.ofIntSingle(1)));
    }
}
