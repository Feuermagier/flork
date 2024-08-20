package de.firemage.flork.flow.value;

import de.firemage.flork.flow.BooleanStatus;
import de.firemage.flork.flow.FlowContext;
import de.firemage.flork.flow.TypeId;
import de.firemage.flork.flow.engine.Relation;

public abstract sealed class ValueSet permits BooleanValueSet, IntValueSet, ObjectValueSet, VoidValue {

    public static ValueSet topForType(TypeId type, FlowContext context) {
        if (!type.isPrimitive()) {
            if (type.getName().equals("java.lang.Integer")) {
                return new BoxedIntValueSet(Nullness.UNKNOWN, IntValueSet.topForInt(), context);
            } else if (context.isEffectivelyFinalType(type)) {
                return ObjectValueSet.forExactType(Nullness.UNKNOWN, type, context);
            } else {
                return ObjectValueSet.forUnconstrainedType(Nullness.UNKNOWN, type, context);
            }
        } else {
            return switch (type.getName()) {
                case "boolean" -> BooleanValueSet.top();
                case "int" -> IntValueSet.topForInt();
                default -> throw new UnsupportedOperationException(type.getName());
            };
        }
    }

    public abstract ValueSet merge(ValueSet other);

    /**
     * May return null if we would lose precision by merging
     *
     * @param other
     * @return
     */
    public abstract ValueSet tryMergeExact(ValueSet other);

    public abstract ValueSet intersect(ValueSet other);

    public abstract boolean isSupersetOf(ValueSet other);

    public boolean isCompatible(ValueSet other) {
        return this.isSupersetOf(other) || other.isSupersetOf(this);
    }

    public abstract boolean isEmpty();

    public abstract BooleanStatus fulfillsRelation(ValueSet other, Relation relation);

    public abstract ValueSet removeNotFulfillingValues(ValueSet other, Relation relation);

    public abstract ValueSet castTo(TypeId newType);

    public abstract boolean equals(Object o);

    public abstract int hashCode();
}
