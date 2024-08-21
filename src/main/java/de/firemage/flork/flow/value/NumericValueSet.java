package de.firemage.flork.flow.value;

public abstract sealed class NumericValueSet extends ValueSet permits IntValueSet, LongValueSet {
    public abstract boolean isZero();
    public abstract boolean isOne();
    public abstract NumericValueSet negate();
    public abstract NumericValueSet add(NumericValueSet other);
    public abstract NumericValueSet subtract(NumericValueSet other);
    public abstract NumericValueSet multiply(NumericValueSet other);
    public abstract NumericValueSet divide(NumericValueSet other);
    public abstract NumericValueSet mod(NumericValueSet other);
}
