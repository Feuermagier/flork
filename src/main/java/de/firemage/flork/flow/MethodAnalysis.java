package de.firemage.flork.flow;

import de.firemage.flork.flow.engine.FlowEngine;
import de.firemage.flork.flow.engine.Relation;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtBlock;
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
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.reference.CtLocalVariableReference;
import spoon.reflect.reference.CtParameterReference;

import java.util.ArrayList;
import java.util.List;

public class MethodAnalysis {
    private final FlowAnalysis flowAnalysis;
    private final CtMethod<?> method;
    private final List<String> parameterNames;
    private final List<MethodExitState> returnStates;

    private MethodAnalysis(CtMethod<?> method, FlowAnalysis flowAnalysis) {
        this.flowAnalysis = flowAnalysis;
        this.method = method;
        this.returnStates = new ArrayList<>();
        this.parameterNames = new ArrayList<>();

        for (CtParameter<?> parameter : method.getParameters()) {
            parameterNames.add(parameter.getSimpleName());
        }

        FlowEngine engine = new FlowEngine(method.getParameters());
        analyzeBlock(method.getBody(), engine);
        if (!engine.isStackEmpty()) {
            throw new IllegalStateException("Stack is not empty after end of method");
        }
        if (!engine.isImpossibleState()) {
            if (method.getType().getQualifiedName().equals("void")) {
                this.returnStates.addAll(buildExitStates(engine));
            } else {
                throw new IllegalStateException("Missing final return in a non-void method");
            }
        }
    }
    
    public static MethodAnalysis analyzeMethod(CtMethod<?> method, FlowAnalysis flowAnalysis) {
        return new MethodAnalysis(method, flowAnalysis);
    }

    public List<MethodExitState> getReturnStates() {
        return this.returnStates;
    }

    public List<String> getOrderedParameterNames() {
        return this.parameterNames;
    }
    
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
        } else if (statement instanceof CtBlock<?> block) {
            analyzeBlock(block, engine);
        } else if (statement instanceof CtReturn<?> ret) {
            analyzeExpression(ret.getReturnedExpression(), engine);
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
            switch (literal.getType().getSimpleName()) {
                case "boolean" -> engine.pushBooleanLiteral((Boolean) literal.getValue());
                case "int" -> engine.pushIntLiteral((Integer) literal.getValue());
                default -> throw new UnsupportedOperationException(literal.getType().getSimpleName());
            }
        } else {
            throw new UnsupportedOperationException(literal.getType().getQualifiedName());
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
        MethodAnalysis analysis =
            this.flowAnalysis.analyzeMethod((CtMethod<?>) invocation.getExecutable().getDeclaration());
        for (int i = invocation.getArguments().size() - 1; i >= 0; i--) {
            analyzeExpression(invocation.getArguments().get(i), engine);
        }
        engine.call(analysis);
    }

    private List<MethodExitState> buildExitStates(FlowEngine engine) {
        return engine.getCurrentStates().stream()
            .map(s -> new MethodExitState(this.method.getType().getQualifiedName().equals("void") ? null : s.peek(),
                s.getParamStates()))
            .toList();
    }
}
