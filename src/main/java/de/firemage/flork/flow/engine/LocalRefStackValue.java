package de.firemage.flork.flow.engine;

public record LocalRefStackValue(SSAVarId local) implements StackValue {
    @Override
    public String toString() {
        return local.toString();
    }
}
