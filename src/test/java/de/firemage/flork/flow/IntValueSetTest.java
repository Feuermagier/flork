package de.firemage.flork.flow;

import de.firemage.flork.flow.value.IntValueSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IntValueSetTest {

    @Test
    void merge() {
        assertEquals(IntValueSet.ofIntSingle(1), IntValueSet.ofIntSingle(1).merge(IntValueSet.ofIntSingle(1)));
        assertEquals(IntValueSet.ofIntRange(1, 2), IntValueSet.ofIntSingle(1).merge(IntValueSet.ofIntSingle(2)));
        assertEquals(IntValueSet.ofIntRange(0, 1), IntValueSet.ofIntSingle(1).merge(IntValueSet.ofIntSingle(0)));
        assertEquals(IntValueSet.ofIntRange(0, 2), IntValueSet.ofIntSingle(1).merge(IntValueSet.ofIntSingle(0).merge(IntValueSet.ofIntSingle(2))));
        assertEquals(IntValueSet.ofIntRange(0, 5), IntValueSet.ofIntRange(0, 5).merge(IntValueSet.ofIntRange(0, 5)));
        assertEquals(IntValueSet.ofIntRange(0, 5), IntValueSet.ofIntRange(0, 5).merge(IntValueSet.ofIntRange(1, 4)));
        assertEquals(IntValueSet.ofIntRange(0, 5), IntValueSet.ofIntRange(1, 4).merge(IntValueSet.ofIntRange(0, 5)));
        assertEquals(IntValueSet.ofIntRange(0, 5), IntValueSet.ofIntRange(0, 3).merge(IntValueSet.ofIntRange(3, 5)));
        assertEquals(IntValueSet.ofIntRange(0, 5), IntValueSet.ofIntRange(3, 5).merge(IntValueSet.ofIntRange(0, 3)));
        assertEquals(IntValueSet.ofIntRange(0, 5), IntValueSet.ofIntRange(4, 5).merge(IntValueSet.ofIntRange(0, 3)));
        assertEquals(IntValueSet.ofIntRange(0, 5), IntValueSet.ofIntRange(0, 1).merge(IntValueSet.ofIntRange(4, 5).merge(IntValueSet.ofIntRange(2, 3))));
    }
}