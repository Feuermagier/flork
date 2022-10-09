package de.firemage.flork.flow.engine;

import de.firemage.flork.flow.BooleanStatus;
import de.firemage.flork.flow.CachedMethod;
import de.firemage.flork.flow.FlowContext;
import de.firemage.flork.flow.MethodExitState;
import de.firemage.flork.flow.TypeId;
import de.firemage.flork.flow.analysis.MethodAnalysis;
import de.firemage.flork.flow.value.BooleanValueSet;
import de.firemage.flork.flow.value.IntValueSet;
import de.firemage.flork.flow.value.ObjectValueSet;
import de.firemage.flork.flow.value.ValueSet;
import de.firemage.flork.flow.value.VoidValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class EngineState {
    private final FlowContext context;

    // The current state of all locals, including a local named "this" for the this-pointer
    private final Map<String, VarState> localsState;

    // The current state of the stack
    private final ValueStack stack;

    // The value of parameters before they are overwritten; to be used in MethodExitState
    private final Map<String, VarState> savedInitialParameterStates;

    // List of locals that are also parameters
    private final Set<String> parameters;

    public EngineState(Map<String, VarState> localsState, Set<String> parameters, FlowContext context) {
        this.context = context;
        this.localsState = localsState;
        this.parameters = parameters;
        this.savedInitialParameterStates = new HashMap<>(parameters.size());
        this.stack = new ValueStack();
    }

    public EngineState(EngineState other) {
        this.context = other.context;
        this.localsState = new HashMap<>(other.localsState);
        this.parameters = other.parameters;
        this.savedInitialParameterStates = new HashMap<>(other.savedInitialParameterStates);
        this.stack = new ValueStack(other.stack);
    }

    public EngineState fork() {
        return new EngineState(this);
    }

    public Map<String, VarState> getParamStates() {
        // Remove relations with non-parameter locals
        Map<String, VarState> paramStates = new HashMap<>(this.savedInitialParameterStates.size());
        for (String param : this.parameters) {
            VarState value;
            if (this.savedInitialParameterStates.containsKey(param)) {
                value = this.savedInitialParameterStates.get(param);
            } else {
                value = this.localsState.get(param);
            }
            paramStates.put(param, new VarState(value.value(), value
                .relations()
                .stream()
                .filter(r -> this.parameters.contains(r.rhs()))
                .collect(Collectors.toSet())));
        }
        return paramStates;
    }

    public void createLocal(String name, TypeId type) {
        this.localsState.put(name, new VarState(ValueSet.topForType(type, this.context), Set.of()));
    }

    public void pushValue(ValueSet value) {
        this.stack.push(new ConcreteStackValue(value));
    }

    public void pushThis() {
        this.pushLocal("this");
    }

    public void pushLocal(String name) {
        this.stack.push(new LocalRefStackValue(name));
    }

    public void storeLocal(String name) {
        if (this.parameters.contains(name) && !this.savedInitialParameterStates.containsKey(name)) {
            this.savedInitialParameterStates.put(name, this.localsState.get(name));
        }
        this.localsState.put(name, new VarState(getValueOf(this.stack.peek()), Set.of()));
        if (this.stack.peek() instanceof LocalRefStackValue ref) {
            this.addRelationAndExtendTransitive(name, new VarRelation(ref.local(), Relation.EQUAL));
        }
    }

    public void pop() {
        this.stack.pop();
    }

    public ValueSet peek() {
        return getValueOf(this.stack.getFirst());
    }

    public ValueSet peekOrVoid() {
        if (this.stack.isEmpty()) {
            return VoidValue.getInstance();
        } else {
            return this.peek();
        }
    }

    public boolean isStackEmpty() {
        return this.stack.isEmpty();
    }

    public void clearStack() {
        this.stack.clear();
    }

    public void negate() {
        StackValue tos = this.stack.pop();
        IntValueSet tosValue = (IntValueSet) getValueOf(tos);
        this.stack.push(new ConcreteStackValue(tosValue.negate()));
    }

    public void add() {
        StackValue rhs = this.stack.pop();
        StackValue lhs = this.stack.pop();
        IntValueSet rhsValue = (IntValueSet) getValueOf(rhs);
        IntValueSet lhsValue = (IntValueSet) getValueOf(lhs);
        if (rhsValue.isSingle(0)) {
            this.stack.push(lhs);
        } else if (lhsValue.isSingle(0)) {
            this.stack.push(rhs);
        } else {
            this.stack.push(new ConcreteStackValue(lhsValue.add(rhsValue)));
        }
    }

    public void subtract() {
        StackValue rhs = this.stack.pop();
        StackValue lhs = this.stack.pop();
        IntValueSet rhsValue = (IntValueSet) getValueOf(rhs);
        IntValueSet lhsValue = (IntValueSet) getValueOf(lhs);
        if (rhsValue.isSingle(0)) {
            this.stack.push(lhs);
        } else if (lhsValue.isSingle(0)) {
            this.stack.push(rhs);
        } else {
            this.stack.push(new ConcreteStackValue(lhsValue.subtract(rhsValue)));
        }
    }

    public void multiply() {
        StackValue rhs = this.stack.pop();
        StackValue lhs = this.stack.pop();
        IntValueSet rhsValue = (IntValueSet) getValueOf(rhs);
        IntValueSet lhsValue = (IntValueSet) getValueOf(lhs);
        if (rhsValue.isSingle(1)) {
            this.stack.push(lhs);
        } else if (lhsValue.isSingle(1)) {
            this.stack.push(rhs);
        } else {
            this.stack.push(new ConcreteStackValue(lhsValue.multiply(rhsValue)));
        }
    }

    public void divide() {
        StackValue rhs = this.stack.pop();
        StackValue lhs = this.stack.pop();
        IntValueSet rhsValue = (IntValueSet) getValueOf(rhs);
        IntValueSet lhsValue = (IntValueSet) getValueOf(lhs);
        if (rhsValue.isSingle(1)) {
            this.stack.push(lhs);
        } else if (lhsValue.isSingle(1)) {
            this.stack.push(rhs);
        } else {
            this.stack.push(new ConcreteStackValue(lhsValue.divide(rhsValue)));
        }
    }

    public void not() {
        StackValue tos = this.stack.pop();
        BooleanValueSet tosValue = (BooleanValueSet) getValueOf(tos);
        this.stack.push(new ConcreteStackValue(tosValue.not()));
    }

    // This is not short circuit!
    public List<EngineState> and() {
        List<EngineState> additionalPaths = new ArrayList<>();
        additionalPaths.add(this);

        StackValue rhs = this.stack.pop();
        StackValue lhs = this.stack.pop();

        BooleanValueSet lhsValue = (BooleanValueSet) getValueOf(lhs);
        BooleanValueSet rhsValue = (BooleanValueSet) getValueOf(rhs);
        if (lhsValue.isFalse()) {
            this.stack.push(new ConcreteStackValue(BooleanValueSet.of(false)));
        } else if (lhsValue.isBottom()) {
            this.stack.push(new ConcreteStackValue(BooleanValueSet.bottom()));
        } else {
            if (lhsValue.isTop()) {
                // Create a second path for lhs == false
                EngineState secondPath = this.fork();
                secondPath.assertStackValue(lhs, BooleanValueSet.of(false));
                secondPath.stack.push(new ConcreteStackValue(BooleanValueSet.of(false)));
                additionalPaths.add(secondPath);
            }
            this.assertStackValue(lhs, BooleanValueSet.of(true));

            if (rhsValue.isTop()) {
                // Create a second path for rhs == false
                EngineState secondPath = this.fork();
                secondPath.assertStackValue(rhs, BooleanValueSet.of(false));
                secondPath.stack.push(new ConcreteStackValue(BooleanValueSet.of(false)));
                additionalPaths.add(secondPath);

                // This path is for rhs == true
                this.assertStackValue(rhs, BooleanValueSet.of(true));
                this.stack.push(new ConcreteStackValue(BooleanValueSet.of(true)));
            } else {
                this.stack.push(rhs);
            }
        }

        return additionalPaths;
    }

    // This is not short circuit!
    public List<EngineState> or() {
        List<EngineState> additionalPaths = new ArrayList<>();
        additionalPaths.add(this);

        StackValue rhs = this.stack.pop();
        StackValue lhs = this.stack.pop();

        BooleanValueSet lhsValue = (BooleanValueSet) getValueOf(lhs);
        BooleanValueSet rhsValue = (BooleanValueSet) getValueOf(rhs);
        if (lhsValue.isTrue()) {
            this.stack.push(new ConcreteStackValue(BooleanValueSet.of(true)));
        } else if (lhsValue.isBottom()) {
            this.stack.push(new ConcreteStackValue(BooleanValueSet.bottom()));
        } else {

            if (lhsValue.isTop()) {
                // Create a second path for lhs == true
                EngineState secondPath = this.fork();
                secondPath.assertStackValue(lhs, BooleanValueSet.of(true));
                secondPath.stack.push(new ConcreteStackValue(BooleanValueSet.of(true)));
                additionalPaths.add(secondPath);
            }
            this.assertStackValue(lhs, BooleanValueSet.of(false));

            if (rhsValue.isTop()) {
                // Create a second path for rhs == true
                EngineState secondPath = this.fork();
                secondPath.assertStackValue(rhs, BooleanValueSet.of(true));
                secondPath.stack.push(new ConcreteStackValue(BooleanValueSet.of(true)));
                additionalPaths.add(secondPath);

                // This path is for rhs == false
                this.assertStackValue(rhs, BooleanValueSet.of(false));
                this.stack.push(new ConcreteStackValue(BooleanValueSet.of(false)));
            } else {
                this.stack.push(rhs);
            }
        }

        return additionalPaths;
    }

    public List<EngineState> compareOp(Relation relation) {
        StackValue rhs = this.stack.pop();
        StackValue lhs = this.stack.pop();

        // Query known relations first
        if (lhs instanceof LocalRefStackValue lhsRef && rhs instanceof LocalRefStackValue rhsRef) {
            VarState lhsState = this.localsState.get(lhsRef.local());
            switch (checkRelation(lhsRef.local(), lhsState, rhsRef.local(), relation)) {
                case ALWAYS -> {
                    this.stack.push(new ConcreteStackValue(BooleanValueSet.of(true)));
                    return List.of(this);
                }
                case NEVER -> {
                    this.stack.push(new ConcreteStackValue(BooleanValueSet.of(false)));
                    return List.of(this);
                }
                case SOMETIMES -> {
                }
            }
        }

        ValueSet lhsValue = getValueOf(lhs);
        ValueSet rhsValue = getValueOf(rhs);

        return switch (lhsValue.fulfillsRelation(rhsValue, relation)) {
            case ALWAYS -> {
                this.stack.push(new ConcreteStackValue(BooleanValueSet.of(true)));
                this.tryAssertRelation(lhs, rhs, relation);
                yield List.of(this);
            }
            case NEVER -> {
                this.stack.push(new ConcreteStackValue(BooleanValueSet.of(false)));
                this.tryAssertRelation(lhs, rhs, relation.negate());
                yield List.of(this);
            }
            case SOMETIMES -> {
                // "this" is the true path, other is the false path
                EngineState notFulfilledState = this.fork();
                notFulfilledState.stack.push(new ConcreteStackValue(BooleanValueSet.of(false)));
                notFulfilledState.assertStackValue(lhs,
                    lhsValue.removeNotFulfillingValues(rhsValue, relation.negate()));
                notFulfilledState.assertStackValue(rhs,
                    rhsValue.removeNotFulfillingValues(lhsValue, relation.negate().invert()));
                notFulfilledState.tryAssertRelation(lhs, rhs, relation.negate());

                this.stack.push(new ConcreteStackValue(BooleanValueSet.of(true)));
                this.assertStackValue(lhs, lhsValue.removeNotFulfillingValues(rhsValue, relation));
                this.assertStackValue(rhs, rhsValue.removeNotFulfillingValues(lhsValue, relation.invert()));
                this.tryAssertRelation(lhs, rhs, relation);
                yield List.of(this, notFulfilledState);
            }
        };
    }

    public List<EngineState> callStatic(CachedMethod method) {
        return this.call(method.getFixedCallAnalysis());
    }

    public List<EngineState> callVirtual(CachedMethod method) {
        if (((ObjectValueSet) this.peek()).isExact()) {
            return this.call(method.getFixedCallAnalysis());
        } else {
            return method.getVirtualCallAnalyses().stream()
                    .flatMap(m -> this.fork()
                            //.assertTos(new ObjectValueSet(Nullness.NON_NULL, m.getThisType()))
                            .call(m)
                            .stream()
                    )
                    .toList();
        }
    }

    public List<EngineState> callConstructor(CachedMethod method) {
        return this.call(method.getFixedCallAnalysis());
    }

    private List<EngineState> call(MethodAnalysis method) {
        if (method.getReturnStates() == null) {
            return List.of(this);
        }

        List<EngineState> result = new ArrayList<>();

        exit:
        for (MethodExitState returnState : method.getReturnStates()) {
            EngineState state = new EngineState(this);
            Map<String, StackValue> paramToArgument = new HashMap<>(method.getOrderedParameterNames().size());

            // Extract the parameters
            for (String param : method.getOrderedParameterNames()) {
                StackValue tos = state.stack.pop();
                paramToArgument.put(param, tos);
            }

            // Check all parameters and add relations
            for (String param : method.getOrderedParameterNames()) {
                VarState paramState = returnState.parameters().get(param);
                StackValue argument = paramToArgument.get(param);
                ValueSet argumentValue = getValueOf(argument).intersect(paramState.value());
                if (argumentValue.isEmpty()) {
                    continue exit;
                }
                for (VarRelation relation : paramState.relations()) {
                    argumentValue =
                        argumentValue.removeNotFulfillingValues(getValueOf(paramToArgument.get(relation.rhs())),
                            relation.relation());
                    if (argumentValue.isEmpty()) {
                        continue exit;
                    }
                    if (argument instanceof LocalRefStackValue lhs &&
                        paramToArgument.get(relation.rhs()) instanceof LocalRefStackValue rhs) {
                        switch (state.checkRelation(lhs.local(), this.localsState.get(lhs.local()), rhs.local(),
                            relation.relation())) {
                            case NEVER -> {
                                continue exit;
                            }
                            default -> state.addRelationAndExtendTransitive(lhs.local(),
                                new VarRelation(rhs.local(), relation.relation()));
                        }

                    }
                }
                state.assertStackValue(argument, argumentValue);
            }
            state.stack.push(new ConcreteStackValue(returnState.value()));

            result.add(state);
        }
        return result;
    }

    public boolean assertTos(ValueSet expectedTos) {
        if (this.peek().isSupersetOf(expectedTos)) {
            this.stack.push(this.assertStackValue(this.stack.pop(), expectedTos));
            return true;
        } else {
            return false;
        }
    }

    private ValueSet getValueOf(StackValue value) {
        if (value instanceof ConcreteStackValue concrete) {
            return concrete.value();
        } else {
            return this.localsState.get(((LocalRefStackValue) value).local()).value();
        }
    }

    private StackValue assertStackValue(StackValue stackValue, ValueSet value) {
        if (!this.getValueOf(stackValue).isSupersetOf(value)) {
            throw new IllegalStateException(this.getValueOf(stackValue) + " is not a superset of " + value);
        } else if (stackValue instanceof LocalRefStackValue ref) {
            this.localsState.put(ref.local(), new VarState(value, this.localsState.get(ref.local()).relations()));
            return stackValue;
        } else {
            return new ConcreteStackValue(value);
        }
    }

    private void tryAssertRelation(StackValue lhs, StackValue rhs, Relation relation) {
        if (lhs instanceof LocalRefStackValue lhsRef && rhs instanceof LocalRefStackValue rhsRef) {
            this.addRelationAndExtendTransitive(lhsRef.local(), new VarRelation(rhsRef.local(), relation));
        }
    }

    private void addRelationAndExtendTransitive(String lhs, VarRelation relation) {
        if (this.localsState.get(lhs).relations().contains(relation) || relation.rhs().equals(lhs)) {
            return;
        }

        VarState local =
            this.localsState.compute(lhs, (k, v) -> addRelationAndTrimValue(v, relation.rhs(), relation.relation()));
        //this.localsState.compute(relation.rhs(), (k, v) -> v.addRelation(new VarRelation(lhs, relation.relation().invert())));

        if (relation.relation() == Relation.NOT_EQUAL) {
            // != is not transitive, but symmetric
            this.localsState.compute(relation.rhs(), (k, v) -> addRelationAndTrimValue(v, lhs, Relation.NOT_EQUAL));
            return;
        }

        local.relations().stream()
            .filter(r -> r.relation() == relation.relation().invert())
            .forEach(r -> addRelationAndExtendTransitive(r.rhs(), relation));
        local.relations().stream()
            .filter(r -> r.relation() == relation.relation())
            .forEach(r -> addRelationAndExtendTransitive(r.rhs(), new VarRelation(lhs, relation.relation().invert())));
    }

    private VarState addRelationAndTrimValue(VarState state, String rhs, Relation relation) {
        Set<VarRelation> relations = new HashSet<>(state.relations());
        relations.add(new VarRelation(rhs, relation));
        ValueSet value = state.value().removeNotFulfillingValues(this.localsState.get(rhs).value(), relation);
        return new VarState(value, relations);
    }

    private BooleanStatus checkRelation(String lhs, VarState lhsState, String rhs, Relation relation) {
        if (lhs.equals(rhs)) {
            if (Relation.EQUAL.implies(relation)) {
                return BooleanStatus.ALWAYS;
            } else if (relation.implies(Relation.NOT_EQUAL)) {
                return BooleanStatus.NEVER;
            }
        }
        if (hasRelation(lhsState, rhs, relation)) {
            return BooleanStatus.ALWAYS;
        } else if (hasRelation(lhsState, rhs, relation.negate())) {
            return BooleanStatus.NEVER;
        } else {
            return BooleanStatus.SOMETIMES;
        }
    }

    private boolean hasRelation(VarState lhsState, String rhs, Relation relation) {
        for (VarRelation r : lhsState.relations()) {
            if (r.relation().implies(relation) && r.rhs().equals(rhs)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EngineState that = (EngineState) o;
        return localsState.equals(that.localsState) && stack.equals(that.stack);
    }

    @Override
    public int hashCode() {
        return Objects.hash(localsState, stack);
    }

    @Override
    public String toString() {
        return this.stack + " " + this.localsState;
    }
}
