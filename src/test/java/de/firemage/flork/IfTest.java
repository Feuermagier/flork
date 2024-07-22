package de.firemage.flork;

import de.firemage.flork.flow.value.IntValueSet;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class IfTest {
    @Test
    void testSimpleIf() throws IOException {
        var code = """
                public class Foo {
                    public int foo(int x) {
                        if (x == 0) {
                            return 0;
                        } else if (x == 1) {
                            return 1;
                        } else {
                            return 2;
                        }
                    }
                }
                """;

        var context = TestUtil.getFlowContext("Foo.java", code, true);
        var method = TestUtil.getMethod("Foo", "foo", context);
        var analysis = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();
        TestUtil.canReturn(IntValueSet.ofIntSingle(0), analysis);
        TestUtil.canReturn(IntValueSet.ofIntSingle(1), analysis);
        TestUtil.canReturn(IntValueSet.ofIntSingle(2), analysis);
    }

    @Test
    void testIfInContext() throws IOException {
        var code = """
                public class Foo {
                    public int foo(int x) {
                        int x = 0;
                        if (x == 0) {
                            x = 1;
                        }
                
                        x = x + 2;
                
                        if (x < 2) {
                            return 0;
                        } else if (x > 3) {
                            return 1;
                        } else {
                            return 2;
                        }
                    }
                }
                """;

        var context = TestUtil.getFlowContext("Foo.java", code, true);
        var method = TestUtil.getMethod("Foo", "foo", context);
        var analysis = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();
        TestUtil.mustReturn(IntValueSet.ofIntSingle(2), analysis);
    }

    @Test
    void testImpossibleIfBranch() throws IOException {
        var code = """
                public class Foo {
                    public int foo(int x) {
                        if (x == 0) {
                            return 0;
                        } else if (x == 0) {
                            return 1;
                        } else {
                            return 2;
                        }
                    }
                }
                """;

        var context = TestUtil.getFlowContext("Foo.java", code, true);
        var method = TestUtil.getMethod("Foo", "foo", context);
        var analysis = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();
        TestUtil.canReturn(IntValueSet.ofIntSingle(0), analysis);
        TestUtil.cannotReturn(IntValueSet.ofIntSingle(1), analysis);
        TestUtil.canReturn(IntValueSet.ofIntSingle(2), analysis);
    }
}
