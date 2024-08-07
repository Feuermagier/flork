package de.firemage.flork.flow.analysis;

import de.firemage.flork.flow.CachedMethod;
import de.firemage.flork.flow.exit.MethodExitState;
import de.firemage.flork.flow.engine.VarId;

import java.util.List;

public interface MethodAnalysis {
    CachedMethod getMethod();

    List<MethodExitState> getReturnStates();

    List<String> getOrderedParameterNames();
}
