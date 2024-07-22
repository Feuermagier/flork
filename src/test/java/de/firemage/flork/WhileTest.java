package de.firemage.flork;

import de.firemage.flork.flow.value.IntValueSet;
import org.junit.jupiter.api.Test;

public class WhileTest {
    @Test
    void testImpossibleWhile() {
        var code = """
            public class Foo {
                public int foo() {
                    int i = 0;
                    while (1 != 1) {
                        i = i + 1;
                    }
                    return i;
                }
            }
            """;

        var context = TestUtil.getFlowContext(code, true);
        var method = TestUtil.getMethod("Foo", "foo", context);
        var analysis = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();
        TestUtil.mustReturn(IntValueSet.ofIntSingle(0), analysis);
    }

    @Test
    void testSingleIterWhile() {
        var code = """
            public class Foo {
                public int foo() {
                    int i = 0;
                    while (i < 1) {
                        i = i + 1;
                    }
                    return i;
                }
            }
            """;

        var context = TestUtil.getFlowContext(code, true);
        var method = TestUtil.getMethod("Foo", "foo", context);
        var analysis = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();
        TestUtil.mustReturn(IntValueSet.ofIntSingle(1), analysis);
    }

    @Test
    void testMultiIterWhile() {
        var code = """
            public class Foo {
                public int foo() {
                    int i = 0;
                    while (i < 10) {
                        i = i + 1;
                    }
                    return i;
                }
            }
            """;

        var context = TestUtil.getFlowContext(code, true);
        var method = TestUtil.getMethod("Foo", "foo", context);
        var analysis = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();
        TestUtil.mustReturn(IntValueSet.ofIntRange(10, Integer.MAX_VALUE), analysis);
    }

    @Test
    void testFieldWriteInLoop() {
        var code = """
            public class Foo {
                int field = 0;
            
                public int foo() {
                    int i = 0;
                    while (i < 10) {
                        i = i + 1;
                        if (i < 3) {
                            field = field - 2;
                        }
                    }
                    return field;
                }
            }
            """;

        var context = TestUtil.getFlowContext(code, true);
        var method = TestUtil.getMethod("Foo", "foo", context);
        var analysis = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();
        TestUtil.mustReturn(IntValueSet.topForInt(), analysis);
    }
}
