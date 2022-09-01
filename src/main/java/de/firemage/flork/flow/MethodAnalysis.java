package de.firemage.flork.flow;

import java.util.List;

public interface MethodAnalysis {
    List<MethodExitState> getReturnStates();

    List<String> getOrderedParameterNames();

    String getName();
}
