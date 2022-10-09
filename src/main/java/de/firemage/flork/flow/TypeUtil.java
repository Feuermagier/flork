package de.firemage.flork.flow;

import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.reflect.reference.CtTypeReferenceImpl;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public final class TypeUtil {
    private TypeUtil() {

    }

    public static TypeId findLowestCommonSupertype(TypeId a, TypeId b,
                                                               FlowContext context) {
        if (a.equals(b)) {
            return a;
        }

        Set<TypeId> aParents = new HashSet<>();
        aParents.add(a);

        TypeId aParent = a;
        while (!aParent.isObject()) {
            aParent = TypeUtil.getBestSuperclass(aParent, context);
            aParents.add(aParent);
        }

        TypeId bParent = b;
        while (true) {
            if (aParents.contains(bParent)) {
                return bParent;
            }
            bParent = TypeUtil.getBestSuperclass(bParent, context);
        }
    }

    public static TypeId getBestSuperclass(TypeId type, FlowContext context) {
        return type.getSuperclass().orElseGet(context::getObject);
    }

    /**
     * Does not return the method itself!!!
     * @param method
     * @param context
     * @return
     */
    public static Stream<? extends CtExecutableReference<?>> getAllOverridingMethods(CtExecutableReference<?> method,
                                                                                     FlowContext context) {
        return context.getModel()
            .getElements(CtExecutable.class::isInstance) // TODO filtering for CtExecutable instead of refs may be suboptimal, but refs include only referenced executables
            .stream()
            .map(e -> ((CtExecutable<?>) e).getReference())
            .filter(e -> e.isOverriding(method) && !e.equals(method));
    }

    public static CtTypeReference<?> buildReference(String qualifiedName, FlowContext context) {
        return context.getFactory().createReference(qualifiedName);
    }

    public static boolean isTrueSubtype(TypeId subtype, TypeId supertype) {
        return !subtype.equals(supertype) && subtype.isSubtypeOf(supertype);
    }
}
