package de.firemage.flork.flow.engine;

import de.firemage.flork.flow.value.ValueSet;

public record ConcreteStackValue(ValueSet value) implements StackValue {
    @Override
    public String toString() {
        return this.value.toString();
    }
}
