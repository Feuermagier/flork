package de.firemage.flork.flow.value;

import de.firemage.flork.flow.BooleanStatus;
import de.firemage.flork.flow.FlowContext;
import de.firemage.flork.flow.TypeId;
import de.firemage.flork.flow.engine.Relation;
import de.firemage.flork.flow.engine.ValueStack;
import spoon.reflect.code.CtNewArray;

import java.util.Objects;

public final class ArrayValueSet extends ValueSet {
    public static ArrayValueSet TOP = new ArrayValueSet(0, Integer.MAX_VALUE, Nullness.UNKNOWN);
    public static ArrayValueSet BOTTOM = new ArrayValueSet(1, 0, Nullness.BOTTOM);

    private final int minLength;
    private final int maxLength;
    private final Nullness nullness;

    public static ArrayValueSet newFromInlineNewArray(CtNewArray<?> newArray, IntValueSet firstDimensionExpression) {
        int minLength, maxLength;
        if (newArray.getElements() != null) {
            minLength = newArray.getElements().size();
            maxLength = newArray.getElements().size();
        } else {
            minLength = (int) firstDimensionExpression.min();
            maxLength = (int) firstDimensionExpression.max();
        }
        return new ArrayValueSet(minLength, maxLength, Nullness.NON_NULL);
    }

    public static ArrayValueSet newFromLength(IntValueSet length) {
        return new ArrayValueSet((int) length.min(), (int) length.max(), Nullness.NON_NULL);
    }

    public ArrayValueSet(int minLength, int maxLength, Nullness nullness) {
        this.minLength = minLength;
        this.maxLength = maxLength;
        this.nullness = nullness;
    }

    public int getMinLength() {
        return minLength;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public Nullness getNullness() {
        return nullness;
    }

    public ArrayValueSet asNonNull() {
        if (this.nullness == Nullness.NON_NULL) {
            return this;
        } else {
            return new ArrayValueSet(this.minLength, this.maxLength, this.nullness.asNonNull());
        }
    }

    @Override
    public ArrayValueSet merge(ValueSet o) {
        var other = (ArrayValueSet) o;

        var nullness = this.nullness.merge(other.nullness);
        var minLength = Math.min(this.minLength, other.minLength);
        var maxLength = Math.max(this.maxLength, other.maxLength);
        return new ArrayValueSet(minLength, maxLength, nullness);
    }

    @Override
    public ArrayValueSet tryMergeExact(ValueSet other) {
        return this.merge(other);
    }

    @Override
    public ArrayValueSet intersect(ValueSet o) {
        var other = (ArrayValueSet) o;

        var nullness = this.nullness.intersect(other.nullness);
        var minLength = Math.max(this.minLength, other.minLength);
        var maxLength = Math.min(this.maxLength, other.maxLength);

        if (maxLength < minLength) {
            return BOTTOM;
        }

        return new ArrayValueSet(minLength, maxLength, nullness);
    }

    @Override
    public boolean isSupersetOf(ValueSet o) {
        var other = (ArrayValueSet) o;
        return this.minLength <= other.minLength
                && this.maxLength >= other.maxLength
                && this.nullness.isSupersetOf(other.nullness);
    }

    @Override
    public boolean isEmpty() {
        return this.maxLength < this.minLength || this.nullness == Nullness.BOTTOM;
    }

    @Override
    public BooleanStatus fulfillsRelation(ValueSet o, Relation relation) {
        var other = (ArrayValueSet) o;
        return switch (relation) {
            case Relation.EQUAL -> {
                if (this.isEmpty() && other.isEmpty()) {
                    yield BooleanStatus.ALWAYS;
                }

                if (this.nullness == Nullness.NULL && other.nullness == Nullness.NULL) {
                    yield BooleanStatus.ALWAYS;
                }

                yield this.intersect(other).isEmpty() ? BooleanStatus.NEVER : BooleanStatus.SOMETIMES;
            }
            case Relation.NOT_EQUAL -> {
                if (this.isEmpty() && other.isEmpty()) {
                    yield BooleanStatus.NEVER;
                }

                if (this.nullness == Nullness.NULL && other.nullness == Nullness.NULL) {
                    yield BooleanStatus.NEVER;
                }

                yield this.intersect(other).isEmpty() ? BooleanStatus.ALWAYS : BooleanStatus.SOMETIMES;
            }
            default -> throw new IllegalArgumentException("Can't compare ArrayValueSets using " + relation);
        };
    }

    @Override
    public ValueSet removeNotFulfillingValues(ValueSet o, Relation relation) {
        var other = (ArrayValueSet) o;
        return switch (relation) {
            case Relation.EQUAL -> this.intersect(other);
            case Relation.NOT_EQUAL -> {
                if (this.nullness == Nullness.NULL && other.nullness == Nullness.NULL) {
                    yield BOTTOM;
                }

                yield this;
            }
            default -> throw new IllegalArgumentException("Can't compare ArrayValueSets using " + relation);
        };
    }

    @Override
    public ValueSet castTo(TypeId newType, FlowContext context) {
        if (newType.type().isArray()) {
            return this;
        } else if (!newType.isPrimitive()) {
            // Arrays can be cast to objects
            return ObjectValueSet.forUnconstrainedType(this.nullness, newType, context);
        } else {
            throw new IllegalArgumentException("Can't cast an ArrayValueSet to " + newType.getName());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArrayValueSet that = (ArrayValueSet) o;

        // TODO this breaks the hashCode/equals contract!!!
        // For a, b empty but a != b, we may have a.equals(b) == true but a.hashCode() != b.hashCode()!!!
        if (this.isEmpty() && that.isEmpty()) {
            return true;
        }

        return minLength == that.minLength && maxLength == that.maxLength && nullness == that.nullness;
    }

    @Override
    public int hashCode() {
        return Objects.hash(minLength, maxLength, nullness);
    }

    @Override
    public String toString() {
        return "arr[" + this.minLength + ", " + this.maxLength + "]";
    }
}
