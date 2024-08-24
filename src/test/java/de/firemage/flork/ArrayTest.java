package de.firemage.flork;

import org.junit.jupiter.api.Test;

import java.io.IOException;

public class ArrayTest {
    @Test
    void testArrayCreation() throws IOException {
        var code = """
                public class Foo {
                    public int[] foo() {
                        return new int[5];
                    }
                }
                """;

        var context = TestUtil.getFlowContext("Foo.java", code, true);
        var method = TestUtil.getMethod("Foo", "foo", context);
        var analysis = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();
    }
}
