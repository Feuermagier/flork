package de.firemage.flork.flow.engine;

import java.util.Objects;

public class SSAVarId {
    private final FieldId fieldId;
    private final int index;

    private SSAVarId(FieldId fieldId, int index) {
        this.fieldId = fieldId;
        this.index = index;
    }

    public static SSAVarId forFresh(FieldId fieldId) {
        return new SSAVarId(fieldId, 0);
    }

    public SSAVarId next() {
        return new SSAVarId(this.fieldId, this.index + 1);
    }

    public FieldId fieldId() {
        return this.fieldId;
    }

    public int index() {
        return this.index;
    }

    public boolean isInitial() {
        return this.index == 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SSAVarId ssaVarId = (SSAVarId) o;
        return index == ssaVarId.index && fieldId.equals(ssaVarId.fieldId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldId, index);
    }

    @Override
    public String toString() {
        return this.fieldId + "'" + this.index;
    }
}
