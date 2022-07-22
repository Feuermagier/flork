package de.firemage.flork.flow;

public final class MathUtil {
    private MathUtil() {

    }

    public static int incSaturating(int x) {
        if (x == Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        } else {
            return x + 1;
        }
    }

    public static int decSaturating(int x) {
        if (x == Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        } else {
            return x - 1;
        }
    }

    public static int saturatingAdd(int x, int y) {
        if (x == 0 || y == 0 || (x > 0 ^ y > 0)) {
            return x + y;
        } else if (x > 0) {
            return Integer.MAX_VALUE - x < y ? Integer.MAX_VALUE : x + y;
        } else {
            return Integer.MIN_VALUE - x > y ? Integer.MIN_VALUE : x + y;
        }
    }
}
