package de.firemage.flork.flow.engine;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Iterator;

// Why tf does ArrayDeque not provide a usable equals implementation???
public class ValueStack extends ArrayDeque<StackValue> {
    public ValueStack() {
        super();
    }

    public ValueStack(Collection<StackValue> other) {
        super(other);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValueStack that = (ValueStack) o;
        Iterator<StackValue> thisIterator = this.iterator();
        Iterator<StackValue> thatIterator = that.iterator();
        while (thisIterator.hasNext() && thatIterator.hasNext()) {
            if (!thisIterator.next().equals(thatIterator.next())) {
                return false;
            }
        }
        return !this.iterator().hasNext() && !thatIterator.hasNext();
    }
}
