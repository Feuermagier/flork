package de.firemage.flork.flow;

public enum StandardExceptions {
    NULL_POINTER("java.lang.NullPointerException"),
    CLASS_CAST("java.lang.ClassCastException");

    private final String qualifiedName;

    StandardExceptions(String qualifiedName) {
        this.qualifiedName = qualifiedName;
    }

    public String getQualifiedName() {
        return qualifiedName;
    }
}
