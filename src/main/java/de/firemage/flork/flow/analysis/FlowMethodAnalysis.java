package de.firemage.flork.flow.analysis;

import de.firemage.flork.flow.CachedMethod;
import de.firemage.flork.flow.FlowContext;
import de.firemage.flork.flow.PrimitiveTypeRelationship;
import de.firemage.flork.flow.exit.MethodExitState;
import de.firemage.flork.flow.TypeId;
import de.firemage.flork.flow.engine.FlowEngine;
import de.firemage.flork.flow.engine.Relation;
import de.firemage.flork.flow.value.BooleanValueSet;
import de.firemage.flork.flow.value.IntValueSet;
import de.firemage.flork.flow.value.LongValueSet;
import de.firemage.flork.flow.value.Nullness;
import de.firemage.flork.flow.value.ObjectValueSet;
import de.firemage.flork.flow.value.VoidValue;
import spoon.reflect.code.BinaryOperatorKind;
import spoon.reflect.code.CtAnnotationFieldAccess;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtComment;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtFieldWrite;
import spoon.reflect.code.CtIf;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLambda;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtSuperAccess;
import spoon.reflect.code.CtThisAccess;
import spoon.reflect.code.CtThrow;
import spoon.reflect.code.CtTry;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.code.CtVariableWrite;
import spoon.reflect.code.CtWhile;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.reference.CtTypeReference;

import java.util.ArrayList;
import java.util.List;

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
        TypeId thisType = method.getThisType().orElse(null);
        ObjectValueSet thisPointer = null;
        if (thisType != null) {
            parameterNames.add("this"); // this is the first parameter

            // Construct the possible values of the this-pointer
            if (context.isEffectivelyFinalType(thisType)) {
                thisPointer = ObjectValueSet.forExactType(Nullness.NON_NULL, thisType, context);
            } else {
                // We don't know the exact type of this, so we use the least upper bound
                // of all possible subtypes
                thisPointer = ObjectValueSet.forUnconstrainedType(Nullness.NON_NULL, thisType, context);
            }
        }

        // Other parameters
        for (CtParameter<?> parameter : executable.getParameters()) {
            parameterNames.add(parameter.getSimpleName());
        }

        this.context.pushLocation();
        this.context.logNoPrefix("=============== " + this.method.getName() + " ===============");

        FlowEngine engine = new FlowEngine(thisType, thisPointer, executable.getParameters(), this.context);
        analyzeBlock(executable.getBody(), engine);

        // Handle thrown exceptions & possible implicit return
        if (!engine.isEmpty()) {
            this.buildExitStates(engine, false);
        }

        this.context.log(this.getReturnStates().size() + " return states: " + this.getReturnStates());
        this.context.logNoPrefix("================== " + this.method.getName() + " completed ==================");
        this.context.popLocation();
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

    // Returns true if the statement is a return statement
    private void analyzeStatement(CtStatement statement, FlowEngine engine) {
        this.context.setCurrentElement(statement);
        switch (statement) {
            case CtExpression<?> expression -> {
                analyzeExpression(expression, engine);
                engine.pop();
            }
            case CtLocalVariable<?> localDefinition -> {
                engine.createLocal(localDefinition.getSimpleName(), new TypeId(localDefinition.getType()));
                if (localDefinition.getAssignment() != null) {
                    analyzeExpression(localDefinition.getAssignment(), engine);
                    doImplicitConversions(localDefinition.getType(), getExpressionType(localDefinition.getAssignment()), engine);
                    engine.storeLocal(localDefinition.getSimpleName());
                    engine.pop();
                }
            }
            case CtIf ifStmt -> analyzeIf(ifStmt, engine);
            case CtBlock<?> block -> analyzeBlock(block, engine);
            case CtReturn<?> ret -> {
                if (ret.getReturnedExpression() == null) {
                    this.buildExitStates(engine, false);
                } else {
                    analyzeExpression(ret.getReturnedExpression(), engine);
                    this.doImplicitConversions(this.method.getExecutable().getType(), getExpressionType(ret.getReturnedExpression()), engine);
                    this.buildExitStates(engine, true);
                }
            }
            case CtWhile whileLoop -> analyzeWhileLoop(whileLoop, engine);
            case CtThrow throwStmt -> {
                analyzeExpression(throwStmt.getThrownExpression(), engine);
                engine.throwException();
            }
            case CtTry tryBlock -> {
                // TODO consider finally blocks
                this.context.log("=== Try block");
                var outerExceptionals = engine.getAndClearExceptionalStates();
                analyzeBlock(tryBlock.getBody(), engine);
                for (var catcher : tryBlock.getCatchers()) {
                    this.context.log("=== Catch " + catcher.getParameter().getType());
                    var catcherEngine = engine.extractExceptionalStatesForHandler(TypeId.ofFallible(catcher.getParameter().getType()).get());
                    if (catcherEngine.isImpossibleState()) {
                        this.context.log("-> Unreachable");
                        continue;
                    }
                    analyzeBlock(catcher.getBody(), catcherEngine);
                    engine.join(catcherEngine);
                }
                engine.addExceptionalStates(outerExceptionals);
            }
            case CtComment ignored -> {
            }
            default -> throw new UnsupportedOperationException(statement.getClass().getName());
        }

        // Clear the stack after every statement
        engine.clearStack();
    }

    private void analyzeExpression(CtExpression<?> expression, FlowEngine engine) {
        this.context.setCurrentElement(expression);
        switch (expression) {
            case CtAssignment<?, ?> assignment -> analyzeAssignment(assignment, engine);
            case CtVariableRead<?> read -> analyzeRead(read, engine);
            case CtLiteral<?> literal -> analyzeLiteral(literal, engine);
            case CtBinaryOperator<?> operator -> {
                switch (operator.getKind()) {
                    case AND -> analyzeAnd(operator, engine);
                    case OR -> analyzeOr(operator, engine);
                    default -> analyzeEagerBinary(operator, engine);
                }
            }
            case CtUnaryOperator<?> operator -> {
                switch (operator.getKind()) {
                    case NOT -> {
                        analyzeExpression(operator.getOperand(), engine);
                        engine.not();
                    }
                    case NEG -> {
                        analyzeExpression(operator.getOperand(), engine);
                        engine.negate();
                    }
                    case POSTINC -> {
                        if (operator.getOperand() instanceof CtVariableWrite<?> write) {
                            String local = write.getVariable().getSimpleName();
                            engine.pushLocal(local);
                            engine.pushValue(IntValueSet.ofIntSingle(1));
                            engine.add();
                            engine.storeLocal(local);
                        } else {
                            throw new UnsupportedOperationException(operator.getOperand().getClass().getName());
                        }
                    }
                    default -> throw new UnsupportedOperationException();
                }
            }
            case CtThisAccess<?> ignored -> engine.pushThis();
            case CtInvocation<?> invocation -> {
                if (invocation.getTarget() != null) {
                    analyzeExpression(invocation.getTarget(), engine);
                }

                analyzeInvocation(invocation, engine);
            }
            case CtConstructorCall<?> constructorCall -> analyzeConstructorCall(constructorCall, engine);
            case CtTypeAccess<?> access -> {
                // Can't handle this for now, so play safeTypeId.ofFallible
                engine.pushValue(ObjectValueSet.forExactType(Nullness.NON_NULL, new TypeId(access.getAccessedType()), this.context));
            }
            case CtVariableWrite<?> ignored ->
                    throw new IllegalStateException("Write expression should be handled in assignment");
            case CtLambda<?> lambda -> {
                // Lambdas basically continue the current control flow, with a few additional variables (the lambda's parameters) added
                // However, we need to make sure that the lambda's variables to captured variables are not immediately visible to the surrounding scope
                // For locals, this is simple: Java requires all captured variables to be "effectively final", so we have to do nothing special here
                // To avoid capturing any knowledge about fields, we clone the engine before analyzing the lambda

                FlowEngine lambdaEngine = engine.cloneEngine();
                for (CtParameter<?> parameter : lambda.getParameters()) {
                    lambdaEngine.createLocal(parameter.getSimpleName(), new TypeId(parameter.getType()));
                }

                this.context.pushLocation();
                this.context.logNoPrefix("=== Lambda Start === ");
                if (lambda.getBody() != null) {
                    analyzeBlock(lambda.getBody(), lambdaEngine);
                } else {
                    analyzeExpression(lambda.getExpression(), lambdaEngine);
                }
                this.context.logNoPrefix("=== Lambda End === ");
                this.context.popLocation();

                engine.pushValue(ObjectValueSet.forUnconstrainedType(Nullness.NON_NULL, new TypeId(lambda.getType()), this.context));
            }
            default ->
                    throw new UnsupportedOperationException(expression.getClass().getName() + " @ " + this.context.getLocation());
        }

        // Process all type casts
        var currentType = expression.getType();
        for (var cast : expression.getTypeCasts().reversed()) {
            // Boxing may need to happen implicitly here
            if (cast.isPrimitive() && !currentType.isPrimitive()) {
                engine.unbox();
            } else if (!cast.isPrimitive() && currentType.isPrimitive()) {
                engine.box();
            }

            engine.castTo(new TypeId(cast));
            currentType = cast;
        }

        expression.putMetadata(FlowContext.VALUE_KEY, engine.peekOrVoid());
    }

    private void analyzeAssignment(CtAssignment<?, ?> assignment, FlowEngine engine) {
        CtExpression<?> lhs = assignment.getAssigned();
        CtExpression<?> rhs = assignment.getAssignment();
        analyzeExpression(rhs, engine);
        doImplicitConversions(getExpressionType(lhs), getExpressionType(rhs), engine);

        if (lhs instanceof CtFieldWrite<?> write) {
            this.analyzeExpression(write.getTarget(), engine);
            engine.storeField(write.getVariable().getSimpleName());
        } else if (lhs instanceof CtVariableWrite<?> write) {
            engine.storeLocal(write.getVariable().getSimpleName());
        } else {
            throw new UnsupportedOperationException(lhs.getClass().getName());
        }
    }

    private void analyzeLiteral(CtLiteral<?> literal, FlowEngine engine) {
        if (literal.getType().isPrimitive()) {
            switch (literal.getType().getQualifiedName()) {
                case "boolean" -> engine.pushValue(BooleanValueSet.of((Boolean) literal.getValue()));
                case "int" -> engine.pushValue(IntValueSet.ofIntSingle((Integer) literal.getValue()));
                case "long" -> engine.pushValue(LongValueSet.ofSingle((Long) literal.getValue()));
                default -> throw new UnsupportedOperationException(literal.getType().getSimpleName());
            }
        } else {
            // I'm pretty sure that the only non-primitive literal is the null literal
            engine.pushValue(ObjectValueSet.getNullSet(this.context));
        }
    }

    private void analyzeRead(CtVariableRead<?> read, FlowEngine engine) {
        if (read instanceof CtFieldRead<?> fieldRead) {
            analyzeExpression(fieldRead.getTarget(), engine);
            engine.pushField(fieldRead.getVariable().getSimpleName());
        } else if (read instanceof CtSuperAccess<?> || read instanceof CtAnnotationFieldAccess<?>) {
            throw new UnsupportedOperationException(read.getClass().getName());
        } else {
            // Local variable read
            engine.pushLocal(read.getVariable().getSimpleName());
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
        // Two things need to be checked:
        // - Operands may be boxed, and need to be unboxed
        // - For numeric operations, one operand may need to be widened to match the other's type

        var lhsType = getExpressionType(operator.getLeftHandOperand());
        var rhsType = getExpressionType(operator.getRightHandOperand());
        var tyRelation = PrimitiveTypeRelationship.compareTypes(lhsType, rhsType);

        analyzeExpression(operator.getLeftHandOperand(), engine);
        if (onlyAppliesToPrimitive(operator.getKind()) && !operator.getLeftHandOperand().getType().isPrimitive()) {
            engine.unbox();
        }
        if (tyRelation == PrimitiveTypeRelationship.RHS_WIDER) {
            engine.castTo(new TypeId(rhsType));
        }

        analyzeExpression(operator.getRightHandOperand(), engine);
        if (onlyAppliesToPrimitive(operator.getKind()) && !operator.getRightHandOperand().getType().isPrimitive()) {
            engine.unbox();
        }
        if (tyRelation == PrimitiveTypeRelationship.LHS_WIDER) {
            engine.castTo(new TypeId(lhsType));
        }

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
        var executable = invocation.getExecutable();

        // Push the this-pointer if we call the super or another (via this(...)) constructor
        // (i.e. a call to a constructor that is not a CtConstructorCall)
        if (executable.isConstructor()) {
            engine.pushThis();
        }

        CachedMethod calledMethod = this.context.getCachedMethod(invocation.getExecutable());
        // Analyze the arguments in reverse order, since they will be popped in this order from the stack
        for (int i = invocation.getArguments().size() - 1; i >= 0; i--) {
            var argument = invocation.getArguments().get(i);
            analyzeExpression(argument, engine);
            this.doImplicitConversions(executable.getParameters().get(i), getExpressionType(argument), engine);
        }
        if (calledMethod.isStatic()) {
            engine.callStatic(calledMethod);
        } else if (calledMethod.isConstructor()) {
            // Super call
            engine.callConstructor(calledMethod);
        } else {
            engine.callVirtual(calledMethod);
        }

        this.context.setCurrentElement(invocation);
    }

    private void analyzeConstructorCall(CtConstructorCall<?> call, FlowEngine engine) {
        CachedMethod calledMethod = this.context.getCachedMethod(call.getExecutable());
        this.context.setCurrentElement(call);

        // Create and push the new object as the this-pointer for the method
        engine.pushValue(
                ObjectValueSet.forExactType(Nullness.NON_NULL, new TypeId(call.getExecutable().getType()), this.context));

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
        int totalStates = engine.getCurrentStates().size();

        // Filter out states that skip the loop
        this.context.log("== while: first condition");
        analyzeExpression(loop.getLoopingExpression(), engine);
        FlowEngine skipBranch = engine.fork(BooleanValueSet.of(false));
        skipBranch.pop();

        // All other branches are now at the start of the loop body
        // For these, the loop condition must be true at least once
        engine.assertTos(BooleanValueSet.of(true));
        engine.pop();

        if (engine.isImpossibleState()) {
            engine.join(skipBranch);
            return;
        }

        // We simulate the first iteration to check for states that iterate only once
        // Also, we record written variables so that we can reset them after the loop
        // for loops that are taken more than once
        // We also want to record the condition evaluation, since it may perform writes
        this.context.log("== while: first iteration " + engine.getCurrentStates().size() + "/" + totalStates);
        engine.beginWritesScope();
        analyzeStatement(loop.getBody(), engine);

        // Filter out states with a single iteration
        this.context.log("== while: second condition");
        analyzeExpression(loop.getLoopingExpression(), engine);
        FlowEngine singleIterationBranch = engine.fork(BooleanValueSet.of(false));
        singleIterationBranch.pop();

        // The remaining branches take the loop again
        // We do not analyze any more iterations, but instead just rest possibly written-to variables
        engine.assertTos(BooleanValueSet.of(true));
        engine.pop();

        if (!engine.isImpossibleState()) {
            // Reset knowledge about written variables
            engine.resetWrittenLocalsAndFields();
            engine.endWritesScope();

            // The loop condition is false after the last iteration
            this.context.log("== while: third condition " + engine.getCurrentStates().size() + "/" + totalStates);
            analyzeExpression(loop.getLoopingExpression(), engine);
            // TODO The following assert filters out some infinite loops; report this
            engine.assertTos(BooleanValueSet.of(false));
            engine.pop();
        }

        engine.join(singleIterationBranch);
        engine.join(skipBranch);
    }

    private void buildExitStates(FlowEngine engine, boolean returningExpression) {
        if (!returningExpression) {
            if (this.effectivelyVoid) {
                engine.pushValue(VoidValue.getInstance());
            } else if (this.method.isConstructor()) {
                engine.pushThis();
            }
        }

        for (var state : engine.getCurrentStates()) {
            this.returnStates.add(MethodExitState.forReturn(state.peek(), state.getInitialState()));
        }

        for (var state : engine.getAndClearExceptionalStates()) {
            this.returnStates.add(MethodExitState.forThrow(state.getActiveException(), state.getInitialState()));
        }

        engine.clear();
    }

    private boolean onlyAppliesToPrimitive(BinaryOperatorKind op) {
        return op != BinaryOperatorKind.EQ && op != BinaryOperatorKind.NE;
    }

    private CtTypeReference<?> getExpressionType(CtExpression<?> expression) {
        if (expression.getTypeCasts().isEmpty()) {
            return expression.getType();
        } else {
            return expression.getTypeCasts().getFirst();
        }
    }

    private void doImplicitConversions(CtTypeReference<?> newType, CtTypeReference<?> oldType, FlowEngine engine) {
        // Boxing / unboxing
        if (oldType.isPrimitive() && !newType.isPrimitive()) {
            // Boxing conversion (JLS 5.1.7)
            engine.box();

            // This is valid Java: Object x = 0
            // Here, the value is boxed first (yielding Integer), and then cast to Object
            // Therefore, we may need to add a cast here
            if (!oldType.box().equals(newType)) {
                engine.castTo(new TypeId(newType));
            }

            return;
        } else if (!oldType.isPrimitive() && newType.isPrimitive()) {
            // Unboxing conversion (JLS 5.1.8)
            engine.unbox();
            return;
        }

        // Reference upcasts
        if (!oldType.isPrimitive()) {
            // Widening reference conversion (JLS 5.1.5)
            // Narrowing reference conversions cannot happen implicitly, so we can ignore them
            engine.castTo(new TypeId(newType));
            return;
        }

        // Primitive conversions
        var relation = PrimitiveTypeRelationship.compareTypes(newType, oldType);
        if (relation == PrimitiveTypeRelationship.LHS_WIDER) {
            engine.castTo(new TypeId(newType));
        }
    }
}
