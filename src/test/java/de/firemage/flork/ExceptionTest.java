package de.firemage.flork;

import de.firemage.flork.flow.value.IntValueSet;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class ExceptionTest {
    @Test
    void trivialThrow() throws IOException {
        var code = """
                public class Foo {
                    public void foo(int x) {
                        throw new IllegalStateException();
                    }
                }
                """;

        var context = TestUtil.getFlowContext("Foo.java", code, true);
        var method = TestUtil.getMethod("Foo", "foo", context);
        var analysis = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();

        var illegalStateException = context.getType("java.lang.IllegalStateException");
        TestUtil.mustThrow(illegalStateException, analysis);
    }

    @Test
    void inferredThrow() throws IOException {
        var code = """
                public class Foo {
                    public void foo(int x) {
                        if (0 == 0) {
                            throw new IllegalStateException();
                        }
                    }
                }
                """;

        var context = TestUtil.getFlowContext("Foo.java", code, true);
        var method = TestUtil.getMethod("Foo", "foo", context);
        var analysis = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();

        var illegalStateException = context.getType("java.lang.IllegalStateException");
        TestUtil.mustThrow(illegalStateException, analysis);
    }

    @Test
    void calledThrow() throws IOException {
        var code = """
                public class Foo {
                    public void foo(int x) {
                        bar();
                    }
                
                    void bar() {
                        throw new IllegalStateException();
                    }
                }
                """;

        var context = TestUtil.getFlowContext("Foo.java", code, true);
        var method = TestUtil.getMethod("Foo", "foo", context);
        var analysis = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();

        var illegalStateException = context.getType("java.lang.IllegalStateException");
        TestUtil.mustThrow(illegalStateException, analysis);
    }

    @Test
    void inferredCalledThrow() throws IOException {
        var code = """
                public class Foo {
                    public void foo(int x) {
                        bar(0);
                    }
                
                    void bar(int x) {
                        if (x == 0) {
                            throw new IllegalStateException();
                        }
                    }
                }
                """;

        var context = TestUtil.getFlowContext("Foo.java", code, true);
        var method = TestUtil.getMethod("Foo", "foo", context);
        var analysis = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();

        var illegalStateException = context.getType("java.lang.IllegalStateException");
        TestUtil.mustThrow(illegalStateException, analysis);
    }

    @Test
    void inferredNotCalledThrow() throws IOException {
        var code = """
                public class Foo {
                    public int foo(int x) {
                        return bar(42);
                    }
                
                    void bar(int x) {
                        if (x == 0) {
                            throw new IllegalStateException();
                        }
                        return -1;
                    }
                }
                """;

        var context = TestUtil.getFlowContext("Foo.java", code, true);
        var method = TestUtil.getMethod("Foo", "foo", context);
        var analysis = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();

        TestUtil.mustReturn(IntValueSet.ofIntSingle(-1), analysis);
    }

    @Test
    void runtimeException() throws IOException {
        var code = """
                public class Foo {
                    public int foo(String x) {
                        try {
                            Integer.parseInt(x);
                            return 0;
                        } catch (NumberFormatException e) {
                            return -1;
                        }
                    }
                }
                """;

        var context = TestUtil.getFlowContext("Foo.java", code, true);
        var method = TestUtil.getMethod("Foo", "foo", context);
        var analysis = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();

        TestUtil.mustReturn(IntValueSet.ofIntSingle(0), analysis);
    }
}
