package de.firemage.flork.flow.engine;

import de.firemage.flork.flow.value.ValueSet;

public record ConcreteStackValue(VarState value) implements StackValue {

    public ConcreteStackValue(ValueSet value) {
        this(new VarState(value));
    }

    @Override
    public String toString() {
        return this.value.toString();
    }
}
