package de.firemage.flork.flow.value;

import de.firemage.flork.flow.BooleanStatus;
import de.firemage.flork.flow.MathUtil;
import de.firemage.flork.flow.TypeId;
import de.firemage.flork.flow.engine.Relation;

import java.util.Objects;

public final class DoubleValueSet extends NumericValueSet {
    public static DoubleValueSet TOP = new DoubleValueSet(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, true);
    public static DoubleValueSet TOP_NO_NAN = new DoubleValueSet(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, false);
    public static DoubleValueSet NAN = new DoubleValueSet(1.0, -1.0, true);
    public static DoubleValueSet BOTTOM = new DoubleValueSet(1.0, -1.0, false);

    private final double min;
    private final double max;
    private final boolean mayBeNaN;

    private DoubleValueSet(double min, double max, boolean mayBeNaN) {
        this.min = min;
        this.max = max;
        this.mayBeNaN = mayBeNaN;
    }

    public static DoubleValueSet ofSingle(double value) {
        if (Double.isNaN(value)) {
            return NAN;
        }
        return new DoubleValueSet(value, value, false);
    }

    public static DoubleValueSet ofRange(double min, double max) {
        if (Double.isNaN(min) || Double.isNaN(max)) {
            throw new IllegalArgumentException("NaN is not allowed as min or max");
        }

        return new DoubleValueSet(min, max, false);
    }

    public static DoubleValueSet ofRangeWithNaN(double min, double max) {
        return new DoubleValueSet(min, max, true);
    }

    @Override
    public boolean isZero() {
        return this.isSingleNonNaN(0.0);
    }

    @Override
    public boolean isOne() {
        return this.isSingleNonNaN(1.0);
    }

    @Override
    public DoubleValueSet negate() {
        return new DoubleValueSet(-this.max, -this.min, this.mayBeNaN);
    }

    @Override
    public DoubleValueSet add(NumericValueSet o) {
        var other = (DoubleValueSet) o;

        if (this == TOP || other == TOP) {
            return TOP;
        }

        if (this.isNaN()) {
            return this;
        } else if (other.isNaN()) {
            return other;
        }

        return new DoubleValueSet(this.min + other.min, this.max + other.max, this.mayBeNaN || other.mayBeNaN);
    }

    @Override
    public DoubleValueSet subtract(NumericValueSet o) {
        var other = (DoubleValueSet) o;

        if (this == TOP || other == TOP) {
            return TOP;
        }

        if (this.isNaN()) {
            return this;
        } else if (other.isNaN()) {
            return other;
        }

        return new DoubleValueSet(this.min - other.max, this.max - other.min, this.mayBeNaN || other.mayBeNaN);
    }

    @Override
    public DoubleValueSet multiply(NumericValueSet o) {
        var other = (DoubleValueSet) o;

        if (this == TOP || other == TOP) {
            return TOP;
        }

        if (this.isNaN()) {
            return this;
        } else if (other.isNaN()) {
            return other;
        }

        double min = MathUtil.min4(this.min * other.min, this.min * other.max, this.max * other.min, this.max * other.max);
        double max = MathUtil.max4(this.min * other.min, this.min * other.max, this.max * other.min, this.max * other.max);
        return new DoubleValueSet(min, max, this.mayBeNaN || other.mayBeNaN);
    }

    @Override
    public DoubleValueSet divide(NumericValueSet o) {
        var other = (DoubleValueSet) o;

        if (this == TOP || other == TOP) {
            return TOP;
        }

        if (this.isNaN()) {
            return this;
        } else if (other.isNaN()) {
            return other;
        }

        boolean mayBeNaN = this.mayBeNaN || other.mayBeNaN;

        DoubleValueSet otherInv;
        if (other.min == 0) {
            otherInv = new DoubleValueSet(1.0 / other.max, Double.POSITIVE_INFINITY, mayBeNaN);
        } else if (other.min < 0 && other.max >= 0) {
            return mayBeNaN ? TOP : TOP_NO_NAN;
        } else {
            otherInv = new DoubleValueSet(1.0 / other.max, 1.0 / other.min, mayBeNaN);
        }

        if (this.isOne()) {
            return otherInv;
        }

        return this.multiply(otherInv);
    }

    @Override
    public DoubleValueSet mod(NumericValueSet o) {
        return TOP;
    }

    @Override
    public DoubleValueSet merge(ValueSet o) {
        var other = (DoubleValueSet) o;

        if (this == TOP || other == TOP) {
            return TOP;
        }

        if (this.isNaN()) {
            return this;
        } else if (other.isNaN()) {
            return other;
        }

        return new DoubleValueSet(Math.min(this.min, other.min), Math.max(this.max, other.max), this.mayBeNaN || other.mayBeNaN);
    }

    @Override
    public DoubleValueSet tryMergeExact(ValueSet o) {
        return this.merge(o);
    }

    @Override
    public DoubleValueSet intersect(ValueSet o) {
        var other = (DoubleValueSet) o;

        if (this == TOP && other == TOP) {
            return this;
        }

        if (this.isNaN()) {
            return other;
        } else if (other.isNaN()) {
            return this;
        }

        return new DoubleValueSet(Math.max(this.min, other.min), Math.min(this.max, other.max), this.mayBeNaN && other.mayBeNaN);
    }

    @Override
    public boolean isSupersetOf(ValueSet o) {
        var other = (DoubleValueSet) o;

        if (this == TOP) {
            return true;
        }

        if (this.isNaN()) {
            return other.isNaN();
        } else if (other.isNaN()) {
            return false;
        }

        return this.min <= other.min && this.max >= other.max && (this.mayBeNaN || !other.mayBeNaN);
    }

    @Override
    public boolean isEmpty() {
        return !this.mayBeNaN && this.min > this.max;
    }

    @Override
    public BooleanStatus fulfillsRelation(ValueSet o, Relation relation) {
        var other = (DoubleValueSet) o;

        if (this.isEmpty() || other.isEmpty()) {
            return BooleanStatus.NEVER;
        }

        if (this.isNaN() || other.isNaN()) {
            return relation == Relation.NOT_EQUAL ? BooleanStatus.ALWAYS : BooleanStatus.NEVER;
        }

        if (this.isSingleNonNaN() && other.isSingleNonNaN()) {
            return switch (relation) {
                case EQUAL -> this.min == other.min ? BooleanStatus.ALWAYS : BooleanStatus.NEVER;
                case NOT_EQUAL -> this.min != other.min ? BooleanStatus.ALWAYS : BooleanStatus.NEVER;
                case LESS_THAN -> this.min < other.min ? BooleanStatus.ALWAYS : BooleanStatus.NEVER;
                case LESS_THAN_EQUAL -> this.min <= other.min ? BooleanStatus.ALWAYS : BooleanStatus.NEVER;
                case GREATER_THAN -> this.min > other.min ? BooleanStatus.ALWAYS : BooleanStatus.NEVER;
                case GREATER_THAN_EQUAL -> this.min >= other.min ? BooleanStatus.ALWAYS : BooleanStatus.NEVER;
            };
        }

        boolean anyNan = this.mayBeNaN || other.mayBeNaN;

        return switch (relation) {
            case EQUAL -> this.hasCommonValues(other) ? BooleanStatus.SOMETIMES : BooleanStatus.NEVER;
            case NOT_EQUAL -> this.hasCommonValues(other) ? BooleanStatus.SOMETIMES : BooleanStatus.ALWAYS;
            case LESS_THAN -> this.max < other.min ? (anyNan ? BooleanStatus.SOMETIMES : BooleanStatus.ALWAYS) : (this.min < other.max ? BooleanStatus.SOMETIMES : BooleanStatus.NEVER);
            case LESS_THAN_EQUAL -> this.max <= other.min ? (anyNan ? BooleanStatus.SOMETIMES : BooleanStatus.ALWAYS) : (this.min <= other.max ? BooleanStatus.SOMETIMES : BooleanStatus.NEVER);
            case GREATER_THAN -> this.min > other.max ? (anyNan ? BooleanStatus.SOMETIMES : BooleanStatus.ALWAYS) : (this.max > other.min ? BooleanStatus.SOMETIMES : BooleanStatus.NEVER);
            case GREATER_THAN_EQUAL -> this.min >= other.max ? (anyNan ? BooleanStatus.SOMETIMES : BooleanStatus.ALWAYS) : (this.max >= other.min ? BooleanStatus.SOMETIMES : BooleanStatus.NEVER);
        };
    }

    @Override
    public DoubleValueSet removeNotFulfillingValues(ValueSet o, Relation relation) {
        var other = (DoubleValueSet) o;

        return switch (relation) {
            case EQUAL -> this.intersect(other);
            case NOT_EQUAL -> {
                if (other.mayBeNaN) {
                    yield this;
                }

                if (other.isSingleNonNaN()) {
                    if (this.min == other.min) {
                        yield new DoubleValueSet(this.min + 1, this.max, this.mayBeNaN);
                    }

                    if (this.max == other.min) {
                        yield new DoubleValueSet(this.min, this.max - 1, this.mayBeNaN);
                    }
                }

                yield this;
            }
            case LESS_THAN -> other.min == Double.NEGATIVE_INFINITY ? BOTTOM : new DoubleValueSet(this.min, Math.min(this.max, other.max - 1), false);
            case LESS_THAN_EQUAL -> new DoubleValueSet(this.min, Math.min(this.max, other.max), false);
            case GREATER_THAN -> other.max == Double.POSITIVE_INFINITY ? BOTTOM : new DoubleValueSet(Math.max(this.min, other.min + 1), this.max, false);
            case GREATER_THAN_EQUAL -> new DoubleValueSet(Math.max(this.max, other.max), this.max, false);
        };
    }

    @Override
    public ValueSet castTo(TypeId newType) {
        // ((int) Double.NaN) is 0

        if (newType.isDouble()) {
            return this;
        } else if (newType.isLong()) {
            if (this.max < this.min) {
                return this.mayBeNaN ? LongValueSet.ofSingle(0) : LongValueSet.BOTTOM;
            }

            long min = (long) this.min;
            long max = (long) this.max;
            if (this.mayBeNaN) {
                min = Math.min(min, 0);
                max = Math.max(max, 0);
            }
            return LongValueSet.ofRange(min, max);
        } else if (newType.isInt()) {
            if (this.max < this.min) {
                return this.mayBeNaN ? LongValueSet.ofSingle(0) : LongValueSet.BOTTOM;
            }

            int min = (int) this.min;
            int max = (int) this.max;
            if (this.mayBeNaN) {
                min = Math.min(min, 0);
                max = Math.max(max, 0);
            }
            return IntValueSet.ofIntRange(min, max);
        } else {
            throw new IllegalArgumentException("Cannot cast double to " + newType.getName());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DoubleValueSet that = (DoubleValueSet) o;

        if (this.isEmpty() && that.isEmpty()) {
            return true;
        }

        return this.min == that.min && this.max == that.max && mayBeNaN == that.mayBeNaN;
    }

    @Override
    public int hashCode() {
        return Objects.hash(min, max, mayBeNaN);
    }

    @Override
    public String toString() {
        if (this.isNaN()) {
            return "[NaN]";
        }

        if (this.mayBeNaN) {
            return "[" + this.min + ", " + this.max + ", NaN]";
        } else {
            return "[" + this.min + ", " + this.max + "]";
        }
    }

    private boolean isSingleNonNaN(double value) {
        return this.min == value && this.max == value && !this.mayBeNaN;
    }

    private boolean isSingleNonNaN() {
        return this.min == this.max && !this.mayBeNaN;
    }

    private boolean isNaN() {
        return this.mayBeNaN && this.max < this.min;
    }

    private boolean hasCommonValues(DoubleValueSet other) {
        // Since NaN != NaN, NaN cannot be a common value
        return this.min <= other.max && this.max >= other.min;
    }
}
