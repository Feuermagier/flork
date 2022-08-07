package de.firemage.flork.flow;

import de.firemage.flork.flow.engine.VarState;

import java.util.Map;

public record MethodExitState(ValueSet value, Map<String, VarState> parameters) {
}
