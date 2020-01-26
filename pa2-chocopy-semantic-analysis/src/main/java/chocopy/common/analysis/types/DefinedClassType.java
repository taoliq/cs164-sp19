package chocopy.common.analysis.types;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;

import chocopy.common.analysis.SymbolTable;

/** Semantic information for a function or method. */
public class DefinedClassType extends SymbolType {

    private final String superclass;
    //can not be extended
    private final Boolean isSpecial;
    public final SymbolTable<SymbolType> symbolTable;

    /** Create a FuncType returning RETURNTYPE0, intiallly parapeterless. */
    public DefinedClassType() {
        this.superclass = "object";
        this.isSpecial = true;
        this.symbolTable = new SymbolTable<>();
    }

    /** Create a FuncType for NAME0 with formal parameter types
     *  PARAMETERS0, returning type RETURNTYPE0. */
    @JsonCreator
    public DefinedClassType(String superclass) {
        this.superclass = superclass;
        this.isSpecial = false;
        this.symbolTable = new SymbolTable<>();
    }

    public DefinedClassType(String superclass, SymbolTable<SymbolType> sym) {
        this.superclass = superclass;
        this.isSpecial = false;
        this.symbolTable = sym;
    }

    public boolean isSpecialClass() {
        return isSpecial;
    }

    @Override
    public boolean isClassType() {
        return true;
    }

    @Override
    public String toString() {
        return "<type>";
    }

}
