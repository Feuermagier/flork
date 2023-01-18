package de.firemage.flork.flow.engine;

public record VarId(VarId parent, String name) {
    public static VarId forLocal(String name) {
        return new VarId(null, name);
    }
    
    public String fieldName() {
        var parts = this.name().split("\\.");
        return parts[parts.length - 1];
    }
    
    public VarId resolveField(String field) {
        return new VarId(this, this.name + "." + field);
    }
    
    @Override
    public String toString() {
        return this.name;
    }
}
