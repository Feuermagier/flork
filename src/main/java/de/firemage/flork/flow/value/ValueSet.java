package de.firemage.flork.flow.value;

import de.firemage.flork.flow.BooleanStatus;
import de.firemage.flork.flow.FlowContext;
import de.firemage.flork.flow.engine.Relation;
import spoon.reflect.reference.CtTypeReference;

public sealed interface ValueSet permits BooleanValueSet, IntValueSet, ObjectValueSet, VoidValue {

    static ValueSet topForType(CtTypeReference<?> type, FlowContext context) {
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

    ValueSet merge(ValueSet other);

    ValueSet intersect(ValueSet other);

    boolean isSupersetOf(ValueSet other);

    boolean isEmpty();

    BooleanStatus fulfillsRelation(ValueSet other, Relation relation);

    ValueSet removeNotFulfillingValues(ValueSet other, Relation relation);
}
