package de.firemage.flork.flow;

import de.firemage.flork.flow.engine.Relation;
import de.firemage.flork.flow.engine.RelationStatus;
import spoon.reflect.reference.CtTypeReference;

public sealed interface ValueSet permits BooleanValueSet, IntValueSet {
    static ValueSet topForType(CtTypeReference<?> type) {
        if (!type.isPrimitive()) {
            throw new UnsupportedOperationException();
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
    
    ValueSet symmetricDifference(ValueSet other);
    
    boolean isSupersetOf(ValueSet other);
    
    boolean isEmpty();
    
    RelationStatus fulfillsRelation(ValueSet other, Relation relation);
    
    ValueSet removeNotFulfillingValues(ValueSet other, Relation relation);
}
