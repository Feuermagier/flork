package de.firemage.flork.flow;

import de.firemage.flork.flow.analysis.HardcodedAnalysisSupplier;
import de.firemage.flork.flow.value.ValueSet;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtInterface;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
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
    private final EnumMap<StandardExceptions, TypeId> standardExceptions = new EnumMap<>(StandardExceptions.class);

    private final Deque<AnalysisLocation> locationStack = new ArrayDeque<>();

    public FlowContext(Factory factory, boolean closedWorld) {
        this.methods = new HashMap<>();
        this.factory = factory;
        this.model = factory.getModel();
        this.closedWorld = closedWorld;

        this.hardcodedMethods = new HardcodedAnalysisSupplier(this);

        for (var exception : StandardExceptions.values()) {
            this.standardExceptions.put(exception, new TypeId(factory.Type().createReference(exception.getQualifiedName())));
        }
    }

    public static String buildQualifiedExecutableName(CtExecutableReference<?> executable) {
        return executable.getDeclaringType().getQualifiedName() + "::" + executable.getSignature();
    }

    public void pushLocation() {
        if (this.locationStack.isEmpty()) {
            this.locationStack.push(new AnalysisLocation());
        } else {
            this.locationStack.push(this.locationStack.peek().next());
        }
    }

    public void popLocation() {
        this.locationStack.pop();
    }

    public void setCurrentElement(CtElement element) {
        this.locationStack.peek().setCurrentElement(element);
    }

    public void log(String message) {
        System.out.println(this.locationStack.peek().formatPrefix() + message);
    }

    public void logNoPrefix(String message) {
        System.out.println(this.locationStack.peek().formatEmptyPrefix() + message);
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
        return new TypeId(this.factory.Type().createReference(name));
    }

    public Stream<TypeId> getAllTypes() {
        return this.model.getAllTypes().stream().map(t -> new TypeId(t.getReference()));
    }

    public boolean isEffectivelyFinalType(TypeId type) {
        if (type.type() instanceof CtInterface<?>) {
            return false;
        } else if (type.type().getDeclaration() == null) {
            // E.g. JDK classes
            return false;
        } else if (type.type().getDeclaration().isFinal()) {
            // Final classes are obviously effectively final
            return true;
        } else if (this.closedWorld) {
            // In a closed world, we know all potential implementers
            // We don't need to consider lambdas here, since lambdas can only implement interfaces
            // and interfaces are caught by the first if
            return this.getAllTypes().noneMatch(t -> t.isSubtypeOf(type) && !t.equals(type));
        }
        return false;
    }

    public ValueSet getExpressionValue(CtExpression<?> expression) {
        return (ValueSet) expression.getMetadata(VALUE_KEY);
    }

    public Factory getFactory() {
        return this.factory;
    }
}
