package de.firemage.flork;

import de.firemage.flork.flow.value.IntValueSet;
import org.junit.jupiter.api.Test;
import spoon.compiler.ModelBuildingException;

import java.io.IOException;
import java.nio.file.Path;

public class A1Test {
    @Test
    public void testFlow() throws ModelBuildingException, IOException {
        var context = TestUtil.getFlowContext(Path.of("test_inputs", "A1"), true);
        var method = TestUtil.getMethod("edu.kit.informatik.Terminal", "printLine", context);
        var analysis = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();
        TestUtil.canReturn(IntValueSet.ofIntSingle(-1), analysis);
        TestUtil.canReturn(IntValueSet.ofIntSingle(0), analysis);
        TestUtil.canReturn(IntValueSet.ofIntSingle(1), analysis);
        TestUtil.canReturn(IntValueSet.ofIntSingle(2), analysis);
    }
}
