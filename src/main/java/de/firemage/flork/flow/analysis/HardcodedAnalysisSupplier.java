package de.firemage.flork.flow.analysis;

import de.firemage.flork.flow.FlowContext;
import de.firemage.flork.flow.engine.VarId;
import spoon.reflect.reference.CtExecutableReference;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class HardcodedAnalysisSupplier {
    private final Map<String, HardcodedMethodAnalysis> hardcodedMethods;

    public HardcodedAnalysisSupplier(FlowContext context) {
        this.hardcodedMethods = new HashMap<>();
        try {
            this.addAnalysis(context.getFactory().Constructor().createReference(Object.class.getConstructor()),
                List.of(), context);
        } catch (NoSuchMethodException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void addAnalysis(CtExecutableReference<?> executable, List<String> parameterNames, FlowContext context) {
        var method = context.getCachedMethod(executable);
        this.hardcodedMethods.put(FlowContext.buildQualifiedExecutableName(executable),
            new HardcodedMethodAnalysis(method, parameterNames, context));
    }

    public Optional<HardcodedMethodAnalysis> getForMethod(CtExecutableReference<?> method) {
        return Optional.ofNullable(this.hardcodedMethods.get(FlowContext.buildQualifiedExecutableName(method)));
    }

}
