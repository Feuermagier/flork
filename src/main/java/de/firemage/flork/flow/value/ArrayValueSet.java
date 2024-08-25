package de.firemage.flork.flow.value;

public class ArrayValueSet {
    private final ObjectValueSet elementType;
    private final int minLength;
    private final int maxLength;

    public ArrayValueSet(ObjectValueSet elementType, int minLength, int maxLength) {
        this.elementType = elementType;
        this.minLength = minLength;
        this.maxLength = maxLength;
    }

    
}
