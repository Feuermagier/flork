package de.firemage.flork.flow;

import de.firemage.flork.flow.value.IntValueSet;
import org.junit.jupiter.api.Test;

class IntegerValueSetTest {
    @Test
    void testAddValue() {
        var a = IntValueSet.ofIntRange(1, 5);
        var b = IntValueSet.ofIntRange(7, 7);
        var c = IntValueSet.ofIntRange(1, 7);
        System.out.println(a.merge(b).splitAtBelow(7));
    }
}
