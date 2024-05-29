package de.firemage.flork.flow.engine;

import de.firemage.flork.flow.BooleanStatus;
import de.firemage.flork.flow.MethodExitState;
import de.firemage.flork.flow.value.ObjectValueSet;
import de.firemage.flork.flow.value.ValueSet;

import java.util.HashMap;
import java.util.Map;

public class CallMapper {
    private final EngineState state;
    private final Map<VarId, Integer> knownValues;
    private final MethodExitState returnState;

    public CallMapper(EngineState state, MethodExitState exitState, Map<VarId, Integer> knownValues) {
        // TODO(performance) maybe delay forking until we know that the exit state's preconditions are fulfilled
        this.state = state.fork();
        this.returnState = exitState;
        this.knownValues = new HashMap<>(knownValues);
    }

    public EngineState map() {
        // Preconditions
        for (var requiredValue : returnState.initialState().entrySet()) {
            // TODO add relations here
            int ownState = this.assertFieldExists(requiredValue.getKey(), knownValues);
            // We already know something about this value!
            VarState ownValue = this.state.varsState.get(ownState);
            if (requiredValue.getValue().value().isSupersetOf(ownValue.value())) {
                // Nothing to do; our value is more specific than the one required
            } else if (ownValue.value().isSupersetOf(requiredValue.getValue().value())) {
                // Our value is more general. Try narrowing it down to the one required
                for (var relation : ownValue.relations()) {
                    if (requiredValue.getValue().value().fulfillsRelation(this.state.varsState.get(relation.rhs()).value(), relation.relation()) != BooleanStatus.NEVER) {
                        return null;
                    }
                }
                // We don't clash with any constraints
                this.state.assertVarValue(ownState, requiredValue.getValue().value());
            } else {
                // The values are disjoint -> the precondition of this state is not met
                return null;
            }
        }

        // Postconditions
        this.state.stack.push(this.state.createNewVarEntry(new VarState(returnState.value())));

        // Reset fields
        this.state.resetAllFields();

        return this.state;
    }

    private int assertFieldExists(VarId field, Map<VarId, Integer> knownValues) {
        if (knownValues.containsKey(field)) {
            return knownValues.get(field);
        }

        if (field.parent() != null) {
            int parentId = this.assertFieldExists(field.parent(), knownValues);
            ObjectValueSet parentValue = (ObjectValueSet) this.state.varsState.get(parentId).value();
            ValueSet value = ValueSet.topForType(parentValue.getFieldType(field.fieldName()), this.state.context);
            int valueId = this.state.createNewVarEntry(new VarState(value));
            FieldId fieldId = FieldId.forField(parentId, field.fieldName());
            SSAVarId ssa = SSAVarId.forFresh(fieldId);
            this.state.liveFields.put(fieldId, ssa);
            this.state.fieldValues.put(ssa, valueId);

            knownValues.put(field, valueId);
            return valueId;
        } else {
            throw new IllegalStateException("Field has no parent");
        }
    }
}
