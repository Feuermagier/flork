package de.firemage.flork.flow.value;

import de.firemage.flork.flow.BooleanStatus;
import de.firemage.flork.flow.MathUtil;
import de.firemage.flork.flow.engine.Relation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * This class assumes that all primitive operations (+, -, *, /, %) result in a value that fits in a long
 * without overflowing.
 * Therefore, you cannot use this class for longs.
 * This class is immutable, including all its fields!
 */
public final class IntValueSet implements ValueSet {
    private final List<IntInterval> intervals;
    private final int bits;
    private final long typeMin;
    private final long typeMax;

    private IntValueSet(int bits, List<IntInterval> intervals) {
        this.bits = bits;
        this.typeMin = -(1L << (bits - 1));
        this.typeMax = (1L << (bits - 1)) - 1;
        this.intervals = Collections.unmodifiableList(intervals); // Just to make sure that nobody ever modifies that list
    }

    public static IntValueSet topForInt() {
        return IntValueSet.ofIntRange(Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    public static IntValueSet ofIntSingle(long value) {
        return ofIntRange(value, value);
    }

    public static IntValueSet ofIntRange(long min, long max) {
        return new IntValueSet(32, List.of(new IntInterval(min, max)));
    }

    private static IntValueSet createTopLike(IntValueSet set) {
        return new IntValueSet(set.bits, List.of(new IntInterval(set.typeMin, set.typeMax)));
    }

    private static IntValueSet createSingleLike(IntValueSet set, long value) {
        return new IntValueSet(set.bits, List.of(new IntInterval(value, value)));
    }

    private static void addInterval(List<IntInterval> intervals, IntInterval newInterval) {
        long min = newInterval.min;
        long max = newInterval.max;
        for (int i = 0; i < intervals.size(); i++) {
            IntInterval interval = intervals.get(i);
            if (min != Long.MIN_VALUE && interval.max < min - 1) {
                // Old:      |--|
                // New: |--|
                continue;
            } else if (max != Long.MAX_VALUE && interval.min > max + 1) {
                intervals.add(i, new IntInterval(min, max));
                return;
            } else if (interval.min <= min && interval.max >= max) {
                // Old:  |-----|
                // New:    |--|
                return;
            } else if (interval.min > min && interval.max >= max) {
                // Old:    |----|
                // New:  |---|
                intervals.set(i, new IntInterval(min, interval.max));
                return;
            } else if (i < intervals.size() - 1 && intervals.get(i + 1).min <= newInterval.max) {
                // Old: |-----|  |--
                // New:     |-------
                // Or
                // Old:   |-----|  |--
                // New: |-------------
                // Just remove this interval, the next interval will include the new and therefore also the old interval
                min = Math.min(min, interval.min);
                intervals.remove(i);
                i--;
            } else {
                // Old: |-----|
                // New:    |-----|
                intervals.set(i, new IntInterval(Math.min(min, interval.min), max));
                return;
            }
        }
        // If we arrive here, there is still something left to add
        intervals.add(new IntInterval(min, max));
    }

    @Override
    public IntValueSet merge(ValueSet o) {
        IntValueSet other = (IntValueSet) o;
        List<IntInterval> result = new ArrayList<>(this.intervals);
        for (IntInterval newInterval : other.intervals) {
            addInterval(result, newInterval);
        }
        return new IntValueSet(this.bits, result);
    }

    @Override
    public IntValueSet intersect(ValueSet o) {
        IntValueSet other = (IntValueSet) o; 
        List<IntInterval> result = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < this.intervals.size() && j < other.intervals.size()) {
            IntInterval a = this.intervals.get(i);
            IntInterval b = other.intervals.get(j);
            if (b.max >= a.min && a.max >= b.min) {
                addInterval(result, new IntInterval(Math.max(a.min, b.min), Math.min(a.max, b.max)));
            }
            if (a.max <= b.max) {
                i++;
            } else {
                j++;
            }
        }
        return new IntValueSet(this.bits, result);
    }

    public IntValueSet symmetricDifference(ValueSet o) {
        IntValueSet other = (IntValueSet) o;
        if (this.isTop() && other.isTop()) {
            return new IntValueSet(this.bits, List.of());
        }
        List<IntInterval> result = new ArrayList<>();
        int i = 0;
        int j = 0;
        long lastMin = Long.MIN_VALUE;
        while (i < this.intervals.size() && j < other.intervals.size()) {
            IntInterval a = this.intervals.get(i);
            IntInterval b = other.intervals.get(j);
            long min = Math.max(Math.min(a.min, b.min), lastMin);
            long max = Math.max(a.min, b.min);
            if (min < max) {
                addInterval(result, new IntInterval(min, max - 1));
            }
            lastMin = Math.min(a.max, b.max) + 1;
            if (a.max <= b.max) {
                i++;
            } else {
                j++;
            }
        }
        for (; i < this.intervals.size(); i++) {
            IntInterval interval = this.intervals.get(i);
            if (interval.max >= lastMin) {
                addInterval(result, new IntInterval(Math.max(interval.min, lastMin), interval.max));
            }
        }
        for (; j < other.intervals.size(); j++) {
            IntInterval interval = other.intervals.get(j);
            if (interval.max >= lastMin) {
                addInterval(result, new IntInterval(Math.max(interval.min, lastMin), interval.max));
            }
        }
        return new IntValueSet(this.bits, result);
    }

    @Override
    public boolean isSupersetOf(ValueSet o) {
        IntValueSet other = (IntValueSet) o;
        if (other.intervals.isEmpty()) {
            return true;
        }
        int i = 0;
        outer:
        for (IntInterval interval : other.intervals) {
            while (i < this.intervals.size()) {
                IntInterval myInterval = this.intervals.get(i);
                if (myInterval.min <= interval.min) {
                    if (myInterval.max >= interval.max) {
                        continue outer;
                    } else {
                        return false;
                    }
                }
                i++;
            }
            return false;
        }
        return true;
    }

    @Override
    public boolean isEmpty() {
        return this.intervals.isEmpty();
    }

    @Override
    public BooleanStatus fulfillsRelation(ValueSet o, Relation relation) {
        IntValueSet other = (IntValueSet) o;
        return switch (relation) {
            case EQUAL -> this.hasCommonValue(other) ? BooleanStatus.SOMETIMES : BooleanStatus.NEVER;
            case NOT_EQUAL -> this.hasCommonValue(other) ? BooleanStatus.SOMETIMES : BooleanStatus.ALWAYS;
            case LESS_THAN -> this.max() < other.min() ? BooleanStatus.ALWAYS :
                (this.min() < other.max() ? BooleanStatus.SOMETIMES : BooleanStatus.NEVER);
            case LESS_THAN_EQUAL -> this.max() <= other.min() ? BooleanStatus.ALWAYS :
                (this.min() <= other.max() ? BooleanStatus.SOMETIMES : BooleanStatus.NEVER);
            case GREATER_THAN -> this.min() > other.max() ? BooleanStatus.ALWAYS :
                (this.max() > other.min() ? BooleanStatus.SOMETIMES : BooleanStatus.NEVER);
            case GREATER_THAN_EQUAL -> this.min() >= other.max() ? BooleanStatus.ALWAYS :
                (this.max() >= other.min() ? BooleanStatus.SOMETIMES : BooleanStatus.NEVER);
        };
    }

    @Override
    public IntValueSet removeNotFulfillingValues(ValueSet o, Relation relation) {
        IntValueSet other = (IntValueSet) o;
        return switch (relation) {
            case LESS_THAN -> this.splitAtBelow(MathUtil.decSaturating(other.max()));
            case LESS_THAN_EQUAL -> this.splitAtBelow(other.max());
            case GREATER_THAN -> this.splitAtAbove(MathUtil.incSaturating(other.min()));
            case GREATER_THAN_EQUAL -> this.splitAtAbove(other.min());
            case EQUAL -> this.intersect(other);
            case NOT_EQUAL -> this;
        };
    }

    public boolean isTop() {
        return this.intervals.size() == 1
            && this.intervals.get(0).min == this.typeMin
            && this.intervals.get(0).max == this.typeMax;
    }

    public boolean isSingle(long value) {
        return this.intervals.size() == 1
            && this.intervals.get(0).min == value
            && this.intervals.get(0).max == value;
    }

    public long min() {
        if (this.intervals.isEmpty()) {
            return this.typeMin;
        } else {
            return this.intervals.get(0).min;
        }
    }

    public long max() {
        if (this.intervals.isEmpty()) {
            return this.typeMax;
        } else {
            return this.intervals.get(this.intervals.size() - 1).max;
        }
    }

    public IntValueSet splitAtAbove(long min) {
        if (min <= this.min()) {
            return this;
        } else {
            List<IntInterval> result = new ArrayList<>(this.intervals.size());
            for (IntInterval interval : this.intervals) {
                if (interval.min >= min) {
                    result.add(interval);
                } else if (interval.max >= min) {
                    result.add(new IntInterval(min, interval.max));
                } else {
                    continue;
                }
            }
            return new IntValueSet(this.bits, result);
        }
    }

    public IntValueSet splitAtBelow(long max) {
        if (max >= this.max()) {
            return this;
        } else {
            List<IntInterval> result = new ArrayList<>(this.intervals.size());
            for (IntInterval interval : this.intervals) {
                if (interval.max <= max) {
                    result.add(interval);
                } else if (interval.min <= max) {
                    result.add(new IntInterval(interval.min, max));
                } else {
                    break;
                }
            }
            return new IntValueSet(this.bits, result);
        }
    }

    public IntValueSet negate() {
        if (this.isTop()) {
            return this;
        }
        List<IntInterval> result = new ArrayList<>(this.intervals.size());
        for (int i = this.intervals.size() - 1; i >= 0; i--) {
            IntInterval interval = this.intervals.get(i);
            if (interval.min != Integer.MIN_VALUE) {
                result.add(new IntInterval(-interval.max, -interval.min));
            } else if (interval.max != Integer.MIN_VALUE) {
                result.add(new IntInterval(-interval.max, Integer.MAX_VALUE));
            }
        }
        IntValueSet resultSet = new IntValueSet(this.bits, result);
        if (this.intervals.get(0).min == Integer.MIN_VALUE) {
            resultSet = resultSet.merge(IntValueSet.ofIntSingle(this.typeMin));
        }
        return resultSet;
    }

    public IntValueSet add(IntValueSet other) {
        if (this.isTop() && other.isTop()) {
            return this;
        }
        List<IntInterval> result = new ArrayList<>(this.intervals.size());
        for (IntInterval a : this.intervals) {
            for (IntInterval b : other.intervals) {
                long min = a.min + b.min;
                long max = a.max + b.max;
                if (min < this.typeMin && max > this.typeMax) {
                    return createTopLike(this);
                } else if (max < this.typeMin || min > this.typeMax) {
                    // Overflows always
                    // TODO raise a warning
                    addInterval(result, new IntInterval(overflowValue(min), overflowValue(max)));
                } else if (max > this.typeMax || min < this.typeMin) {
                    // Only min or max overflows
                    addInterval(result, new IntInterval(this.typeMin, overflowValue(max)));
                    addInterval(result, new IntInterval(overflowValue(min), this.typeMax));
                } else {
                    addInterval(result, new IntInterval(overflowValue(min), overflowValue(max)));
                }
            }
        }
        return new IntValueSet(this.bits, result);
    }

    public IntValueSet subtract(IntValueSet other) {
        if (this.isTop() && other.isTop()) {
            return this;
        }
        List<IntInterval> result = new ArrayList<>(this.intervals.size());
        for (IntInterval a : this.intervals) {
            for (IntInterval b : other.intervals) {
                long min = a.min - b.max;
                long max = a.max - b.min;
                if (min < this.typeMin && max > this.typeMax) {
                    return createTopLike(this);
                } else if (max < this.typeMin || min > this.typeMax) {
                    // Overflows always
                    // TODO raise a warning
                    addInterval(result, new IntInterval(overflowValue(min), overflowValue(max)));
                } else if (max > this.typeMax || min < this.typeMin) {
                    // Only min or max overflows
                    addInterval(result, new IntInterval(this.typeMin, overflowValue(max)));
                    addInterval(result, new IntInterval(overflowValue(min), this.typeMax));
                } else {
                    addInterval(result, new IntInterval(overflowValue(min), overflowValue(max)));
                }
            }
        }
        return new IntValueSet(this.bits, result);
    }

    public IntValueSet multiply(IntValueSet other) {
        if (this.isTop() && other.isTop()) return this;
        if (this.isSingle(0) || other.isSingle(0)) return IntValueSet.createSingleLike(this, 0);
        if (this.isSingle(1)) return other;
        if (other.isSingle(1)) return this;
        if (this.isSingle(-1)) return other.negate();
        if (other.isSingle(-1)) return this.negate();

        List<IntInterval> result = new ArrayList<>(this.intervals.size());
        for (IntInterval a : this.intervals) {
            for (IntInterval b : other.intervals) {
                long min = MathUtil.min4(a.min * b.min, a.min * b.max, a.max * b.min, a.max * b.max);
                long max = MathUtil.max4(a.min * b.min, a.min * b.max, a.max * b.min, a.max * b.max);
                if (Math.abs(max - min) > this.typeMax - this.typeMin) {
                    return IntValueSet.createTopLike(this);
                } else if (max < this.typeMin || min > this.typeMax) {
                    // Overflows always
                    // TODO raise a warning
                    addInterval(result, new IntInterval(overflowValue(min), overflowValue(max)));
                } else if (max > this.typeMax || min < this.typeMin) {
                    // Only min or max overflows
                    addInterval(result, new IntInterval(this.typeMin, overflowValue(max)));
                    addInterval(result, new IntInterval(overflowValue(min), this.typeMax));
                } else {
                    addInterval(result, new IntInterval(overflowValue(min), overflowValue(max)));
                }
            }
        }
        return new IntValueSet(this.bits, result);
    }

    public IntValueSet divide(IntValueSet other) {
        if (this.isTop() && other.isTop()) return this;
        if (other.isSingle(1)) return this;
        if (other.isSingle(-1)) return this.negate();

        List<IntInterval> result = new ArrayList<>(this.intervals.size());
        for (IntInterval a : this.intervals) {
            for (IntInterval b : other.intervals) {
                long min = MathUtil.min4(a.min / b.min, a.min / b.max, a.max / b.min, a.max / b.max);
                long max = MathUtil.max4(a.min / b.min, a.min / b.max, a.max / b.min, a.max / b.max);
                if (min < this.typeMin) {
                    addInterval(result, new IntInterval(0, max));
                    addInterval(result, new IntInterval(this.typeMax, this.typeMax));
                } else {
                    addInterval(result, new IntInterval(min, max));
                }
            }
        }
        return new IntValueSet(this.bits, result);
    }

    public boolean hasCommonValue(IntValueSet other) {
        int i = 0;
        int j = 0;
        while (i < this.intervals.size() && j < other.intervals.size()) {
            IntInterval a = this.intervals.get(i);
            IntInterval b = other.intervals.get(j);
            if (a.max <= b.max) {
                if (a.max >= b.min) {
                    // a: -----|
                    // b:   |----|
                    return true;
                } else {
                    // a: ---|
                    // b:      |----|
                    i++;
                }
            } else {
                if (b.max >= a.min) {
                    // a:   |----|
                    // b: ----|
                    return true;
                } else {
                    // a:     |----|
                    // b: --|
                    j++;
                }
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "{" + this.intervals.stream().map(IntInterval::toString).collect(Collectors.joining(", "))
            .replace(String.valueOf(this.typeMin), "MIN")
            .replace("[" + this.typeMax, "[MAX")
            .replace(this.typeMax + "]", "MAX]") + "}";
    }

    private long overflowValue(long value) {
        if (value > this.typeMax) {
            return overflowValue(value - this.typeMax + this.typeMin - 1);
        } else if (value < this.typeMin) {
            return overflowValue(value - this.typeMin + this.typeMax + 1);
        } else {
            return value;
        }
    }

    public record IntInterval(long min, long max) {
        public IntInterval {
            if (min > max) {
                throw new IllegalStateException("min > max");
            }
        }

        @Override
        public String toString() {
            return "[" + this.min + ", " + this.max + "]";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IntValueSet that = (IntValueSet) o;
        return bits == that.bits && intervals.equals(that.intervals);
    }

    @Override
    public int hashCode() {
        return Objects.hash(intervals, bits);
    }
}
