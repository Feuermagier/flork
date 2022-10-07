package de.firemage.flork.flow;

import de.firemage.flork.flow.analysis.FlowMethodAnalysis;
import de.firemage.flork.flow.analysis.MethodAnalysis;
import de.firemage.flork.flow.analysis.StubMethodAnalysis;
import de.firemage.flork.flow.value.ValueSet;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtExecutableReference;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FlowContext {
    public static final String VALUE_KEY = "flork.value";

    private final Map<String, MethodAnalysis> hardcodedMethods;
    private final Map<String, CachedMethod> methods;
    private final CtModel model;
    private final boolean closedWorld;

    public FlowContext(CtModel model, boolean closedWorld) {
        this.hardcodedMethods = new HashMap<>();
        this.hardcodedMethods.put("java.lang.Object::java.lang.Object()", StubMethodAnalysis.forParameterlessVoid("java.lang.Object.<init>", this));
        
        this.methods = new HashMap<>();
        this.model = model;
        this.closedWorld = closedWorld;
    }

    public CtModel getModel() {
        return this.model;
    }

    public boolean isClosedWorld() {
        return closedWorld;
    }
    
    public CachedMethod getCachedMethod(CtExecutableReference<?> executable) {
        return this.methods.computeIfAbsent(this.buildQualifiedExecutableName(executable), m -> new CachedMethod(executable, this));
    }

    public MethodAnalysis analyzeMethod(CtExecutableReference<?> method) {
        Object analysis = method.getMetadata("flork.methodAnalysis");
        if (analysis != null) {
            System.out.println("= Retrieved cached analysis of " + method.getSignature());
            return (MethodAnalysis) analysis;
        } else {
            MethodAnalysis newAnalysis;
            String qualifiedName = buildQualifiedExecutableName(method);
            if (hardcodedMethods.containsKey(qualifiedName)) {
                System.out.println("= Using hardcoded analysis of " + method.getSignature());
                newAnalysis = hardcodedMethods.get(qualifiedName);
            } else if (method.getDeclaration() == null) {
                System.out.println("= Using stub analysis for " + method.getSignature());
                newAnalysis = StubMethodAnalysis.forReferencedExecutable(method, this);
            } else {
                newAnalysis = FlowMethodAnalysis.analyzeMethod(method.getDeclaration(), this);
            }
            method.putMetadata("flork.methodAnalysis", newAnalysis);
            return newAnalysis;
        }
    }

    public ValueSet getExpressionValue(CtExpression<?> expression) {
        return (ValueSet) expression.getMetadata(VALUE_KEY);
    }

    private String buildQualifiedExecutableName(CtExecutableReference<?> executable) {
        return executable.getDeclaringType().getQualifiedName() + "::" + executable.getSignature();
    }
}
