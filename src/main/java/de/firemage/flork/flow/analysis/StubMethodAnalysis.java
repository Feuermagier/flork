package de.firemage.flork.flow.analysis;

import de.firemage.flork.flow.CachedMethod;
import de.firemage.flork.flow.FlowContext;
import de.firemage.flork.flow.MethodExitState;
import de.firemage.flork.flow.TypeId;
import de.firemage.flork.flow.engine.VarState;
import de.firemage.flork.flow.value.ValueSet;
import de.firemage.flork.flow.value.VoidValue;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.reflect.reference.CtTypeReferenceImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class StubMethodAnalysis implements MethodAnalysis {
    private final CachedMethod method;
    private final List<MethodExitState> returnStates;
    private final List<String> parameters;

    public static StubMethodAnalysis forReferencedExecutable(CachedMethod method, FlowContext context) {
        var paramNames = new ArrayList<String>();
        var paramTypes = new HashMap<String, TypeId>();
        var parameters = method.getExecutable().getParameters();
        for (int i = 0; i < parameters.size(); i++) {
            paramTypes.put("p" + i, new TypeId(parameters.get(i)));
            paramNames.add("p" + i);
        }
        return new StubMethodAnalysis(method, paramNames, paramTypes, context);
    }

    public StubMethodAnalysis(CachedMethod method, List<String> parameterNames,
                              Map<String, TypeId> parameterTypes,
                              FlowContext context) {
        this.method = method;
        this.parameters = new ArrayList<>(parameterNames);

        var returnType = TypeId.ofFallible(method.getExecutable().getType());
        if (returnType.isEmpty() || returnType.get().isVoid()) {
            this.returnStates = List.of(new MethodExitState(VoidValue.getInstance(),
                createGenericParameterConditions(parameterTypes, context)));
        } else {
            this.returnStates = List.of(new MethodExitState(ValueSet.topForType(returnType.get(), context),
                createGenericParameterConditions(parameterTypes, context)));
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
    public CachedMethod getMethod() {
        return this.method;
    }

    public static Map<String, VarState> createGenericParameterConditions(Map<String, TypeId> parameters,
                                                                         FlowContext context) {
        return parameters.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> new VarState(ValueSet.topForType(e.getValue(), context), Set.of())));
    }
}
