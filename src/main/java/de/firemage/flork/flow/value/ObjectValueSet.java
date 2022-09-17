package de.firemage.flork.flow.value;

import de.firemage.flork.flow.BooleanStatus;
import de.firemage.flork.flow.FlowContext;
import de.firemage.flork.flow.TypeUtil;
import de.firemage.flork.flow.engine.Relation;
import spoon.reflect.reference.CtTypeReference;

import java.util.Objects;

public final class ObjectValueSet extends ValueSet {
    private final FlowContext context;
    private final Nullness nullness;
    private final CtTypeReference<?> supertype;
    private final boolean exact;

    public static ObjectValueSet bottom(FlowContext context) {
        return new ObjectValueSet(Nullness.BOTTOM, null, true, context);
    }

    public ObjectValueSet(Nullness nullness, CtTypeReference<?> supertype, boolean exact, FlowContext context) {
        this.nullness = nullness;
        this.supertype = supertype;
        this.exact = exact;
        this.context = context;
    }

    public CtTypeReference<?> getSupertype() {
        return supertype;
    }

    @Override
    public ValueSet merge(ValueSet o) {
        ObjectValueSet other = (ObjectValueSet) o;
        if (this.isEmpty()) {
            return other;
        } else if (other.isEmpty()) {
            return this;
        } else {
            boolean resultExact = this.exact && other.exact && typesMatch(this, other);
            var resultType = TypeUtil.findLowestCommonSupertype(this.supertype, other.supertype, this.context);
            return new ObjectValueSet(this.nullness.merge(other.nullness), resultType, resultExact, this.context);
        }
    }

    @Override
    public ValueSet tryMergeExact(ValueSet o) {
        ObjectValueSet other = (ObjectValueSet) o;
        if (this.isEmpty()) {
            return other;
        } else if (other.isEmpty()) {
            return this;
        } else if (this.exact && other.exact) {
            if (typesMatch(this, other)) {
                return new ObjectValueSet(this.nullness.merge(other.nullness), this.supertype, true, this.context);
            } else {
                return null;
            }
        } else if (this.isSupersetOf(other)) {
            return this;
        } else if (other.isSupersetOf(this)) {
            return other;
        } else {
            return null;
        }
    }

    @Override
    public ValueSet intersect(ValueSet o) {
        ObjectValueSet other = (ObjectValueSet) o;
        if (this.nullness.intersect(other.nullness) == Nullness.BOTTOM) {
            return ObjectValueSet.bottom(this.context);
        } else if (this.exact && other.exact) {
            if (typesMatch(this, other)) {
                return new ObjectValueSet(this.nullness.intersect(other.nullness), this.supertype, true, this.context);
            } else {
                return ObjectValueSet.bottom(this.context);
            }
        } else if (other.supertype.isSubtypeOf(this.supertype)) {
            return new ObjectValueSet(this.nullness.intersect(other.nullness), other.supertype, false, this.context);
        } else if (this.supertype.isSubtypeOf(other.supertype)) {
            return new ObjectValueSet(this.nullness.intersect(other.nullness), this.supertype, false, this.context);
        } else {
            return ObjectValueSet.bottom(this.context);
        }
    }

    @Override
    public boolean isSupersetOf(ValueSet o) {
        ObjectValueSet other = (ObjectValueSet) o;
        if (other.isEmpty()) {
            return true;
        } else if (this.isEmpty()) {
            return false;
        }

        return this.nullness.isSupersetOf(other.nullness) && other.supertype.isSubtypeOf(this.supertype);
    }

    @Override
    public boolean isEmpty() {
        return this.nullness == Nullness.BOTTOM;
    }

    @Override
    public BooleanStatus fulfillsRelation(ValueSet other, Relation relation) {
        if (this.isEmpty()) {
            return relation == Relation.EQUAL ? BooleanStatus.NEVER : BooleanStatus.ALWAYS;
        } else if (this.intersect(other).isEmpty()) {
            return relation == Relation.EQUAL ? BooleanStatus.ALWAYS : BooleanStatus.NEVER;
        } else {
            return BooleanStatus.SOMETIMES;
        }
    }

    @Override
    public ValueSet removeNotFulfillingValues(ValueSet o, Relation relation) {
        ObjectValueSet other = (ObjectValueSet) o;
        if (relation == Relation.EQUAL) {
            return this.intersect(other);
        } else if (relation == Relation.NOT_EQUAL) {
            if (this.nullness == Nullness.NON_NULL && other.nullness == Nullness.NULL
                    || this.nullness == Nullness.NULL && other.nullness == Nullness.NON_NULL) {
                return ObjectValueSet.bottom(this.context);
            } else {
                return this;
            }
        } else {
            throw new IllegalArgumentException("Cannot compare objects with " + relation);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ObjectValueSet that = (ObjectValueSet) o;
        return exact == that.exact && nullness == that.nullness && Objects.equals(supertype, that.supertype);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nullness, supertype, exact);
    }

    @Override
    public String toString() {
        return this.nullness.toString().toLowerCase() + "/" + (this.exact ? "=" : "<=") + this.supertype.getQualifiedName();
    }
    
    private boolean typesMatch(ObjectValueSet a, ObjectValueSet b)  {
        return a.supertype.getQualifiedName().equals(b.supertype.getQualifiedName());
    }
}
