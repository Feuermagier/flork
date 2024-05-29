package de.firemage.flork.flow.engine;

/**
 * Represents the name of a local or field of an object through its chain of identifiers
 * E.g. this.x.y, or foo (which would be a local)
 * This class doesn't store the actual value, since it may change over time
 * For the value at a specific point in time, use {@link FieldId}.
 * This class is not engine state-specific.
 * @param parent
 * @param name
 */
public record VarId(VarId parent, String name) {
    public static final VarId THIS = VarId.forLocal("this");

    public static VarId forLocal(String name) {
        return new VarId(null, name);
    }

    public static VarId forOwnField(String name) {
        return new VarId(VarId.THIS, name);
    }
    
    public String fieldName() {
        var parts = this.name().split("\\.");
        return parts[parts.length - 1];
    }
    
    public VarId resolveField(String field) {
        return new VarId(this, this.name + "." + field);
    }

    public VarId relativize(VarId parent) {
        if (this.parent == null || !this.name.startsWith(parent.name)) {
            return null;
        }

        return new VarId(parent, this.name.substring(parent.name.length()));
    }

    @Override
    public String toString() {
        return this.name;
    }
}
