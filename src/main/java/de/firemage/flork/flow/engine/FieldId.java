package de.firemage.flork.flow.engine;

/**
 * Represents the name of a field of an object through its chain of values
 *
 * @param parent The value id of this object's parent within a specific engine state
 * @param fieldName
 */
public record FieldId(int parent, String fieldName) {
    public static FieldId THIS = FieldId.forLocal("this");

    public static FieldId forLocal(String name) {
        return new FieldId(-1, name);
    }
    
    public static FieldId forField(int parent, String name) {
        return new FieldId(parent, name);
    }

    public static FieldId forOwnField(String name) {
        return new FieldId(EngineState.THIS_VALUE, name);
    }
    
    public boolean isLocal() {
        return this.parent < 0;
    }

    public boolean isLocalOrOwnField() {
        return this.parent <= 0;
    }

    @Override
    public String toString() {
        if (this.isLocal()) {
            return this.fieldName;
        } else {
            return "$" + this.parent + "." + this.fieldName;
        }
    }
}
