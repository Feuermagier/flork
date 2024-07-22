package de.firemage.flork;

import de.firemage.flork.flow.value.IntValueSet;
import org.junit.jupiter.api.Test;
import spoon.compiler.ModelBuildingException;

import java.io.IOException;

public class DispatchTest {
    @Test
    void testDynamicDispatch() throws ModelBuildingException, IOException {
        var code = """
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

        var context = TestUtil.getFlowContext("Foo.java", code, true);
        var method = TestUtil.getMethod("Foo", "foo", context);
        var analysis = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();
        TestUtil.canReturn(IntValueSet.ofIntSingle(-1), analysis);
        TestUtil.canReturn(IntValueSet.ofIntSingle(0), analysis);
        TestUtil.canReturn(IntValueSet.ofIntSingle(1), analysis);
        TestUtil.canReturn(IntValueSet.ofIntSingle(2), analysis);
    }
}
