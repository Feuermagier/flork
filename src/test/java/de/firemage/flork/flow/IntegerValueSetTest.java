package de.firemage.flork.flow;

import org.junit.jupiter.api.Test;

class IntegerValueSetTest {
    @Test
    void testAddValue() {
        var a = IntegerValueSet.ofRange(1, 5);
        var b = IntegerValueSet.ofRange(7, 7);
        var c = IntegerValueSet.ofRange(1, 7);
        System.out.println(a.merge(b).splitAtBelow(7));
    }
}
