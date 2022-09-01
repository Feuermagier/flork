package de.firemage.flork.flow.engine;

import de.firemage.flork.flow.value.ValueSet;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public record VarState(ValueSet value, Set<VarRelation> relations) {
    public VarState addRelation(VarRelation relation) {
        Set<VarRelation> result = new HashSet<>(this.relations.size() + 1);
        result.addAll(this.relations);
        result.add(relation);
        return new VarState(this.value, result);
    }

    @Override
    public String toString() {
        return "[" + this.value + (!this.relations.isEmpty() ? ", " + this.relations.stream().map(VarRelation::toString).collect(
            Collectors.joining(", ")) : "") + "]";
    }
}
