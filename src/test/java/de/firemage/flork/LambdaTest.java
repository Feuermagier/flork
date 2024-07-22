package de.firemage.flork;

import org.junit.jupiter.api.Test;

import java.io.IOException;

public class LambdaTest {
    @Test
    void testSimpleConsumer() throws IOException {
        var code = """
                import java.util.function.Function;
                
                public class Foo {
                    public int foo() {
                        Function<Foo, Integer> p = x -> {
                            System.out.println("Hello");
                            return 1;
                        };
                        return p.apply(new Foo());
                    }
                }
                """;
        var context = TestUtil.getFlowContext("Foo.java", code, true);
        var method = TestUtil.getMethod("Foo", "foo", context);
        var analysis = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();
    }
}
