package de.firemage.flork.flow.value;

import de.firemage.flork.flow.BooleanStatus;
import de.firemage.flork.flow.TypeId;
import de.firemage.flork.flow.engine.Relation;

/**
 * Placeholder for expressions of type void (e.g. calls to void methods, constructor calls) so they
 * can be treated in the same way as other expressions
 */
public final class VoidValue extends ValueSet {
    private static final VoidValue INSTANCE = new VoidValue();

    private VoidValue() {
    }

    public static VoidValue getInstance() {
        return INSTANCE;
    }

    @Override
    public String toString() {
        return "void";
    }

    @Override
    public ValueSet merge(ValueSet other) {
        if (other instanceof VoidValue) {
            return this;
        } else {
            throw new IllegalArgumentException("Cannot merge a void value with " + other);
        }
    }

    @Override
    public ValueSet tryMergeExact(ValueSet other) {
        return this.merge(other);
    }

    @Override
    public ValueSet intersect(ValueSet other) {
        if (other instanceof VoidValue) {
            return this;
        } else {
            throw new IllegalArgumentException("Cannot intersect a void value with " + other);
        }
    }

    @Override
    public boolean isSupersetOf(ValueSet other) {
        if (other instanceof VoidValue) {
            return true;
        } else {
            throw new IllegalArgumentException("Cannot compare a void value with " + other);
        }
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public BooleanStatus fulfillsRelation(ValueSet other, Relation relation) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ValueSet removeNotFulfillingValues(ValueSet other, Relation relation) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ValueSet castTo(TypeId newType) {
        throw new IllegalStateException("Cannot cast void to " + newType.getName());
    }

    @Override
    public boolean equals(Object o) {
        return o == INSTANCE;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }
}
