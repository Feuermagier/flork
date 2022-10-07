package de.firemage.flork.flow.analysis;

import de.firemage.flork.flow.FlowContext;
import de.firemage.flork.flow.MethodExitState;
import de.firemage.flork.flow.engine.VarState;
import de.firemage.flork.flow.value.ValueSet;
import de.firemage.flork.flow.value.VoidValue;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class StubMethodAnalysis implements MethodAnalysis {
    private final List<MethodExitState> returnStates;
    private final String name;
    private final List<String> parameters;

    public StubMethodAnalysis(String name, CtTypeReference<?> returnType, List<String> parameterNames,
                              Map<String, CtTypeReference<?>> parameterTypes,
                              FlowContext context) {
        this.name = name;
        this.parameters = new ArrayList<>(parameterNames);
        if (returnType == null || returnType.getSimpleName().equals("void")) {
            this.returnStates = List.of(new MethodExitState(VoidValue.getInstance(),
                createGenericParameterConditions(parameterTypes, context)));
        } else {
            this.returnStates = List.of(new MethodExitState(ValueSet.topForType(returnType, context),
                createGenericParameterConditions(parameterTypes, context)));
        }
    }

    public static StubMethodAnalysis forParameterlessVoid(String name, FlowContext context) {
        return new StubMethodAnalysis(name, null, List.of(), Map.of(), context);
    }

    public static StubMethodAnalysis forReferencedExecutable(CtExecutableReference<?> method, FlowContext context) {
        var paramNames = new ArrayList<String>();
        var paramTypes = new HashMap<String, CtTypeReference<?>>();
        for (int i = 0; i < method.getParameters().size(); i++) {
            paramTypes.put("p" + i, method.getParameters().get(i));
            paramNames.add("p" + i);
        }
        return new StubMethodAnalysis(method.getSimpleName(), method.getType(), paramNames, paramTypes, context);
    }

    private static Map<String, VarState> createGenericParameterConditions(Map<String, CtTypeReference<?>> parameters,
                                                                          FlowContext context) {
        return parameters.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey,
                e -> new VarState(ValueSet.topForType(e.getValue(), context), Set.of())));
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
}
