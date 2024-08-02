package de.firemage.flork.flow.exit;

import de.firemage.flork.flow.TypeId;
import de.firemage.flork.flow.value.ValueSet;

import java.util.List;

public class MethodExitState {
    private final List<ValueSet> parameterPreconditions;
    private final ValueSet returnValue;
    private final TypeId thrownException;

    public static MethodExitState forReturn(ValueSet value, List<ValueSet> parameterPreconditions) {
        return new MethodExitState(value, null, parameterPreconditions);
    }

    public static MethodExitState forThrow(TypeId exception, List<ValueSet> parameterPreconditions) {
        return new MethodExitState(null, exception, parameterPreconditions);
    }

    private MethodExitState(ValueSet returnValue, TypeId thrownException, List<ValueSet> parameterPreconditions) {
        this.parameterPreconditions = parameterPreconditions;
        this.returnValue = returnValue;
        this.thrownException = thrownException;
    }

    public List<ValueSet> getParameterPrecondition() {
        return parameterPreconditions;
    }

    public ValueSet getReturnValue() {
        return returnValue;
    }

    public TypeId getThrownException() {
        return thrownException;
    }
}
