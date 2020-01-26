package chocopy.common.astnodes;

import java.util.List;

import chocopy.common.analysis.NodeAnalyzer;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java_cup.runtime.ComplexSymbolFactory.Location;

/** Collects the error messages in a Program.  There is exactly one per
 *  Program node. */
public class Errors extends Node {

    /** The accumulated error messages in the order added. */
    public final List<CompilerError> errors;

    /** True iff multiple semantic errors allowed on a node. */
    @JsonIgnore
    private boolean allowMultipleErrors;

    /** An Errors whose list of CompilerErrors is ERRORS.  The list should be
     *  modified using this.add. */
    @JsonCreator
    public Errors(List<CompilerError> errors) {
        super(null, null);
        this.errors = errors;
        allowMultipleErrors = true;
    }

    /** Return true iff there are any errors. */
    public boolean hasErrors() {
        return !this.errors.isEmpty();
    }

    /** Prevent multiple semantic errors on the same node. */
    public void suppressMultipleErrors() {
        allowMultipleErrors = false;
    }

    /** Add a new semantic error message attributed to NODE, with message
     *  String.format(MESSAGEFORM, ARGS). */
    public void semError(Node node, String messageForm, Object... args) {
        if (allowMultipleErrors || !node.hasError()) {
            String msg = String.format(messageForm, args);
            CompilerError err = new CompilerError(null, null, msg, false);
            err.setLocation(node.getLocation());
            add(err);
            if (!node.hasError()) {
                node.setErrorMsg(msg);
            }
        }
    }

    /** Add a new syntax error message attributed to the source text
     *  between LEFT and RIGHT, and with message
     *  String.format(MESSAGEFORM, ARGS). */
    public void syntaxError(Location left, Location right,
                            String messageForm, Object... args) {
        add(new CompilerError(left, right, String.format(messageForm, args),
                              true));
    }

    /** Add ERR to the list of errors. */
    public void add(CompilerError err) {
        errors.add(err);
    }

    public <T> T dispatch(NodeAnalyzer<T> analyzer) {
        return analyzer.analyze(this);
    }

}
