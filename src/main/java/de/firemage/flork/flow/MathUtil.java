package de.firemage.flork.flow;

public final class MathUtil {
    private MathUtil() {

    }

    public static long incSaturating(long x) {
        if (x == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        } else {
            return x + 1;
        }
    }

    public static long decSaturating(long x) {
        if (x == Long.MIN_VALUE) {
            return Long.MIN_VALUE;
        } else {
            return x - 1;
        }
    }

    public static long min4(long a, long b, long c, long d) {
        return Math.min(a, Math.min(b, Math.min(c, d)));
    }

    public static long max4(long a, long b, long c, long d) {
        return Math.max(a, Math.max(b, Math.max(c, d)));
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

    public static OverflowType checkMulForOverflow(int x, int y) {
        long r = (long) x * (long) y;
        if ((int) r != r) {
            return (x >= 0) == (y >= 0) ? OverflowType.POS_TO_NEG : OverflowType.NEG_TO_POS;
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
