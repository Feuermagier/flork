package de.firemage.flork.flow;

import spoon.reflect.declaration.CtElement;

public final class AnalysisLocation {
    private String file;
    private String line;
    private int indentation;

    public AnalysisLocation() {
        this.indentation = 0;
    }

    private AnalysisLocation(int indentation) {
        this.indentation = indentation;
    }

    public void setCurrentElement(CtElement element) {
        var position = element.getPosition();
        if (position != null && position.isValidPosition()) {
            this.line = String.valueOf(position.getLine());
            if (position.getFile() != null) {
                this.file = position.getFile().getName().replace(".java", "");
            } else {
                this.file = "?";
            }
        } else {
            this.line = "?";
            this.file = "?";
        }
    }

    public void increaseIndentation() {
        this.indentation++;
    }

    public void decreaseIndentation() {
        this.indentation--;
    }

    public String formatPrefix() {
        String prefix = String.format("(%s:%s) ", this.file, this.line);
        return ("%-" + (this.indentation * 2 + 15) + "s").formatted(prefix);
    }

    public String formatEmptyPrefix() {
        return " ".repeat(this.formatPrefix().length());
    }

    public AnalysisLocation next() {
        return new AnalysisLocation(this.indentation + 1);
    }
}
