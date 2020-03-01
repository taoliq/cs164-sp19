package chocopy.common.codegen;

import chocopy.common.analysis.types.ValueType;
import chocopy.common.astnodes.Literal;

/** Code-generation related information about a global variable. */
public class GlobalVarInfo extends VarInfo {

    /** This variable resides in static storage tagged with LABEL. The
     *  label is prepended with "$" to prevent name clashes. */
    protected final Label label;

    /**
     * A descriptor for a global variable named VARNAME of type VARTYPE
     * whose initial value is labeled with INITIALVALUE (null if
     * no initializtion value). */
    public GlobalVarInfo(String varName, ValueType varType,
                         Literal initialValue) {
        super(varName, varType, initialValue);
        this.label = new Label(String.format("$%s", varName));
    }

    /** Return the code location of this variable. */
    public Label getLabel() {
        return label;
    }
}
