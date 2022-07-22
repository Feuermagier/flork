package de.firemage.flork;

public sealed interface StatementControlFlowResult
    permits ExceptionStatementResult, NonBreakingStatementResult,
    ReturnedStatementResult {
}
