package de.firemage.flork.flow.value;

import de.firemage.flork.flow.BooleanStatus;
import de.firemage.flork.flow.TypeId;
import de.firemage.flork.flow.engine.Relation;

/**
 * A lot less sophisticated than IntValueSet
 */
public final class LongValueSet extends NumericValueSet {
    public static final LongValueSet TOP = new LongValueSet(Long.MIN_VALUE, Long.MAX_VALUE);
    public static final LongValueSet BOTTOM = new LongValueSet(1, -1);

    private final long min;
    private final long max;

    private LongValueSet(long min, long max) {
        this.min = min;
        this.max = max;
    }

    public static LongValueSet ofRange(long min, long max) {
        if (min > max) {
            throw new IllegalArgumentException("min > max");
        }

        return new LongValueSet(min, max);
    }

    public static LongValueSet ofSingle(long value) {
        return new LongValueSet(value, value);
    }

    @Override
    public boolean isZero() {
        return this.min == 0 && this.max == 0;
    }

    @Override
    public boolean isOne() {
        return this.min == 1 && this.max == 1;
    }

    @Override
    public LongValueSet negate() {
        if (this.isSingle()) {
            long value = -this.min;
            return LongValueSet.ofSingle(value);
        }

        if (this.min == Long.MIN_VALUE || this.max == Long.MIN_VALUE) {
            return TOP;
        }

        return new LongValueSet(-this.max, -this.min);
    }

    @Override
    public LongValueSet add(NumericValueSet o) {
        var other = (LongValueSet) o;

        if (this.isSingle() && other.isSingle()) {
            long value = this.min + other.min;
            return LongValueSet.ofSingle(value);
        }

        if (addWouldOverflow(this.max, other.max) || addWouldOverflow(this.min, other.min)) {
            return TOP;
        }
        return new LongValueSet(this.min + other.min, this.max + other.max);
    }

    @Override
    public LongValueSet subtract(NumericValueSet other) {
        return this.add(((LongValueSet) other).negate());
    }

    @Override
    public LongValueSet multiply(NumericValueSet o) {
        var other = (LongValueSet) o;
        if (this.isSingle() && other.isSingle()) {
            long value = this.min * other.min;
            return LongValueSet.ofSingle(value);
        }

        return TOP;
    }

    @Override
    public LongValueSet divide(NumericValueSet o) {
        var other = (LongValueSet) o;
        if (this.isSingle() && other.isSingle()) {
            long value = this.min / other.min;
            return LongValueSet.ofSingle(value);
        }

        return TOP;
    }

    @Override
    public LongValueSet mod(NumericValueSet o) {
        var other = (LongValueSet) o;
        if (this.isSingle() && other.isSingle()) {
            long value = this.min % other.min;
            return LongValueSet.ofSingle(value);
        }

        return TOP;
    }

    @Override
    public LongValueSet merge(ValueSet o) {
        var other = (LongValueSet) o;
        return new LongValueSet(Math.min(this.min, other.min), Math.max(this.max, other.max));
    }

    @Override
    public LongValueSet tryMergeExact(ValueSet other) {
        return this.merge(other);
    }

    @Override
    public LongValueSet intersect(ValueSet o) {
        var other = (LongValueSet) o;
        return new LongValueSet(Math.max(this.min, other.min), Math.min(this.max, other.max));
    }

    @Override
    public boolean isSupersetOf(ValueSet o) {
        var other = (LongValueSet) o;
        return this.min <= other.min && other.max <= this.max;
    }

    @Override
    public boolean isEmpty() {
        return this.min > this.max;
    }

    @Override
    public BooleanStatus fulfillsRelation(ValueSet o, Relation relation) {
        var other = (LongValueSet) o;

        if (this.isSingle() && other.isSingle()) {
            return switch (relation) {
                case EQUAL -> this.min == other.min ? BooleanStatus.ALWAYS : BooleanStatus.NEVER;
                case NOT_EQUAL -> this.min != other.min ? BooleanStatus.ALWAYS : BooleanStatus.NEVER;
                case LESS_THAN -> this.min < other.min ? BooleanStatus.ALWAYS : BooleanStatus.NEVER;
                case LESS_THAN_EQUAL -> this.min <= other.min ? BooleanStatus.ALWAYS : BooleanStatus.NEVER;
                case GREATER_THAN -> this.min > other.min ? BooleanStatus.ALWAYS : BooleanStatus.NEVER;
                case GREATER_THAN_EQUAL -> this.min >= other.min ? BooleanStatus.ALWAYS : BooleanStatus.NEVER;
            };
        }

        if (this.isEmpty() || other.isEmpty()) {
            return BooleanStatus.NEVER;
        }

        return switch (relation) {
            case EQUAL -> this.hasCommonValue(other) ? BooleanStatus.SOMETIMES : BooleanStatus.NEVER;
            case NOT_EQUAL -> this.hasCommonValue(other) ? BooleanStatus.SOMETIMES : BooleanStatus.ALWAYS;
            case LESS_THAN ->
                    this.max < other.min ? BooleanStatus.ALWAYS : (this.min < other.max ? BooleanStatus.SOMETIMES : BooleanStatus.NEVER);
            case LESS_THAN_EQUAL ->
                    this.max <= other.min ? BooleanStatus.ALWAYS : (this.min <= other.max ? BooleanStatus.SOMETIMES : BooleanStatus.NEVER);
            case GREATER_THAN ->
                    this.min > other.max ? BooleanStatus.ALWAYS : (this.max > other.min ? BooleanStatus.SOMETIMES : BooleanStatus.NEVER);
            case GREATER_THAN_EQUAL ->
                    this.min >= other.max ? BooleanStatus.ALWAYS : (this.max >= other.min ? BooleanStatus.SOMETIMES : BooleanStatus.NEVER);
        };
    }

    @Override
    public LongValueSet removeNotFulfillingValues(ValueSet o, Relation relation) {
        var other = (LongValueSet) o;
        return switch (relation) {
            case EQUAL -> this.intersect(other);
            case NOT_EQUAL -> {
                if (this.isSingle() && other.isSingle()) {
                    yield this.min == other.min ? BOTTOM : this;
                } else if (this.isSingle()) {
                    if (this.min == other.min) {
                        yield LongValueSet.ofRange(other.min + 1, other.max);
                    } else if (this.min == other.max) {
                        yield LongValueSet.ofRange(other.min, other.max - 1);
                    }
                } else if (other.isSingle()) {
                    if (other.min == this.min) {
                        yield LongValueSet.ofRange(this.min + 1, this.max);
                    } else if (other.min == this.max) {
                        yield LongValueSet.ofRange(this.min, this.max - 1);
                    }
                }
                yield this;
            }
            case LESS_THAN -> this.intersect(new LongValueSet(Math.min(other.max - 1, this.min), other.max - 1));
            case LESS_THAN_EQUAL -> this.intersect(new LongValueSet(this.min, other.max));
            case GREATER_THAN -> this.intersect(new LongValueSet(other.min + 1, Math.max(other.min + 1, this.max)));
            case GREATER_THAN_EQUAL -> this.intersect(new LongValueSet(other.min, this.max));
        };
    }

    @Override
    public ValueSet castTo(TypeId newType) {
        if (newType.isLong()) {
            return this;
        } else if (newType.isInt()) {
            return IntValueSet.ofIntRange((int) this.min, (int) this.max);
        } else if (newType.isDouble()) {
            return DoubleValueSet.ofRange(this.min, this.max);
        } else {
            throw new IllegalArgumentException("Cannot cast long to " + newType.getName());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LongValueSet that = (LongValueSet) o;

        if (this.isEmpty() && that.isEmpty()) {
            return true;
        }

        return this.min == that.min && this.max == that.max;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(this.min) * 31 + Long.hashCode(this.max);
    }

    @Override
    public String toString() {
        return "[" + getValueString(this.min) + "L, " + getValueString(this.max) + "L]";
    }

    public boolean isSingle() {
        return this.min == this.max;
    }

    private boolean hasCommonValue(LongValueSet other) {
        return this.min <= other.max && other.min <= this.max;
    }

    private static boolean addWouldOverflow(long a, long b) {
        long r = a + b;
        return ((a ^ r) & (b ^ r)) < 0;
    }

    private static String getValueString(long value) {
        return value == Long.MIN_VALUE ? "MIN" : value == Long.MAX_VALUE ? "MAX" : Long.toString(value);
    }
}
