package de.firemage.flork.flow.value;

public enum Nullness {
    NULL,
    UNKNOWN,
    NON_NULL,
    BOTTOM;

    public Nullness merge(Nullness other) {
        if (this == BOTTOM) {
            return other;
        } else if (other == BOTTOM) {
            return this;
        } else if (this == UNKNOWN || other == UNKNOWN) {
            return UNKNOWN;
        } else if (this != other) {
            return UNKNOWN;
        } else {
            return this;
        }
    }

    public Nullness intersect(Nullness other) {
        if (this == BOTTOM || other == BOTTOM) {
            return BOTTOM;
        } else if (this == UNKNOWN) {
            return other;
        } else if (other == UNKNOWN) {
            return this;
        } else if (this != other) {
            return BOTTOM;
        } else {
            return this;
        }
    }

    public boolean isSupersetOf(Nullness other) {
        if (this == UNKNOWN) {
            return true;
        } else if (other == BOTTOM) {
            return true;
        } else if (this == other) {
            return true;
        } else {
            return false;
        }
    }

    public boolean canBeNull() {
        return this == NULL || this == UNKNOWN;
    }
}
