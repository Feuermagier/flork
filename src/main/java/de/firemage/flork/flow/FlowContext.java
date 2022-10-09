package de.firemage.flork.flow;

import de.firemage.flork.flow.analysis.HardcodedAnalysisSupplier;
import de.firemage.flork.flow.value.ValueSet;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class FlowContext {
    public static final String VALUE_KEY = "flork.value";

    private final HardcodedAnalysisSupplier hardcodedMethods;
    private final Map<String, CachedMethod> methods;
    private final Factory factory;
    private final CtModel model;
    private final boolean closedWorld;

    private int indentationLevel = -1;

    public FlowContext(Factory factory, boolean closedWorld) {
        this.methods = new HashMap<>();
        this.factory = factory;
        this.model = factory.getModel();
        this.closedWorld = closedWorld;

        this.hardcodedMethods = new HardcodedAnalysisSupplier(this);
    }

    public static String buildQualifiedExecutableName(CtExecutableReference<?> executable) {
        return executable.getDeclaringType().getQualifiedName() + "::" + executable.getSignature();
    }

    public void increaseIndentation() {
        this.indentationLevel++;
    }

    public void decreaseIndentation() {
        this.indentationLevel--;
    }

    public void log(String message) {
        System.out.println("    ".repeat(this.indentationLevel) + message);
    }

    public CtModel getModel() {
        return this.model;
    }

    public TypeId getObject() {
        return new TypeId(this.model.filterChildren(t -> t instanceof CtClass c && c.getQualifiedName().equals("java.lang.Object"))
                .<CtClass>first().getReference());
    }

    public boolean isClosedWorld() {
        return closedWorld;
    }

    public CachedMethod getCachedMethod(CtExecutableReference<?> executable) {
        return this.methods.computeIfAbsent(buildQualifiedExecutableName(executable), m -> new CachedMethod(executable, this));
    }

    public HardcodedAnalysisSupplier getHardcodedMethods() {
        return this.hardcodedMethods;
    }

    public TypeId getType(String name) {
        return this.model.filterChildren(t -> t instanceof CtType c && c.getQualifiedName().equals(name))
                .map(t -> new TypeId(((CtType) t).getReference()))
                .first();
    }

    public Stream<TypeId> getAllTypes() {
        return this.model.getAllTypes().stream().map(t -> new TypeId(t.getReference()));
    }

    public boolean isEffectivelyFinalType(TypeId type) {
        if (type.type().getDeclaration() != null && type.type().getDeclaration().isFinal()) {
            return true;
        } else {
            return this.getAllTypes().noneMatch(t -> t.isSubtypeOf(type) && !t.equals(type));
        }
    }

    public ValueSet getExpressionValue(CtExpression<?> expression) {
        return (ValueSet) expression.getMetadata(VALUE_KEY);
    }

    public Factory getFactory() {
        return this.factory;
    }
}
