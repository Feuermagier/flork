package de.firemage.flork;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

public class A1Test {
    @Test
    public void testTerminalPrintLine() throws IOException {
        var context = TestUtil.getFlowContext(Path.of("test_inputs", "A1"), true);
        var method = TestUtil.getMethod("edu.kit.informatik.Terminal", "printLine", context);
        var analysis = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();
    }

    @Test
    public void testMain() throws IOException {
        var context = TestUtil.getFlowContext(Path.of("test_inputs", "A1"), true);
        var method = TestUtil.getMethod("edu.kit.informatik.UI", "main", context);
        var analysis = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();
    }

    @Test
    public void testTerminalReadLine() throws IOException {
        var context = TestUtil.getFlowContext(Path.of("test_inputs", "A1"), true);
        var method = TestUtil.getMethod("edu.kit.informatik.Terminal", "readLine", context);
        var analysis = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();
    }
}
