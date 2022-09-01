package de.firemage.flork.flow.hardcoded;

import de.firemage.flork.flow.FlowContext;
import de.firemage.flork.flow.MethodExitState;
import de.firemage.flork.flow.value.ValueSet;
import de.firemage.flork.flow.MethodAnalysis;
import de.firemage.flork.flow.value.VoidValue;
import de.firemage.flork.flow.engine.VarState;
import spoon.reflect.reference.CtTypeReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class StubMethodAnalysis implements MethodAnalysis {
    private final List<MethodExitState> returnStates;
    private final String name;
    private final List<String> parameters;

    public static StubMethodAnalysis forParameterlessVoid(String name, FlowContext context) {
        return new StubMethodAnalysis(name, null, Map.of(), context);
    }

    public StubMethodAnalysis(String name, CtTypeReference<?> returnType, Map<String, CtTypeReference<?>> parameters, FlowContext context) {
        this.name = name;
        this.parameters = new ArrayList<>(parameters.keySet());
        if (returnType == null || returnType.getSimpleName().equals("void")) {
            this.returnStates = List.of(new MethodExitState(VoidValue.getInstance(),
                    createGenericParameterConditions(parameters, context)));
        } else {
            this.returnStates = List.of(new MethodExitState(ValueSet.topForType(returnType, context),
                    createGenericParameterConditions(parameters, context)));
        }
    }

    @Override
    public List<MethodExitState> getReturnStates() {
        return this.returnStates;
    }

    @Override
    public List<String> getOrderedParameterNames() {
        return this.parameters;
    }

    @Override
    public String getName() {
        return this.name;
    }

    private Map<String, VarState> createGenericParameterConditions(Map<String, CtTypeReference<?>> parameters, FlowContext context) {
        return parameters.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> new VarState(ValueSet.topForType(e.getValue(), context), Set.of())));
    }
}
