package de.firemage.flork.flow;

import spoon.reflect.CtModel;

public class FlowContext {
    private final CtModel model;
    private final boolean smallWorld;

    public FlowContext(CtModel model, boolean smallWorld) {
        this.model = model;
        this.smallWorld = smallWorld;
    }

    public CtModel getModel() {
        return this.model;
    }
}
