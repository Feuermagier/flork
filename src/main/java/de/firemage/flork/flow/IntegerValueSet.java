package de.firemage.flork.flow;

import java.util.ArrayList;
import java.util.List;

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

    public IntegerValueSet add(IntegerValueSet other, boolean subtract) {
        IntegerValueSet result = new IntegerValueSet(new ArrayList<>(this.intervals.size()));
        for (IntegerInterval myInterval : this.intervals) {
            for (IntegerInterval otherInterval : other.intervals) {
                int min = myInterval.min + (subtract ? -otherInterval.max : otherInterval.min);
                int max = myInterval.max + (subtract ? -otherInterval.min : otherInterval.max);
                if (max < min) {
                    // Overflow
                    result = result.merge(IntegerValueSet.ofRange(min, Integer.MAX_VALUE));
                    result = result.merge(IntegerValueSet.ofRange(Integer.MIN_VALUE, max));
                } else {
                    result = result.merge(IntegerValueSet.ofRange(min, max));
                }
            }
        }
        return result;
    }

    public record IntegerInterval(int min, int max) {
        public IntegerInterval {
            if (min > max) {
                throw new IllegalStateException("min > max");
            }
        }
    }
}
