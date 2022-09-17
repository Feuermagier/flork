package de.firemage.flork.flow;

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
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.code.CtVariableWrite;
import spoon.reflect.code.CtWhile;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.reference.CtLocalVariableReference;
import spoon.reflect.reference.CtParameterReference;
import java.util.ArrayList;
import java.util.List;

public class FlowMethodAnalysis implements MethodAnalysis {
    private final FlowAnalysis flowAnalysis;
    private final CtExecutable<?> method;
    private final List<String> parameterNames;
    private final List<MethodExitState> returnStates;
    private final boolean effectivelyVoid;

    private FlowMethodAnalysis(CtExecutable<?> method, FlowAnalysis flowAnalysis) {
        this.flowAnalysis = flowAnalysis;
        this.method = method;
        this.returnStates = new ArrayList<>();
        this.parameterNames = new ArrayList<>();
        this.effectivelyVoid = method instanceof CtConstructor || method.getType().getSimpleName().equals("void");

        for (CtParameter<?> parameter : method.getParameters()) {
            parameterNames.add(parameter.getSimpleName());
        }

        System.out.println("================== Analyzing " + this.method.getSimpleName() + " ==================");

        FlowEngine engine = new FlowEngine(method.getParameters(), this.flowAnalysis.getContext());
        analyzeBlock(method.getBody(), engine);
        if (!engine.isStackEmpty()) {
            throw new IllegalStateException("Stack is not empty after end of method");
        }
        if (!engine.isImpossibleState()) {
            if (this.effectivelyVoid) {
                this.returnStates.addAll(buildExitStates(engine));
            } else {
                throw new IllegalStateException("Missing final return in a non-void method");
            }
        }

        System.out.println("================== " + this.method.getSimpleName() + " completed ==================");
    }

    public static MethodAnalysis analyzeMethod(CtExecutable<?> method, FlowAnalysis flowAnalysis) {
        return new FlowMethodAnalysis(method, flowAnalysis);
    }

    @Override
    public List<MethodExitState> getReturnStates() {
        return this.returnStates;
    }

    @Override
    public List<String> getOrderedParameterNames() {
        return this.parameterNames;
    }

    @Override
    public String getName() {
        return this.method.getSignature();
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
            engine.createLocal(localDefinition.getSimpleName(), localDefinition.getType());
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
                engine.pushValue(VoidValue.getInstance());
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
        } else if (expression instanceof CtInvocation<?> invocation) {
            analyzeInvocation(invocation, engine);
        } else if (expression instanceof CtConstructorCall<?> constructorCall) {
            analyzeConstructorCall(constructorCall, engine);
        } else {
            throw new UnsupportedOperationException(expression.getClass().getName());
        }

        expression.putMetadata(FlowAnalysis.VALUE_KEY, engine.peek());
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
            System.out.println(literal.getType().getQualifiedName());
            engine.pushValue(new ObjectValueSet(Nullness.NULL, literal.getType(), true, this.flowAnalysis.getContext()));
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
        MethodAnalysis analysis = this.flowAnalysis.analyzeMethod(invocation.getExecutable());
        for (int i = invocation.getArguments().size() - 1; i >= 0; i--) {
            analyzeExpression(invocation.getArguments().get(i), engine);
        }
        engine.call(analysis);
    }

    private void analyzeConstructorCall(CtConstructorCall<?> call, FlowEngine engine) {
        MethodAnalysis analysis = this.flowAnalysis.analyzeMethod(call.getExecutable());
        for (int i = call.getArguments().size() - 1; i >= 0; i--) {
            analyzeExpression(call.getArguments().get(i), engine);
        }
        engine.call(analysis); // Call the constructor
        engine.pop(); // Pop the void value pushed by the constructor
        engine.pushValue(new ObjectValueSet(Nullness.NON_NULL, call.getExecutable().getType(), true,
                this.flowAnalysis.getContext())); // Push the new object
    }
    
    private void analyzeIf(CtIf ifStmt, FlowEngine engine) {
        analyzeExpression(ifStmt.getCondition(), engine);
        // Then branch
        FlowEngine thenBranch = engine.fork(BooleanValueSet.of(true));
        thenBranch.pop();
        if (!thenBranch.isImpossibleState()) {
            System.out.println("=== Then: " + thenBranch);
            analyzeStatement(ifStmt.getThenStatement(), thenBranch);
        } else {
            System.out.println("=== Then: (Unreachable)");
        }

        // Else branch
        engine.assertTos(BooleanValueSet.of(false));
        engine.pop();
        if (!engine.isImpossibleState()) {
            System.out.println("=== Else: " + engine);
            if (ifStmt.getElseStatement() != null) {
                analyzeStatement(ifStmt.getElseStatement(), engine);
            }
        } else {
            System.out.println("=== Else: (Unreachable)");
        }

        engine.join(thenBranch);
        System.out.println("== End if: " + engine);
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
