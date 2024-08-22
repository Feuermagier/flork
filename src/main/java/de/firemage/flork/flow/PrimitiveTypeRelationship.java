package de.firemage.flork.flow;

import spoon.reflect.reference.CtTypeReference;

public enum PrimitiveTypeRelationship {
    SAME,
    LHS_WIDER,
    RHS_WIDER;

    /**
     * As always, the method assumes that the types are generally compatible. If e.g. lhs is boolean and rhs is int,
     * the output may be arbitrarily wrong.
     * @param lhs
     * @param rhs
     * @return
     */
    public static PrimitiveTypeRelationship compareTypes(CtTypeReference<?> lhs, CtTypeReference<?> rhs) {
        if (!lhs.isPrimitive() && !rhs.isPrimitive()) {
            // Comparing objects is always possible
            return SAME;
        }

        String lhsName = lhs.getQualifiedName();
        String rhsName = rhs.getQualifiedName();

        if (lhsName.equals(rhsName)) {
            return SAME;
        }

        return switch (lhsName) {
            case "byte" -> RHS_WIDER;
            case "short" -> switch (rhsName) {
                case "int", "long", "float", "double" -> RHS_WIDER;
                default -> LHS_WIDER;
            };
            case "char" -> switch (rhsName) {
                case "int", "long", "float", "double" -> RHS_WIDER;
                default -> LHS_WIDER;
            };
            case "int" -> switch (rhsName) {
                case "long", "float", "double" -> RHS_WIDER;
                default -> LHS_WIDER;
            };
            case "long" -> switch (rhsName) {
                case "float", "double" -> RHS_WIDER;
                default -> LHS_WIDER;
            };
            case "float" -> switch (rhsName) {
                case "double" -> RHS_WIDER;
                default -> LHS_WIDER;
            };
            default -> throw new IllegalStateException();
        };
    }

    private static boolean isNumeric(String type) {
        return switch (type) {
            case "byte", "short", "int", "long", "float", "double" -> true;
            default -> false;
        };
    }
}
