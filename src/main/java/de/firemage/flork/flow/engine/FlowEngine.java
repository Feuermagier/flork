package de.firemage.flork.flow.engine;

import de.firemage.flork.flow.CachedMethod;
import de.firemage.flork.flow.FlowContext;
import de.firemage.flork.flow.TypeId;
import de.firemage.flork.flow.value.ObjectValueSet;
import de.firemage.flork.flow.value.ValueSet;
import spoon.reflect.declaration.CtParameter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class FlowEngine {
    private final FlowContext context;
    private Set<EngineState> states;

    public FlowEngine(ObjectValueSet thisPointer, List<CtParameter<?>> parameters, FlowContext context) {
        this.context = context;
        this.states = new HashSet<>();
        this.states.add(new EngineState(thisPointer, parameters, context));
    }

    private FlowEngine(Set<EngineState> states, FlowContext context) {
        this.context = context;
        this.states = states;
    }

    public FlowEngine fork(ValueSet expectedTos) {
        return new FlowEngine(this.states.stream()
            .map(EngineState::fork)
            .filter(state -> state.assertTos(expectedTos))
            .collect(Collectors.toCollection(HashSet::new)), this.context);
    }

    public void clear() {
        this.states.clear();
    }

    /**
     * This does not fork!!! Beware of unwanted cross references
     *
     * @param expectedTos
     */
    public void assertTos(ValueSet expectedTos) {
        this.states.removeIf(state -> !state.assertTos(expectedTos));
    }

    public void join(FlowEngine other) {
        this.states.addAll(other.states);
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

    public Set<EngineState> getCurrentStates() {
        return this.states;
    }

    public void createLocal(String name, TypeId type) {
        for (EngineState state : this.states) {
            state.createVariable(name, type);
        }
        this.log("createLocal");
    }

    public void pushValue(ValueSet value) {
        for (EngineState state : this.states) {
            state.pushValue(value);
        }
        this.log("push " + value);
    }

    public void pushThis() {
        for (EngineState state : this.states) {
            state.pushThis();
        }
        this.log("pushThis");
    }

    public void pushLocal(String name) {
        for (EngineState state : this.states) {
            state.pushVar(name);
        }
        this.log("pushLocal");
    }

    public void pushField(String name) {
        for (EngineState state : this.states) {
            state.pushField(name);
        }
        this.log("pushField");
    }

    public void storeLocal(String name) {
        for (EngineState state : this.states) {
            state.storeVar(name);
        }
        this.log("storeLocal");
    }

    public void storeField(String name) {
        for (EngineState state : this.states) {
            state.storeField(name);
        }
        this.log("storeField");
    }

    public void pop() {
        for (EngineState state : this.states) {
            state.pop();
        }
        this.log("pop");
    }

    public void negate() {
        for (EngineState state : this.states) {
            state.negate();
        }
        this.log("negate");
    }

    public void add() {
        for (EngineState state : this.states) {
            state.add();
        }
        this.log("add");
    }

    public void subtract() {
        for (EngineState state : this.states) {
            state.subtract();
        }
        this.log("subtract");
    }

    public void multiply() {
        for (EngineState state : this.states) {
            state.multiply();
        }
        this.log("multiply");
    }

    public void divide() {
        for (EngineState state : this.states) {
            state.divide();
        }
        this.log("divide");
    }


    public void not() {
        for (EngineState state : this.states) {
            state.not();
        }
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

    @Override
    public String toString() {
        return "Current states (" + this.states.size() + "): " + this.states;
    }

    private void collectStates(Function<EngineState, List<EngineState>> fn) {
        this.states = this.states.stream().flatMap(s -> fn.apply(s).stream()).collect(Collectors.toSet());
    }

    private void log(String instruction) {
        this.context.log(instruction + ", " + this);
    }
}
