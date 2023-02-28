package de.firemage.flork.flow.engine;

public record VarRelation(int rhs, Relation relation) {

    @Override
    public String toString() {
        return switch (this.relation) {
            case EQUAL -> "==";
            case NOT_EQUAL -> "!=";
            case LESS_THAN -> "<";
            case LESS_THAN_EQUAL -> "<=";
            case GREATER_THAN -> ">";
            case GREATER_THAN_EQUAL -> ">=";
        } + " $" + this.rhs;
    }
}
