package de.firemage.flork.flow.value;

import de.firemage.flork.flow.FlowContext;
import de.firemage.flork.flow.engine.Relation;

import java.util.Objects;
import java.util.Set;

public final class BoxedIntValueSet extends ObjectValueSet {
    private final FlowContext context;
    private final IntValueSet value;

    public BoxedIntValueSet(Nullness nullness, IntValueSet value, FlowContext context) {
        super(nullness, context.getType("java.lang.Integer"), Set.of(context.getType("java.lang.Integer")), context);
        this.value = value;
        this.context = context;
    }

    public IntValueSet value() {
        return value;
    }

    @Override
    public ValueSet merge(ValueSet o) {
        BoxedIntValueSet other = (BoxedIntValueSet) o;
        return new BoxedIntValueSet(this.nullness.merge(other.nullness), this.value.merge(other.value), this.context);
    }

    @Override
    public ValueSet tryMergeExact(ValueSet o) {
        return this.merge(o);
    }

    @Override
    public ValueSet intersect(ValueSet o) {
       BoxedIntValueSet other = (BoxedIntValueSet) o;
         return new BoxedIntValueSet(this.nullness.intersect(other.nullness), this.value.intersect(other.value), this.context);
    }

    @Override
    public boolean isSupersetOf(ValueSet o) {
        if (o instanceof BoxedIntValueSet other) {
            return this.nullness.isSupersetOf(other.nullness) && this.value.isSupersetOf(other.value);
        } else {
            return super.isSupersetOf(o);
        }
    }

    @Override
    public ValueSet removeNotFulfillingValues(ValueSet o, Relation relation) {
        return super.removeNotFulfillingValues(o, relation);
    }

    @Override
    public BoxedIntValueSet asNonNull() {
        if (this.nullness == Nullness.NON_NULL) {
            return this;
        } else if (this.nullness == Nullness.UNKNOWN) {
            return new BoxedIntValueSet(Nullness.NON_NULL, this.value, this.context);
        } else if (this.nullness == Nullness.NULL) {
            throw new IllegalStateException("would throw an NPE");
        } else {
            throw new IllegalStateException("Nullness is bottom");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BoxedIntValueSet that = (BoxedIntValueSet) o;
        return nullness == that.nullness && value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nullness, value);
    }

    @Override
    public String toString() {
        return nullness.toString() + "/" + value.toString();
    }
}
