package de.firemage.flork.flow.engine;

import de.firemage.flork.flow.value.ValueSet;

import java.util.Set;
import java.util.stream.Collectors;

public record VarState(ValueSet value, Set<VarRelation> relations) {
    
    public VarState(ValueSet value) {
        this(value, Set.of());
    }

    @Override
    public String toString() {
        return "[" + this.value +
            (!this.relations.isEmpty() ? ", " + this.relations.stream().map(VarRelation::toString).collect(
                Collectors.joining(", ")) : "") + "]";
    }
}
