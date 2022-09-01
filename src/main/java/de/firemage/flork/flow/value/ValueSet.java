package de.firemage.flork.flow.value;

import de.firemage.flork.flow.BooleanStatus;
import de.firemage.flork.flow.FlowContext;
import de.firemage.flork.flow.engine.Relation;
import spoon.reflect.reference.CtTypeReference;

public sealed abstract class ValueSet permits BooleanValueSet, IntValueSet, ObjectValueSet, VoidValue {

    public static ValueSet topForType(CtTypeReference<?> type, FlowContext context) {
        if (!type.isPrimitive()) {
            return new ObjectValueSet(Nullness.UNKNOWN, type, false, context);
        } else {
            return switch (type.getSimpleName()) {
                case "boolean" -> BooleanValueSet.top();
                case "int" -> IntValueSet.topForInt();
                default -> throw new UnsupportedOperationException(type.getSimpleName());
            };
        }
    }

    public abstract ValueSet merge(ValueSet other);

    public abstract ValueSet intersect(ValueSet other);

    public abstract boolean isSupersetOf(ValueSet other);

    public abstract boolean isEmpty();

    public abstract BooleanStatus fulfillsRelation(ValueSet other, Relation relation);

    public abstract ValueSet removeNotFulfillingValues(ValueSet other, Relation relation);
    
    public abstract boolean equals(Object o);
    
    public abstract int hashCode();
}
