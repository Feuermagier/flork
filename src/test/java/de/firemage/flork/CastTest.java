package de.firemage.flork;

import de.firemage.flork.flow.value.Nullness;
import de.firemage.flork.flow.value.ObjectValueSet;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class CastTest {
    @Test
    void testUpcast() throws IOException {
        var code = """
                public class Foo {
                    public Foo foo() {
                        Bar bar = new Bar();
                        return (Foo) bar;
                    }
                }
                
                class Bar extends Foo { }
                """;

        var context = TestUtil.getFlowContext("Foo.java", code, true);
        var method = TestUtil.getMethod("Foo", "foo", context);
        var analysis = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();

        var barType = context.getType("Bar");
        TestUtil.mustReturn(ObjectValueSet.forExactType(Nullness.NON_NULL, barType, context), analysis);
    }

    @Test
    void testDowncastKnownValue() throws IOException {
        var code = """
                import de.firemage.flork.flow.annotation.FlorkOpaque;
                
                public class Foo {
                    public Bar foo() {
                        return (Bar) makeFoo();
                    }
                
                    Foo makeFoo() {
                        return new Bar();
                    }
                }
                
                class Bar extends Foo { }
                """;

        var context = TestUtil.getFlowContext("Foo.java", code, true);
        var method = TestUtil.getMethod("Foo", "foo", context);
        var analysis = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();

        var barType = context.getType("Bar");
        TestUtil.mustReturn(ObjectValueSet.forExactType(Nullness.NON_NULL, barType, context), analysis);
    }

    @Test
    void testDowncastUnknownValue() throws IOException {
        var code = """
                import de.firemage.flork.flow.annotation.FlorkOpaque;
                
                public class Foo {
                    public Bar foo() {
                        return (Bar) makeFoo();
                    }
                
                    @FlorkOpaque
                    Foo makeFoo() {
                        return new Bar();
                    }
                }
                
                class Bar extends Foo { }
                """;

        var context = TestUtil.getFlowContext("Foo.java", code, true);
        var method = TestUtil.getMethod("Foo", "foo", context);
        var analysis = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();

        var barType = context.getType("Bar");
        TestUtil.mustReturn(ObjectValueSet.forUnconstrainedType(Nullness.UNKNOWN, barType, context), analysis);
    }

    @Test
    void testDowncastUpcast() throws IOException {
        var code = """
                import de.firemage.flork.flow.annotation.FlorkOpaque;
                
                public class Foo {
                    public Foo foo() {
                        return (Foo) (Bar) makeFoo();
                    }
                
                    @FlorkOpaque
                    Foo makeFoo() {
                        return new Bar();
                    }
                }
                
                class Bar extends Foo { }
                """;

        var context = TestUtil.getFlowContext("Foo.java", code, true);
        var method = TestUtil.getMethod("Foo", "foo", context);
        var analysis = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();

        var barType = context.getType("Bar");
        TestUtil.mustReturn(ObjectValueSet.forUnconstrainedType(Nullness.UNKNOWN, barType, context), analysis);
    }

    @Test
    void testDowncastDowncast() throws IOException {
        var code = """
                import de.firemage.flork.flow.annotation.FlorkOpaque;
                
                public class Foo {
                    public Baz foo() {
                        return (Baz) (Bar) makeFoo();
                    }
                
                    @FlorkOpaque
                    Foo makeFoo() {
                        return new Bar();
                    }
                }
                
                class Bar extends Foo { }
                
                class Baz extends Bar { }
                """;

        var context = TestUtil.getFlowContext("Foo.java", code, true);
        var method = TestUtil.getMethod("Foo", "foo", context);
        var analysis = context.getCachedMethod(method.getReference()).getFixedCallAnalysis();

        var bazType = context.getType("Baz");
        TestUtil.mustReturn(ObjectValueSet.forUnconstrainedType(Nullness.UNKNOWN, bazType, context), analysis);
    }
}
