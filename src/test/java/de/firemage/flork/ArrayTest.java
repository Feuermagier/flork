package de.firemage.flork;

import org.junit.jupiter.api.Test;

import java.io.IOException;

public class ArrayTest {
    @Test
    void testArrayCreation() throws IOException {
        var code = """
                public class Foo {
                    public void foo() {
                        var x = new int[5][3];
                        var x = new int[5][];
                        Integer[][] y = {{1, 2, 3}, {4}};
                    }
                }
                """;

        var context = TestUtil.getFlowContext("Foo.java", code, true);
        var method = TestUtil.getMethod("Foo", "foo", context);
        var analysis = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();
    }

    @Test
    void testArrayCasts() throws IOException {
        var code = """
                public class Foo {
                    public void foo() {
                        Object[] y = {{1, 2, 3}, {4}};
                        Object z = y;
                        Object[] wtf = (Object[]) z;
                    }
                }
                """;

        var context = TestUtil.getFlowContext("Foo.java", code, true);
        var method = TestUtil.getMethod("Foo", "foo", context);
        var analysis = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();
    }
}
