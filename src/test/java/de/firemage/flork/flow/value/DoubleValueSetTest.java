package de.firemage.flork.flow.value;

import de.firemage.flork.flow.BooleanStatus;
import de.firemage.flork.flow.engine.Relation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class DoubleValueSetTest {
    @Test
    void testAdd() {
        assertEquals(DoubleValueSet.ofSingle(2.0), DoubleValueSet.ofSingle(1.0).add(DoubleValueSet.ofSingle(1.0)));
        assertEquals(DoubleValueSet.ofRange(2.0, 5.0), DoubleValueSet.ofRange(1.0, 2.0).add(DoubleValueSet.ofRange(1.0, 3.0)));
        assertEquals(DoubleValueSet.ofRange(2.0, Double.POSITIVE_INFINITY), DoubleValueSet.ofSingle(1.0).add(DoubleValueSet.ofRange(1.0, Double.POSITIVE_INFINITY)));
        assertEquals(DoubleValueSet.ofRange(Double.NEGATIVE_INFINITY, -1.0), DoubleValueSet.ofSingle(1.0).add(DoubleValueSet.ofRange(Double.NEGATIVE_INFINITY, -2.0)));
        assertEquals(DoubleValueSet.ofRangeWithNaN(2.0, 5.0), DoubleValueSet.ofRangeWithNaN(1.0, 2.0).add(DoubleValueSet.ofRange(1.0, 3.0)));

        assertSame(DoubleValueSet.NAN, DoubleValueSet.ofSingle(1.0).add(DoubleValueSet.NAN));
        assertSame(DoubleValueSet.TOP, DoubleValueSet.TOP.add(DoubleValueSet.TOP));
    }

    @Test
    void testSubtract() {
        assertEquals(DoubleValueSet.ofSingle(0.0), DoubleValueSet.ofSingle(1.0).subtract(DoubleValueSet.ofSingle(1.0)));
        assertEquals(DoubleValueSet.ofRange(-2.0, 1.0), DoubleValueSet.ofRange(1.0, 2.0).subtract(DoubleValueSet.ofRange(1.0, 3.0)));
        assertEquals(DoubleValueSet.ofRange(Double.NEGATIVE_INFINITY, 0.0), DoubleValueSet.ofSingle(1.0).subtract(DoubleValueSet.ofRange(1.0, Double.POSITIVE_INFINITY)));
        assertEquals(DoubleValueSet.ofRange(3.0, Double.POSITIVE_INFINITY), DoubleValueSet.ofSingle(1.0).subtract(DoubleValueSet.ofRange(Double.NEGATIVE_INFINITY, -2.0)));
        assertEquals(DoubleValueSet.ofRangeWithNaN(-2.0, 1.0), DoubleValueSet.ofRangeWithNaN(1.0, 2.0).subtract(DoubleValueSet.ofRange(1.0, 3.0)));

        assertSame(DoubleValueSet.NAN, DoubleValueSet.ofSingle(1.0).subtract(DoubleValueSet.NAN));
        assertSame(DoubleValueSet.TOP, DoubleValueSet.TOP.subtract(DoubleValueSet.TOP));
    }

    @Test
    void testMultiply() {
        assertEquals(DoubleValueSet.ofSingle(6.0), DoubleValueSet.ofSingle(3.0).multiply(DoubleValueSet.ofSingle(2.0)));
        assertEquals(DoubleValueSet.ofSingle(-6.0), DoubleValueSet.ofSingle(-3.0).multiply(DoubleValueSet.ofSingle(2.0)));
        assertEquals(DoubleValueSet.ofSingle(-6.0), DoubleValueSet.ofSingle(3.0).multiply(DoubleValueSet.ofSingle(-2.0)));
        assertEquals(DoubleValueSet.ofSingle(6.0), DoubleValueSet.ofSingle(-3.0).multiply(DoubleValueSet.ofSingle(-2.0)));

        assertEquals(DoubleValueSet.ofRange(1.0, 4.0), DoubleValueSet.ofRange(1.0, 2.0).multiply(DoubleValueSet.ofRange(1.0, 2.0)));
        assertEquals(DoubleValueSet.ofRange(-2.0, 4.0), DoubleValueSet.ofRange(-1.0, 2.0).multiply(DoubleValueSet.ofRange(1.0, 2.0)));
        assertEquals(DoubleValueSet.ofRange(-4.0, 2.0), DoubleValueSet.ofRange(-2.0, -1.0).multiply(DoubleValueSet.ofRange(-1.0, 2.0)));

        assertEquals(DoubleValueSet.ofRange(Double.NEGATIVE_INFINITY, 4.0), DoubleValueSet.ofRange(1.0, 2.0).multiply(DoubleValueSet.ofRange(Double.NEGATIVE_INFINITY, 2.0)));
        assertEquals(DoubleValueSet.ofRange(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY), DoubleValueSet.ofRange(Double.NEGATIVE_INFINITY, 1.0).multiply(DoubleValueSet.ofRange(-1.0, 1.0)));

        assertEquals(DoubleValueSet.ofRangeWithNaN(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY), DoubleValueSet.ofRange(Double.NEGATIVE_INFINITY, 1.0).multiply(DoubleValueSet.ofRangeWithNaN(-1.0, 1.0)));

        assertSame(DoubleValueSet.TOP, DoubleValueSet.TOP.multiply(DoubleValueSet.TOP));
        assertSame(DoubleValueSet.NAN, DoubleValueSet.ofSingle(1.0).multiply(DoubleValueSet.NAN));
    }

    @Test
    void testDivide() {
        assertEquals(DoubleValueSet.ofSingle(3.0 / 2.0), DoubleValueSet.ofSingle(3.0).divide(DoubleValueSet.ofSingle(2.0)));
        assertEquals(DoubleValueSet.ofSingle(-3.0 / 2.0), DoubleValueSet.ofSingle(-3.0).divide(DoubleValueSet.ofSingle(2.0)));
        assertEquals(DoubleValueSet.ofSingle(-3.0 / 2.0), DoubleValueSet.ofSingle(3.0).divide(DoubleValueSet.ofSingle(-2.0)));
        assertEquals(DoubleValueSet.ofSingle(3.0 / 2.0), DoubleValueSet.ofSingle(-3.0).divide(DoubleValueSet.ofSingle(-2.0)));

        assertEquals(DoubleValueSet.ofRange(0.5, 2.0), DoubleValueSet.ofRange(1.0, 2.0).divide(DoubleValueSet.ofRange(1.0, 2.0)));
        assertEquals(DoubleValueSet.ofRange(-1.0, 2.0), DoubleValueSet.ofRange(-1.0, 2.0).divide(DoubleValueSet.ofRange(1.0, 2.0)));
        assertEquals(DoubleValueSet.ofRange(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY), DoubleValueSet.ofRange(-2.0, -1.0).divide(DoubleValueSet.ofRange(-1.0, 2.0)));

        assertEquals(DoubleValueSet.ofRange(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY), DoubleValueSet.ofRange(1.0, 2.0).divide(DoubleValueSet.ofRange(Double.NEGATIVE_INFINITY, 0.0)));
        assertEquals(DoubleValueSet.ofRange(0.0, Double.POSITIVE_INFINITY), DoubleValueSet.ofRange(1.0, 2.0).divide(DoubleValueSet.ofRange(0.0, Double.POSITIVE_INFINITY)));
        assertEquals(DoubleValueSet.ofRange(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY), DoubleValueSet.ofRange(-1.0, 2.0).divide(DoubleValueSet.ofRange(0.0, Double.POSITIVE_INFINITY)));

        assertEquals(DoubleValueSet.ofRangeWithNaN(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY), DoubleValueSet.ofRange(Double.NEGATIVE_INFINITY, 1.0).divide(DoubleValueSet.ofRangeWithNaN(-1.0, 1.0)));

        assertSame(DoubleValueSet.TOP, DoubleValueSet.TOP.divide(DoubleValueSet.TOP));
        assertSame(DoubleValueSet.NAN, DoubleValueSet.ofSingle(1.0).divide(DoubleValueSet.NAN));
    }

    @Test
    void testFulfillsRelation() {
        assertEquals(BooleanStatus.SOMETIMES, DoubleValueSet.TOP.fulfillsRelation(DoubleValueSet.TOP, Relation.EQUAL));
        assertEquals(BooleanStatus.SOMETIMES, DoubleValueSet.TOP.fulfillsRelation(DoubleValueSet.TOP, Relation.NOT_EQUAL));

        assertEquals(BooleanStatus.ALWAYS, DoubleValueSet.NAN.fulfillsRelation(DoubleValueSet.NAN, Relation.NOT_EQUAL));
        assertEquals(BooleanStatus.NEVER, DoubleValueSet.NAN.fulfillsRelation(DoubleValueSet.NAN, Relation.EQUAL));
        assertEquals(BooleanStatus.NEVER, DoubleValueSet.NAN.fulfillsRelation(DoubleValueSet.NAN, Relation.LESS_THAN));
        assertEquals(BooleanStatus.NEVER, DoubleValueSet.NAN.fulfillsRelation(DoubleValueSet.NAN, Relation.LESS_THAN_EQUAL));
        assertEquals(BooleanStatus.NEVER, DoubleValueSet.NAN.fulfillsRelation(DoubleValueSet.NAN, Relation.GREATER_THAN));
        assertEquals(BooleanStatus.NEVER, DoubleValueSet.NAN.fulfillsRelation(DoubleValueSet.NAN, Relation.GREATER_THAN_EQUAL));

        assertEquals(BooleanStatus.SOMETIMES, DoubleValueSet.ofRange(1.0, 2.0).fulfillsRelation(DoubleValueSet.ofRange(1.0, 2.0), Relation.EQUAL));
        assertEquals(BooleanStatus.SOMETIMES, DoubleValueSet.ofRange(1.0, 2.0).fulfillsRelation(DoubleValueSet.ofRangeWithNaN(1.0, 2.0), Relation.EQUAL));
        assertEquals(BooleanStatus.NEVER, DoubleValueSet.ofRange(1.0, 2.0).fulfillsRelation(DoubleValueSet.ofRange(3.0, 4.0), Relation.EQUAL));
        assertEquals(BooleanStatus.NEVER, DoubleValueSet.ofRange(1.0, 2.0).fulfillsRelation(DoubleValueSet.ofRangeWithNaN(3.0, 4.0), Relation.EQUAL));

        assertEquals(BooleanStatus.SOMETIMES, DoubleValueSet.ofRange(1.0, 2.0).fulfillsRelation(DoubleValueSet.ofRange(1.0, 2.0), Relation.NOT_EQUAL));
        assertEquals(BooleanStatus.ALWAYS, DoubleValueSet.ofRange(1.0, 2.0).fulfillsRelation(DoubleValueSet.ofRange(3.0, 4.0), Relation.NOT_EQUAL));

        assertEquals(BooleanStatus.SOMETIMES, DoubleValueSet.ofRange(1.0, 2.0).fulfillsRelation(DoubleValueSet.ofRange(1.0, 2.0), Relation.LESS_THAN));
        assertEquals(BooleanStatus.SOMETIMES, DoubleValueSet.ofRange(1.0, 2.0).fulfillsRelation(DoubleValueSet.ofRangeWithNaN(1.0, 2.0), Relation.LESS_THAN));
        assertEquals(BooleanStatus.ALWAYS, DoubleValueSet.ofRange(1.0, 2.0).fulfillsRelation(DoubleValueSet.ofRange(3.0, 4.0), Relation.LESS_THAN));
        assertEquals(BooleanStatus.SOMETIMES, DoubleValueSet.ofRange(1.0, 2.0).fulfillsRelation(DoubleValueSet.ofRange(2.0, 4.0), Relation.LESS_THAN));
        assertEquals(BooleanStatus.SOMETIMES, DoubleValueSet.ofRange(1.0, 2.0).fulfillsRelation(DoubleValueSet.ofRangeWithNaN(3.0, 4.0), Relation.LESS_THAN));
        assertEquals(BooleanStatus.NEVER, DoubleValueSet.ofRange(1.0, 2.0).fulfillsRelation(DoubleValueSet.ofRange(-2.0, -1.0), Relation.LESS_THAN));

        assertEquals(BooleanStatus.SOMETIMES, DoubleValueSet.ofRange(1.0, 2.0).fulfillsRelation(DoubleValueSet.ofRange(1.0, 2.0), Relation.LESS_THAN_EQUAL));
        assertEquals(BooleanStatus.SOMETIMES, DoubleValueSet.ofRange(1.0, 2.0).fulfillsRelation(DoubleValueSet.ofRangeWithNaN(1.0, 2.0), Relation.LESS_THAN_EQUAL));
        assertEquals(BooleanStatus.ALWAYS, DoubleValueSet.ofRange(1.0, 2.0).fulfillsRelation(DoubleValueSet.ofRange(3.0, 4.0), Relation.LESS_THAN_EQUAL));
        assertEquals(BooleanStatus.ALWAYS, DoubleValueSet.ofRange(1.0, 2.0).fulfillsRelation(DoubleValueSet.ofRange(2.0, 4.0), Relation.LESS_THAN_EQUAL));
        assertEquals(BooleanStatus.SOMETIMES, DoubleValueSet.ofRange(1.0, 2.0).fulfillsRelation(DoubleValueSet.ofRangeWithNaN(3.0, 4.0), Relation.LESS_THAN_EQUAL));
        assertEquals(BooleanStatus.NEVER, DoubleValueSet.ofRange(1.0, 2.0).fulfillsRelation(DoubleValueSet.ofRange(-2.0, -1.0), Relation.LESS_THAN_EQUAL));

        assertEquals(BooleanStatus.SOMETIMES, DoubleValueSet.ofRange(1.0, 2.0).fulfillsRelation(DoubleValueSet.ofRange(1.0, 2.0), Relation.GREATER_THAN));
        assertEquals(BooleanStatus.SOMETIMES, DoubleValueSet.ofRange(1.0, 2.0).fulfillsRelation(DoubleValueSet.ofRangeWithNaN(1.0, 2.0), Relation.GREATER_THAN));
        assertEquals(BooleanStatus.ALWAYS, DoubleValueSet.ofRange(3.0, 4.0).fulfillsRelation(DoubleValueSet.ofRange(1.0, 2.0), Relation.GREATER_THAN));
        assertEquals(BooleanStatus.SOMETIMES, DoubleValueSet.ofRange(2.0, 4.0).fulfillsRelation(DoubleValueSet.ofRange(1.0, 2.0), Relation.GREATER_THAN));
        assertEquals(BooleanStatus.SOMETIMES, DoubleValueSet.ofRange(3.0, 4.0).fulfillsRelation(DoubleValueSet.ofRangeWithNaN(1.0, 2.0), Relation.GREATER_THAN));
        assertEquals(BooleanStatus.NEVER, DoubleValueSet.ofRange(-2.0, -1.0).fulfillsRelation(DoubleValueSet.ofRange(1.0, 2.0), Relation.GREATER_THAN));

        assertEquals(BooleanStatus.SOMETIMES, DoubleValueSet.ofRange(1.0, 2.0).fulfillsRelation(DoubleValueSet.ofRange(1.0, 2.0), Relation.GREATER_THAN_EQUAL));
        assertEquals(BooleanStatus.SOMETIMES, DoubleValueSet.ofRange(1.0, 2.0).fulfillsRelation(DoubleValueSet.ofRangeWithNaN(1.0, 2.0), Relation.GREATER_THAN_EQUAL));
        assertEquals(BooleanStatus.ALWAYS, DoubleValueSet.ofRange(3.0, 4.0).fulfillsRelation(DoubleValueSet.ofRange(1.0, 2.0), Relation.GREATER_THAN_EQUAL));
        assertEquals(BooleanStatus.ALWAYS, DoubleValueSet.ofRange(2.0, 4.0).fulfillsRelation(DoubleValueSet.ofRange(1.0, 2.0), Relation.GREATER_THAN_EQUAL));
        assertEquals(BooleanStatus.SOMETIMES, DoubleValueSet.ofRange(3.0, 4.0).fulfillsRelation(DoubleValueSet.ofRangeWithNaN(1.0, 2.0), Relation.GREATER_THAN_EQUAL));
        assertEquals(BooleanStatus.NEVER, DoubleValueSet.ofRange(-2.0, -1.0).fulfillsRelation(DoubleValueSet.ofRange(1.0, 2.0), Relation.GREATER_THAN_EQUAL));
    }

    @Test
    void testRemoveNotFulfillingValues() {
        assertEquals(DoubleValueSet.ofRange(1.0, 2.0), DoubleValueSet.ofRange(1.0, 2.0).removeNotFulfillingValues(DoubleValueSet.ofRange(1.0, 2.0), Relation.EQUAL));
        assertEquals(DoubleValueSet.ofRange(1.0, 2.0), DoubleValueSet.ofRange(1.0, 2.0).removeNotFulfillingValues(DoubleValueSet.ofRangeWithNaN(1.0, 2.0), Relation.EQUAL));
        assertEquals(DoubleValueSet.BOTTOM, DoubleValueSet.ofRange(1.0, 2.0).removeNotFulfillingValues(DoubleValueSet.ofRange(3.0, 4.0), Relation.EQUAL));
        assertEquals(DoubleValueSet.BOTTOM, DoubleValueSet.ofRange(1.0, 2.0).removeNotFulfillingValues(DoubleValueSet.ofRangeWithNaN(3.0, 4.0), Relation.EQUAL));

        assertEquals(DoubleValueSet.BOTTOM, DoubleValueSet.ofSingle(1.0).removeNotFulfillingValues(DoubleValueSet.ofSingle(1.0), Relation.NOT_EQUAL));
        assertEquals(DoubleValueSet.ofSingle(1.0), DoubleValueSet.ofSingle(1.0).removeNotFulfillingValues(DoubleValueSet.ofSingle(2.0), Relation.NOT_EQUAL));
        assertEquals(DoubleValueSet.ofSingle(1.0), DoubleValueSet.ofSingle(1.0).removeNotFulfillingValues(DoubleValueSet.ofRangeWithNaN(2.0, 2.0), Relation.NOT_EQUAL));
        assertEquals(DoubleValueSet.ofRange(1.0, 2.0), DoubleValueSet.ofRange(1.0, 2.0).removeNotFulfillingValues(DoubleValueSet.ofRange(1.0, 2.0), Relation.NOT_EQUAL));
        assertEquals(DoubleValueSet.ofRange(1.0, 2.0), DoubleValueSet.ofRange(1.0, 2.0).removeNotFulfillingValues(DoubleValueSet.ofRangeWithNaN(1.0, 2.0), Relation.NOT_EQUAL));
        assertEquals(DoubleValueSet.ofSingle(2.0), DoubleValueSet.ofRange(1.0, 2.0).removeNotFulfillingValues(DoubleValueSet.ofSingle(1.0), Relation.NOT_EQUAL));
        assertEquals(DoubleValueSet.ofSingle(1.0), DoubleValueSet.ofRange(1.0, 2.0).removeNotFulfillingValues(DoubleValueSet.ofSingle(2.0), Relation.NOT_EQUAL));
        assertEquals(DoubleValueSet.ofRange(1.0, 2.0), DoubleValueSet.ofRange(1.0, 2.0).removeNotFulfillingValues(DoubleValueSet.ofRangeWithNaN(2.0, 2.0), Relation.NOT_EQUAL));

        assertEquals(DoubleValueSet.ofSingle(1.0), DoubleValueSet.ofRange(1.0, 2.0).removeNotFulfillingValues(DoubleValueSet.ofRange(1.0, 2.0), Relation.LESS_THAN));
        assertEquals(DoubleValueSet.ofRange(1.0, 2.0), DoubleValueSet.ofRange(1.0, 2.0).removeNotFulfillingValues(DoubleValueSet.ofRange(3.0, 4.0), Relation.LESS_THAN));
        assertEquals(DoubleValueSet.BOTTOM, DoubleValueSet.ofRange(3.0, 4.0).removeNotFulfillingValues(DoubleValueSet.ofRange(1.0, 2.0), Relation.LESS_THAN));
    }
}
