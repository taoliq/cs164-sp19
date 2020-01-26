package chocopy.common.codegen;

import chocopy.common.analysis.types.ValueType;
import chocopy.common.astnodes.Literal;

/** Information about a variable or attribute. */
public abstract class VarInfo extends SymbolInfo {

    /** Name of variable or attribute. */
    protected final String varName;
    /** Runtime location of initial value for this variable or attribute. */
    protected final Literal initialValue;
    /** Static type of the variable. */
    protected final ValueType varType;

    /**
     * A descriptor for variable or attribute VARNAME with VARTYPE as its static
     * type and INITIALVALUE as its initial value (or null if None).
     */
    public VarInfo(String varName, ValueType varType, Literal initialValue) {
        this.varName = varName;
        this.varType = varType;
        this.initialValue = initialValue;
    }

    /** Returns the name of this variable or attribute. */
    public String getVarName() {
        return varName;
    }

    /** Returns the type of this variable or attribute. */
    public ValueType getVarType() {
        return varType;
    }

    /** Returns the initial value of this variable or attribute. */
    public Literal getInitialValue() {
        return initialValue;
    }

}
