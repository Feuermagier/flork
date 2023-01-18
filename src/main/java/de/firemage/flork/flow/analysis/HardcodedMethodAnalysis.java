package de.firemage.flork.flow.analysis;

import de.firemage.flork.flow.CachedMethod;
import de.firemage.flork.flow.FlowContext;
import de.firemage.flork.flow.MethodExitState;
import de.firemage.flork.flow.TypeId;
import de.firemage.flork.flow.engine.VarId;
import de.firemage.flork.flow.value.ValueSet;
import de.firemage.flork.flow.value.VoidValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class HardcodedMethodAnalysis implements MethodAnalysis {
    private final List<MethodExitState> returnStates;
    private final List<VarId> parameters;

    private CachedMethod method;

    public HardcodedMethodAnalysis(CachedMethod method, List<VarId> parameterNames, FlowContext context) {
        if (parameterNames.size() != method.getExecutable().getParameters().size()) {
            throw new IllegalArgumentException(
                "The number of parameter names does not match the number of parameters reported by the executable");
        }

        this.parameters = new ArrayList<>(parameterNames);
        this.method = method;

        var parameterTypes = new HashMap<VarId, TypeId>();
        for (int i = 0; i < parameterNames.size(); i++) {
            parameterTypes.put(parameterNames.get(i), new TypeId(method.getExecutable().getParameters().get(i)));
        }

        var returnType = new TypeId(method.getExecutable().getType());

        if (returnType.isVoid()) {
            this.returnStates = List.of(new MethodExitState(VoidValue.getInstance(),
                StubMethodAnalysis.createGenericParameterConditions(parameterTypes, context)));
        } else {
            this.returnStates = List.of(new MethodExitState(ValueSet.topForType(returnType, context),
                StubMethodAnalysis.createGenericParameterConditions(parameterTypes, context)));
        }
    }

    /* package-private */ void setCachedMethod(CachedMethod method) {
        if (this.method != null && !this.method.equals(method)) {
            throw new IllegalStateException("Cannot overwrite the associated cached method");
        }
        this.method = method;
    }

    @Override
    public CachedMethod getMethod() {
        if (this.method == null) {
            throw new IllegalStateException("method not yet set");
        }
        return this.method;
    }

    @Override
    public List<MethodExitState> getReturnStates() {
        return this.returnStates;
    }

    @Override
    public List<VarId> getOrderedParameterNames() {
        return this.parameters;
    }
}
