package de.firemage.flork.flow.engine;

public record LocalRefStackValue(String local) implements StackValue {
    @Override
    public String toString() {
        return local;
    }
}
