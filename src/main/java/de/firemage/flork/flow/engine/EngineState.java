package de.firemage.flork.flow.engine;

import de.firemage.flork.flow.BooleanStatus;
import de.firemage.flork.flow.CachedMethod;
import de.firemage.flork.flow.FlowContext;
import de.firemage.flork.flow.MethodExitState;
import de.firemage.flork.flow.SetStack;
import de.firemage.flork.flow.TypeId;
import de.firemage.flork.flow.analysis.MethodAnalysis;
import de.firemage.flork.flow.value.BooleanValueSet;
import de.firemage.flork.flow.value.IntValueSet;
import de.firemage.flork.flow.value.Nullness;
import de.firemage.flork.flow.value.ObjectValueSet;
import de.firemage.flork.flow.value.ValueSet;
import de.firemage.flork.flow.value.VoidValue;
import spoon.reflect.declaration.CtParameter;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
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
    public static final int THIS_VALUE = 0;

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
    // Maps vars to their *declared* type. Useful for resetting values
    private final Map<FieldId, TypeId> types;
    // Stores the *initial* value of each parameter & own field. Useful to construct preconditions for states - immutable
    // May contain null to indicate that we never used the initial value (but overwrite it)
    private final Map<VarId, Integer> initialFieldValues;

    // Fields that have been written to in a given context
    // Useful e.g. to reset all written fields after loops
    // The stack represents nested contexts (i.e. blocks)
    private final SetStack<FieldId> writtenLocalsAndOwnFields;

    public EngineState(TypeId thisType, ObjectValueSet thisPointer, List<CtParameter<?>> parameters, FlowContext context) {
        this.context = context;

        this.fieldValues = new HashMap<>(parameters.size() + 1);
        this.liveFields = new HashMap<>(parameters.size() + 1);
        this.varsState = new ArrayList<>(parameters.size() + 1);
        this.types = new HashMap<>(parameters.size() + 1);
        this.initialFieldValues = new HashMap<>(parameters.size());

        if (thisPointer != null) {
            FieldId thisId = FieldId.forLocal("this");
            SSAVarId thisSSA = SSAVarId.forFresh(thisId);
            this.liveFields.put(thisId, thisSSA); // this is alive, and maps to our created SSA value
            if (this.createNewVarEntry(new VarState(thisPointer)) != THIS_VALUE) {
                throw new IllegalStateException("Value of THIS is unexpectedly not 0 - this is a bug");
            }
            this.fieldValues.put(thisSSA, THIS_VALUE); // Store the id to the this value and get its id (is always 0)
            this.types.put(FieldId.THIS, thisType); // Remember which type this is
        }

        for (CtParameter<?> parameter : parameters) {
            FieldId fieldId = FieldId.forLocal(parameter.getSimpleName());
            SSAVarId ssa = SSAVarId.forFresh(fieldId);
            this.liveFields.put(fieldId, ssa);
            int value = this.createNewVarEntry(new VarState(ValueSet.topForType(new TypeId(parameter.getType()), this.context)));
            this.fieldValues.put(ssa, value);
            this.initialFieldValues.put(VarId.forLocal(parameter.getSimpleName()), value);
            this.types.put(FieldId.forLocal(fieldId.fieldName()), TypeId.ofFallible(parameter.getType()).orElseThrow());
        }

        this.stack = new ValueStack();
        this.writtenLocalsAndOwnFields = new SetStack<>(2);
    }

    private EngineState(EngineState other) {
        this.context = other.context;
        this.varsState = new ArrayList<>(other.varsState);
        this.stack = new ValueStack(other.stack);
        this.liveFields = new HashMap<>(other.liveFields);
        this.fieldValues = new HashMap<>(other.fieldValues);
        this.types = new HashMap<>(other.types);
        this.initialFieldValues = new HashMap<>(other.initialFieldValues);
        this.writtenLocalsAndOwnFields = new SetStack<>(other.writtenLocalsAndOwnFields);
    }

    public EngineState fork() {
        return new EngineState(this);
    }

    public Map<VarId, VarState> getInitialState() {
        Map<VarId, VarState> paramStates = new HashMap<>(this.initialFieldValues.size());
        for (var field : this.initialFieldValues.entrySet()) {
            VarState state = this.varsState.get(field.getValue());
            //TODO add relations to other initial values
            paramStates.put(field.getKey(), new VarState(state.value()));
        }
        return paramStates;
    }

    public void createVariable(String name, TypeId type) {
        FieldId field = FieldId.forLocal(name);
        SSAVarId ssa = SSAVarId.forFresh(field);
        this.liveFields.put(field, ssa);
        this.fieldValues.put(ssa, this.createNewVarEntry(new VarState(ValueSet.topForType(type, this.context))));
        this.types.put(FieldId.forLocal(name), type);
    }

    public void beginWritesScope() {
        this.writtenLocalsAndOwnFields.pushEmpty();
    }

    public void endWritesScope() {
        var writes = this.writtenLocalsAndOwnFields.pop();
        // Add writes from current context to enclosing context, since these are also writes inside the enclosing context
        if (this.writtenLocalsAndOwnFields.peek() != null) {
            this.writtenLocalsAndOwnFields.addAllToLast(writes);
        }
    }

    public void resetWrittenLocalsAndFields() {
        // We reset all locals and own fields that have been written to in the current context
        // We unconditionally reset all "transitive fields" (i.e. fields of fields of this)
        this.liveFields.entrySet().removeIf(f -> {
            // Edit the entry of locals to their respective top type
            if (this.writtenLocalsAndOwnFields.peek().contains(f.getKey())) {
                SSAVarId ssa = f.getValue().next();
                ValueSet newValue = ValueSet.topForType(this.types.get(f.getKey()), this.context);
                this.fieldValues.put(ssa, this.createNewVarEntry(new VarState(newValue)));
                f.setValue(ssa);
                return false;
            }
            return !f.getKey().isLocalOrOwnField();
        });
    }

    public void resetTransitiveFields() {
        this.liveFields.entrySet().removeIf(f -> !f.getKey().isLocalOrOwnField());
    }

    public void resetAllFields() {
        this.liveFields.entrySet().removeIf(f -> !f.getKey().isLocal());
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
        this.assertNonNull(parent);

        SSAVarId ssaId = this.liveFields.computeIfAbsent(new FieldId(parent, field), id -> {
            VarState parentState = this.varsState.get(parent);
            TypeId type = ((ObjectValueSet) parentState.value()).getFieldType(field);

            ValueSet value = ValueSet.topForType(type, this.context);
            SSAVarId ssa = SSAVarId.forFresh(id);
            this.fieldValues.put(ssa, this.createNewVarEntry(new VarState(value)));
            this.types.put(id, type); // Record the type of the field
            return ssa;
        });
        this.stack.push(this.fieldValues.get(ssaId));

        if (parent == THIS_VALUE) {
            // Remember the first known field value
            this.initialFieldValues.computeIfAbsent(VarId.forOwnField(field), varId -> this.fieldValues.get(ssaId));
        }
    }

    public void storeVar(String variable) {
        FieldId fieldId = FieldId.forLocal(variable);
        SSAVarId ssa = this.liveFields.get(fieldId).next();
        this.liveFields.put(fieldId, ssa);
        this.fieldValues.put(ssa, this.stack.peek());
        this.recordWrite(fieldId);
    }

    public void storeField(String name) {
        int objValue = this.stack.pop();
        FieldId fieldId = FieldId.forField(objValue, name);
        SSAVarId ssa = this.liveFields.computeIfAbsent(fieldId, SSAVarId::forFresh).next();
        this.liveFields.put(fieldId, ssa);
        this.fieldValues.put(ssa, this.stack.peek());

        if (objValue == THIS_VALUE) {
            this.recordWrite(fieldId);
        }

        // We know that the object cannot be null, or an exception would have been thrown
        VarState oldState = this.varsState.get(objValue);
        this.varsState.set(objValue, new VarState(((ObjectValueSet) oldState.value()).asNonNull(),
            oldState.relations()));

        if (objValue == THIS_VALUE) {
            // If we don't assume an initial value for the field yet, we will never assume any since it is now overridden
            this.initialFieldValues.putIfAbsent(VarId.forOwnField(name), null);
        }
    }

    public void pop() {
        this.stack.pop();
    }

    public ValueSet peek() {
        return this.varsState.get(this.stack.peek()).value();
    }

    public boolean isTOSThis() {
        return this.stack.peek() == 0;
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
        this.assertNonNull(thisVar);

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
            // No analysis available, so assume the worst and reset everything
            for (int i = 0; i < method.getMethod().getExecutable().getParameters().size(); i++) {
                this.pop();
            }
            this.resetAllFields();
            return List.of(this);
        }

        // We don't know what happened to non-own fields
        this.resetTransitiveFields();

        Map<VarId, Integer> knownValues = new HashMap<>(method.getOrderedParameterNames().size());

        // Extract the parameters
        var orderedParameters = method.getOrderedParameterNames();
        for (int i = orderedParameters.size() - 1; i >= 0; i--) {
            var param = orderedParameters.get(i);
            knownValues.put(param, this.stack.pop());
        }

        if (callee >= 0) {
            // Map known fields
            this.collectFieldValues(VarId.THIS, callee, knownValues);
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
        var oldState = this.varsState.get(id);
        if (!oldState.value().isSupersetOf(value)) {
            throw new IllegalStateException(this.varsState.get(id).value() + " is not a superset of " + value);
        } else {
            this.varsState.set(id, new VarState(value, oldState.relations()));
        }
    }

    void assertNonNull(int id) {
        var oldState = this.varsState.get(id);
        this.varsState.set(id, new VarState(((ObjectValueSet) oldState.value()).asNonNull(), oldState.relations()));
    }

    private void recordWrite(FieldId field) {
        if (!field.isLocalOrOwnField()) {
            throw new IllegalArgumentException("Cannot write to non-local field " + field);
        }

        if (this.writtenLocalsAndOwnFields.peek() != null) {
            this.writtenLocalsAndOwnFields.addToLast(field);
        }
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
            Objects.equals(initialFieldValues, that.initialFieldValues) &&
            Objects.equals(fieldValues, that.fieldValues) &&
            Objects.equals(liveFields, that.liveFields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(varsState, stack, initialFieldValues, fieldValues, liveFields);
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
