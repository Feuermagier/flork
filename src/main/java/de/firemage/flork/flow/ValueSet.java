package de.firemage.flork.flow;

import spoon.reflect.reference.CtTypeReference;

public sealed interface ValueSet permits BooleanValueSet, IntegerValueSet {
    static ValueSet topForType(CtTypeReference<?> type) {
        if (!type.isPrimitive()) {
            throw new UnsupportedOperationException();
        } else {
            return switch (type.getSimpleName()) {
                case "boolean" -> BooleanValueSet.top();
                case "int" -> IntegerValueSet.top();
                default -> throw new UnsupportedOperationException(type.getSimpleName());
            };
        }
    }

    ValueSet merge(ValueSet other);
    
    boolean isSupersetOf(ValueSet other);
}
