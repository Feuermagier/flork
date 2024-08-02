package de.firemage.flork;

import org.junit.jupiter.api.Test;

import java.io.IOException;

public class BoxingTest {
    @Test
    void testBoxingInCalls() throws IOException {
        var code = """
                public class Foo {
                    public void foo(int i) {
                        bar(i);
                    }
                
                    public void bar(Integer i) { }
                }
                """;

        var context = TestUtil.getFlowContext("Foo.java", code, true);
        var method = TestUtil.getMethod("Foo", "foo", context);
        var analysis = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();
    }

    @Test
    void testUnboxingInCalls() throws IOException {
        var code = """
                public class Foo {
                    public void foo(Integer i) {
                        bar(i);
                    }
                
                    public void bar(int i) { }
                }
                """;

        var context = TestUtil.getFlowContext("Foo.java", code, true);
        var method = TestUtil.getMethod("Foo", "foo", context);
        var analysis = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();
    }

    @Test
    void testUnboxingInReturns() throws IOException {
        var code = """
                public class Foo {
                    public int foo(Integer i) {
                        return i;
                    }
                }
                """;

        var context = TestUtil.getFlowContext("Foo.java", code, true);
        var method = TestUtil.getMethod("Foo", "foo", context);
        var analysis = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();
    }

    @Test
    void testBoxingInReturns() throws IOException {
        var code = """
                public class Foo {
                    public Integer foo(int i) {
                        return i;
                    }
                }
                """;

        var context = TestUtil.getFlowContext("Foo.java", code, true);
        var method = TestUtil.getMethod("Foo", "foo", context);
        var analysis = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();
    }

    @Test
    void testUnboxingInBinary() throws IOException {
        var code = """
                public class Foo {
                    public void foo(Integer x, Integer y) {
                        int a = x + y;
                        int b = x - y;
                        int c = x * y;
                        int d = x / y;
                    }
                }
                """;

        var context = TestUtil.getFlowContext("Foo.java", code, true);
        var method = TestUtil.getMethod("Foo", "foo", context);
        var analysis = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();
    }
}
