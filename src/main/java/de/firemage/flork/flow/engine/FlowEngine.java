package de.firemage.flork.flow.engine;

import de.firemage.flork.flow.CachedMethod;
import de.firemage.flork.flow.FlowContext;
import de.firemage.flork.flow.TypeId;
import de.firemage.flork.flow.exit.MethodExitState;
import de.firemage.flork.flow.value.ObjectValueSet;
import de.firemage.flork.flow.value.ValueSet;
import spoon.reflect.declaration.CtParameter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class FlowEngine {
    private final FlowContext context;
    private List<EngineState> states;
    private List<EngineState> exceptionalStates;

    public FlowEngine(TypeId thisType, ObjectValueSet thisPointer, List<CtParameter<?>> parameters, FlowContext context) {
        this.context = context;
        this.states = new ArrayList<>();
        this.exceptionalStates = new ArrayList<>();
        this.states.add(new EngineState(thisType, thisPointer, parameters, context));
    }

    private FlowEngine(List<EngineState> states, FlowContext context) {
        this.context = context;
        this.states = states;
        this.exceptionalStates = new ArrayList<>();
    }

    public FlowEngine fork(ValueSet expectedTos) {
        return new FlowEngine(this.states.stream()
                .map(EngineState::fork)
                .filter(state -> state.assertTos(expectedTos))
                .collect(Collectors.toCollection(ArrayList::new)), this.context);
    }

    public FlowEngine extractExceptionalStatesForHandler(TypeId type) {
        var handledStates = new ArrayList<EngineState>();
        this.exceptionalStates.removeIf(state -> {
            if (state.hasActiveException() && state.activeException.isSubtypeOf(type)) {
                state.clearActiveException();
                handledStates.add(state);
                return true;
            }
            return false;
        });
        return new FlowEngine(handledStates, this.context);
    }

    public List<EngineState> getAndClearExceptionalStates() {
        var result = this.exceptionalStates;
        this.exceptionalStates = new ArrayList<>();
        return result;
    }

    public void addExceptionalStates(Collection<EngineState> states) {
        this.exceptionalStates.addAll(states);
    }

    public FlowEngine cloneEngine() {
        return new FlowEngine(this.states.stream()
                .map(EngineState::fork)
                .collect(Collectors.toCollection(ArrayList::new)), this.context);
    }

    public void clear() {
        this.states.clear();
        this.exceptionalStates.clear();
    }

    public boolean isEmpty() {
        return this.states.isEmpty() && this.exceptionalStates.isEmpty();
    }

    public void beginWritesScope() {
        for (EngineState state : this.states) {
            state.beginWritesScope();
        }
        this.log("beginWritesScope");
    }

    public void endWritesScope() {
        for (EngineState state : this.states) {
            state.endWritesScope();
        }
        this.log("endWritesScope");
    }

    /**
     * This does not fork!!! Beware of unwanted cross-references
     *
     * @param expectedTos
     */
    public void assertTos(ValueSet expectedTos) {
        this.states.removeIf(state -> !state.assertTos(expectedTos));
        this.log("assertTos " + expectedTos);
    }

    public void join(FlowEngine other) {
        this.states.addAll(other.states);
        this.exceptionalStates.addAll(other.exceptionalStates);
    }

    public boolean isStackEmpty() {
        return this.states.stream().allMatch(EngineState::isStackEmpty);
    }

    public boolean isImpossibleState() {
        return this.states.isEmpty();
    }

    public void clearStack() {
        for (EngineState state : this.states) {
            state.clearStack();
        }
        this.log("clearStack");
    }

    public ValueSet peek() {
        return this.states.stream().map(EngineState::peek).reduce(ValueSet::merge).orElse(null);
    }

    public ValueSet peekOrVoid() {
        return this.states.stream().map(EngineState::peekOrVoid).reduce(ValueSet::merge).orElse(null);
    }

    public List<ValueSet> peekAll() {
        return this.states.stream().map(EngineState::peek).toList();
    }

    public List<EngineState> getCurrentStates() {
        return this.states;
    }

    public void createLocal(String name, TypeId type) {
        this.forEachState(state -> state.createVariable(name, type));
        this.log("createLocal");
    }

    public void resetWrittenLocalsAndFields() {
        // A simple loop should be enough since no state should become exceptional here, but whatever
        this.forEachState(EngineState::resetWrittenLocalsAndFields);
        this.log("resetWrittenFields");
    }

    public void pushValue(ValueSet value) {
        this.forEachState(state -> state.pushValue(value));
        this.log("push " + value);
    }

    public void pushThis() {
        this.forEachState(EngineState::pushThis);
        this.log("pushThis");
    }

    public void pushLocal(String name) {
        this.forEachState(state -> state.pushVar(name));
        this.log("pushLocal");
    }

    public void pushField(String name) {
        this.forEachState(state -> state.pushField(name));
        this.log("pushField");
    }

    public void storeLocal(String name) {
        this.forEachState(state -> state.storeVar(name));
        this.log("storeLocal");
    }

    public void storeField(String name) {
        this.forEachState(state -> state.storeField(name));
        this.log("storeField");
    }

    public void pop() {
        this.forEachState(EngineState::pop);
        this.log("pop");
    }

    public void negate() {
        this.forEachState(EngineState::negate);
        this.log("negate");
    }

    public void add() {
        this.forEachState(EngineState::add);
        this.log("add");
    }

    public void subtract() {
        this.forEachState(EngineState::subtract);
        this.log("subtract");
    }

    public void multiply() {
        this.forEachState(EngineState::multiply);
        this.log("multiply");
    }

    public void divide() {
        this.forEachState(EngineState::divide);
        this.log("divide");
    }


    public void not() {
        this.forEachState(EngineState::not);
        this.log("not");
    }

    public void and() {
        this.collectStates(EngineState::and);
        this.log("and");
    }

    public void or() {
        this.collectStates(EngineState::or);
        this.log("or");
    }

    public void compareOp(Relation relation) {
        this.collectStates(s -> s.compareOp(relation));
        this.log(relation.toString());
    }

    public void callVirtual(CachedMethod method) {
        this.collectStates(s -> s.callVirtual(method));
        this.log("callVirtual " + method.getName());
    }

    public void callStatic(CachedMethod method) {
        this.collectStates(s -> s.callStatic(method));
        this.log("callStatic " + method.getName());
    }

    public void callConstructor(CachedMethod method) {
        this.collectStates(s -> s.callConstructor(method));
        this.log("callConstructor " + method.getName());
    }

    public void box() {
        this.forEachState(EngineState::box);
        this.log("box");
    }

    public void unbox() {
        this.forEachState(EngineState::unbox);
        this.log("unbox");
    }

    public void cast(TypeId newType) {
        this.forEachState(s -> s.cast(newType));
        this.log("cast " + newType.getName());
    }

    public void throwException() {
        this.forEachState(EngineState::throwException);
    }

    @Override
    public String toString() {
        return "States (" + this.states.size() + "): " + this.states;
    }

    private void forEachState(Consumer<EngineState> fn) {
        this.states.removeIf(state -> {
            fn.accept(state);
            if (state.hasActiveException()) {
                this.exceptionalStates.add(state);
                return true;
            }
            return false;
        });
    }

    private void collectStates(Function<EngineState, List<EngineState>> fn) {
        List<EngineState> newStates = new ArrayList<>(this.states.size());
        for (var state : this.states) {
            var newStatesForState = fn.apply(state);
            for (var newState : newStatesForState) {
                if (newState.hasActiveException()) {
                    this.exceptionalStates.add(newState);
                } else {
                    newStates.add(newState);
                }
            }
        }
        this.states = newStates;
    }

    private void log(String instruction) {
        this.context.log(instruction + ", " + this);
    }
}
