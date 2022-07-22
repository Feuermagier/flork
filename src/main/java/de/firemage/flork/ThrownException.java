package de.firemage.flork;

import spoon.reflect.declaration.CtElement;

public class ThrownException {
    private final String qualifiedName;
    private final CtElement source;

    public ThrownException(String qualifiedName, CtElement source) {
        this.qualifiedName = qualifiedName;
        this.source = source;
    }
}
