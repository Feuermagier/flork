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
        int r = x + y;

        if ((x < 0) != (y < 0)) {
            return r;
        }

        if ((r < 0) != (x < 0)) {
            return x < 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        }
        return r;
    }

    public static OverflowType checkAddForOverflow(int x, int y) {
        int r = x + y;
        // From java.lang.Math#addExact
        if (((x ^ r) & (y ^ r)) < 0) {
            return x < 0 ? OverflowType.NEG_TO_POS : OverflowType.POS_TO_NEG;
        }
        return OverflowType.NONE;
    }

    public static OverflowType checkSubForOverflow(int x, int y) {
        int r = x - y;
        // From java.lang.Math#subtractExact
        if (((x ^ y) & (x ^ r)) < 0) {
            return x < 0 ? OverflowType.NEG_TO_POS : OverflowType.POS_TO_NEG;
        }
        return OverflowType.NONE;
    }

    public enum OverflowType implements Comparable<OverflowType> {
        // Keep the order!!!
        NEG_TO_POS,
        NONE,
        POS_TO_NEG;
        
        public static OverflowType min(OverflowType a, OverflowType b) {
            if (a.compareTo(b) <= 0) {
                return a;
            } else {
                return b;
            }
        }

        public static OverflowType max(OverflowType a, OverflowType b) {
            if (a.compareTo(b) > 0) {
                return a;
            } else {
                return b;
            }
        }
    }
}
