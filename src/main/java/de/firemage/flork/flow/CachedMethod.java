package de.firemage.flork.flow;

import de.firemage.flork.flow.analysis.MethodAnalysis;
import de.firemage.flork.flow.analysis.StubMethodAnalysis;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.reference.CtExecutableReference;

import java.util.List;

public class CachedMethod {
    private final FlowContext context;
    private final CtExecutableReference<?> method;
    private StubMethodAnalysis unknownAnalysis;
    private List<MethodAnalysis> subtypeAnalyses;
    private MethodAnalysis localAnalysis;

    public CachedMethod(CtExecutableReference<?> method, FlowContext context) {
        this.context = context;
        this.method = method;
        this.unknownAnalysis = null;
        this.subtypeAnalyses = null;
        this.localAnalysis = null;
    }

    public String getName() {
        return this.method.getSimpleName();
    }
    
    public boolean isStatic() {
        return this.method.isStatic();
    }
    
    public boolean isFinal() {
        return this.method.isFinal();
    }
    
    public boolean isConstructor() {
        return this.method.isConstructor();
    }

    public MethodAnalysis getConstructorCallAnalysis() {
        if (!this.isConstructor()) {
            throw new IllegalStateException("Cannot constructor-call the non-constructor method " + this.getName());
        }
        return this.getLocalAnalysis();
    }
    
    public MethodAnalysis getStaticCallAnalyses() {
        if (!this.isStatic()) {
            throw new IllegalStateException("Cannot static-call the non-static method " + this.getName());
        }
        return this.getLocalAnalysis();
    }

    public List<MethodAnalysis> getVirtualCallAnalyses() {
        if (this.isStatic()) {
            throw new IllegalStateException("Cannot virtual-call the non-virtual method " + this.getName());
        } else if (this.isFinal() 
            || this.method.getDeclaringType() != null && this.method.getDeclaringType().getDeclaration().isFinal()) {
            return List.of(this.getLocalAnalysis());
        } else if (this.context.isClosedWorld()) {
            if (this.subtypeAnalyses == null) {
                this.subtypeAnalyses =
                    TypeUtil.getAllOverridingMethods(this.method, this.context).map(this.context::analyzeMethod)
                        .toList();
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
