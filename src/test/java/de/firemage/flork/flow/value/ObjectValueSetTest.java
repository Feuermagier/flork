package de.firemage.flork.flow.value;

import de.firemage.flork.flow.FlowContext;
import de.firemage.flork.flow.engine.Relation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import spoon.Launcher;
import spoon.support.compiler.FileSystemFolder;
import spoon.support.compiler.VirtualFile;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectValueSetTest {
    private static final String CODE = """
            public class Top {}
            class MidA extends Top {}
            class Child1MidA extends MidA {}
            class Child2MidA extends MidA {}
            class MidB extends Top {}
            class Child1MidB extends MidB {}
            class Child2MidB extends MidB {}
            """;

    private static FlowContext context;
    
    @BeforeAll
    static void setup() {
        Launcher launcher = new Launcher();
        launcher.addInputResource(new VirtualFile(CODE));
        launcher.addInputResource(new FileSystemFolder("jdk-minified"));
        launcher.getEnvironment().setShouldCompile(false);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.buildModel();
        context = new FlowContext(launcher.getFactory(), true);
    }

    @Test
    void isSupersetOf() {
        var top = context.getType("Top");
        var midA = context.getType("MidA");
        var child1MidA = context.getType("Child1MidA");
        var midB = context.getType("MidB");

        // top vs. top - differences in nullability
        assertTrue(ObjectValueSet.forUnconstrainedType(Nullness.UNKNOWN, top, context)
                .isSupersetOf(ObjectValueSet.forUnconstrainedType(Nullness.UNKNOWN, top, context)));
        assertTrue(ObjectValueSet.forUnconstrainedType(Nullness.UNKNOWN, top, context)
                .isSupersetOf(ObjectValueSet.forUnconstrainedType(Nullness.NON_NULL, top, context)));
        assertTrue(ObjectValueSet.forUnconstrainedType(Nullness.UNKNOWN, top, context)
                .isSupersetOf(ObjectValueSet.forUnconstrainedType(Nullness.NULL, top, context)));
        assertFalse(ObjectValueSet.forUnconstrainedType(Nullness.NULL, top, context)
                .isSupersetOf(ObjectValueSet.forUnconstrainedType(Nullness.NON_NULL, top, context)));
        assertFalse(ObjectValueSet.forUnconstrainedType(Nullness.NON_NULL, top, context)
                .isSupersetOf(ObjectValueSet.forUnconstrainedType(Nullness.NULL, top, context)));
        assertTrue(ObjectValueSet.forExactType(Nullness.UNKNOWN, top, context)
                .isSupersetOf(ObjectValueSet.forExactType(Nullness.UNKNOWN, top, context)));

        // top vs midA
        assertTrue(ObjectValueSet.forUnconstrainedType(Nullness.UNKNOWN, top, context)
                .isSupersetOf(ObjectValueSet.forUnconstrainedType(Nullness.UNKNOWN, midA, context)));
        assertFalse(ObjectValueSet.forExactType(Nullness.UNKNOWN, top, context)
                .isSupersetOf(ObjectValueSet.forUnconstrainedType(Nullness.UNKNOWN, midA, context)));
        assertFalse(ObjectValueSet.forUnconstrainedType(Nullness.UNKNOWN, midA, context)
                .isSupersetOf(ObjectValueSet.forUnconstrainedType(Nullness.UNKNOWN, top, context)));
        assertTrue(new ObjectValueSet(Nullness.UNKNOWN, top, Set.of(midA), context)
                .isSupersetOf(ObjectValueSet.forUnconstrainedType(Nullness.UNKNOWN, midA, context)));
        assertTrue(new ObjectValueSet(Nullness.UNKNOWN, top, Set.of(midB), context)
                .isSupersetOf(ObjectValueSet.forUnconstrainedType(Nullness.UNKNOWN, midA, context)));

        // top vs child1midA
        assertTrue(ObjectValueSet.forUnconstrainedType(Nullness.UNKNOWN, top, context)
                .isSupersetOf(ObjectValueSet.forUnconstrainedType(Nullness.UNKNOWN, child1MidA, context)));
        assertFalse(ObjectValueSet.forExactType(Nullness.UNKNOWN, top, context)
                .isSupersetOf(ObjectValueSet.forUnconstrainedType(Nullness.UNKNOWN, child1MidA, context)));
        assertFalse(ObjectValueSet.forUnconstrainedType(Nullness.UNKNOWN, child1MidA, context)
                .isSupersetOf(ObjectValueSet.forUnconstrainedType(Nullness.UNKNOWN, top, context)));
        assertFalse(new ObjectValueSet(Nullness.UNKNOWN, top, Set.of(midA), context)
                .isSupersetOf(ObjectValueSet.forUnconstrainedType(Nullness.UNKNOWN, child1MidA, context)));
        assertTrue(new ObjectValueSet(Nullness.UNKNOWN, top, Set.of(midB), context)
                .isSupersetOf(ObjectValueSet.forUnconstrainedType(Nullness.UNKNOWN, child1MidA, context)));
    }

    @Test
    void intersect() {
        var top = context.getType("Top");
        var midA = context.getType("MidA");
        var child1MidA = context.getType("Child1MidA");
        var midB = context.getType("MidB");

        testIntersect(
                ObjectValueSet.forUnconstrainedType(Nullness.UNKNOWN, top, context),
                ObjectValueSet.forUnconstrainedType(Nullness.UNKNOWN, top, context),
                ObjectValueSet.forUnconstrainedType(Nullness.UNKNOWN, top, context)
        );

        testIntersect(
                ObjectValueSet.forUnconstrainedType(Nullness.UNKNOWN, midA, context),
                ObjectValueSet.forUnconstrainedType(Nullness.UNKNOWN, top, context),
                ObjectValueSet.forUnconstrainedType(Nullness.UNKNOWN, midA, context)
        );

        testIntersect(
                ObjectValueSet.forExactType(Nullness.UNKNOWN, midA, context),
                ObjectValueSet.forUnconstrainedType(Nullness.UNKNOWN, top, context),
                ObjectValueSet.forExactType(Nullness.UNKNOWN, midA, context)
        );

        testIntersect(
                new ObjectValueSet(Nullness.UNKNOWN, top, Set.of(midA), context),
                new ObjectValueSet(Nullness.UNKNOWN, top, Set.of(child1MidA), context),
                new ObjectValueSet(Nullness.UNKNOWN, top, Set.of(midA), context)
        );

        testIntersect(
                new ObjectValueSet(Nullness.UNKNOWN, top, Set.of(midA, midB), context),
                new ObjectValueSet(Nullness.UNKNOWN, top, Set.of(midA), context),
                new ObjectValueSet(Nullness.UNKNOWN, top, Set.of(midB), context)
        );

        testIntersect(
                ObjectValueSet.bottom(context),
                new ObjectValueSet(Nullness.UNKNOWN, midA, Set.of(midA), context),
                new ObjectValueSet(Nullness.UNKNOWN, top, Set.of(top), context)
        );

        testIntersect(
                ObjectValueSet.bottom(context),
                new ObjectValueSet(Nullness.UNKNOWN, midA, Set.of(), context),
                new ObjectValueSet(Nullness.UNKNOWN, midB, Set.of(), context)
        );

        testIntersect(
                ObjectValueSet.bottom(context),
                new ObjectValueSet(Nullness.UNKNOWN, midA, Set.of(midA), context),
                new ObjectValueSet(Nullness.UNKNOWN, midB, Set.of(), context)
        );
    }

    @Test
    void merge() {
        var top = context.getType("Top");
        var midA = context.getType("MidA");
        var child1MidA = context.getType("Child1MidA");
        var midB = context.getType("MidB");

        testMerge(
                new ObjectValueSet(Nullness.UNKNOWN, top, Set.of(top), context),
                new ObjectValueSet(Nullness.UNKNOWN, top, Set.of(top), context),
                new ObjectValueSet(Nullness.UNKNOWN, top, Set.of(top), context)
        );

        testMerge(
                new ObjectValueSet(Nullness.UNKNOWN, top, Set.of(top), context),
                new ObjectValueSet(Nullness.NON_NULL, top, Set.of(top), context),
                new ObjectValueSet(Nullness.NULL, top, Set.of(top), context)
        );

        testMerge(
                new ObjectValueSet(Nullness.NULL, top, Set.of(top), context),
                new ObjectValueSet(Nullness.NULL, top, Set.of(top), context),
                new ObjectValueSet(Nullness.NULL, top, Set.of(top), context)
        );

        testMerge(
                new ObjectValueSet(Nullness.UNKNOWN, top, Set.of(midA), context),
                new ObjectValueSet(Nullness.UNKNOWN, top, Set.of(top), context),
                new ObjectValueSet(Nullness.UNKNOWN, midA, Set.of(midA), context)
        );

        testMerge(
                new ObjectValueSet(Nullness.UNKNOWN, top, Set.of(child1MidA), context),
                new ObjectValueSet(Nullness.UNKNOWN, top, Set.of(top), context),
                new ObjectValueSet(Nullness.UNKNOWN, child1MidA, Set.of(child1MidA), context)
        );

        testMerge(
                new ObjectValueSet(Nullness.UNKNOWN, top, Set.of(), context),
                new ObjectValueSet(Nullness.UNKNOWN, top, Set.of(midB), context),
                new ObjectValueSet(Nullness.UNKNOWN, top, Set.of(midA), context)
        );

        testMerge(
                new ObjectValueSet(Nullness.UNKNOWN, top, Set.of(midA, midB), context),
                new ObjectValueSet(Nullness.UNKNOWN, midB, Set.of(midB), context),
                new ObjectValueSet(Nullness.UNKNOWN, midA, Set.of(midA), context)
        );

        testMerge(
            new ObjectValueSet(Nullness.UNKNOWN, top, Set.of(midB), context),
            new ObjectValueSet(Nullness.UNKNOWN, top, Set.of(midB), context),
            new ObjectValueSet(Nullness.UNKNOWN, midA, Set.of(midA), context)
        );
    }
    
    @Test
    void removeNotFulfillingValues() {
        var top = context.getType("Top");
        var midA = context.getType("MidA");
        var child1MidA = context.getType("Child1MidA");
        var midB = context.getType("MidB");
        
        // Relation.EQUAL
        assertEquals(
            new ObjectValueSet(Nullness.UNKNOWN, child1MidA, Set.of(), context),
            ObjectValueSet.forUnconstrainedType(Nullness.UNKNOWN, top, context)
                .removeNotFulfillingValues(ObjectValueSet.forUnconstrainedType(Nullness.UNKNOWN, child1MidA, context), Relation.EQUAL)
        );
        
        // Relation.NOT_EQUAL
        assertEquals(ObjectValueSet.forUnconstrainedType(Nullness.NULL, top, context),
            ObjectValueSet.forUnconstrainedType(Nullness.NULL, top, context)
                .removeNotFulfillingValues(ObjectValueSet.forUnconstrainedType(Nullness.NON_NULL, top, context), Relation.NOT_EQUAL)
        );

        assertEquals(ObjectValueSet.forUnconstrainedType(Nullness.NON_NULL, top, context),
            ObjectValueSet.forUnconstrainedType(Nullness.NON_NULL, top, context)
                .removeNotFulfillingValues(ObjectValueSet.forUnconstrainedType(Nullness.NON_NULL, top, context), Relation.NOT_EQUAL)
        );

        assertEquals(ObjectValueSet.bottom(context),
            ObjectValueSet.forUnconstrainedType(Nullness.NULL, top, context)
                .removeNotFulfillingValues(ObjectValueSet.forUnconstrainedType(Nullness.NULL, top, context), Relation.NOT_EQUAL)
        );
        
        assertEquals(ObjectValueSet.bottom(context),
            ObjectValueSet.forUnconstrainedType(Nullness.NULL, top, context)
                .removeNotFulfillingValues(ObjectValueSet.forUnconstrainedType(Nullness.NULL, midA, context), Relation.NOT_EQUAL)
        );
    }

    private void testIntersect(ObjectValueSet expected, ObjectValueSet a, ObjectValueSet b) {
        assertEquals(expected, a.intersect(b));
        assertEquals(expected, b.intersect(a));
    }

    private void testMerge(ObjectValueSet expected, ObjectValueSet a, ObjectValueSet b) {
        assertEquals(expected, a.merge(b));
        assertEquals(expected, b.merge(a));
    }
}
