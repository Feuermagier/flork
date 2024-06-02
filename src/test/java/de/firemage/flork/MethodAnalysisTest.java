package de.firemage.flork;

import de.firemage.flork.flow.value.IntValueSet;
import org.junit.jupiter.api.Test;
import spoon.compiler.ModelBuildingException;

class MethodAnalysisTest {
    private static final String CODE = """
            public class Foo {     
                public int foo(int x) {
                    int foo = this.bar(x);
                    return foo;
                }
                        
                public int bar(int x) {
                    if (x == 1) {
                        return 0;
                    } else {
                        return -1;
                    }
                }
            }
                        
            class Bar extends Foo {
                @Override
                public int bar(int x) {
                    if (x == 1) {
                        return 1;
                    } else {
                        return 2;
                    }
                }
            }
            """;
    @Test
    public void testFlow() throws ModelBuildingException {
        var context = TestUtil.getFlowContext(CODE, true);
        var method = TestUtil.getMethod("Foo", "foo", context);
        var analysis = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();
        TestUtil.canReturn(IntValueSet.ofIntSingle(-1), analysis);
        TestUtil.canReturn(IntValueSet.ofIntSingle(0), analysis);
        TestUtil.canReturn(IntValueSet.ofIntSingle(1), analysis);
        TestUtil.canReturn(IntValueSet.ofIntSingle(2), analysis);
    }
}
