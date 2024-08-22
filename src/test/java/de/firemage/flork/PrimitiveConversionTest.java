package de.firemage.flork;

import de.firemage.flork.flow.value.BoxedIntValueSet;
import de.firemage.flork.flow.value.IntValueSet;
import de.firemage.flork.flow.value.Nullness;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class PrimitiveConversionTest {
    @Test
    void intToLongConversion() throws IOException {
        var code = """
                public class Foo {
                    public void foo(int x) {
                        long y = x + 1L;
                    }
                }
                """;

        var context = TestUtil.getFlowContext("Foo.java", code, true);
        var method = TestUtil.getMethod("Foo", "foo", context);
        var analysis = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();
    }

    @Test
    void intToLongConversionWithCast() throws IOException {
        var code = """
                public class Foo {
                    public void foo(int x) {
                        long y = x + (long) ((int) x);
                    }
                }
                """;

        var context = TestUtil.getFlowContext("Foo.java", code, true);
        var method = TestUtil.getMethod("Foo", "foo", context);
        var analysis = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();
    }

    @Test
    void intToLongConversionOnDeclaration() throws IOException {
        var code = """
                public class Foo {
                    public void foo(int x) {
                        long y = x;
                        long z = x + 1L;
                    }
                }
                """;

        var context = TestUtil.getFlowContext("Foo.java", code, true);
        var method = TestUtil.getMethod("Foo", "foo", context);
        var analysis = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();
    }

    @Test
    void intToLongConversionOnAssignment() throws IOException {
        var code = """
                public class Foo {
                    public void foo(int x) {
                        long y = 0L;
                        y = x;
                        long z = y + 1L;
                    }
                }
                """;

        var context = TestUtil.getFlowContext("Foo.java", code, true);
        var method = TestUtil.getMethod("Foo", "foo", context);
        var analysis = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();
    }

    @Test
    void testUpcastWithBoxing() throws IOException {
        var code = """
                public class Foo {
                    public Object foo() {
                        return 0;
                    }
                }
                """;

        var context = TestUtil.getFlowContext("Foo.java", code, true);
        var method = TestUtil.getMethod("Foo", "foo", context);
        var analysis = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();

        TestUtil.mustReturn(new BoxedIntValueSet(Nullness.NON_NULL, IntValueSet.ofIntSingle(0), context), analysis);
    }
}
