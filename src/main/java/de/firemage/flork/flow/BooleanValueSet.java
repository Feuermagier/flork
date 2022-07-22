package de.firemage.flork.flow;

public record BooleanValueSet(BooleanValueSet.State state) implements ValueSet {
    
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

    public enum State {
        TOP, TRUE, FALSE, BOTTOM
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
}
