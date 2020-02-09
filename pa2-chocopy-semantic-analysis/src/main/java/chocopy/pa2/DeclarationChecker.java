package chocopy.pa2;

import chocopy.common.analysis.AbstractNodeAnalyzer;
import chocopy.common.analysis.types.DefinedClassType;
import chocopy.common.analysis.types.FuncType;
import chocopy.common.analysis.types.SymbolType;
import chocopy.common.analysis.types.ValueType;
import chocopy.common.astnodes.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

// import chocopy.common.analysis.SymbolTable;

/**
 * Analyzes declarations to create a top-level symbol table.
 */
public class DeclarationChecker extends AbstractNodeAnalyzer<SymbolType> {

    /** Current symbol table.  Changes with new declarative region. */
    private SymbolTable<SymbolType> sym = null;
    /** Global symbol table. */
    private SymbolTable<SymbolType> globals;
    /** Receiver for semantic error messages. */
    private final Errors errors;

    /** Symbol table stack. To track the parent symbol table. */
    private Stack<SymbolTable<SymbolType>> stk = new Stack<>();

    /** Creates a type checker using GLOBALSYMBOLS for the initial global
     *  symbol table and ERRORS0 to receive semantic errors. */
    public DeclarationChecker(SymbolTable<SymbolType> globalSymbols, Errors errors0) {
        globals = globalSymbols;
        errors = errors0;
    }

    @Override
    public SymbolType analyze(Program program) {
        stk.push(globals);
        sym = stk.peek();

        for (Declaration decl : program.declarations) {
            decl.dispatch(this);
        }

        for (Stmt stmt : program.statements) {
            if (stmt instanceof ReturnStmt) {
                errors.semError(stmt,
                        "Return statement cannot appear at the top level");
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
        // avoid shadow class name in previous declaration analyzer
        if (globals.declares(name)
                && (globals.get(name) instanceof DefinedClassType)) {
            return true;
        }

        return sym.get(name) != null
                && (sym.get(name) instanceof DefinedClassType);
    }

    @Override
    public SymbolType analyze(ListType type) {
        return ValueType.annotationToValueType(type);
    }
    
    @Override
    public SymbolType analyze(GlobalDecl globalDecl) {
        Identifier id = globalDecl.getIdentifier();
        String name = id.name;
        if (sym.declares(name)) {
            errors.semError(id,
                            "Duplicate declaration of identifier in same "
                            + "scope: %s",
                            name);
            return null;
        }
        if (!globals.declares(name)  || !globals.get(name).isValueType()) {
            errors.semError(id,
                            "Not a global variable: %s",
                            name);
            return null;
        }
        return globals.get(name);
    }

    @Override
    public SymbolType analyze(NonLocalDecl nonLocalDecl) {
        SymbolTable<SymbolType> outerSym = sym.getParent();
        Identifier id = nonLocalDecl.getIdentifier();
        String name = id.name;
        
        if (sym.declares(name)) {
            errors.semError(id,
                            "Duplicate declaration of identifier in same "
                            + "scope: %s",
                            name);
            return null;
        }
        if (outerSym == globals
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

        sym = sym.getScope(funcName);
        if (sym == null) {
            System.out.println(funcName + " scope is empty!!!!");
        }
        stk.push(sym);

        for (TypedVar var : funcDef.params) {
            Identifier id = var.identifier;
            String name = id.name;
            SymbolType type = var.dispatch(this);
//            if (type == null) {
//                continue;
//            }
            if (existsClassName(name)) {
                // name is the defined type
                errors.semError(id,
                                "Cannot shadow class name: %s",
                                name);
            }
        }

        for (Declaration decl : funcDef.declarations) {
            Identifier id = decl.getIdentifier();
            String name = id.name;
            SymbolType type = decl.dispatch(this);

            // global and nonlocal variable
            if (type != null && !sym.declares(name)) {
                sym.put(name, type);
            }

            if (existsClassName(name)) {
                // name is the defined type
                errors.semError(id,
                                "Cannot shadow class name: %s",
                                name);
            }
        }

        for (Stmt stmt : funcDef.statements) {
            stmt.dispatch(this);
        }

        funcDef.returnType.dispatch(this);

        stk.pop();
        sym = stk.peek();
        return sym.get(funcName);
    }

    @Override 
    public SymbolType analyze(AssignStmt stmt) {
        for (Expr expr : stmt.targets) {
            if (expr instanceof Identifier) {
                Identifier id = (Identifier) expr ;
                String name = id.name;
//                System.out.println("assgin var name: " + name);
                if (!sym.declares(name)) {
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

        sym = globals.getScope(className);
        if (sym == null) {
            sym = stk.peek();
            System.out.println(className + " scope is empty!!!!");
            return null;
        }
        stk.push(sym);

        //check attributes and methods
        for (Declaration decl : classDef.declarations) {
            Identifier id = decl.getIdentifier();
//            String name = id.name;
            SymbolType type = decl.dispatch(this);

            if (type == null) {
                continue;
            }

            if (type instanceof FuncType) {
                checkValidClassMethod(className, id, (FuncType) type);
            }
        }
        stk.pop();
        sym = stk.peek();
        return globals.get(className);
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
        SymbolTable<SymbolType> superSym = sym.getParent();
        return superSym.get(funcName) == null
                || !(superSym.get(funcName) instanceof ValueType);
    }

    private boolean isSameMethodSignatures(String funcName, FuncType funcType) {
        SymbolTable<SymbolType> superSym = sym.getParent();
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

        return superFuncType.returnType.equals(funcType.returnType);
    } 

}
