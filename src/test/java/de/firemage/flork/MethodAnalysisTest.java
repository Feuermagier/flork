package de.firemage.flork;

import de.firemage.flork.flow.FlowAnalysis;
import de.firemage.flork.flow.FlowContext;
import org.junit.jupiter.api.Test;
import spoon.Launcher;
import spoon.compiler.ModelBuildingException;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtMethod;
import spoon.support.compiler.FileSystemFile;
import spoon.support.compiler.FileSystemFolder;

class MethodAnalysisTest {
    @Test
    public void testFlow() throws ModelBuildingException {
        Launcher launcher = new Launcher();
        launcher.addInputResource(new FileSystemFile(this.getClass().getResource("Test.java").getFile()));
        launcher.addInputResource(new FileSystemFolder("C:\\Users\\flose\\Downloads\\jdk-minified"));
        launcher.getEnvironment().setShouldCompile(false);
        //launcher.getEnvironment().setSourceClasspath(new String[] {this.getClass().getResource("./").getFile()});
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setComplianceLevel(17);

        CtModel model = launcher.buildModel();

        var analysis = new FlowAnalysis(new FlowContext(model, true));
        var method = (CtMethod<?>) model.getUnnamedModule().getElements(CtMethod.class::isInstance).get(0);
        System.out.println(method);
        var result = analysis.analyzeMethod(method.getReference());

        System.out.println(result.getReturnStates().size() + " return states: " + result.getReturnStates());
    }
}
