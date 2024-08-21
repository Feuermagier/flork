package de.firemage.flork;

import de.firemage.flork.flow.value.IntValueSet;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class OpaqueMethodTest {
    @Test
    void testEquals() throws IOException {
        var code = """
                public class Foo {
                    public int foo() {
                        if (new Object().equals(new Object())) {
                            return 0;
                        } else {
                            return 1;
                        }
                    }
                }
                """;
        var context = TestUtil.getFlowContext("Foo.java", code, true);
        var method = TestUtil.getMethod("Foo", "foo", context);
        var analysis = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();
        TestUtil.canReturn(IntValueSet.ofIntSingle(0), analysis);
        TestUtil.canReturn(IntValueSet.ofIntSingle(1), analysis);
    }
}
