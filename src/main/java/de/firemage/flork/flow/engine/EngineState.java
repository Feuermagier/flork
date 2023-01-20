package de.firemage.flork.flow.engine;

import de.firemage.flork.flow.BooleanStatus;
import de.firemage.flork.flow.CachedMethod;
import de.firemage.flork.flow.FlowContext;
import de.firemage.flork.flow.MethodExitState;
import de.firemage.flork.flow.TypeId;
import de.firemage.flork.flow.analysis.MethodAnalysis;
import de.firemage.flork.flow.value.BooleanValueSet;
import de.firemage.flork.flow.value.IntValueSet;
import de.firemage.flork.flow.value.Nullness;
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
    private static final VarId THIS = VarId.forLocal("this");

    private final FlowContext context;

    // The current state of all locals, including a local named "this" for the this-pointer
    private final Map<SSAVarId, VarState> varsState;

    // The current state of the stack
    private final ValueStack stack;

    // List of locals that are also parameters
    private final Set<VarId> parameters;
    
    // Maps VarIds to their live SSAVarId
    private final Map<VarId, SSAVarId> liveVariables;

    public EngineState(Map<VarId, VarState> initialValues, Set<VarId> parameters, FlowContext context) {
        this.context = context;
        this.parameters = parameters;
        
        this.varsState = new HashMap<>(parameters.size());
        this.liveVariables = new HashMap<>(parameters.size());
        for (var value : initialValues.entrySet()) {
            SSAVarId id = SSAVarId.forFresh(value.getKey());
            this.varsState.put(id, value.getValue());
            this.liveVariables.put(value.getKey(), id);
        }
        this.stack = new ValueStack();
    }

    public EngineState(EngineState other) {
        this.context = other.context;
        this.varsState = new HashMap<>(other.varsState);
        this.parameters = other.parameters;
        this.stack = new ValueStack(other.stack);
        this.liveVariables = new HashMap<>(other.liveVariables);
    }

    public EngineState fork() {
        return new EngineState(this);
    }

    public Map<VarId, VarState> getParamStates() {
        // Remove relations with non-parameter locals
        Map<VarId, VarState> paramStates = new HashMap<>(this.parameters.size());
        for (VarId param : this.parameters) {
            VarState value = this.varsState.entrySet().stream()
                .filter(e -> e.getKey().varId().equals(param) && e.getKey().isInitial())
                .findAny()
                .get()
                .getValue();
            paramStates.put(param, new VarState(value.value(), value
                .relations()
                .stream()
                .filter(r -> this.parameters.contains(r.rhs()))
                .collect(Collectors.toSet())));
        }
        return paramStates;
    }

    public void createVariable(VarId name, TypeId type) {
        this.varsState.put(SSAVarId.forFresh(name), new VarState(ValueSet.topForType(type, this.context), Set.of()));
    }

    public void pushValue(ValueSet value) {
        this.stack.push(new ConcreteStackValue(value));
    }

    public void pushThis() {
        this.pushVar(THIS);
    }

    public void pushVar(VarId variable) {
        this.stack.push(new LocalRefStackValue(this.liveVariables.get(variable)));
    }

    public void pushField(String field) {
        StackValue tos = this.stack.pop();
        if (tos instanceof LocalRefStackValue ref) {
            VarId path = ref.local().varId().resolveField(field);
            this.createVarIfMissing(path);
            this.pushVar(path);
        } else if (tos instanceof ConcreteStackValue concrete) {
            if (concrete.value() instanceof ObjectValueSet object) {
                this.pushValue(ValueSet.topForType(object.getFieldType(field), this.context));
            } else {
                throw new IllegalStateException();
            }
        }
    }

    public void storeVar(VarId variable) {
        SSAVarId ssa = this.getNextVar(variable);
        this.varsState.put(ssa, new VarState(getValueOf(this.stack.peek()), Set.of()));
        this.liveVariables.put(ssa.varId(), ssa);
        if (this.stack.peek() instanceof LocalRefStackValue ref) {
            this.addRelationAndExtendTransitive(ssa, new VarRelation(ref.local(), Relation.EQUAL));
        }
    }
    
    public void storeField(String name) {
        var tos = this.stack.pop();
        if (tos instanceof LocalRefStackValue fieldRef) {
            SSAVarId ssa = this.getNextVar(fieldRef.local().varId().resolveField(name));
            this.varsState.put(ssa, new VarState(getValueOf(this.stack.peek()), Set.of()));
            if (this.stack.peek() instanceof LocalRefStackValue ref) {
                this.addRelationAndExtendTransitive(ssa, new VarRelation(ref.local(), Relation.EQUAL));
            }
        } else {
            //TODO parse assignments to fields of non-locals (e.g. getValue().x = 3)
        }
    }

    public void pop() {
        this.stack.pop();
    }

    public ValueSet peek() {
        return getValueOf(this.stack.peek());
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
            VarState lhsState = this.varsState.get(lhsRef.local());
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
        int thisOffset = method.getExecutable().getParameters().size();
        if (((ObjectValueSet) this.getValueOf(this.stack.peek(thisOffset))).isExact()) {
            return this.call(method.getFixedCallAnalysis());
        } else {
            List<EngineState> resultStates = new ArrayList<>();
            for (MethodAnalysis analysis : method.getVirtualCallAnalyses()) {
                EngineState state = this.fork();
                ObjectValueSet requiredThis =
                    ObjectValueSet.forUnconstrainedType(Nullness.NON_NULL, analysis.getMethod().getThisType().get(),
                        this.context);
                state.stack.overwrite(assertStackValue(state.stack.peek(thisOffset), requiredThis), thisOffset);
                resultStates.addAll(state.call(analysis));
            }
            return resultStates;
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
            Map<VarId, StackValue> paramToArgument = new HashMap<>(method.getOrderedParameterNames().size());

            var orderedParameters = method.getOrderedParameterNames();

            // Extract the parameters
            for (int i = orderedParameters.size() - 1; i >= 0; i--) {
                var param = orderedParameters.get(i);
                StackValue tos = state.stack.pop();
                paramToArgument.put(param, tos);
            }

            // Check all parameters and add relations
            for (int i = orderedParameters.size() - 1; i >= 0; i--) {
                var param = orderedParameters.get(i);
                VarState paramState = returnState.parameters().get(param);
                StackValue argument = paramToArgument.get(param);
                ValueSet argumentValue = getValueOf(argument).intersect(paramState.value());
                if (argumentValue.isEmpty()) {
                    continue exit;
                }
                for (VarRelation relation : paramState.relations()) {
                    argumentValue = argumentValue.removeNotFulfillingValues(
                        getValueOf(paramToArgument.get(relation.rhs().varId())), 
                        relation.relation()
                    );
                    if (argumentValue.isEmpty()) {
                        continue exit;
                    }
                    if (argument instanceof LocalRefStackValue lhs &&
                        paramToArgument.get(relation.rhs().varId()) instanceof LocalRefStackValue rhs) {
                        switch (state.checkRelation(lhs.local(), this.varsState.get(lhs.local()), rhs.local(),
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
            return this.varsState.get(((LocalRefStackValue) value).local()).value();
        }
    }

    private StackValue assertStackValue(StackValue stackValue, ValueSet value) {
        if (!this.getValueOf(stackValue).isSupersetOf(value)) {
            throw new IllegalStateException(this.getValueOf(stackValue) + " is not a superset of " + value);
        } else if (stackValue instanceof LocalRefStackValue ref) {
            this.varsState.put(ref.local(), new VarState(value, this.varsState.get(ref.local()).relations()));
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

    private void addRelationAndExtendTransitive(SSAVarId lhs, VarRelation relation) {
        if (this.varsState.get(lhs).relations().contains(relation) || relation.rhs().equals(lhs)) {
            return;
        }

        VarState local =
            this.varsState.compute(lhs, (k, v) -> addRelationAndTrimValue(v, relation.rhs(), relation.relation()));
        //this.localsState.compute(relation.rhs(), (k, v) -> v.addRelation(new VarRelation(lhs, relation.relation().invert())));
        
        if (relation.relation() == Relation.NOT_EQUAL) {
            // != is not transitive, but symmetric
            this.varsState.compute(relation.rhs(), (k, v) -> addRelationAndTrimValue(v, lhs, Relation.NOT_EQUAL));
            return;
        }

        local.relations().stream()
            .filter(r -> r.relation() == relation.relation().invert())
            .forEach(r -> addRelationAndExtendTransitive(r.rhs(), relation));
        local.relations().stream()
            .filter(r -> r.relation() == relation.relation())
            .forEach(r -> addRelationAndExtendTransitive(r.rhs(), new VarRelation(lhs, relation.relation().invert())));
    }

    private VarState addRelationAndTrimValue(VarState state, SSAVarId rhs, Relation relation) {
        Set<VarRelation> relations = new HashSet<>(state.relations());
        relations.add(new VarRelation(rhs, relation));
        ValueSet value = state.value().removeNotFulfillingValues(this.varsState.get(rhs).value(), relation);
        return new VarState(value, relations);
    }

    private BooleanStatus checkRelation(SSAVarId lhs, VarState lhsState, SSAVarId rhs, Relation relation) {
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

    private boolean hasRelation(VarState lhsState, SSAVarId rhs, Relation relation) {
        for (VarRelation r : lhsState.relations()) {
            if (r.relation().implies(relation) && r.rhs().equals(rhs)) {
                return true;
            }
        }
        return false;
    }
    
    private SSAVarId getNextVar(VarId varId) {
        return this.liveVariables.compute(varId, (id, ssa) -> {
            if (ssa == null) {
                return SSAVarId.forFresh(varId);
            } else {
                return ssa.next();
            }
        });
    }
    
    private void createVarIfMissing(VarId varId) {
        this.liveVariables.computeIfAbsent(varId, v -> {
            VarState parent = this.varsState.get(this.liveVariables.get(v.parent()));
            if (parent.value() instanceof ObjectValueSet object) {
                SSAVarId ssa = SSAVarId.forFresh(varId);
                var state = new VarState(ValueSet.topForType(object.getFieldType(varId.fieldName()), this.context), Set.of());
                this.varsState.put(ssa, state);
                return ssa;
            } else {
                throw new IllegalStateException();
            }
        });
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EngineState that = (EngineState) o;
        return varsState.equals(that.varsState) && stack.equals(that.stack) && liveVariables.equals(that.liveVariables);
    }

    @Override
    public int hashCode() {
        return Objects.hash(varsState, stack, liveVariables);
    }

    @Override
    public String toString() {
        return this.stack + " " + this.varsState;
    }
}
