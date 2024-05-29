package de.firemage.flork.flow;

import de.firemage.flork.flow.analysis.FlowMethodAnalysis;
import de.firemage.flork.flow.analysis.MethodAnalysis;
import de.firemage.flork.flow.analysis.StubMethodAnalysis;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtExecutableReference;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class CachedMethod {
    private final FlowContext context;
    private final CtExecutableReference<?> method;
    private final String qualifiedName;
    private final boolean effectivelyFinal;

    private StubMethodAnalysis unknownAnalysis;
    private List<MethodAnalysis> virtualCallAnalyses;
    private MethodAnalysis localAnalysis;

    public CachedMethod(CtExecutableReference<?> method, FlowContext context) {
        this.context = context;
        this.method = method;
        this.virtualCallAnalyses = null;
        this.localAnalysis = null;
        this.qualifiedName = FlowContext.buildQualifiedExecutableName(method);

        this.effectivelyFinal = method.isConstructor()
                || method.isStatic()
                || method.getDeclaration() instanceof CtMethod m && m.isPrivate()
                || method.isFinal()
                || method.getDeclaringType() != null && method.getDeclaringType().getDeclaration().isFinal()
                || context.isClosedWorld() && TypeUtil.getAllOverridingMethods(this.method, this.context).findAny().isEmpty();
    }

    public String getName() {
        return this.method.getDeclaringType().getQualifiedName() + "::" + this.method.getSignature();
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

    public Optional<TypeId> getThisType() {
        if (this.isStatic()) {
            return Optional.empty();
        } else {
            return Optional.of(new TypeId(this.method.getDeclaringType()));
        }
    }

    public CtExecutableReference<?> getExecutable() {
        return this.method;
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
        }

        if (this.virtualCallAnalyses == null) {
            if (this.effectivelyFinal) {
                this.virtualCallAnalyses = List.of(this.getLocalAnalysis());
            } else if (this.context.isClosedWorld()) {
                this.virtualCallAnalyses = Stream.concat(
                                TypeUtil.getAllOverridingMethods(this.method, this.context)
                                        .map(this.context::getCachedMethod),
                                Stream.of(this)
                        )
                        .map(CachedMethod::getLocalAnalysis)
                        .toList();
            } else {
                this.virtualCallAnalyses = List.of(this.getUnknownAnalysis());
            }
        }
        return this.virtualCallAnalyses;
    }

    private StubMethodAnalysis getUnknownAnalysis() {
        if (this.unknownAnalysis == null) {
            this.unknownAnalysis = StubMethodAnalysis.forReferencedExecutable(this, this.context);
        }
        return this.unknownAnalysis;
    }

    private MethodAnalysis getLocalAnalysis() {
        if (this.localAnalysis == null) {
            if (this.context.getHardcodedMethods().getForMethod(this.method).isPresent()) {
                this.context.log("= Using hardcoded analysis of " + this.getName());
                this.localAnalysis = this.context.getHardcodedMethods().getForMethod(this.method).get();
            } else if (this.method.getDeclaration() != null) {
                // Method is present in the classpath
                this.localAnalysis = FlowMethodAnalysis.analyzeMethod(this, this.method.getDeclaration(), this.context);
            } else {
                this.context.log("= Using stub analysis for " + this.getName());
                this.localAnalysis = StubMethodAnalysis.forReferencedExecutable(this, this.context);
            }
        } else {
            this.context.log("= Retrieved cached analysis of " + this.method.getSignature());
        }
        return this.localAnalysis;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CachedMethod that = (CachedMethod) o;
        return qualifiedName.equals(that.qualifiedName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(qualifiedName);
    }

    @Override
    public String toString() {
        return this.getName();
    }
}
