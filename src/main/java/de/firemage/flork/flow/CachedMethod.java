package de.firemage.flork.flow;

import de.firemage.flork.flow.analysis.MethodAnalysis;
import de.firemage.flork.flow.analysis.StubMethodAnalysis;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtExecutableReference;
import java.util.List;

public class CachedMethod {
    private final FlowContext context;
    private final CtExecutableReference<?> method;
    private final boolean effectivelyFinal;
    private final List<? extends CtExecutableReference<?>> overridingMethods;

    private StubMethodAnalysis unknownAnalysis;
    private List<MethodAnalysis> subtypeAnalyses;
    private MethodAnalysis localAnalysis;

    public CachedMethod(CtExecutableReference<?> method, FlowContext context) {
        this.context = context;
        this.method = method;
        this.unknownAnalysis = null;
        this.subtypeAnalyses = null;
        this.localAnalysis = null;

        if (context.isClosedWorld()) {
            this.overridingMethods = TypeUtil.getAllOverridingMethods(this.method, this.context).toList();
        } else {
            this.overridingMethods = null; // Assuming that nobody tries to access the field...
        }

        this.effectivelyFinal = method.isConstructor()
                || method.isStatic()
                || method.getDeclaration() instanceof CtMethod m && m.isPrivate()
                || method.isFinal()
                || method.getDeclaringType() != null && method.getDeclaringType().getDeclaration().isFinal()
                || context.isClosedWorld() && this.overridingMethods.isEmpty();
    }

    public String getName() {
        return this.method.getSimpleName();
    }

    public boolean isStatic() {
        return this.method.isStatic();
    }

    public boolean isEffectivelyFinal() {
        return this.effectivelyFinal;
    }

    public boolean isConstructor() {
        return this.method.isConstructor();
    }

    /**
     * For constructors, static methods and methods that can be proven to be of a specific type
     *
     * @return
     */
    public MethodAnalysis getFixedCallAnalysis() {
        return this.getLocalAnalysis();
    }

    public List<MethodAnalysis> getVirtualCallAnalyses() {
        if (this.isStatic()) {
            throw new IllegalStateException("Cannot virtual-call the non-virtual method " + this.getName());
        } else if (this.effectivelyFinal) {
            return List.of(this.getLocalAnalysis());
        } else if (this.context.isClosedWorld()) {
            if (this.subtypeAnalyses == null) {
                this.subtypeAnalyses = this.overridingMethods.stream().map(this.context::analyzeMethod).toList();
            }
            return this.subtypeAnalyses;
        } else {
            return List.of(this.getUnknownAnalysis());
        }
    }

    private StubMethodAnalysis getUnknownAnalysis() {
        if (this.unknownAnalysis == null) {
            this.unknownAnalysis = StubMethodAnalysis.forReferencedExecutable(this.method, this.context);
        }
        return this.unknownAnalysis;
    }

    private MethodAnalysis getLocalAnalysis() {
        if (this.localAnalysis == null) {
            this.localAnalysis = this.context.analyzeMethod(this.method);
        }
        return this.localAnalysis;
    }
}
