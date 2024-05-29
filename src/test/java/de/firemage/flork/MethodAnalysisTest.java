package de.firemage.flork;

import org.junit.jupiter.api.Test;
import spoon.compiler.ModelBuildingException;

class MethodAnalysisTest {
    @Test
    public void testFlow() throws ModelBuildingException {
        var context = TestUtil.getFlowContext("Test.java", true);
        var method = TestUtil.getMethod("Foo", "foo", context);
        System.out.println(method);
        var result = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();
    }
}
