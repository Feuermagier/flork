package de.firemage.flork.flow;

import spoon.reflect.reference.CtTypeReference;
import java.util.Objects;
import java.util.Optional;

public record TypeId(CtTypeReference<?> type) {
    public static Optional<TypeId> ofFallible(CtTypeReference<?> type) {
        return type == null ? Optional.empty() : Optional.of(new TypeId(type));
    }

    public String getName() {
        return this.type.getQualifiedName();
    }

    public boolean isSubtypeOf(TypeId other) {
        return this.type.isSubtypeOf(other.type);
    }

    public Optional<TypeId> getSuperclass() {
        return Optional.ofNullable(this.type.getSuperclass()).map(TypeId::new);
    }

    public boolean isObject() {
        return this.getName().equals("java.lang.Object");
    }

    public boolean isNulltype() {
        return this.getName().equals("<nulltype>");
    }

    public boolean isPrimitive() {
        return this.type.isPrimitive();
    }

    public boolean isVoid() {
        return this.getName().equals("void");
    }

    public boolean isBoolean() {
        return this.getName().equals("boolean");
    }

    public boolean isInt() {
        return this.getName().equals("int");
    }

    public boolean isLong() {
        return this.getName().equals("long");
    }

    public boolean isFloat() {
        return this.getName().equals("float");
    }

    public boolean isDouble() {
        return this.getName().equals("double");
    }

    public boolean isByte() {
        return this.getName().equals("byte");
    }

    public boolean isShort() {
        return this.getName().equals("short");
    }

    public boolean isChar() {
        return this.getName().equals("char");
    }

    public boolean isJDKType() {
        return this.type.isShadow();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TypeId typeId = (TypeId) o;
        return this.getName().equals(typeId.getName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getName());
    }
}
