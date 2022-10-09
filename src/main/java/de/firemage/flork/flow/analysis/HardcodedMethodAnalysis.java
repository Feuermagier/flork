package de.firemage.flork.flow.analysis;

import de.firemage.flork.flow.CachedMethod;
import de.firemage.flork.flow.FlowContext;
import de.firemage.flork.flow.MethodExitState;
import de.firemage.flork.flow.TypeId;
import de.firemage.flork.flow.value.ValueSet;
import de.firemage.flork.flow.value.VoidValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HardcodedMethodAnalysis implements MethodAnalysis {
    private final List<MethodExitState> returnStates;
    private final List<String> parameters;

    // Set after initialization to avoid manually constructing a CtExecutableReference
    private CachedMethod method;

    public HardcodedMethodAnalysis(TypeId returnType, List<String> parameterNames,
                                   Map<String, TypeId> parameterTypes,
                                   FlowContext context) {
        this.parameters = new ArrayList<>(parameterNames);
        if (returnType == null || returnType.isVoid()) {
            this.returnStates = List.of(new MethodExitState(VoidValue.getInstance(),
                    StubMethodAnalysis.createGenericParameterConditions(parameterTypes, context)));
        } else {
            this.returnStates = List.of(new MethodExitState(ValueSet.topForType(returnType, context),
                    StubMethodAnalysis.createGenericParameterConditions(parameterTypes, context)));
        }
    }

    public static HardcodedMethodAnalysis forParameterlessVoid(FlowContext context) {
        return new HardcodedMethodAnalysis(null, List.of(), Map.of(), context);
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
    public List<String> getOrderedParameterNames() {
        return this.parameters;
    }
}
