package de.firemage.flork.flow.engine;

public record FieldId(int parent, String fieldName) {
    public static FieldId forLocal(String name) {
        return new FieldId(-1, name);
    }
    
    public static FieldId forField(int parent, String name) {
        return new FieldId(parent, name);
    }
    
    public boolean isLocal() {
        return this.parent < 0;
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
