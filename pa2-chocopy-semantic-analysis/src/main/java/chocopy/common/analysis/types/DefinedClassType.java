package chocopy.common.analysis.types;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;

/** Semantic information for a function or method. */
public class DefinedClassType extends SymbolType {

    private final String superclass;
    private final String className;
    //can not be extended
    private final Boolean isSpecial;

    /** Create a FuncType returning RETURNTYPE0, intiallly parapeterless. */
    public DefinedClassType(String className) {
        this.superclass = "object";
        this.className = className;
        this.isSpecial = true;
    }

    /** Create a FuncType for NAME0 with formal parameter types
     *  PARAMETERS0, returning type RETURNTYPE0. */
    @JsonCreator
    public DefinedClassType(String superclass, String className) {
        this.superclass = superclass;
        this.className = className;
        this.isSpecial = false;
    }

    public boolean isSpecialClass() {
        return isSpecial;
    }

    @Override
    public String className() {
        return className;
    }

    public String superClassName() {
        return superclass;
    }

    @Override
    public String toString() {
        return "<type>";
    }

}
