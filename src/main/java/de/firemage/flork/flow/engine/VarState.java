package de.firemage.flork.flow.engine;

import de.firemage.flork.flow.BooleanStatus;
import de.firemage.flork.flow.value.ValueSet;

import java.util.Set;
import java.util.stream.Collectors;

public record VarState(ValueSet value, Set<VarRelation> relations) {

    public VarState(ValueSet value) {
        this(value, Set.of());
    }

    public BooleanStatus canFulfillRelation(ValueSet other, Relation relation) {
        if (this.value.equals(other)) {
            if (Relation.EQUAL.implies(relation)) {
                return BooleanStatus.ALWAYS;
            } else if (relation.implies(Relation.NOT_EQUAL)) {
                return BooleanStatus.NEVER;
            }
        }

        for (VarRelation r : this.relations) {
            if (r.relation().implies(relation)) {
                return BooleanStatus.ALWAYS;
            } else if (r.relation().implies(relation.negate())) {
                return BooleanStatus.NEVER;
            }
        }

        return this.value.fulfillsRelation(other, relation);
    }

    @Override
    public String toString() {
        return "[" + this.value +
            (!this.relations.isEmpty() ? ", " + this.relations.stream().map(VarRelation::toString).collect(
                Collectors.joining(", ")) : "") + "]";
    }
}
