package de.firemage.flork.flow.analysis;

import de.firemage.flork.flow.CachedMethod;
import de.firemage.flork.flow.FlowContext;
import de.firemage.flork.flow.MethodExitState;
import de.firemage.flork.flow.TypeId;
import de.firemage.flork.flow.engine.FlowEngine;
import de.firemage.flork.flow.engine.Relation;
import de.firemage.flork.flow.value.BooleanValueSet;
import de.firemage.flork.flow.value.IntValueSet;
import de.firemage.flork.flow.value.Nullness;
import de.firemage.flork.flow.value.ObjectValueSet;
import de.firemage.flork.flow.value.VoidValue;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtIf;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtThisAccess;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.code.CtVariableWrite;
import spoon.reflect.code.CtWhile;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.reference.CtLocalVariableReference;
import spoon.reflect.reference.CtParameterReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FlowMethodAnalysis implements MethodAnalysis {
    private final FlowContext context;
    private final CachedMethod method;
    private final List<String> parameterNames;
    private final List<MethodExitState> returnStates;
    private final boolean effectivelyVoid;

    private FlowMethodAnalysis(CachedMethod method, CtExecutable<?> executable, FlowContext context) {
        this.context = context;
        this.method = method;
        this.returnStates = new ArrayList<>();
        this.parameterNames = new ArrayList<>();
        this.effectivelyVoid = executable.getType().getSimpleName().equals("void");

        // This pointer
        Optional<ObjectValueSet> thisPointer = this.method.getThisType().map(t -> ObjectValueSet.forUnconstrainedType(Nullness.NON_NULL, t, context));

        if (thisPointer.isPresent()) {
            parameterNames.add("this"); // this is the first parameter
        }

        // Other parameters

        for (CtParameter<?> parameter : executable.getParameters()) {
            parameterNames.add(parameter.getSimpleName());
        }

        this.context.increaseIndentation();
        this.context.log("=============== " + this.method.getName() + " ===============");

        FlowEngine engine = new FlowEngine(thisPointer.orElse(null), executable.getParameters(), this.context);
        analyzeBlock(executable.getBody(), engine);
        if (!engine.isStackEmpty()) {
            throw new IllegalStateException("Stack is not empty after end of method");
        }
        if (!engine.isImpossibleState()) {
            if (this.effectivelyVoid) {
                this.returnStates.addAll(buildExitStates(engine));
            } else if (this.method.isConstructor()) {
                engine.pushThis();
                this.returnStates.addAll(buildExitStates(engine));
            } else {
                throw new IllegalStateException("Missing final return in a non-void method");
            }
        }

        this.context.log("================== " + this.method.getName() + " completed ==================");
        this.context.decreaseIndentation();
    }

    public static MethodAnalysis analyzeMethod(CachedMethod method, CtExecutable<?> executable, FlowContext context) {
        return new FlowMethodAnalysis(method, executable, context);
    }

    @Override
    public CachedMethod getMethod() {
        return this.method;
    }

    @Override
    public List<MethodExitState> getReturnStates() {
        return this.returnStates;
    }

    @Override
    public List<String> getOrderedParameterNames() {
        return this.parameterNames;
    }

    private void analyzeBlock(CtBlock<?> block, FlowEngine engine) {
        for (CtStatement statement : block.getStatements()) {
            analyzeStatement(statement, engine);
        }
    }

    private void analyzeStatement(CtStatement statement, FlowEngine engine) {
        if (statement instanceof CtExpression<?> expression) {
            analyzeExpression(expression, engine);
            engine.pop();
        } else if (statement instanceof CtLocalVariable<?> localDefinition) {
            engine.createLocal(localDefinition.getSimpleName(), new TypeId(localDefinition.getType()));
            if (localDefinition.getAssignment() != null) {
                analyzeExpression(localDefinition.getAssignment(), engine);
                engine.storeLocal(localDefinition.getSimpleName());
                engine.pop();
            }
        } else if (statement instanceof CtIf ifStmt) {
            analyzeIf(ifStmt, engine);
        } else if (statement instanceof CtBlock<?> block) {
            analyzeBlock(block, engine);
        } else if (statement instanceof CtReturn<?> ret) {
            if (ret.getReturnedExpression() == null) {
                if (this.method.isConstructor()) {
                    engine.pushThis();
                } else {
                    engine.pushValue(VoidValue.getInstance());
                }
            } else {
                analyzeExpression(ret.getReturnedExpression(), engine);
            }
            this.returnStates.addAll(buildExitStates(engine));
            engine.pop();
            if (!engine.isStackEmpty()) {
                throw new IllegalStateException("Stack is not empty");
            }
            engine.clear();
        } else {
            throw new UnsupportedOperationException(statement.getClass().getName());
        }

        // Clear the stack after every statement
        engine.clearStack();
    }

    private void analyzeExpression(CtExpression<?> expression, FlowEngine engine) {
        if (expression instanceof CtAssignment<?, ?> assignment) {
            analyzeAssignment(assignment, engine);
        } else if (expression instanceof CtVariableRead<?> read) {
            analyzeRead(read, engine);
        } else if (expression instanceof CtLiteral<?> literal) {
            analyzeLiteral(literal, engine);
        } else if (expression instanceof CtBinaryOperator<?> operator) {
            switch (operator.getKind()) {
                case AND -> analyzeAnd(operator, engine);
                case OR -> analyzeOr(operator, engine);
                default -> analyzeEagerBinary(operator, engine);
            }
        } else if (expression instanceof CtUnaryOperator<?> operator) {
            analyzeExpression(operator.getOperand(), engine);
            switch (operator.getKind()) {
                case NOT -> engine.not();
                case NEG -> engine.negate();
                default -> throw new UnsupportedOperationException();
            }
        } else if (expression instanceof CtThisAccess<?>) {
            engine.pushThis();
        } else if (expression instanceof CtInvocation<?> invocation) {
            if (invocation.getTarget() != null) {
                analyzeExpression(invocation.getTarget(), engine);
            }

            analyzeInvocation(invocation, engine);
        } else if (expression instanceof CtConstructorCall<?> constructorCall) {
            analyzeConstructorCall(constructorCall, engine);
        } else if (expression instanceof CtTypeAccess<?> access) {
            // TODO not sure what to do with this - ignore for now, but may be relevant for static field accesses
        } else {
            throw new UnsupportedOperationException(expression.getClass().getName());
        }

        expression.putMetadata(FlowContext.VALUE_KEY, engine.peekOrVoid());
    }

    private void analyzeAssignment(CtAssignment<?, ?> assignment, FlowEngine engine) {
        CtExpression<?> lhs = assignment.getAssigned();
        CtExpression<?> rhs = assignment.getAssignment();
        analyzeExpression(rhs, engine);

        if (lhs instanceof CtVariableWrite<?> write) {
            if (write.getVariable() instanceof CtLocalVariableReference<?> local) {
                engine.storeLocal(local.getSimpleName());
            } else if (write.getVariable() instanceof CtParameterReference<?> parameter) {
                engine.storeLocal(parameter.getSimpleName());
            } else {
                throw new UnsupportedOperationException(write.getVariable().getClass().getSimpleName());
            }
        } else {
            throw new UnsupportedOperationException(lhs.getClass().getName());
        }
    }

    private void analyzeLiteral(CtLiteral<?> literal, FlowEngine engine) {
        if (literal.getType().isPrimitive()) {
            switch (literal.getType().getQualifiedName()) {
                case "boolean" -> engine.pushValue(BooleanValueSet.of((Boolean) literal.getValue()));
                case "int" -> engine.pushValue(IntValueSet.ofIntSingle((Integer) literal.getValue()));
                default -> throw new UnsupportedOperationException(literal.getType().getSimpleName());
            }
        } else {
            // I'm pretty sure that the only non-primitive literal is the null literal
            engine.pushValue(ObjectValueSet.getNullSet(this.context));
        }
    }

    private void analyzeRead(CtVariableRead<?> read, FlowEngine engine) {
        CtVariable<?> variable = read.getVariable().getDeclaration();
        if (variable instanceof CtLocalVariable<?> local) {
            engine.pushLocal(local.getSimpleName());
        } else if (variable instanceof CtParameter<?> parameter) {
            engine.pushLocal(parameter.getSimpleName());
        } else {
            throw new UnsupportedOperationException(read.getVariable().getDeclaration().getClass().getName());
        }
    }

    private void analyzeAnd(CtBinaryOperator<?> and, FlowEngine engine) {
        analyzeExpression(and.getLeftHandOperand(), engine);
        FlowEngine rhsBranch = engine.fork(BooleanValueSet.of(true));
        engine.assertTos(BooleanValueSet.of(false));
        analyzeExpression(and.getRightHandOperand(), rhsBranch);
        rhsBranch.and();
        engine.join(rhsBranch);
    }

    private void analyzeOr(CtBinaryOperator<?> or, FlowEngine engine) {
        analyzeExpression(or.getLeftHandOperand(), engine);
        FlowEngine rhsBranch = engine.fork(BooleanValueSet.of(false));
        engine.assertTos(BooleanValueSet.of(true));
        analyzeExpression(or.getRightHandOperand(), rhsBranch);
        rhsBranch.or();
        engine.join(rhsBranch);
    }

    private void analyzeEagerBinary(CtBinaryOperator<?> operator, FlowEngine engine) {
        analyzeExpression(operator.getLeftHandOperand(), engine);
        analyzeExpression(operator.getRightHandOperand(), engine);
        switch (operator.getKind()) {
            case PLUS -> engine.add();
            case MINUS -> engine.subtract();
            case MUL -> engine.multiply();
            case DIV -> engine.divide();
            case LT -> engine.compareOp(Relation.LESS_THAN);
            case LE -> engine.compareOp(Relation.LESS_THAN_EQUAL);
            case GT -> engine.compareOp(Relation.GREATER_THAN);
            case GE -> engine.compareOp(Relation.GREATER_THAN_EQUAL);
            case EQ -> engine.compareOp(Relation.EQUAL);
            case NE -> engine.compareOp(Relation.NOT_EQUAL);
            default -> throw new IllegalStateException(operator.getKind().toString());
        }
    }

    private void analyzeInvocation(CtInvocation<?> invocation, FlowEngine engine) {
        // Hack: push the this-pointer if we call the super or another (via this(...)) constructor
        // (i.e. a call to a constructor that is not a CtConstructorCall)
        if (invocation.getExecutable().isConstructor()) {
            engine.pushThis();
        }

        CachedMethod calledMethod = this.context.getCachedMethod(invocation.getExecutable());
        for (int i = invocation.getArguments().size() - 1; i >= 0; i--) {
            analyzeExpression(invocation.getArguments().get(i), engine);
        }
        if (calledMethod.isStatic()) {
            engine.callStatic(calledMethod);
        } else {
            engine.callVirtual(calledMethod);
        }
    }

    private void analyzeConstructorCall(CtConstructorCall<?> call, FlowEngine engine) {
        CachedMethod calledMethod = this.context.getCachedMethod(call.getExecutable());

        // Create and push the new object as the this-pointer for the method
        engine.pushValue(ObjectValueSet.forExactType(Nullness.NON_NULL, new TypeId(call.getExecutable().getType()), this.context));

        for (int i = call.getArguments().size() - 1; i >= 0; i--) {
            analyzeExpression(call.getArguments().get(i), engine);
        }
        engine.callConstructor(calledMethod); // Call the constructor
    }

    private void analyzeIf(CtIf ifStmt, FlowEngine engine) {
        analyzeExpression(ifStmt.getCondition(), engine);
        // Then branch
        FlowEngine thenBranch = engine.fork(BooleanValueSet.of(true));
        thenBranch.pop();
        if (!thenBranch.isImpossibleState()) {
            this.context.log("=== Then: " + thenBranch);
            analyzeStatement(ifStmt.getThenStatement(), thenBranch);
        } else {
            this.context.log("=== Then: (Unreachable)");
        }

        // Else branch
        engine.assertTos(BooleanValueSet.of(false));
        engine.pop();
        if (!engine.isImpossibleState()) {
            this.context.log("=== Else: " + engine);
            if (ifStmt.getElseStatement() != null) {
                analyzeStatement(ifStmt.getElseStatement(), engine);
            }
        } else {
            this.context.log("=== Else: (Unreachable)");
        }

        engine.join(thenBranch);
        this.context.log("== End if: " + engine);
    }

    private void analyzeWhileLoop(CtWhile loop, FlowEngine engine) {
        // Filter out states that skip the loop
        analyzeExpression(loop.getLoopingExpression(), engine);
        FlowEngine skipBranch = engine.fork(BooleanValueSet.of(false));
        skipBranch.pop();

        // All other branches are now at the start of the loop body

    }

    private List<MethodExitState> buildExitStates(FlowEngine engine) {
        return engine.getCurrentStates().stream()
                .map(s -> new MethodExitState(this.effectivelyVoid ? VoidValue.getInstance() : s.peek(), s.getParamStates()))
                .toList();
    }
}
