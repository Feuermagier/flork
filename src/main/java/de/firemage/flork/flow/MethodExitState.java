package de.firemage.flork.flow;

import de.firemage.flork.flow.engine.VarId;
import de.firemage.flork.flow.engine.VarState;
import de.firemage.flork.flow.value.ValueSet;

import java.util.Map;

/**
 * @param initialState parameters + fields
 */
public record MethodExitState(ValueSet value, Map<VarId, VarState> initialState) {

}
