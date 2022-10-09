package de.firemage.flork.flow.analysis;

import de.firemage.flork.flow.FlowContext;
import spoon.reflect.reference.CtExecutableReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class HardcodedAnalysisSupplier {
    private final Map<String, HardcodedMethodAnalysis> hardcodedMethods;

    public HardcodedAnalysisSupplier(FlowContext context) {
        this.hardcodedMethods = new HashMap<>();
        this.hardcodedMethods.put(
                "java.lang.Object::java.lang.Object()",
                HardcodedMethodAnalysis.forParameterlessVoid(context)
        );
    }

    public Optional<MethodAnalysis> getForMethod(CtExecutableReference<?> method) {
        return Optional.ofNullable(this.hardcodedMethods.get(FlowContext.buildQualifiedExecutableName(method)));
    }

}
