package de.firemage.flork.flow.engine;

import de.firemage.flork.flow.BooleanValueSet;
import de.firemage.flork.flow.IntegerValueSet;
import de.firemage.flork.flow.MathUtil;
import de.firemage.flork.flow.ValueSet;
import spoon.reflect.reference.CtTypeReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class EngineState {
    private final Map<String, ValueSet> localsState;
    private final ValueStack stack;

    public EngineState(Map<String, ValueSet> localsState) {
        this.localsState = localsState;
        this.stack = new ValueStack();
    }

    public EngineState(EngineState other) {
        this.localsState = new HashMap<>(other.localsState);
        this.stack = new ValueStack(other.stack);
    }

    public EngineState fork() {
        return new EngineState(this);
    }

    public void createLocal(String name, CtTypeReference<?> type) {
        this.localsState.put(name, ValueSet.topForType(type));
    }

    public void pushBooleanLiteral(boolean value) {
        this.stack.push(new ConcreteStackValue(BooleanValueSet.of(value)));
    }

    public void pushIntLiteral(int value) {
        this.stack.push(new ConcreteStackValue(IntegerValueSet.ofRange(value, value)));
    }

    public void pushLocal(String name) {
        this.stack.push(new LocalRefStackValue(name));
    }

    public void storeLocal(String name) {
        this.localsState.put(name, getValueOf(this.stack.peek()));
    }

    public void pop() {
        this.stack.pop();
    }
    
    public ValueSet peek() {
        return getValueOf(this.stack.getFirst());
    }

    public boolean isStackEmpty() {
        return this.stack.isEmpty();
    }
    
    public void negate() {
        StackValue tos = this.stack.pop();
        IntegerValueSet tosValue = (IntegerValueSet) getValueOf(tos);
        this.stack.push(new ConcreteStackValue(tosValue.negate()));
    }
    
    public void add() {
        IntegerValueSet rhs = (IntegerValueSet) getValueOf(this.stack.pop());
        IntegerValueSet lhs = (IntegerValueSet) getValueOf(this.stack.pop());
        this.stack.push(new ConcreteStackValue(lhs.add(rhs)));
    }

    public void subtract() {
        IntegerValueSet rhs = (IntegerValueSet) getValueOf(this.stack.pop());
        IntegerValueSet lhs = (IntegerValueSet) getValueOf(this.stack.pop());
        this.stack.push(new ConcreteStackValue(lhs.subtract(rhs)));
    }

    public void not() {
        StackValue tos = this.stack.pop();
        BooleanValueSet tosValue = (BooleanValueSet) getValueOf(tos);
        this.stack.push(new ConcreteStackValue(tosValue.not()));
    }

    // This is not short circuit!
    public List<EngineState> and() {
        List<EngineState> additionalPaths = new ArrayList<>();

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

    public List<EngineState> lessThan() {
        return this.compareOp(false, false);
    }

    public List<EngineState> lessThanEquals() {
        return this.compareOp(true, false);
    }

    public List<EngineState> greaterThan() {
        return this.compareOp(false, true);
    }

    public List<EngineState> greaterThanEquals() {
        return this.compareOp(true, true);
    }
    
    public List<EngineState> compareOp(boolean equalsOk, boolean greaterThan) {
        List<EngineState> additionalPaths = new ArrayList<>();

        StackValue rhs = this.stack.pop();
        StackValue lhs = this.stack.pop();
        
        if (greaterThan) {
            var temp = rhs;
            rhs = lhs;
            lhs = temp;
        }

        IntegerValueSet lhsValue = (IntegerValueSet) getValueOf(lhs);
        IntegerValueSet rhsValue = (IntegerValueSet) getValueOf(rhs);
        
        if (lhsValue.min() > rhsValue.max() || (!equalsOk && lhsValue.min() == rhsValue.max())) {
            this.stack.push(new ConcreteStackValue(BooleanValueSet.of(false)));
        } else if (lhsValue.max() < rhsValue.min() || (equalsOk && lhsValue.max() == rhsValue.min())) {
            this.stack.push(new ConcreteStackValue(BooleanValueSet.of(true)));
        } else {
            // Second path: lhs >(=) b
            EngineState secondPath = this.fork();
            secondPath.stack.push(new ConcreteStackValue(BooleanValueSet.of(false)));
            secondPath.assertStackValue(lhs, lhsValue.splitAtAbove(equalsOk ? MathUtil.incSaturating(rhsValue.min()) : rhsValue.min()));
            secondPath.assertStackValue(rhs, rhsValue.splitAtBelow(equalsOk ? MathUtil.decSaturating(lhsValue.max()) : lhsValue.max()));
            additionalPaths.add(secondPath);

            this.stack.push(new ConcreteStackValue(BooleanValueSet.of(true)));
            this.assertStackValue(lhs, lhsValue.splitAtBelow(equalsOk ? rhsValue.max() : MathUtil.decSaturating(rhsValue.max())));
            this.assertStackValue(rhs, rhsValue.splitAtAbove(equalsOk ? lhsValue.min() : MathUtil.incSaturating(lhsValue.min())));
        }
        
        return additionalPaths;
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
            return this.localsState.get(((LocalRefStackValue) value).local());
        }
    }

    private StackValue assertStackValue(StackValue stackValue, ValueSet value) {
        if (!this.getValueOf(stackValue).isSupersetOf(value)) {
            throw new IllegalStateException(this.getValueOf(stackValue) + " is not a superset of " + value);
        } else if (stackValue instanceof LocalRefStackValue ref) {
            this.localsState.put(ref.local(), value);
            return stackValue;
        } else {
            return new ConcreteStackValue(value);
        }
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
