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
import spoon.reflect.declaration.CtParameter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * INVARIANT: Only the fields of this class are mutable. Everything else (VarStates, ValueSets, VarRelations, FieldIds, ...)
 * are IMMUTABLE. This ensures that a shallow copy of the datastructures of this class (namely the maps, as well as the varIdCounter) CLONES
 * the entire engine state.
 */
public class EngineState {
    private static final VarId THIS = VarId.forLocal("this");

    final FlowContext context;

    // The current state of all values
    // The mapping of entries may only change if we assert that a certain property holds (e.g. a condition is false 
    // because we take the else-branch of an if statement)
    // When normal program flow of the analyzed program overwrites a value, a new id needs to be created
    // And a new value needs to be stored in this map
    final List<VarState> varsState;

    // The current state of the stack
    final ValueStack stack;
    // Maps each SSAVarId to its current value
    final Map<SSAVarId, Integer> fieldValues;
    // Store the current value of each field / local where we have any knowledge
    final Map<FieldId, SSAVarId> liveFields;
    // List of locals that are also parameters - immutable
    private final Set<String> parameters;

    public EngineState(ObjectValueSet thisPointer, List<CtParameter<?>> parameters, FlowContext context) {
        this.context = context;

        this.parameters = new HashSet<>(parameters.size());
        this.fieldValues = new HashMap<>(parameters.size() + 1);
        this.liveFields = new HashMap<>(parameters.size() + 1);
        this.varsState = new ArrayList<>(parameters.size() + 1);

        int thisValue = this.varsState.size();
        this.varsState.add(new VarState(thisPointer));
        FieldId thisId = FieldId.forLocal("this");
        SSAVarId thisSSA = SSAVarId.forFresh(thisId);
        this.liveFields.put(thisId, thisSSA);
        this.fieldValues.put(thisSSA, thisValue);

        for (CtParameter<?> parameter : parameters) {
            this.parameters.add(parameter.getSimpleName());
            FieldId fieldId = FieldId.forLocal(parameter.getSimpleName());
            SSAVarId ssa = SSAVarId.forFresh(fieldId);
            this.liveFields.put(fieldId, ssa);
            int valueId = this.varsState.size();
            this.varsState.add(new VarState(ValueSet.topForType(new TypeId(parameter.getType()), this.context)));
            this.fieldValues.put(ssa, valueId);
        }

        this.stack = new ValueStack();
    }

    private EngineState(EngineState other) {
        this.context = other.context;
        this.varsState = new ArrayList<>(other.varsState);
        this.parameters = other.parameters;
        this.stack = new ValueStack(other.stack);
        this.liveFields = new HashMap<>(other.liveFields);
        this.fieldValues = new HashMap<>(other.fieldValues);
    }

    public EngineState fork() {
        return new EngineState(this);
    }

    public Map<VarId, VarState> getParamStates() {
        // Remove relations with non-parameter locals
        Map<VarId, VarState> paramStates = new HashMap<>(this.parameters.size());
        for (var field : this.liveFields.entrySet()) {
            if (!field.getValue().isInitial()) {
                continue;
            }
            if (field.getKey().isLocal() && !this.parameters.contains(field.getKey().fieldName())) {
                continue;
            }
            VarState state = this.varsState.get(this.fieldValues.get(field.getValue()));
            //TODO add relations and fields
            paramStates.put(this.buildVarId(field.getKey()), new VarState(state.value()));
        }
        return paramStates;
    }

    public void createVariable(String name, TypeId type) {
        VarState state = new VarState(ValueSet.topForType(type, this.context), Set.of());
        int id = this.createNewVarEntry(state);
        FieldId field = FieldId.forLocal(name);
        SSAVarId ssa = SSAVarId.forFresh(field);
        this.liveFields.put(field, ssa);
        this.fieldValues.put(ssa, id);
    }

    public void pushValue(ValueSet value) {
        this.stack.push(this.createNewVarEntry(new VarState(value)));
    }

    public void pushThis() {
        this.pushVar("this");
    }

    public void pushVar(String variable) {
        this.stack.push(this.fieldValues.get(this.liveFields.get(FieldId.forLocal(variable))));
    }

    public void pushField(String field) {
        int parent = this.stack.pop();

        // Parent cannot be null
        VarState parentState = this.varsState.get(parent);
        this.varsState.set(parent, new VarState(((ObjectValueSet) parentState.value()).asNonNull(),
            parentState.relations()));

        SSAVarId ssaId = this.liveFields.computeIfAbsent(new FieldId(parent, field), id -> {
            ObjectValueSet parentObject = (ObjectValueSet) this.varsState.get(id.parent()).value();
            ValueSet value = ValueSet.topForType(parentObject.getFieldType(id.fieldName()), this.context);
            SSAVarId ssa = SSAVarId.forFresh(id);
            this.fieldValues.put(ssa, this.createNewVarEntry(new VarState(value)));
            return ssa;
        });
        this.stack.push(this.fieldValues.get(ssaId));
    }

    public void storeVar(String variable) {
        FieldId fieldId = FieldId.forLocal(variable);
        SSAVarId ssa = this.liveFields.get(fieldId).next();
        this.liveFields.put(fieldId, ssa);
        this.fieldValues.put(ssa, this.stack.peek());
    }

    public void storeField(String name) {
        int objValue = this.stack.pop();
        FieldId fieldId = FieldId.forField(objValue, name);
        SSAVarId ssa = this.liveFields.computeIfAbsent(fieldId, SSAVarId::forFresh).next();
        this.liveFields.put(fieldId, ssa);
        this.fieldValues.put(ssa, this.stack.peek());

        // We know that the object cannot be null, or an exception would have been thrown
        VarState oldState = this.varsState.get(objValue);
        this.varsState.set(objValue, new VarState(((ObjectValueSet) oldState.value()).asNonNull(),
            oldState.relations()));
    }

    public void pop() {
        this.stack.pop();
    }

    public ValueSet peek() {
        return this.varsState.get(this.stack.peek()).value();
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
        IntValueSet tos = (IntValueSet) this.varsState.get(this.stack.pop()).value();
        this.stack.push(this.createNewVarEntry(new VarState(tos.negate())));
    }

    public void add() {
        int rhs = this.stack.pop();
        int lhs = this.stack.pop();
        IntValueSet rhsValue = (IntValueSet) this.varsState.get(rhs).value();
        IntValueSet lhsValue = (IntValueSet) this.varsState.get(lhs).value();
        if (rhsValue.isSingle(0)) {
            this.stack.push(lhs);
        } else if (lhsValue.isSingle(0)) {
            this.stack.push(rhs);
        } else {
            this.stack.push(this.createNewVarEntry(new VarState(lhsValue.add(rhsValue))));
        }
    }

    public void subtract() {
        int rhs = this.stack.pop();
        int lhs = this.stack.pop();
        IntValueSet rhsValue = (IntValueSet) this.varsState.get(rhs).value();
        IntValueSet lhsValue = (IntValueSet) this.varsState.get(lhs).value();
        if (rhsValue.isSingle(0)) {
            this.stack.push(lhs);
        } else {
            this.stack.push(this.createNewVarEntry(new VarState(lhsValue.subtract(rhsValue))));
        }
    }

    public void multiply() {
        int rhs = this.stack.pop();
        int lhs = this.stack.pop();
        IntValueSet rhsValue = (IntValueSet) this.varsState.get(rhs).value();
        IntValueSet lhsValue = (IntValueSet) this.varsState.get(lhs).value();
        if (rhsValue.isSingle(1)) {
            this.stack.push(lhs);
        } else if (lhsValue.isSingle(1)) {
            this.stack.push(rhs);
        } else {
            this.stack.push(this.createNewVarEntry(new VarState(lhsValue.multiply(rhsValue))));
        }
    }

    public void divide() {
        int rhs = this.stack.pop();
        int lhs = this.stack.pop();
        IntValueSet rhsValue = (IntValueSet) this.varsState.get(rhs).value();
        IntValueSet lhsValue = (IntValueSet) this.varsState.get(lhs).value();
        if (rhsValue.isSingle(1)) {
            this.stack.push(lhs);
        } else {
            this.stack.push(this.createNewVarEntry(new VarState(lhsValue.divide(rhsValue))));
        }
    }

    public void not() {
        BooleanValueSet tosValue = (BooleanValueSet) this.varsState.get(this.stack.pop()).value();
        this.stack.push(this.createNewVarEntry(new VarState(tosValue.not())));
    }

    // This is not short circuit!
    public List<EngineState> and() {
        List<EngineState> additionalPaths = new ArrayList<>();
        additionalPaths.add(this);

        int rhs = this.stack.pop();
        int lhs = this.stack.pop();

        BooleanValueSet lhsValue = (BooleanValueSet) this.varsState.get(lhs).value();
        BooleanValueSet rhsValue = (BooleanValueSet) this.varsState.get(rhs).value();
        if (lhsValue.isFalse()) {
            this.stack.push(this.createNewVarEntry(new VarState(BooleanValueSet.of(false))));
        } else if (lhsValue.isBottom()) {
            this.stack.push(this.createNewVarEntry(new VarState(BooleanValueSet.bottom())));
        } else {
            if (lhsValue.isTop()) {
                // Create a second path for lhs == false
                EngineState secondPath = this.fork();
                secondPath.assertVarValue(lhs, BooleanValueSet.of(false));
                secondPath.stack.push(secondPath.createNewVarEntry(new VarState(BooleanValueSet.of(false))));
                additionalPaths.add(secondPath);
            }
            this.assertVarValue(lhs, BooleanValueSet.of(true));

            if (rhsValue.isTop()) {
                // Create a second path for rhs == false
                EngineState secondPath = this.fork();
                secondPath.assertVarValue(rhs, BooleanValueSet.of(false));
                secondPath.stack.push(secondPath.createNewVarEntry(new VarState(BooleanValueSet.of(false))));
                additionalPaths.add(secondPath);

                // This path is for rhs == true
                this.assertVarValue(rhs, BooleanValueSet.of(true));
                this.stack.push(this.createNewVarEntry(new VarState(BooleanValueSet.of(true))));
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

        int rhs = this.stack.pop();
        int lhs = this.stack.pop();

        BooleanValueSet lhsValue = (BooleanValueSet) this.varsState.get(lhs).value();
        BooleanValueSet rhsValue = (BooleanValueSet) this.varsState.get(rhs).value();
        if (lhsValue.isTrue()) {
            this.stack.push(this.createNewVarEntry(new VarState(BooleanValueSet.of(true))));
        } else if (lhsValue.isBottom()) {
            this.stack.push(this.createNewVarEntry(new VarState(BooleanValueSet.bottom())));
        } else {

            if (lhsValue.isTop()) {
                // Create a second path for lhs == true
                EngineState secondPath = this.fork();
                secondPath.assertVarValue(lhs, BooleanValueSet.of(true));
                secondPath.stack.push(secondPath.createNewVarEntry(new VarState(BooleanValueSet.of(true))));
                additionalPaths.add(secondPath);
            }
            this.assertVarValue(lhs, BooleanValueSet.of(false));

            if (rhsValue.isTop()) {
                // Create a second path for rhs == true
                EngineState secondPath = this.fork();
                secondPath.assertVarValue(rhs, BooleanValueSet.of(true));
                secondPath.stack.push(secondPath.createNewVarEntry(new VarState(BooleanValueSet.of(true))));
                additionalPaths.add(secondPath);

                // This path is for rhs == false
                this.assertVarValue(rhs, BooleanValueSet.of(false));
                this.stack.push(this.createNewVarEntry(new VarState(BooleanValueSet.of(false))));
            } else {
                this.stack.push(rhs);
            }
        }

        return additionalPaths;
    }

    public List<EngineState> compareOp(Relation relation) {
        int rhs = this.stack.pop();
        int lhs = this.stack.pop();

        // Query known relations first
        switch (checkRelation(lhs, rhs, relation)) {
            case ALWAYS -> {
                this.stack.push(this.createNewVarEntry(new VarState(BooleanValueSet.of(true))));
                return List.of(this);
            }
            case NEVER -> {
                this.stack.push(this.createNewVarEntry(new VarState(BooleanValueSet.of(false))));
                return List.of(this);
            }
            case SOMETIMES -> {
            }
        }

        VarState lhsValue = this.varsState.get(lhs);
        VarState rhsValue = this.varsState.get(rhs);

        return switch (lhsValue.value().fulfillsRelation(rhsValue.value(), relation)) {
            case ALWAYS -> {
                this.stack.push(this.createNewVarEntry(new VarState(BooleanValueSet.of(true))));
                this.tryAssertRelation(lhs, rhs, relation);
                yield List.of(this);
            }
            case NEVER -> {
                this.stack.push(this.createNewVarEntry(new VarState(BooleanValueSet.of(false))));
                this.tryAssertRelation(lhs, rhs, relation.negate());
                yield List.of(this);
            }
            case SOMETIMES -> {
                // "this" is the true path, other is the false path
                EngineState notFulfilledState = this.fork();
                notFulfilledState.stack.push(
                    notFulfilledState.createNewVarEntry(new VarState(BooleanValueSet.of(false))));
                notFulfilledState.tryAssertRelation(lhs, rhs, relation.negate());

                this.stack.push(this.createNewVarEntry(new VarState(BooleanValueSet.of(true))));
                this.tryAssertRelation(lhs, rhs, relation);
                yield List.of(this, notFulfilledState);
            }
        };
    }

    public List<EngineState> callStatic(CachedMethod method) {
        return this.call(-1, method.getFixedCallAnalysis());
    }

    public List<EngineState> callVirtual(CachedMethod method) {
        int thisVar = this.stack.peek(method.getExecutable().getParameters().size());
        if (((ObjectValueSet) this.varsState.get(thisVar).value()).isExact()) {
            return this.call(-1, method.getFixedCallAnalysis());
        } else {
            List<EngineState> resultStates = new ArrayList<>();
            for (MethodAnalysis analysis : method.getVirtualCallAnalyses()) {
                EngineState state = this.fork();
                ObjectValueSet requiredThis =
                    ObjectValueSet.forUnconstrainedType(Nullness.NON_NULL,
                        analysis.getMethod().getThisType().orElseThrow(), this.context);

                state.assertVarValue(thisVar, requiredThis);
                resultStates.addAll(state.call(thisVar, analysis));
            }
            return resultStates;
        }
    }

    public List<EngineState> callConstructor(CachedMethod method) {
        return this.call(-1, method.getFixedCallAnalysis());
    }

    // Recursively creates a mapping between (string-based) field paths and values
    private void collectFieldValues(VarId parent, int id, Map<VarId, Integer> knownValues) {
        for (var fieldId : this.fieldValues.entrySet()) {
            if (fieldId.getKey().fieldId().parent() == id) {
                VarId path = parent.resolveField(fieldId.getKey().fieldId().fieldName());
                knownValues.put(path, fieldId.getValue());
                this.collectFieldValues(path, fieldId.getValue(), knownValues);
            }
        }
    }

    private List<EngineState> call(int callee, MethodAnalysis method) {
        if (method.getReturnStates() == null) {
            // TODO reset all fields that may have been mutated
            return List.of(this);
        }

        Map<VarId, Integer> knownValues = new HashMap<>(method.getOrderedParameterNames().size());

        // Extract the parameters
        var orderedParameters = method.getOrderedParameterNames();
        for (int i = orderedParameters.size() - 1; i >= 0; i--) {
            var param = orderedParameters.get(i);
            knownValues.put(param, this.stack.pop());
        }

        if (callee >= 0) {
            // Map known fields
            this.collectFieldValues(THIS, callee, knownValues);
        }

        List<EngineState> result = new ArrayList<>();
        for (MethodExitState returnState : method.getReturnStates()) {
            EngineState newState = new CallMapper(this, returnState, knownValues).map();
            if (newState != null) {
                result.add(newState);
            }
        }
        return result;
    }

    public boolean assertTos(ValueSet expectedTos) {
        if (this.peek().isSupersetOf(expectedTos)) {
            this.assertVarValue(this.stack.peek(), expectedTos);
            return true;
        } else {
            return false;
        }
    }

    // Previous assertStackValue
    void assertVarValue(int id, ValueSet value) {
        if (!this.varsState.get(id).value().isSupersetOf(value)) {
            throw new IllegalStateException(this.varsState.get(id).value() + " is not a superset of " + value);
        } else {
            // TODO don't throw away relations of the old VarState
            this.varsState.set(id, new VarState(value));
        }
    }

    private VarId buildVarId(FieldId field) {
        if (field.isLocal()) {
            return VarId.forLocal(field.fieldName());
        }
        FieldId parent = this.fieldValues.entrySet().stream()
            .filter(e -> e.getValue() == field.parent())
            .findAny()
            .orElseThrow()
            .getKey().fieldId();
        return buildVarId(parent).resolveField(field.fieldName());
    }

    private void tryAssertRelation(int lhs, int rhs, Relation relation) {
        this.addRelationAndExtendTransitive(lhs, new VarRelation(rhs, relation));
    }

    private void addRelationAndExtendTransitive(int lhs, VarRelation relation) {
        if (this.varsState.get(lhs).relations().contains(relation) || relation.rhs() == lhs) {
            return;
        }

        VarState local = addRelationAndTrimValue(this.varsState.get(lhs), relation.rhs(), relation.relation());
        this.varsState.set(lhs, local);
        //this.localsState.compute(relation.rhs(), (k, v) -> v.addRelation(new VarRelation(lhs, relation.relation().invert())));

        if (relation.relation() == Relation.NOT_EQUAL) {
            // != is not transitive, but symmetric
            this.varsState.set(relation.rhs(),
                addRelationAndTrimValue(this.varsState.get(relation.rhs()), lhs, Relation.NOT_EQUAL));
            return;
        }

        local.relations().stream()
            .filter(r -> r.relation() == relation.relation().invert())
            .forEach(r -> addRelationAndExtendTransitive(r.rhs(), relation));
        local.relations().stream()
            .filter(r -> r.relation() == relation.relation())
            .forEach(r -> addRelationAndExtendTransitive(r.rhs(), new VarRelation(lhs, relation.relation().invert())));
    }

    private VarState addRelationAndTrimValue(VarState state, int rhs, Relation relation) {
        Set<VarRelation> relations = new HashSet<>(state.relations());
        relations.add(new VarRelation(rhs, relation));
        ValueSet value = state.value().removeNotFulfillingValues(this.varsState.get(rhs).value(), relation);
        return new VarState(value, relations);
    }

    private BooleanStatus checkRelation(int lhs, int rhs, Relation relation) {
        if (lhs == rhs) {
            if (Relation.EQUAL.implies(relation)) {
                return BooleanStatus.ALWAYS;
            } else if (relation.implies(Relation.NOT_EQUAL)) {
                return BooleanStatus.NEVER;
            }
        }
        if (hasRelation(lhs, rhs, relation)) {
            return BooleanStatus.ALWAYS;
        } else if (hasRelation(lhs, rhs, relation.negate())) {
            return BooleanStatus.NEVER;
        } else {
            return BooleanStatus.SOMETIMES;
        }
    }

    private boolean hasRelation(int lhs, int rhs, Relation relation) {
        for (VarRelation r : this.varsState.get(lhs).relations()) {
            if (r.relation().implies(relation) && r.rhs() == rhs) {
                return true;
            }
        }
        return false;
    }

    int createNewVarEntry(VarState state) {
        int id = this.varsState.size();
        this.varsState.add(state);
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EngineState that = (EngineState) o;
        return Objects.equals(varsState, that.varsState) && Objects.equals(stack, that.stack) &&
            Objects.equals(parameters, that.parameters) &&
            Objects.equals(fieldValues, that.fieldValues) &&
            Objects.equals(liveFields, that.liveFields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(varsState, stack, parameters, fieldValues, liveFields);
    }

    @Override
    public String toString() {
        return "stack: " + this.stack
            + " fields: [" + this.liveFields.entrySet().stream()
            .map(e -> e.getKey() + ": $" + this.fieldValues.get(e.getValue()))
            .collect(Collectors.joining(", "))
            + "] values: " + this.varsState;
    }
}
