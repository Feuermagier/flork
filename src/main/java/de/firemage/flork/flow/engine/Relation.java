package de.firemage.flork.flow.engine;

public enum Relation {
    EQUAL,
    NOT_EQUAL,
    LESS_THAN,
    LESS_THAN_EQUAL,
    GREATER_THAN,
    GREATER_THAN_EQUAL;

    public Relation invert() {
        return switch (this) {
            case EQUAL -> EQUAL;
            case NOT_EQUAL -> NOT_EQUAL;
            case LESS_THAN -> GREATER_THAN;
            case LESS_THAN_EQUAL -> GREATER_THAN_EQUAL;
            case GREATER_THAN -> LESS_THAN;
            case GREATER_THAN_EQUAL -> LESS_THAN_EQUAL;
        };
    }

    public Relation negate() {
        return switch (this) {
            case EQUAL -> NOT_EQUAL;
            case NOT_EQUAL -> EQUAL;
            case LESS_THAN -> GREATER_THAN_EQUAL;
            case LESS_THAN_EQUAL -> GREATER_THAN;
            case GREATER_THAN -> LESS_THAN_EQUAL;
            case GREATER_THAN_EQUAL -> LESS_THAN;
        };
    }

    public boolean implies(Relation other) {
        if (this == other) {
            return true;
        }

        return switch (this) {
            case EQUAL -> other == LESS_THAN_EQUAL || other == GREATER_THAN_EQUAL;
            case LESS_THAN -> other == NOT_EQUAL || other == LESS_THAN_EQUAL;
            case GREATER_THAN -> other == NOT_EQUAL || other == GREATER_THAN_EQUAL;
            case NOT_EQUAL, LESS_THAN_EQUAL, GREATER_THAN_EQUAL -> false;
        };
    }
}
