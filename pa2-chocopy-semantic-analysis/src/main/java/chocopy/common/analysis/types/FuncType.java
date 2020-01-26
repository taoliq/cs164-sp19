package chocopy.common.analysis.types;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;

/** Semantic information for a function or method. */
public class FuncType extends SymbolType {

    /** Types of parameters. */
    public final List<ValueType> parameters;
    /** Function's return type. */
    public final ValueType returnType;

    /** Create a FuncType returning RETURNTYPE0, intiallly parapeterless. */
    public FuncType(ValueType returnType0) {
        this(new ArrayList<>(), returnType0);
    }


    /** Create a FuncType for NAME0 with formal parameter types
     *  PARAMETERS0, returning type RETURNTYPE0. */
    @JsonCreator
    public FuncType(List<ValueType> parameters0,
                    ValueType returnType0) {
        this.parameters = parameters0;
        this.returnType = returnType0;
    }

    @Override
    public boolean isFuncType() {
        return true;
    }

    /** Return the type of the K-th parameter. */
    public ValueType getParamType(int k) {
        return parameters.get(k);
    }

    @Override
    public String toString() {
        return "<function>";
    }

}
