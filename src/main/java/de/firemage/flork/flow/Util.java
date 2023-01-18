package de.firemage.flork.flow;

import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Util {
    public static <K, V> Map<K, V> mergeMaps(Map<K, V> a, Map<K, V> b, BinaryOperator<V> mergeFunction) {
        return Stream.concat(a.entrySet().stream(), b.entrySet().stream()).collect(Collectors.toMap(Map.Entry::getKey,
            Map.Entry::getValue, mergeFunction));
    }

    public static <K, V> Map<K, V> mergeMapsFailIfNull(Map<K, V> a, Map<K, V> b, BinaryOperator<V> mergeFunction) {
        try {
            return Stream.concat(a.entrySet().stream(), b.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey,
                    Map.Entry::getValue, (x, y) -> {
                        var result = mergeFunction.apply(x, y);
                        if (result == null) {
                            throw new NullValueException();
                        }
                        return result;
                    }));
        } catch (NullValueException ex) {
            return null;
        }
    }
    
    private static class NullValueException extends RuntimeException { }
}
