package chocopy.pa2;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import chocopy.common.analysis.AbstractNodeAnalyzer;
//import chocopy.common.analysis.SymbolTable;
import chocopy.common.analysis.types.ClassValueType;
import chocopy.common.analysis.types.DefinedClassType;
import chocopy.common.analysis.types.FuncType;
import chocopy.common.analysis.types.SymbolType;
import chocopy.common.analysis.types.ValueType;
import chocopy.common.astnodes.*;
import chocopy.common.astnodes.Declaration;
import chocopy.common.astnodes.Errors;
import chocopy.common.astnodes.FuncDef;
import chocopy.common.astnodes.GlobalDecl;
import chocopy.common.astnodes.Identifier;
import chocopy.common.astnodes.NonLocalDecl;
import chocopy.common.astnodes.Program;
import chocopy.common.astnodes.TypedVar;
import chocopy.common.astnodes.VarDef;

/**
 * Analyzes declarations to create a top-level symbol table.
 */
public class DeclarationAnalyzer extends AbstractNodeAnalyzer<SymbolType> {

    /** Current symbol table.  Changes with new declarative region. */
    private SymbolTable<SymbolType> sym = new SymbolTable<>();
    /** Global symbol table. */
    private final SymbolTable<SymbolType> globals = sym;
    /** Receiver for semantic error messages. */
    private final Errors errors;

    /** ********************** */
    /** Symbol table stack. To track the parent symbol table. */
    private Stack<SymbolTable<SymbolType>> stk = new Stack<>();

    /** A new declaration analyzer sending errors to ERRORS0. */
    public DeclarationAnalyzer(Errors errors0) {
        errors = errors0;
    }


    public SymbolTable<SymbolType> getGlobals() {
        return globals;
    }

    private DefinedClassType initObjectClass() {
        List<ValueType> params = new ArrayList<>();
        params.add(ValueType.OBJECT_TYPE);
        FuncType initFunc = new FuncType(params, ValueType.NONE_TYPE);

        SymbolTable<SymbolType> objSym = new SymbolTable<>(globals);
        objSym.put("__init__", initFunc);
        globals.putScope("object", objSym);
        return new DefinedClassType(null, "object");
    }

    @Override
    public SymbolType analyze(Program program) {
        List<ValueType> params = new ArrayList<>();
        params.add(ValueType.OBJECT_TYPE);
        globals.put("print", new FuncType(params, ValueType.NONE_TYPE));
        globals.put("input", new FuncType(ValueType.STR_TYPE));
        globals.put("len", new FuncType(params, ValueType.INT_TYPE));
        // predefined type name
        globals.put("str", new DefinedClassType("object"));
        globals.put("bool", new DefinedClassType("object"));
        globals.put("int", new DefinedClassType("object"));
//        sym.put("<None>", SymbolType.NONE_TYPE);
        globals.put("object", initObjectClass());

        stk.push(globals);
        sym = stk.peek();

        for (Declaration decl : program.declarations) {

            Identifier id = decl.getIdentifier();
            String name = id.name;
            SymbolType type = decl.dispatch(this);

            if (type == null) {
                continue;
            }
            if (sym.declares(name)) {
                errors.semError(id,
                                "Duplicate declaration of identifier in same "
                                + "scope: %s",
                                name);
            } else {
                sym.put(name, type);
            }

        }



        return null;
    }

    @Override
    public SymbolType analyze(VarDef varDef) {
        return varDef.var.dispatch(this);
    }

    @Override
    public SymbolType analyze(TypedVar typedVar) {
        return typedVar.type.dispatch(this);
    }

    @Override
    public SymbolType analyze(ClassType type) {
        String name = type.className;
        if (name.equals("<None>")) {
            return SymbolType.NONE_TYPE;
        }
        return ValueType.annotationToValueType(type);
    }

    @Override
    public SymbolType analyze(ListType type) {
        return ValueType.annotationToValueType(type);
    }


    @Override
    public SymbolType analyze(FuncDef funcDef) {
        Identifier funcId = funcDef.getIdentifier();
        String funcName = funcId.name;
        SymbolTable<SymbolType> curSym = new SymbolTable<>(sym);

        stk.push(curSym);
        sym.putScope(funcName, curSym);
        sym = stk.peek();

        List<ValueType> params = new ArrayList<>();
        for (TypedVar var : funcDef.params) {
            Identifier id = var.identifier;
            String name = id.name;
            SymbolType type = var.dispatch(this);
            if (type == null) {
                continue;
            }
            if (sym.declares(name)) {
                errors.semError(id,
                                "Duplicate declaration of identifier in same "
                                + "scope: %s",
                                name);
            } else {
                sym.put(name, type);
                params.add((ValueType) type);
            }
        }

        for (Declaration decl : funcDef.declarations) {
            Identifier id = decl.getIdentifier();
            String name = id.name;
            SymbolType type = decl.dispatch(this);

            if (type == null) {
                continue;
            }

            if (sym.declares(name)) {
                errors.semError(id,
                                "Duplicate declaration of identifier in same "
                                + "scope: %s",
                                name);
            } else {
                sym.put(name, type);
            }
        }

        ValueType returnType = 
            (ValueType) funcDef.returnType.dispatch(this);
        sym.put("return", returnType);
        
        stk.pop();
        sym = stk.peek();
        return new FuncType(params, returnType);
    }

    @Override 
    public SymbolType analyze(ClassDef classDef) {
        Identifier superId = classDef.superClass;
        String superClassName = superId.name;
        String className = classDef.name.name;
        if (!globals.declares(superClassName)) {
            errors.semError(superId,
                            "Super-class not defined: %s",
                            superClassName);
            return null;
        }
        if (!(globals.get(superClassName) instanceof DefinedClassType)) {
            errors.semError(superId,
                            "Super-class must be a class: %s",
                            superClassName);
            return null;
        }
        if (((DefinedClassType) globals.get(superClassName)).isSpecialClass()) {
            errors.semError(superId,
                            "Cannot extend special class: %s",
                            superClassName);
            return null;
        }

        SymbolTable<SymbolType> superSym = globals.getScope(superClassName);
        SymbolTable<SymbolType> curSym = new SymbolTable<>(superSym);
        globals.putScope(className, curSym);

        stk.push(curSym);
        sym = stk.peek();
        sym.put(className, new DefinedClassType(superClassName, className));
        //check attributes and methods
        for (Declaration decl : classDef.declarations) {
            Identifier id = decl.getIdentifier();
            String name = id.name;
            SymbolType type = decl.dispatch(this);

            if (type == null) {
                continue;
            }

            if (sym.declares(name)) {
                errors.semError(id,
                        "Duplicate declaration of identifier in same "
                                + "scope: %s",
                        name);
                continue;
            }
            if (globals.declares(name)
                    && globals.get(name) instanceof DefinedClassType) {
                // name is the defined type
                errors.semError(id,
                        "Cannot shadow class name: %s",
                        name);
                continue;
            }
            if (type instanceof ValueType && superSym.declares(name)) {
                errors.semError(id,
                        "Cannot re-define attribute: %s",
                        name);
                continue;
            }

            sym.put(name, type);
        }
        stk.pop();
        sym = stk.peek();
        return new DefinedClassType(superClassName, className);
    }

}
