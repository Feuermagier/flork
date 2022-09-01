package de.firemage.flork.flow;

import de.firemage.flork.flow.hardcoded.StubMethodAnalysis;
import de.firemage.flork.flow.value.ValueSet;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtExecutableReference;
import java.util.HashMap;
import java.util.Map;

public class FlowAnalysis {
    /* package-private */ static final String VALUE_KEY = "flork.value";

    private final Map<String, MethodAnalysis> hardcodedMethods;

    private final FlowContext context;

    public FlowAnalysis(FlowContext context) {
        this.context = context;

        this.hardcodedMethods = new HashMap<>();
        this.hardcodedMethods.put("java.lang.Object::java.lang.Object()", StubMethodAnalysis.forParameterlessVoid("java.lang.Object.<init>", context));
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

    public FlowContext getContext() {
        return this.context;
    }

    private String buildQualifiedExecutableName(CtExecutableReference<?> executable) {
        return executable.getDeclaringType().getQualifiedName() + "::" + executable.getSignature();
    }
}
