package de.firemage.flork.flow.analysis;

import de.firemage.flork.flow.CachedMethod;
import de.firemage.flork.flow.MethodExitState;
import spoon.reflect.reference.CtTypeReference;

import java.util.List;

public interface MethodAnalysis {
    CachedMethod getMethod();

    List<MethodExitState> getReturnStates();

    List<String> getOrderedParameterNames();
}
