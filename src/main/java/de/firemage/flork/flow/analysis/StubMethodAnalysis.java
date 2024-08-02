package de.firemage.flork.flow.analysis;

import de.firemage.flork.flow.CachedMethod;
import de.firemage.flork.flow.FlowContext;
import de.firemage.flork.flow.exit.MethodExitState;
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
    private final List<String> parameters;

    public StubMethodAnalysis(CachedMethod method, List<String> parameterNames,
                              List<TypeId> parameterTypes,
                              FlowContext context) {
        this.method = method;
        this.parameters = new ArrayList<>(parameterNames);

        var returnType = TypeId.ofFallible(method.getExecutable().getType());
        if (returnType.isEmpty() || returnType.get().isVoid()) {
            this.returnStates = List.of(MethodExitState.forReturn(VoidValue.getInstance(),
                    createGenericParameterConditions(parameterTypes, context)));
        } else {
            this.returnStates = List.of(MethodExitState.forReturn(ValueSet.topForType(returnType.get(), context),
                    createGenericParameterConditions(parameterTypes, context)));
        }
    }

    public static StubMethodAnalysis forReferencedExecutable(CachedMethod method, FlowContext context) {
        var executable = method.getExecutable();

        var paramNames = new ArrayList<String>();
        var paramTypes = new ArrayList<TypeId>();
        var parameters = executable.getParameters();

        if (!executable.isStatic()) {
            paramNames.add("this");
            paramTypes.add(new TypeId(executable.getDeclaringType()));
        }

        for (int i = 0; i < parameters.size(); i++) {
            paramNames.add("p" + i);
            paramTypes.add(new TypeId(parameters.get(i)));
        }
        return new StubMethodAnalysis(method, paramNames, paramTypes, context);
    }

    public static List<ValueSet> createGenericParameterConditions(List<TypeId> parameters, FlowContext context) {
        return parameters.stream().map(t -> ValueSet.topForType(t, context)).toList();
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
}
