package de.firemage.flork.flow.value;

import de.firemage.flork.flow.BooleanStatus;
import de.firemage.flork.flow.engine.Relation;

import java.util.Objects;

public final class BooleanValueSet extends ValueSet {
    private final BooleanValueSet.State state;
    
    public BooleanValueSet(BooleanValueSet.State state) {
        this.state = state;
    }
    
    public static BooleanValueSet bottom() {
        return new BooleanValueSet(State.BOTTOM);
    }

    public static BooleanValueSet top() {
        return new BooleanValueSet(State.TOP);
    }

    public static BooleanValueSet of(boolean value) {
        return new BooleanValueSet(value ? State.TRUE : State.FALSE);
    }

    @Override
    public ValueSet merge(ValueSet other) {
        if (other instanceof BooleanValueSet set) {
            if (set.state == State.BOTTOM || this.state == State.BOTTOM) {
                return new BooleanValueSet(State.BOTTOM);
            } else if (set.state == this.state) {
                return new BooleanValueSet(this.state);
            } else {
                return new BooleanValueSet(State.TOP);
            }
        } else {
            throw new IllegalArgumentException("other must be a BooleanValueSet and not " + other.getClass().getName());
        }
    }

    @Override
    public BooleanValueSet intersect(ValueSet o) {
        BooleanValueSet other = (BooleanValueSet) o;
        if (this.equals(other)) {
            return this;
        } else if (this.isTop()) {
            return other;
        } else if (other.isTop()) {
            return this;
        } else {
            return BooleanValueSet.bottom();
        }
    }

    public BooleanValueSet not() {
        return switch (this.state) {
            case TOP -> BooleanValueSet.top();
            case BOTTOM -> BooleanValueSet.bottom();
            case TRUE -> BooleanValueSet.of(false);
            case FALSE -> BooleanValueSet.of(true);
        };
    }

    public boolean isTop() {
        return this.state == State.TOP;
    }

    public boolean isFalse() {
        return this.state == State.FALSE;
    }

    public boolean isTrue() {
        return this.state == State.TRUE;
    }

    public boolean isBottom() {
        return this.state == State.BOTTOM;
    }

    @Override
    public String toString() {
        return switch (this.state) {
            case TOP -> "(true,false)";
            case BOTTOM -> "⊥";
            case TRUE -> "true";
            case FALSE -> "false";
        };
    }

    @Override
    public boolean isSupersetOf(ValueSet other) {
        BooleanValueSet set = (BooleanValueSet) other;
        return this.state == set.state || this.isTop() && !set.isBottom();
    }

    @Override
    public boolean isEmpty() {
        return this.isBottom();
    }

    @Override
    public BooleanStatus fulfillsRelation(ValueSet o, Relation relation) {
        BooleanValueSet other = (BooleanValueSet) o;
        if (this.isBottom() || other.isBottom()) {
            throw new IllegalStateException("Cannot compare bottom values");
        }

        return switch (relation) {
            case EQUAL -> (this.isTop() || other.isTop()) ? BooleanStatus.SOMETIMES :
                (this.equals(other) ? BooleanStatus.ALWAYS : BooleanStatus.NEVER);
            case NOT_EQUAL -> (this.isTop() || other.isTop()) ? BooleanStatus.SOMETIMES :
                (this.equals(other) ? BooleanStatus.NEVER : BooleanStatus.ALWAYS);
            case LESS_THAN, LESS_THAN_EQUAL, GREATER_THAN, GREATER_THAN_EQUAL -> throw new UnsupportedOperationException();
        };
    }

    @Override
    public BooleanValueSet removeNotFulfillingValues(ValueSet o, Relation relation) {
        return switch (relation) {
            case EQUAL -> this.intersect(o);
            case NOT_EQUAL -> throw new UnsupportedOperationException();
            default -> throw new IllegalStateException("Cannot compare booleans with " + relation);
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BooleanValueSet that = (BooleanValueSet) o;
        return state == that.state;
    }

    @Override
    public int hashCode() {
        return Objects.hash(state);
    }

    public enum State {
        TOP, TRUE, FALSE, BOTTOM
    }
}
