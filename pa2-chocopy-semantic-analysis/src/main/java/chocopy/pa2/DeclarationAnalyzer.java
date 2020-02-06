package chocopy.pa2;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import chocopy.common.analysis.AbstractNodeAnalyzer;
// import chocopy.common.analysis.SymbolTable;
import chocopy.common.analysis.types.ClassValueType;
import chocopy.common.analysis.types.DefinedClassType;
import chocopy.common.analysis.types.FuncType;
import chocopy.common.analysis.types.SymbolType;
import chocopy.common.analysis.types.ValueType;
import chocopy.common.astnodes.*;

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

    /*************************/
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

        SymbolTable<SymbolType> objSym = new SymbolTable<>();
        objSym.put("__init__", initFunc);
        sym.putScope("object", objSym);
        return new DefinedClassType(null, "object");
    }

    @Override
    public SymbolType analyze(Program program) {
        List<ValueType> params = new ArrayList<>();
        params.add(ValueType.OBJECT_TYPE);
        sym.put("print", new FuncType(params, ValueType.NONE_TYPE));
        sym.put("input", new FuncType(ValueType.STR_TYPE));
        sym.put("len", new FuncType(params, ValueType.INT_TYPE));
        // predefined type name
        sym.put("str", new DefinedClassType("object"));
        sym.put("bool", new DefinedClassType("object"));
        sym.put("int", new DefinedClassType("object"));
//        sym.put("<None>", SymbolType.NONE_TYPE);
        sym.put("object", initObjectClass());

        stk.push(sym);

        List<FuncDef> funcDefList = new ArrayList<>();
        for (Declaration decl : program.declarations) {
            if (decl instanceof FuncDef) {
                funcDefList.add((FuncDef) decl);
                continue;
            }

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
        for (FuncDef funcDef : funcDefList) {
            Identifier id = funcDef.getIdentifier();
            String name = id.name;
            SymbolType type = funcDef.dispatch(this);

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
        if (!existsClassName(name)) {
            errors.semError(type,
                            "Invalid type annotation; "
                            + "there is no class named: %s",
                            name);
            return null;
        }
        return ValueType.annotationToValueType(type);
    }

    private boolean existsClassName(String name) {
        if (globals.declares(name) 
                && (globals.get(name) instanceof DefinedClassType)) {
            return true;
        }

        SymbolTable<SymbolType> curSym = stk.peek();
        if (curSym.get(name) != null 
                && (curSym.get(name) instanceof DefinedClassType)) {
            return true;
        }
        
        return false;
    }

    @Override
    public SymbolType analyze(ListType type) {
        return ValueType.annotationToValueType(type);
    }
    
    @Override
    public SymbolType analyze(GlobalDecl globalDecl) {
        SymbolTable<SymbolType> curSym = stk.peek();
        Identifier id = globalDecl.getIdentifier();
        String name = id.name;
        if (curSym.declares(name)) {
            errors.semError(id,
                            "Duplicate declaration of identifier in same "
                            + "scope: %s",
                            name);
            return null;
        } else if (!globals.declares(name)  || !globals.get(name).isValueType()) {
            errors.semError(id,
                            "Not a global variable: %s",
                            name);
            return null;
        }
        return globals.get(name);
    }

    @Override
    public SymbolType analyze(NonLocalDecl nonLocalDecl) {
        SymbolTable<SymbolType> curSym = stk.peek();
        SymbolTable<SymbolType> outerSym = curSym.getParent();
        Identifier id = nonLocalDecl.getIdentifier();
        String name = id.name;
        
        if (curSym.declares(name)) {
            errors.semError(id,
                            "Duplicate declaration of identifier in same "
                            + "scope: %s",
                            name);
            return null;
        } else if (outerSym == globals 
                    || !outerSym.declares(name) 
                    || !outerSym.get(name).isValueType()) {
            errors.semError(id,
                            "Not a nonlocal variable: %s",
                            name);
            return null;
        }
        return outerSym.get(name);
    }


    @Override
    public SymbolType analyze(FuncDef funcDef) {
        Identifier funcId = funcDef.getIdentifier();
        String funcName = funcId.name;
        SymbolTable<SymbolType> curSym = new SymbolTable<>(stk.peek());
        stk.peek().putScope(funcName, curSym);
        stk.push(curSym);

        List<ValueType> params = new ArrayList<>();
        for (TypedVar var : funcDef.params) {
            Identifier id = var.identifier;
            String name = id.name;
            SymbolType type = var.dispatch(this);
            if (type == null) {
                continue;
            }
            if (curSym.declares(name)) {
                errors.semError(id,
                                "Duplicate declaration of identifier in same "
                                + "scope: %s",
                                name);
            } else if (globals.declares(name) 
                        && globals.get(name) instanceof DefinedClassType) {
                // name is the defined type
                errors.semError(id,
                                "Cannot shadow class name: %s",
                                name);
            } else {
                curSym.put(name, type);
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

            if (curSym.declares(name)) {
                errors.semError(id,
                                "Duplicate declaration of identifier in same "
                                + "scope: %s",
                                name);
            } else if (globals.declares(name) 
                        && globals.get(name) instanceof DefinedClassType) {
                // name is the defined type
                errors.semError(id,
                                "Cannot shadow class name: %s",
                                name);
            } else {
                curSym.put(name, type);
            }
        }

        for (Stmt stmt : funcDef.statements) {
            stmt.dispatch(this);
        }

        ValueType returnType = 
            (ValueType) funcDef.returnType.dispatch(this);

        curSym.put("return", returnType);
        
        stk.pop();
        return new FuncType(params, returnType);
    }

    @Override 
    public SymbolType analyze(AssignStmt stmt) {
        SymbolTable<SymbolType> curSym = stk.peek();
        for (Expr expr : stmt.targets) {
            if (expr instanceof Identifier) {
                Identifier id = (Identifier) expr ;
                String name = id.name;
                if (!curSym.declares(name)) {
                    errors.semError(id,
                            "Cannot assign to variable that is " 
                            + "not explicitly declared in this scope: %s",
                            name);
                }
            }
        }
        return null;
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
        } else if (!(globals.get(superClassName) instanceof DefinedClassType)) {
            errors.semError(superId,
                            "Super-class must be a class: %s",
                            superClassName);
            return null;
        } else if (((DefinedClassType) globals.get(superClassName)).isSpecialClass()) {
            errors.semError(superId,
                            "Cannot extend special class: %s",
                            superClassName);
            return null;
        }

        // SymbolTable<SymbolType> superSym = 
        //         ((DefinedClassType) globals.get(superClassName)).symbolTable;
        
        SymbolTable<SymbolType> superSym = globals.getScope(superClassName);
        SymbolTable<SymbolType> curSym = new SymbolTable<>(superSym);
        stk.push(curSym);
        curSym.put(className, new DefinedClassType(superClassName, className));
        //check attributes and methods
        for (Declaration decl : classDef.declarations) {
            Identifier id = decl.getIdentifier();
            String name = id.name;
            SymbolType type = decl.dispatch(this);

            if (type == null) {
                continue;
            }

            if (curSym.declares(name)) {
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
            if (type instanceof FuncType) {
                checkValidClassMethod(className, id, (FuncType) type);
            }
            curSym.put(name, type);
        }
        stk.pop();
        globals.putScope(className, curSym);
        return new DefinedClassType(superClassName, className);
    }

    private void checkValidClassMethod(String className, Identifier funcId, FuncType funcType) {
        String funcName = funcId.name;
        if (!isValidFirstParameter(className, funcType)) {
            errors.semError(funcId,
                            "First parameter of the following method " 
                            + "must be of the enclosing class: %s",
                            funcName);
            return;
        }

        if (!isValidMethodName(funcName)) {
            errors.semError(funcId,
                            "Cannot re-define attribute: %s",
                            funcName);
            return;
        }

        if (!isSameMethodSignatures(funcName, funcType)) {
            errors.semError(funcId, 
                            "Method overridden with different type signature: %s", 
                            funcName);
            return;
        }
    }

    private boolean isValidFirstParameter(String className, FuncType funcType) {
        if (funcType.parameters.size() == 0) {
            return false;
        }

        String fstParam = funcType.getParamType(0).className();
        return fstParam.equals(className);
    }

    private boolean isValidMethodName(String funcName) {
        SymbolTable<SymbolType> superSym = stk.peek().getParent();
        if (superSym.get(funcName) != null
                && superSym.get(funcName) instanceof ValueType) {
            return false;
        }
        return true;
    }

    private boolean isSameMethodSignatures(String funcName, FuncType funcType) {
        SymbolTable<SymbolType> superSym = stk.peek().getParent();
        FuncType superFuncType = (FuncType) superSym.get(funcName);

        if (superFuncType == null) {
            return true;
        }

        if (superFuncType.parameters.size() != funcType.parameters.size()) {
            return false;
        }

        if (funcName.equals("__init__") && funcType.parameters.size() == 1) {
            return true;
        }

        for (int i = 1; i < funcType.parameters.size(); i++) {
            ValueType superParam = superFuncType.parameters.get(i);
            ValueType param = funcType.parameters.get(i);
            if (!superParam.equals(param)) {
                return false;
            }
        }

        if (!superFuncType.returnType.equals(funcType.returnType)) {
            return false;
        }

        return true;
    } 

}
