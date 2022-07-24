package de.firemage.flork.flow;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;

public record IntegerValueSet(List<IntegerInterval> intervals) implements ValueSet {

    public static IntegerValueSet top() {
        return IntegerValueSet.ofRange(Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    public static IntegerValueSet ofSingle(int value) {
        return new IntegerValueSet(List.of(new IntegerInterval(value, value)));
    }

    public static IntegerValueSet ofRange(int min, int max) {
        return new IntegerValueSet(List.of(new IntegerInterval(min, max)));
    }

    private static void addInterval(List<IntegerInterval> intervals, IntegerInterval newInterval) {
        int min = newInterval.min;
        int max = newInterval.max;
        for (int i = 0; i < intervals.size(); i++) {
            IntegerInterval interval = intervals.get(i);
            if (interval.max < min) {
                // Old:      |--|
                // New: |--|
                continue;
            } else if (interval.min > max) {
                intervals.add(i, new IntegerInterval(min, max));
                return;
            } else if (interval.min <= min && interval.max >= max) {
                // Old:  |-----|
                // New:    |--|
                return;
            } else if (interval.min > min && interval.max >= max) {
                // Old:    |----|
                // New:  |---|
                intervals.set(i, new IntegerInterval(min, interval.max));
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
                intervals.set(i, new IntegerInterval(Math.min(min, interval.min), max));
                return;
            }
        }
        // If we arrive here, there is still something left to add
        intervals.add(new IntegerInterval(min, max));
    }

    public int min() {
        if (this.intervals.isEmpty()) {
            return Integer.MIN_VALUE;
        } else {
            return this.intervals.get(0).min;
        }
    }

    public int max() {
        if (this.intervals.isEmpty()) {
            return Integer.MAX_VALUE;
        } else {
            return this.intervals.get(this.intervals.size() - 1).max;
        }
    }

    public IntegerValueSet splitAtBelow(int max) {
        if (max >= this.max()) {
            return this;
        } else {
            List<IntegerInterval> result = new ArrayList<>(this.intervals.size());
            for (IntegerInterval interval : this.intervals) {
                if (interval.max <= max) {
                    result.add(interval);
                } else if (interval.min <= max) {
                    result.add(new IntegerInterval(interval.min, max));
                } else {
                    break;
                }
            }
            return new IntegerValueSet(result);
        }
    }

    public IntegerValueSet splitAtAbove(int min) {
        if (min <= this.min()) {
            return this;
        } else {
            List<IntegerInterval> result = new ArrayList<>(this.intervals.size());
            for (IntegerInterval interval : this.intervals) {
                if (interval.min >= min) {
                    result.add(interval);
                } else if (interval.max >= min) {
                    result.add(new IntegerInterval(min, interval.max));
                } else {
                    continue;
                }
            }
            return new IntegerValueSet(result);
        }
    }

    @Override
    public IntegerValueSet merge(ValueSet o) {
        List<IntegerInterval> result = new ArrayList<>(this.intervals);
        for (IntegerInterval newInterval : ((IntegerValueSet) o).intervals) {
            addInterval(result, newInterval);
        }
        return new IntegerValueSet(result);
        /*
        IntegerValueSet other = (IntegerValueSet) o;

        ArrayList<IntegerInterval> result = new ArrayList<>(Math.max(this.intervals.size(), other.intervals.size()));
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;

        int i = 0;
        int j = 0;
        while (i < this.intervals.size() && j < other.intervals.size()) {
            IntegerInterval myInterval = this.intervals.get(i);
            IntegerInterval otherInterval = other.intervals.get(j);
            // Make sure that myInterval.min < otherInterval.min
            if (myInterval.min > otherInterval.min) {
                otherInterval = myInterval;
            }
            
            if (myInterval.max < min || myInterval.min > max) {
                if (min <= max) result.add(new IntegerInterval(min, max));
                min = myInterval.min;
                max = myInterval.max;
            } else {
                min = Math.min(min, myInterval.min);
                max = Math.max(max, myInterval.max);
            }

            if (otherInterval.max < min || otherInterval.min > max) {
                if (min <= max) result.add(new IntegerInterval(min, max));
                min = otherInterval.min;
                max = otherInterval.max;
            } else {
                min = Math.min(min, otherInterval.min);
                max = Math.max(max, otherInterval.max);
            }
            i++;
            j++;
        }
        if (min <= max) {
            result.add(new IntegerInterval(min, max));
        }
        
        for (; i < this.intervals.size(); i++) {
            if (this.intervals.get(i).min <= max) {
                result.add(this.intervals.get(i));
            }
        }
        
        for (; j < other.intervals.size(); j++) {
            result.add(other.intervals.get(j));
        }
        
        return new IntegerValueSet(result);
         */
    }

    @Override
    public boolean isSupersetOf(ValueSet o) {
        IntegerValueSet other = (IntegerValueSet) o;
        if (other.intervals.isEmpty()) {
            return true;
        }
        int i = 0;
        outer:
        for (IntegerInterval interval : other.intervals) {
            while (i < this.intervals.size()) {
                IntegerInterval myInterval = this.intervals.get(i);
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

    public boolean containsValue(int value) {
        for (IntegerInterval interval : this.intervals) {
            if (value >= interval.min && value <= interval.max) {
                return true;
            }
        }
        return false;
    }

    public IntegerValueSet negate() {
        if (this.intervals.size() == 1
            && this.intervals.get(0).min == Integer.MIN_VALUE
            && this.intervals.get(0).max == Integer.MAX_VALUE) {
            return this;
        }
        List<IntegerInterval> result = new ArrayList<>(this.intervals.size());
        for (int i = this.intervals.size() - 1; i >= 0; i--) {
            IntegerInterval interval = this.intervals.get(i);
            if (interval.min != Integer.MIN_VALUE) {
                result.add(new IntegerInterval(-interval.max, -interval.min));
            } else if (interval.max != Integer.MIN_VALUE) {
                result.add(new IntegerInterval(-interval.max, Integer.MAX_VALUE));
            }
        }
        IntegerValueSet resultSet = new IntegerValueSet(result);
        if (this.intervals.get(0).min == Integer.MIN_VALUE) {
            resultSet = resultSet.merge(IntegerValueSet.ofSingle(Integer.MIN_VALUE));
        }
        return resultSet;
    }

    public IntegerValueSet add(IntegerValueSet other) {
        return handleBiFunc(other, (a, b) -> new FuncResult(a + b, MathUtil.checkAddForOverflow(a, b)));
    }

    public IntegerValueSet subtract(IntegerValueSet other) {
        return handleBiFunc(other, (a, b) -> new FuncResult(a - b, MathUtil.checkSubForOverflow(a, b)));
    }
    
    private IntegerValueSet handleBiFunc(IntegerValueSet other, BiFunction<Integer, Integer, FuncResult> func) {
        ArrayList<IntegerInterval> result = new ArrayList<>(this.intervals.size());
        for (IntegerInterval myInterval : this.intervals) {
            for (IntegerInterval otherInterval : other.intervals) {
                handleBiFuncOnInterval(myInterval, otherInterval, func, result);
            }
        }
        return new IntegerValueSet(result);
    }

    private void handleBiFuncOnInterval(IntegerInterval lhs, IntegerInterval rhs,
                                         BiFunction<Integer, Integer, FuncResult> func,
                                         ArrayList<IntegerInterval> result) {
        // Find the min and max values
        FuncResult minMin = func.apply(lhs.min, rhs.min);
        FuncResult minMax = func.apply(lhs.min, rhs.max);
        FuncResult maxMin = func.apply(lhs.max, rhs.min);
        FuncResult maxMax = func.apply(lhs.max, rhs.max);
        
        FuncResult min = minMin.min(minMax).min(maxMin).min(maxMax);
        FuncResult max = minMin.max(minMax).max(maxMin).max(maxMax);

        if (min.overflow == MathUtil.OverflowType.NONE && max.overflow == MathUtil.OverflowType.NONE) {
            addInterval(result, new IntegerInterval(min.value, max.value));
        } else if (min.overflow == MathUtil.OverflowType.POS_TO_NEG || max.overflow == MathUtil.OverflowType.NEG_TO_POS) {
            // TODO report this guaranteed overflow
            addInterval(result, new IntegerInterval(min.value, max.value));
        } else if (min.overflow == MathUtil.OverflowType.NEG_TO_POS && max.overflow == MathUtil.OverflowType.POS_TO_NEG) {
            // Anything is possible
            addInterval(result, new IntegerInterval(Integer.MIN_VALUE, Integer.MAX_VALUE));
        } else {
            addInterval(result, new IntegerInterval(Integer.MIN_VALUE, max.value));
            addInterval(result, new IntegerInterval(min.value, Integer.MAX_VALUE));
        }
    }

    public record IntegerInterval(int min, int max) {
        public IntegerInterval {
            if (min > max) {
                throw new IllegalStateException("min > max");
            }
        }
    }

    record FuncResult(int value, MathUtil.OverflowType overflow) {
        public FuncResult min(FuncResult other) {
            int relation = this.overflow.compareTo(other.overflow);
            if (relation == 0) {
                if (this.overflow == MathUtil.OverflowType.NEG_TO_POS) {
                    return this.value <= other.value ? other : this;
                } else {
                    return this.value <= other.value ? this : other;
                }
            } else if (relation < 0) {
                return this;
            } else {
                return other;
            }
        }

        public FuncResult max(FuncResult other) {
            int relation = this.overflow.compareTo(other.overflow);
            if (relation == 0) {
                if (this.overflow == MathUtil.OverflowType.NEG_TO_POS) {
                    return this.value >= other.value ? other : this;
                } else {
                    return this.value >= other.value ? this : other;
                }
            } else if (relation < 0) {
                return other;
            } else {
                return this;
            }
        }
    }
}
