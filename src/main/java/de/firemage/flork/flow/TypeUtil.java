package de.firemage.flork.flow;

import spoon.reflect.declaration.CtClass;
import spoon.reflect.reference.CtTypeReference;
import java.util.HashSet;
import java.util.Set;

public final class TypeUtil {
    private TypeUtil() {

    }

    public static CtTypeReference<?> findLowestCommonSupertype(CtTypeReference<?> a, CtTypeReference<?> b, FlowContext context) {
        if (a.getQualifiedName().equals(b.getQualifiedName())) {
            return a;
        }

        Set<String> aParents = new HashSet<>();
        aParents.add(a.getQualifiedName());

        CtTypeReference<?> aParent = a;
        while (!aParent.getQualifiedName().equals("java.lang.Object")) {
            aParent = TypeUtil.getBestSuperclass(aParent, context);
            aParents.add(aParent.getQualifiedName());
        }

        CtTypeReference<?> bParent = b;
        while (true) {
            if (aParents.contains(bParent.getQualifiedName())) {
                return bParent;
            }
            bParent = TypeUtil.getBestSuperclass(bParent, context);
        }
    }

    public static CtTypeReference<?> getBestSuperclass(CtTypeReference<?> type, FlowContext context) {
        CtTypeReference<?> superclass = type.getSuperclass();
        if (superclass == null) {
            return context.getModel()
                    .filterChildren(e -> e instanceof CtClass c && c.getQualifiedName().equals("java.lang.Object"))
                    .<CtClass>first().getReference();
        } else {
            return superclass;
        }
    }
}
