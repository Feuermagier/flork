package de.firemage.flork;

public final class ExceptionStatementResult implements
    StatementControlFlowResult {
    private final ThrownException exception;

    public ExceptionStatementResult(ThrownException exception) {
        this.exception = exception;
    }

    public ThrownException getException() {
        return exception;
    }
}
