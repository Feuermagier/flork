package de.firemage.flork.flow.engine;

import de.firemage.flork.flow.MethodAnalysis;
import de.firemage.flork.flow.ValueSet;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.reference.CtTypeReference;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class FlowEngine {
    private Set<EngineState> states;

    public FlowEngine(List<CtParameter<?>> parameters) {
        Map<String, VarState> initialValues = new HashMap<>();
        Set<String> parameterNames = new HashSet<>();
        for (CtParameter<?> parameter : parameters) {
            initialValues.put(parameter.getSimpleName(), new VarState(ValueSet.topForType(parameter.getType()), Set.of()));
            parameterNames.add(parameter.getSimpleName());
        }
        this.states = new HashSet<>();
        this.states.add(new EngineState(initialValues, parameterNames));
    }

    private FlowEngine(Set<EngineState> states) {
        this.states = states;
    }

    public FlowEngine fork(ValueSet expectedTos) {
        return new FlowEngine(this.states.stream()
            .map(EngineState::fork)
            .filter(state -> state.assertTos(expectedTos))
            .collect(Collectors.toCollection(HashSet::new)));
    }
    
    public void clear() {
        this.states.clear();
    }

    /**
     * This does not fork!!! Beware of unwanted cross references
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

    public ValueSet peek() {
        return this.states.stream().map(EngineState::peek).reduce(ValueSet::merge).orElse(null);
    }

    public List<ValueSet> peekAll() {
        return this.states.stream().map(EngineState::peek).toList();
    }
    
    public Set<EngineState> getCurrentStates() {
        return this.states;
    }

    public void createLocal(String name, CtTypeReference<?> type) {
        for (EngineState state : this.states) {
            state.createLocal(name, type);
        }
        this.log("createLocal");
    }

    public void pushBooleanLiteral(boolean value) {
        for (EngineState state : this.states) {
            state.pushBooleanLiteral(value);
        }
        this.log("pushBooleanLiteral");
    }

    public void pushIntLiteral(int value) {
        for (EngineState state : this.states) {
            state.pushIntLiteral(value);
        }
        this.log("pushIntLiteral");
    }

    public void pushLocal(String name) {
        for (EngineState state : this.states) {
            state.pushLocal(name);
        }
        this.log("pushLocal");
    }

    public void storeLocal(String name) {
        for (EngineState state : this.states) {
            state.storeLocal(name);
        }
        this.log("storeLocal");
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
    
    public void call(MethodAnalysis method) {
        this.collectStates(s -> s.call(method));
        this.log("call " + method.getName());
    }
    
    private void log(String instruction) {
        System.out.println(instruction + ", " + this);
    }
    
    private void collectStates(Function<EngineState, List<EngineState>> fn) {
        this.states = this.states.stream().flatMap(s -> fn.apply(s).stream()).collect(Collectors.toSet());
    }

    @Override
    public String toString() {
        return "Current states (" + this.states.size() + "): " + this.states;
    }
}
