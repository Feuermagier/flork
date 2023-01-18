package de.firemage.flork.flow.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.stream.Collectors;

// Why tf does ArrayDeque not provide a usable equals implementation???
public class ValueStack {
    private final ArrayList<StackValue> values;
    private int head; // Points to the next free slot
    
    public ValueStack() {
        this.values = new ArrayList<>();
        this.head = 0;
    }

    public ValueStack(ValueStack other) {
        this.values = new ArrayList<>(other.values);
        this.head = other.head;
    }
    
    public StackValue peek() {
        return this.values.get(this.head - 1);
    }
    
    public StackValue peek(int offset) {
        return this.values.get(this.head - 1 - offset);
    }
    
    public void push(StackValue value) {
        if (this.head < this.values.size()) {
            this.values.set(this.head, value);
        } else {
            this.values.add(value);
        }
        this.head++;
    }
    
    public void overwrite(StackValue value, int offset) {
        this.values.set(this.head - 1 - offset, value);
    }
    
    public StackValue pop() {
        this.head--;
        return this.values.get(this.head);
    }
    
    public boolean isEmpty() {
        return this.head == 0;
    }
    
    public void clear() {
        this.values.clear();
        this.head = 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValueStack that = (ValueStack) o;
        return this.values.equals(that.values) && this.head == that.head;
    }

    @Override
    public int hashCode() {
        return Objects.hash(values, head);
    }

    @Override
    public String toString() {
        return "[" + this.values.stream().limit(this.head).map(Object::toString).collect(Collectors.joining(", ")) + " ^]";
    }
}
