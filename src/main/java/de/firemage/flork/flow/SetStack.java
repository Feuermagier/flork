package de.firemage.flork.flow;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public class SetStack<E> implements Iterable<Set<E>> {
    private final Deque<Set<E>> stack;

    public SetStack(int initialCapacity) {
        this.stack = new ArrayDeque<>(initialCapacity);
    }

    public SetStack(SetStack<E> other) {
        this.stack = new ArrayDeque<>(other.stack);
        for (var set : other.stack) {
            this.stack.add(new HashSet<>(set));
        }
    }

    public void pushEmpty() {
        this.stack.push(new HashSet<>());
    }

    public Set<E> pop() {
        return this.stack.pop();
    }

    public Set<E> peek() {
        return this.stack.peek();
    }

    public void addToLast(E element) {
        this.stack.peek().add(element);
    }

    public void addAllToLast(Collection<E> element) {
        this.stack.peek().addAll(element);
        var x = true;
        Consumer<Integer> p = (y) -> {
            if (x) {
                System.out.println(y);
            }
        };
    }

    public int size() {
        return this.stack.size();
    }

    public boolean lastContains(E element) {
        return this.stack.peek().contains(element);
    }

    @Override
    public Iterator<Set<E>> iterator() {
        return this.stack.iterator();
    }
}
