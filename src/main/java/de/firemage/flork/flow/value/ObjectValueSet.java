package de.firemage.flork.flow.value;

import de.firemage.flork.flow.BooleanStatus;
import de.firemage.flork.flow.FlowContext;
import de.firemage.flork.flow.TypeId;
import de.firemage.flork.flow.TypeUtil;
import de.firemage.flork.flow.engine.Relation;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public sealed class ObjectValueSet extends ValueSet permits BoxedIntValueSet {
    private static ObjectValueSet nullSet;

    private final FlowContext context;
    protected final Nullness nullness;
    private final TypeId supertype;
    private final Set<TypeId> lowerLimitingTypes;
    private final boolean exact;

    public ObjectValueSet(Nullness nullness, TypeId supertype, Set<TypeId> lowerLimitingTypes, FlowContext context) {
        this.nullness = nullness;
        this.supertype = supertype;
        this.lowerLimitingTypes = lowerLimitingTypes;
        this.context = context;
        this.exact = supertype == null || lowerLimitingTypes.contains(supertype);
    }

    public ObjectValueSet(ObjectValueSet other) {
        this(other.nullness, other.supertype, new HashSet<>(other.lowerLimitingTypes),
            other.context);
    }

    public static ObjectValueSet getNullSet(FlowContext context) {
        if (nullSet == null) {
            nullSet =
                ObjectValueSet.forExactType(Nullness.NULL, new TypeId(context.getFactory().Type().NULL_TYPE), context);
        }
        return nullSet;
    }

    public static ObjectValueSet forExactType(Nullness nullness, TypeId type, FlowContext context) {
        return new ObjectValueSet(nullness, type, Set.of(type), context);
    }

    public static ObjectValueSet forUnconstrainedType(Nullness nullness, TypeId type, FlowContext context) {
        return new ObjectValueSet(nullness, type, Set.of(), context);
    }

    public static ObjectValueSet bottom(FlowContext context) {
        return new ObjectValueSet(Nullness.BOTTOM, null, Set.of(), context);
    }

    private static boolean typesMatch(ObjectValueSet a, ObjectValueSet b) {
        return a.supertype.equals(b.supertype);
    }

    private static Set<TypeId> mergeLowerBounds(TypeId aRoot, Set<TypeId> a, TypeId bRoot, Set<TypeId> b) {
        return Stream.concat(
            a.stream().map(type -> {
                for (TypeId other : b) {
                    if (type.isSubtypeOf(other)) {
                        return type;
                    } else if (other.isSubtypeOf(type)) {
                        return other;
                    }
                }
                return null;
            }).filter(Objects::nonNull),
            Stream.concat(
                // Types for which the respective other set does not make any statement
                a.stream().filter(t -> TypeUtil.areSiblingTypes(bRoot, t)),
                b.stream().filter(t -> TypeUtil.areSiblingTypes(aRoot, t)))
        ).collect(Collectors.toSet());
    }

    private static Set<TypeId> intersectLowerBounds(Set<TypeId> a, Set<TypeId> b) {
        return Stream.concat(
            a.stream().map(type -> b.stream().filter(type::isSubtypeOf).findAny().orElse(type)),
            b.stream().map(type -> a.stream().filter(type::isSubtypeOf).findAny().orElse(type))
        ).collect(Collectors.toSet());
    }

    public TypeId getSupertype() {
        return supertype;
    }

    public boolean isExact() {
        return this.exact;
    }

    public TypeId getFieldType(String name) {
        return new TypeId(this.supertype.type().getDeclaredOrInheritedField(name).getType());
    }

    public ObjectValueSet asNonNull() {
        if (this.nullness == Nullness.NON_NULL) {
            return this;
        } else if (this.nullness == Nullness.UNKNOWN) {
            return new ObjectValueSet(Nullness.NON_NULL, this.supertype, this.lowerLimitingTypes, this.context);
        } else if (this.nullness == Nullness.NULL) {
            throw new IllegalStateException("would throw an NPE");
        } else {
            throw new IllegalStateException("Nullness is bottom");
        }
    }

    @Override
    public ValueSet merge(ValueSet o) {
        ObjectValueSet other = (ObjectValueSet) o;
        if (this.isEmpty()) {
            return other;
        } else if (other.isEmpty()) {
            return this;
        } else {
            var resultType = TypeUtil.findLowestCommonSupertype(this.supertype, other.supertype, this.context);
            var resultBounds =
                mergeLowerBounds(this.supertype, this.lowerLimitingTypes, other.supertype, other.lowerLimitingTypes);
            return new ObjectValueSet(this.nullness.merge(other.nullness), resultType, resultBounds, this.context);
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
                return new ObjectValueSet(this.nullness.merge(other.nullness), this.supertype, this.lowerLimitingTypes, this.context);
            } else {
                return null;
            }
        } else if (this.isSupersetOf(other)) {
            return this;
        } else if (other.isSupersetOf(this)) {
            return other;
        } else {
            // TODO this may not be entirely true as exact merging may be
            //  possible if isSupersetOf returns false for incompatible lower bounds
            return null;
        }
    }

    @Override
    public ValueSet intersect(ValueSet o) {
        ObjectValueSet other = (ObjectValueSet) o;

        // This or other is the nulltype, for which we do not know supertypes / lower bounds
        if (this.supertype.isNulltype() && other.nullness.canBeNull() || other.supertype.isNulltype() && this.nullness.canBeNull()) {
            return ObjectValueSet.getNullSet(this.context);
        }

        if (this.nullness.intersect(other.nullness) == Nullness.BOTTOM) {
            return ObjectValueSet.bottom(this.context);
        } else if (other.supertype.isSubtypeOf(this.supertype)
            && this.lowerLimitingTypes.stream().noneMatch(t -> TypeUtil.isTrueSubtype(other.supertype, t))) {
            return new ObjectValueSet(
                this.nullness.intersect(other.nullness),
                other.supertype,
                intersectLowerBounds(this.lowerLimitingTypes, other.lowerLimitingTypes),
                this.context
            );
        } else if (this.supertype.isSubtypeOf(other.supertype)
            && other.lowerLimitingTypes.stream().noneMatch(t -> TypeUtil.isTrueSubtype(this.supertype, t))) {
            return new ObjectValueSet(
                this.nullness.intersect(other.nullness),
                this.supertype,
                intersectLowerBounds(this.lowerLimitingTypes, other.lowerLimitingTypes),
                this.context
            );
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
        } else if (this.nullness == Nullness.NULL && other.nullness == Nullness.NULL) {
            return true;
        }

        return this.nullness.isSupersetOf(other.nullness)
            && other.supertype.isSubtypeOf(this.supertype)
            && this.lowerLimitingTypes.stream()
            .noneMatch(t -> other.supertype.isSubtypeOf(t) && !t.equals(other.supertype));
    }

    @Override
    public boolean isEmpty() {
        return this.nullness == Nullness.BOTTOM;
    }

    @Override
    public BooleanStatus fulfillsRelation(ValueSet other, Relation relation) {
        if (this.isEmpty()) {
            return relation == Relation.EQUAL ? BooleanStatus.NEVER : BooleanStatus.ALWAYS;
        } else if (this.nullness == Nullness.NULL && ((ObjectValueSet) other).nullness == Nullness.NULL) {
            return relation == Relation.EQUAL ? BooleanStatus.ALWAYS : BooleanStatus.NEVER;
        } else if (this.intersect(other).isEmpty()) {
            return switch (relation) {
                case EQUAL -> BooleanStatus.NEVER;
                case NOT_EQUAL -> BooleanStatus.ALWAYS;
                default -> BooleanStatus.SOMETIMES;
            };
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
            if (this.nullness == Nullness.NULL && other.nullness == Nullness.NULL) {
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
        return this.lowerLimitingTypes.equals(that.lowerLimitingTypes)
            && nullness == that.nullness
            && Objects.equals(supertype, that.supertype);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nullness, supertype, lowerLimitingTypes);
    }

    @Override
    public String toString() {
        if (this.supertype == null) {
            return "bot";
        }

        String result = nullness.toString();
        result += "/" + (this.isExact() ? "=" : "<=") + this.supertype.getName();
        if (!this.isExact() && !this.lowerLimitingTypes.isEmpty()) {
            result +=
                "," + this.lowerLimitingTypes.stream().map(t -> ">=" + t.getName()).collect(Collectors.joining(","));
        }
        return result;
    }
}
