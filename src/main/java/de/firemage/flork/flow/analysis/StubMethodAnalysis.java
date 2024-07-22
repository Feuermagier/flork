package de.firemage.flork.flow.analysis;

import de.firemage.flork.flow.CachedMethod;
import de.firemage.flork.flow.FlowContext;
import de.firemage.flork.flow.MethodExitState;
import de.firemage.flork.flow.TypeId;
import de.firemage.flork.flow.engine.VarId;
import de.firemage.flork.flow.engine.VarState;
import de.firemage.flork.flow.value.ValueSet;
import de.firemage.flork.flow.value.VoidValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class StubMethodAnalysis implements MethodAnalysis {
    private final CachedMethod method;
    private final List<MethodExitState> returnStates;
    private final List<VarId> parameters;

    public StubMethodAnalysis(CachedMethod method, List<VarId> parameterNames,
                              Map<VarId, TypeId> parameterTypes,
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

    public static StubMethodAnalysis forReferencedExecutable(CachedMethod method, FlowContext context) {
        var executable = method.getExecutable();

        var paramNames = new ArrayList<VarId>();
        var paramTypes = new HashMap<VarId, TypeId>();
        var parameters = executable.getParameters();

        if (!executable.isStatic()) {
            paramNames.add(VarId.forLocal("this"));
            paramTypes.put(VarId.THIS, new TypeId(executable.getDeclaringType()));
        }

        for (int i = 0; i < parameters.size(); i++) {
            VarId param = VarId.forLocal("p" + i);
            paramTypes.put(param, new TypeId(parameters.get(i)));
            paramNames.add(param);
        }
        return new StubMethodAnalysis(method, paramNames, paramTypes, context);
    }

    public static Map<VarId, VarState> createGenericParameterConditions(Map<VarId, TypeId> parameters,
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
    public List<VarId> getOrderedParameterNames() {
        return this.parameters;
    }

    @Override
    public CachedMethod getMethod() {
        return this.method;
    }
}
