package de.firemage.flork.flow;

import de.firemage.flork.flow.engine.VarState;
import de.firemage.flork.flow.value.ValueSet;

import java.util.Map;

public record MethodExitState(ValueSet value, Map<String, VarState> parameters) {

}
