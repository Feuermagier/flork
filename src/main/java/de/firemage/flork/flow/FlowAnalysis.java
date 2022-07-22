package de.firemage.flork.flow;

import de.firemage.flork.flow.engine.FlowEngine;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtIf;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtLocalVariable;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FlowAnalysis {
    private final CtModel model;
    private final List<ValueSet> expressionValues;
    private final List<Map<String, ValueSet>> blockValues;

    public FlowAnalysis(CtModel model) {
        this.model = model;
        expressionValues = new ArrayList<>();
        blockValues = new ArrayList<>();
    }

    public FlowEngine analyzeMethod(CtMethod<?> method) {
        FlowEngine engine = new FlowEngine(method.getParameters());
        analyzeBlock(method.getBody(), engine);
        if (!engine.isStackEmpty()) {
            throw new IllegalStateException("Stack is not empty after method");
        }
        return engine;
    }

    public ValueSet getExpressionValue(CtExpression<?> expression) {
        return this.expressionValues.get(this.getExpressionNumber(expression));
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
            System.out.println("=== Then: " + thenBranch);
            analyzeStatement(ifStmt.getThenStatement(), thenBranch);
            // Else branch
            if (ifStmt.getElseStatement() != null) {
                engine.assertTos(BooleanValueSet.of(false));
                engine.pop();
                System.out.println("=== Else: " + engine);
                analyzeStatement(ifStmt.getElseStatement(), engine);
            } else {
                engine.pop();
                System.out.println("=== Else: " + engine);
            }
            engine.join(thenBranch);
            System.out.println("== End if: " + engine);
        } else if (statement instanceof CtBlock<?> block) {
            analyzeBlock(block, engine);
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
        } else {
            throw new UnsupportedOperationException(expression.getClass().getName());
        }

        this.expressionValues.add(this.getExpressionNumber(expression), engine.peek());
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
            case LT -> engine.lessThan();
            case LE -> engine.lessThanEquals();
            case GT -> engine.greaterThan();
            case GE -> engine.greaterThanEquals();
            default -> throw new IllegalStateException(operator.getKind().toString());
        }
    }

    private int getExpressionNumber(CtExpression<?> expression) {
        Object number = expression.getMetadata("flork.number");
        if (number != null) {
            return (Integer) number;
        } else {
            int newNumber = this.expressionValues.size();
            expression.putMetadata("flork.number", newNumber);
            this.expressionValues.add(ValueSet.topForType(expression.getType()));
            return newNumber;
        }
    }

    private int getBlockNumber(CtBlock<?> block) {
        Object number = block.getMetadata("flork.number");
        if (number != null) {
            return (Integer) number;
        } else {
            int newNumber = this.blockValues.size();
            block.putMetadata("flork.number", newNumber);
            this.blockValues.add(new HashMap<>());
            return newNumber;
        }
    }
}
