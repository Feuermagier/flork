package de.firemage.flork.flow.engine;

public class SSAVarId {
    private final VarId varId;
    private final int index;
    
    private SSAVarId(VarId varId, int index) {
        this.varId = varId;
        this.index = index;
    }
    
    public static SSAVarId forFresh(VarId varId) {
        return new SSAVarId(varId, 0);
    }
    
    public SSAVarId next() {
        return new SSAVarId(this.varId, this.index + 1);
    }
    
    public VarId varId() {
        return this.varId;
    }
    
    public int index() {
        return this.index;
    }
    
    public boolean isInitial() {
        return this.index == 0;
    }

    @Override
    public String toString() {
        return this.varId.name() + "'" + this.index;
    }
}
