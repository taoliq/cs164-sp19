package chocopy.common.codegen;

import chocopy.common.analysis.types.ValueType;
import chocopy.common.astnodes.Literal;

/** Code-generation information about a local variable or parameter. */
public class StackVarInfo extends VarInfo {

    /** Information about the enclosing function. */
    protected final FuncInfo funcInfo;

    /**
     * A descriptor for a local variable or parameter VARNAME of type VARTYPE,
     * whose initial value is given by INITIALVALUE (null if no initial value),
     * and which is nested immediately within the function described
     * by FUNCINFO.
     */
    public StackVarInfo(String varName, ValueType varType, Literal initialValue,
                        FuncInfo funcInfo) {
        super(varName, varType, initialValue);
        this.funcInfo = funcInfo;
    }

    /**
     * Returns the descriptor of the function in which this var is defined.
     */
    public FuncInfo getFuncInfo() {
        return funcInfo;
    }
}
