package de.firemage.flork.flow.analysis;

import de.firemage.flork.flow.MethodExitState;

import java.util.List;

public interface MethodAnalysis {
    List<MethodExitState> getReturnStates();

    List<String> getOrderedParameterNames();

    String getName();
}
