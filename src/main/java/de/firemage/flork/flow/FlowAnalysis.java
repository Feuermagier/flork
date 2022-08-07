package de.firemage.flork.flow;

import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtMethod;

public class FlowAnalysis {
    /* package-private */ static final String VALUE_KEY = "flork.value";

    public FlowAnalysis() {
    }

    public MethodAnalysis analyzeMethod(CtMethod<?> method) {
        Object analysis = method.getMetadata("flork.methodAnalysis");
        if (analysis != null) {
            return (MethodAnalysis) analysis;
        } else {
            MethodAnalysis newAnalysis = MethodAnalysis.analyzeMethod(method, this);
            method.putMetadata("flork.methodAnalysis", newAnalysis);
            return newAnalysis;
        }
    }

    public ValueSet getExpressionValue(CtExpression<?> expression) {
        return (ValueSet) expression.getMetadata(VALUE_KEY);
    }
}
