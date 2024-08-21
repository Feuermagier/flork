package de.firemage.flork.flow.value;

import de.firemage.flork.flow.BooleanStatus;
import de.firemage.flork.flow.engine.Relation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LongValueSetTest {
    @Test
    void negate() {
        assertEquals(LongValueSet.ofRange(-2, -1), LongValueSet.ofRange(1, 2).negate());
        assertEquals(LongValueSet.ofRange(-1, 1), LongValueSet.ofRange(-1, 1).negate());
        assertEquals(LongValueSet.ofSingle(Long.MIN_VALUE), LongValueSet.ofSingle(Long.MIN_VALUE).negate());
        assertEquals(LongValueSet.ofSingle(Long.MIN_VALUE + 1), LongValueSet.ofSingle(Long.MAX_VALUE).negate());
        assertSame(LongValueSet.top(), LongValueSet.top().negate());
    }

    @Test
    void add() {
        assertSame(LongValueSet.top(), LongValueSet.top().add(LongValueSet.top()));
        assertEquals(LongValueSet.ofSingle(2), LongValueSet.ofSingle(1).add(LongValueSet.ofSingle(1)));
        assertEquals(LongValueSet.ofRange(1, 3), LongValueSet.ofRange(0, 1).add(LongValueSet.ofRange(1, 2)));
        assertSame(LongValueSet.top(), LongValueSet.ofSingle(1).add(LongValueSet.top()));
        assertEquals(LongValueSet.ofSingle(Long.MIN_VALUE), LongValueSet.ofSingle(Long.MAX_VALUE).add(LongValueSet.ofSingle(1)));
        assertSame(LongValueSet.top(), LongValueSet.ofSingle(Long.MAX_VALUE).add(LongValueSet.ofRange(0, 1)));
    }

    @Test
    void subtract() {
        assertSame(LongValueSet.top(), LongValueSet.top().subtract(LongValueSet.top()));
        assertEquals(LongValueSet.ofSingle(0), LongValueSet.ofSingle(1).subtract(LongValueSet.ofSingle(1)));
        assertEquals(LongValueSet.ofRange(-2, 0), LongValueSet.ofRange(0, 1).subtract(LongValueSet.ofRange(1, 2)));
        assertSame(LongValueSet.top(), LongValueSet.ofSingle(1).subtract(LongValueSet.top()));
        assertEquals(LongValueSet.ofSingle(Long.MAX_VALUE), LongValueSet.ofSingle(Long.MIN_VALUE).subtract(LongValueSet.ofSingle(1)));
        assertSame(LongValueSet.top(), LongValueSet.ofSingle(Long.MIN_VALUE).subtract(LongValueSet.ofRange(0, 1)));
    }

    @Test
    void merge() {
        assertEquals(LongValueSet.ofSingle(1), LongValueSet.ofSingle(1).merge(LongValueSet.ofSingle(1)));
        assertEquals(LongValueSet.ofRange(1, 2), LongValueSet.ofSingle(1).merge(LongValueSet.ofSingle(2)));
        assertEquals(LongValueSet.ofRange(1, 2), LongValueSet.ofRange(1, 2).merge(LongValueSet.ofSingle(2)));
        assertEquals(LongValueSet.ofRange(1, 2), LongValueSet.ofSingle(1).merge(LongValueSet.ofRange(1, 2)));
        assertEquals(LongValueSet.ofRange(1, 2), LongValueSet.ofRange(1, 2).merge(LongValueSet.ofRange(1, 2)));
        assertEquals(LongValueSet.ofRange(-2, 2), LongValueSet.ofRange(-2, -1).merge(LongValueSet.ofRange(1, 2)));
    }

    @Test
    void fulfillsRelation() {
        assertEquals(BooleanStatus.SOMETIMES, LongValueSet.ofRange(1, 2).fulfillsRelation(LongValueSet.ofSingle(1), Relation.EQUAL));
        assertEquals(BooleanStatus.NEVER, LongValueSet.ofRange(1, 2).fulfillsRelation(LongValueSet.ofSingle(-1), Relation.EQUAL));
        assertEquals(BooleanStatus.SOMETIMES, LongValueSet.ofRange(1, 2).fulfillsRelation(LongValueSet.ofRange(1, 3), Relation.LESS_THAN));
        assertEquals(BooleanStatus.NEVER, LongValueSet.ofRange(2, 3).fulfillsRelation(LongValueSet.ofRange(1, 2), Relation.LESS_THAN));
        assertEquals(BooleanStatus.SOMETIMES, LongValueSet.ofRange(2, 3).fulfillsRelation(LongValueSet.ofRange(1, 2), Relation.LESS_THAN_EQUAL));
        assertEquals(BooleanStatus.NEVER, LongValueSet.ofRange(1, 2).fulfillsRelation(LongValueSet.ofRange(2, 3), Relation.GREATER_THAN));
        assertEquals(BooleanStatus.SOMETIMES, LongValueSet.ofRange(1, 2).fulfillsRelation(LongValueSet.ofRange(2, 3), Relation.GREATER_THAN_EQUAL));
    }

    @Test
    void removeNotFulfillingValues() {
        assertEquals(LongValueSet.ofSingle(1), LongValueSet.ofRange(1, 2).removeNotFulfillingValues(LongValueSet.ofSingle(1), Relation.EQUAL));
        assertTrue(LongValueSet.ofRange(1, 2).removeNotFulfillingValues(LongValueSet.ofSingle(-1), Relation.EQUAL).isEmpty());

        assertEquals(LongValueSet.ofSingle(2), LongValueSet.ofRange(1, 2).removeNotFulfillingValues(LongValueSet.ofSingle(1), Relation.NOT_EQUAL));
        assertTrue(LongValueSet.ofSingle(1).removeNotFulfillingValues(LongValueSet.ofSingle(1), Relation.NOT_EQUAL).isEmpty());

        assertEquals(LongValueSet.ofRange(1, 2), LongValueSet.ofRange(1, 2).removeNotFulfillingValues(LongValueSet.ofRange(1, 3), Relation.LESS_THAN));
        assertEquals(LongValueSet.ofSingle(1), LongValueSet.ofRange(1, 2).removeNotFulfillingValues(LongValueSet.ofRange(1, 2), Relation.LESS_THAN));
        assertTrue(LongValueSet.ofRange(2, 3).removeNotFulfillingValues(LongValueSet.ofRange(1, 2), Relation.LESS_THAN).isEmpty());

        assertEquals(LongValueSet.ofRange(1, 2), LongValueSet.ofRange(1, 2).removeNotFulfillingValues(LongValueSet.ofRange(1, 2), Relation.LESS_THAN_EQUAL));
        assertEquals(LongValueSet.ofSingle(1), LongValueSet.ofRange(1, 2).removeNotFulfillingValues(LongValueSet.ofRange(0, 1), Relation.LESS_THAN_EQUAL));
        assertTrue(LongValueSet.ofRange(3, 4).removeNotFulfillingValues(LongValueSet.ofRange(1, 2), Relation.LESS_THAN_EQUAL).isEmpty());

        assertEquals(LongValueSet.ofRange(1, 2), LongValueSet.ofRange(1, 2).removeNotFulfillingValues(LongValueSet.ofRange(0, 1), Relation.GREATER_THAN));
        assertEquals(LongValueSet.ofSingle(2), LongValueSet.ofRange(1, 2).removeNotFulfillingValues(LongValueSet.ofRange(1, 2), Relation.GREATER_THAN));
        assertTrue(LongValueSet.ofRange(1, 2).removeNotFulfillingValues(LongValueSet.ofRange(2, 3), Relation.GREATER_THAN).isEmpty());

        assertEquals(LongValueSet.ofRange(1, 2), LongValueSet.ofRange(1, 2).removeNotFulfillingValues(LongValueSet.ofRange(1, 2), Relation.GREATER_THAN_EQUAL));
        assertEquals(LongValueSet.ofSingle(2), LongValueSet.ofRange(1, 2).removeNotFulfillingValues(LongValueSet.ofRange(2, 3), Relation.GREATER_THAN_EQUAL));
        assertTrue(LongValueSet.ofRange(1, 2).removeNotFulfillingValues(LongValueSet.ofRange(3, 4), Relation.GREATER_THAN_EQUAL).isEmpty());
    }
}
