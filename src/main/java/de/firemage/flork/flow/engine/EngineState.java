package de.firemage.flork.flow.engine;

import de.firemage.flork.flow.BooleanStatus;
import de.firemage.flork.flow.CachedMethod;
import de.firemage.flork.flow.FlowContext;
import de.firemage.flork.flow.exit.MethodExitState;
import de.firemage.flork.flow.SetStack;
import de.firemage.flork.flow.TypeId;
import de.firemage.flork.flow.analysis.MethodAnalysis;
import de.firemage.flork.flow.value.BooleanValueSet;
import de.firemage.flork.flow.value.BoxedIntValueSet;
import de.firemage.flork.flow.value.IntValueSet;
import de.firemage.flork.flow.value.Nullness;
import de.firemage.flork.flow.value.NumericValueSet;
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
    // Store the current value of each field / local where we have any knowledge
    final Map<FieldId, Integer> liveFields;
    // Maps vars to their *declared* type. Useful for resetting values
    private final Map<FieldId, TypeId> types;
    // Stores the *initial* value of each parameter. Useful to construct preconditions for states - immutable
    private final List<Integer> initialParamValues;

    // Fields that have been written to in a given context
    // Useful e.g. to reset all written fields after loops
    // The stack represents nested contexts (i.e. blocks)
    private final SetStack<FieldId> writtenLocalsAndOwnFields;

    TypeId activeException = null;

    public EngineState(TypeId thisType, ObjectValueSet thisPointer, List<CtParameter<?>> parameters, FlowContext context) {
        this.context = context;

        this.liveFields = new HashMap<>(parameters.size() + 1);
        this.varsState = new ArrayList<>(parameters.size() + 1);
        this.types = new HashMap<>(parameters.size() + 1);
        this.initialParamValues = new ArrayList<>(parameters.size() + 1);

        if (thisPointer != null) {
            if (this.createNewVarEntry(new VarState(thisPointer)) != THIS_VALUE) {
                throw new IllegalStateException("Value of THIS is unexpectedly not 0 - this is a bug");
            }
            this.liveFields.put(FieldId.THIS, THIS_VALUE); // Store the id to the this value
            this.initialParamValues.add(THIS_VALUE);
            this.types.put(FieldId.THIS, thisType); // Remember which type this is
        }

        for (CtParameter<?> parameter : parameters) {
            FieldId fieldId = FieldId.forLocal(parameter.getSimpleName());
            int value = this.createNewVarEntry(new VarState(ValueSet.topForType(new TypeId(parameter.getType()), this.context)));
            this.liveFields.put(fieldId, value);
            this.initialParamValues.add(value);
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
        this.types = new HashMap<>(other.types);
        this.initialParamValues = new ArrayList<>(other.initialParamValues);
        this.writtenLocalsAndOwnFields = new SetStack<>(other.writtenLocalsAndOwnFields);
        this.activeException = other.activeException;
    }

    public EngineState fork() {
        return new EngineState(this);
    }

    public boolean hasActiveException() {
        return this.activeException != null;
    }

    public TypeId getActiveException() {
        return this.activeException;
    }

    public void clearActiveException() {
        this.activeException = null;
    }

    public List<ValueSet> getInitialState() {
        return this.initialParamValues.stream().map(this.varsState::get).map(VarState::value).toList();
    }

    public void createVariable(String name, TypeId type) {
        FieldId field = FieldId.forLocal(name);
        this.liveFields.put(field, this.createNewVarEntry(new VarState(ValueSet.topForType(type, this.context))));
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
                ValueSet newValue = ValueSet.topForType(this.types.get(f.getKey()), this.context);
                f.setValue(this.createNewVarEntry(new VarState(newValue)));
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
        this.stack.push(this.liveFields.get(FieldId.forLocal(variable)));
    }

    public void pushField(String field) {
        int parent = this.stack.pop();

        // Parent cannot be null
        this.assertNonNull(parent);

        var fieldId = new FieldId(parent, field);

        int value = this.liveFields.computeIfAbsent(fieldId, id -> {
            VarState parentState = this.varsState.get(parent);
            TypeId type = ((ObjectValueSet) parentState.value()).getFieldType(field);

            int valueId = this.createNewVarEntry(new VarState(ValueSet.topForType(type, this.context)));
            this.types.put(id, type); // Record the type of the field
            return valueId;
        });
        this.stack.push(this.liveFields.get(fieldId));
    }

    public void storeVar(String variable) {
        FieldId fieldId = FieldId.forLocal(variable);
        this.liveFields.put(fieldId, this.stack.peek());
        this.recordWrite(fieldId);
    }

    public void storeField(String name) {
        int objValue = this.stack.pop();
        FieldId fieldId = FieldId.forField(objValue, name);
        this.liveFields.put(fieldId, this.stack.peek());

        if (objValue == THIS_VALUE) {
            this.recordWrite(fieldId);
        }

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
        var tos = (NumericValueSet) this.varsState.get(this.stack.pop()).value();
        this.stack.push(this.createNewVarEntry(new VarState(tos.negate())));
    }

    public void add() {
        int rhs = this.stack.pop();
        int lhs = this.stack.pop();
        var rhsValue = (NumericValueSet) this.varsState.get(rhs).value();
        var lhsValue = (NumericValueSet) this.varsState.get(lhs).value();
        if (rhsValue.isZero()) {
            this.stack.push(lhs);
        } else if (lhsValue.isZero()) {
            this.stack.push(rhs);
        } else {
            this.stack.push(this.createNewVarEntry(new VarState(lhsValue.add(rhsValue))));
        }
    }

    public void subtract() {
        int rhs = this.stack.pop();
        int lhs = this.stack.pop();
        var rhsValue = (NumericValueSet) this.varsState.get(rhs).value();
        var lhsValue = (NumericValueSet) this.varsState.get(lhs).value();
        if (rhsValue.isZero()) {
            this.stack.push(lhs);
        } else {
            this.stack.push(this.createNewVarEntry(new VarState(lhsValue.subtract(rhsValue))));
        }
    }

    public void multiply() {
        int rhs = this.stack.pop();
        int lhs = this.stack.pop();
        var rhsValue = (NumericValueSet) this.varsState.get(rhs).value();
        var lhsValue = (NumericValueSet) this.varsState.get(lhs).value();
        if (rhsValue.isOne()) {
            this.stack.push(lhs);
        } else if (lhsValue.isOne()) {
            this.stack.push(rhs);
        } else {
            this.stack.push(this.createNewVarEntry(new VarState(lhsValue.multiply(rhsValue))));
        }
    }

    public void divide() {
        int rhs = this.stack.pop();
        int lhs = this.stack.pop();
        var rhsValue = (NumericValueSet) this.varsState.get(rhs).value();
        var lhsValue = (NumericValueSet) this.varsState.get(lhs).value();
        if (rhsValue.isOne()) {
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
            return this.call(thisVar, method.getFixedCallAnalysis());
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
        int thisVar = this.stack.peek(method.getExecutable().getParameters().size());
        return this.call(thisVar, method.getFixedCallAnalysis());
    }

    public void box() {
        int value = this.stack.pop();
        IntValueSet valueSet = (IntValueSet) this.varsState.get(value).value();
        this.stack.push(this.createNewVarEntry(new VarState(new BoxedIntValueSet(Nullness.NON_NULL, valueSet, this.context))));
    }

    public void unbox() {
        int value = this.stack.pop();
        this.assertNonNull(value);
        BoxedIntValueSet boxedValue = (BoxedIntValueSet) this.varsState.get(value).value();
        this.stack.push(this.createNewVarEntry(new VarState(boxedValue.value())));
    }

    public void cast(TypeId newType) {
        int value = this.stack.pop();
        ValueSet valueSet = this.varsState.get(value).value();
        this.stack.push(this.createNewVarEntry(new VarState(valueSet.castTo(newType))));
    }

    public void throwException() {
        ObjectValueSet value = (ObjectValueSet) this.varsState.get(this.stack.pop()).value();
        this.activeException = value.getSupertype();
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

        int parameterCount = method.getOrderedParameterNames().size();

        // We don't know what happened to non-own fields
        this.resetTransitiveFields();

        // Extract the parameters
        List<Integer> parameters = new ArrayList<>(parameterCount);
        if (callee != -1) {
            // Instance methods have an implicit this parameter
            parameters.add(callee);
            for (int i = 1; i < method.getOrderedParameterNames().size(); i++) {
                parameters.add(this.stack.pop());
            }
        } else {
            for (int i = 0; i < method.getOrderedParameterNames().size(); i++) {
                parameters.add(this.stack.pop());
            }
        }

        // Pop the caller
        this.pop();

        List<EngineState> result = new ArrayList<>();
        returnState:
        for (MethodExitState exitState : method.getReturnStates()) {
            var precondition = exitState.getParameterPrecondition();

            // Check whether the preconditions apply
            for (int i = 0; i < parameterCount; i++) {
                var param = this.varsState.get(parameters.get(i)).value();
                if (!precondition.get(i).isCompatible(param)) {
                    continue returnState;
                }
            }

            // The preconditions apply, so fork the engine and narrow parameters down to the preconditions
            EngineState newState = this.fork();
            for (int i = 0; i < parameterCount; i++) {
                // newState.assertVarValue(parameters.get(i), precondition.get(i));
                var oldState = newState.varsState.get(parameters.get(i));
                var newValue = precondition.get(i).intersect(oldState.value());
                this.varsState.set(parameters.get(i), new VarState(newValue, oldState.relations()));
            }

            // Handle exit state
            if (exitState.getReturnValue() != null) {
                // Normal return
                var returnValue = newState.createNewVarEntry(new VarState(exitState.getReturnValue()));
                newState.stack.push(returnValue);
            } else {
                // Exception thrown
                newState.activeException = exitState.getThrownException();
            }

            result.add(newState);
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
            if (!value.isSupersetOf(oldState.value())) {
                throw new IllegalStateException(this.varsState.get(id).value() + " is not a superset of " + value);
            }
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
        // this.localsState.compute(relation.rhs(), (k, v) -> v.addRelation(new VarRelation(lhs, relation.relation().invert())));

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
                Objects.equals(initialParamValues, that.initialParamValues) &&
                Objects.equals(liveFields, that.liveFields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(varsState, stack, initialParamValues, liveFields);
    }

    @Override
    public String toString() {
        return "stack: " + this.stack
                + " fields: [" + this.liveFields.entrySet().stream()
                .map(e -> e.getKey() + ": $" + e.getValue())
                .collect(Collectors.joining(", "))
                + "] values: " + this.varsState;
    }
}
