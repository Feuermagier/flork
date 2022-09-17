package de.firemage.flork.flow;

import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableWrite;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class VarUtil {
    private static int i;
    private VarUtil() {}
    
    private static Stream<String> streamOverwrittenLocals(CtStatement statement) {
        if (statement instanceof CtBlock<?> block) {
            return block.getStatements().stream().flatMap(VarUtil::streamOverwrittenLocals);
        } else if (statement instanceof CtVariableWrite<?> write) {
            throw new NullPointerException(String.valueOf(++i));
        }
    }
}
