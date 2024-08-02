package de.firemage.flork.flow.exit;

import de.firemage.flork.flow.engine.VarState;
import de.firemage.flork.flow.value.ValueSet;

import java.util.List;
import java.util.Map;

public record Precondition(List<ValueSet> parameters, Map<String, ValueSet> ownFields) {
}
