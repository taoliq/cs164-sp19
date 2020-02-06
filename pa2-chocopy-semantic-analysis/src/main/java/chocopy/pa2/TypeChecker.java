package chocopy.pa2;

import chocopy.common.astnodes.*;
import chocopy.common.analysis.AbstractNodeAnalyzer;
// import chocopy.common.analysis.SymbolTable;
import chocopy.common.analysis.types.*;
import static chocopy.common.analysis.types.SymbolType.*;

import java.net.http.WebSocket.Listener;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;


/** Analyzer that performs ChocoPy type checks on all nodes.  Applied after
 *  collecting declarations. */
public class TypeChecker extends AbstractNodeAnalyzer<SymbolType> {

    /** The current symbol table (changes depending on the function
     *  being analyzed). */
    private SymbolTable<SymbolType> sym;
    /** Collector for errors. */
    private Errors errors;

    /************************ */
    /** Symbol table stack. To track the parent symbol table. */
    private Stack<SymbolTable<SymbolType>> stk = new Stack<>();

    /** Creates a type checker using GLOBALSYMBOLS for the initial global
     *  symbol table and ERRORS0 to receive semantic errors. */
    public TypeChecker(SymbolTable<SymbolType> globalSymbols, Errors errors0) {
        sym = globalSymbols;
        errors = errors0;
    }

    /** Inserts an error message in NODE if there isn't one already.
     *  The message is constructed with MESSAGE and ARGS as for
     *  String.format. */
    private void err(Node node, String message, Object... args) {
        errors.semError(node, message, args);
    }

    @Override
    public SymbolType analyze(Program program) {
        stk.push(sym);
        for (Declaration decl : program.declarations) {
            decl.dispatch(this);
        }
        for (Stmt stmt : program.statements) {
            stmt.dispatch(this);
        }
        stk.pop();
        return null;
    }

    @Override
    public SymbolType analyze(VarDef varDef) {
        SymbolTable<SymbolType> curSym = stk.peek();
        String varName = varDef.var.identifier.name;
        SymbolType varType = curSym.get(varName);
        SymbolType valType = varDef.value.dispatch(this);

        if (!isTypeCompatible(varType, valType)) {
            err(varDef,
                    "Expected type `%s`; got type `%s`",
                    varType, valType);
        }
        return null;
    }  

    @Override
    public SymbolType analyze(FuncDef funcDef) {
        Identifier id = funcDef.getIdentifier();
        SymbolTable<SymbolType> curSym = stk.peek().getScope(id.name);
        stk.push(curSym);
        for (Declaration decl : funcDef.declarations) {
            decl.dispatch(this);
        }
        for (Stmt stmt : funcDef.statements) {
            stmt.dispatch(this);
        }
        stk.pop();
        return null;
    }


    @Override
    public SymbolType analyze(ClassDef classDef) {
        Identifier id = classDef.getIdentifier();
        SymbolTable<SymbolType> curSym = sym.getScope(id.name);
        stk.push(curSym);
        for (Declaration decl : classDef.declarations) {
            decl.dispatch(this);
        }
        stk.pop();
        return null;
    }

    @Override
    public SymbolType analyze(ExprStmt s) {
        s.expr.dispatch(this);
        return null;
    }

    @Override
    public SymbolType analyze(MethodCallExpr methodCallExpr) {
        SymbolType methodType = methodCallExpr.method.dispatch(this);
        List<ValueType> params = null;
        List<SymbolType> args = new ArrayList<>();

        if (!methodType.isFuncType()) {
            SymbolType objType = methodCallExpr.method.object.getInferredType();
            String className = objType.className();
            String attrName = methodCallExpr.method.member.name;

            err(methodCallExpr,
                    "There is no method named `%s` in class `%s`",
                    attrName, className);
            return methodType;
        }
        for (Expr e : methodCallExpr.args) {
            args.add(e.dispatch(this));
        }
        params = ((FuncType) methodType).parameters;
        checkParametersType(methodCallExpr, params.subList(1, params.size()) ,args);
        return methodCallExpr.setInferredType(((FuncType) methodType).returnType);
    }

    private void checkParametersType(
            Node node, List<ValueType> params, List<SymbolType> args) {
        if (params.size() != args.size()) {
            err(node,
                    "Expected %d arguments; got %d",
                    params.size(), args.size());
            return;
        }
        for (int i = 0; i < params.size(); i++) {
            SymbolType param = params.get(i);
            SymbolType arg = args.get(i);
            if (!isAncestor(param, arg)) {
                err(node,
                        "Expected type `%s`; got type `%s` in parameter %d",
                        param, arg, i+1);
            }
        }
    }

    @Override
    public SymbolType analyze(CallExpr callExpr) {
        SymbolTable<SymbolType> curSym = stk.peek();
//        callExpr.function.dispatch(this);
        String funcName = callExpr.function.name;
        SymbolType funcType = curSym.get(funcName);
        SymbolType rtnType = SymbolType.OBJECT_TYPE;
        List<ValueType> params = new ArrayList<>();
        List<SymbolType> args = new ArrayList<>();

        for (Expr e : callExpr.args) {
            args.add(e.dispatch(this));
        }

        if (funcType instanceof FuncType) {
            callExpr.function.dispatch(this);
            params = ((FuncType) funcType).parameters;
            rtnType = ((FuncType) funcType).returnType;
        } else if (funcType instanceof DefinedClassType) {
            params = ((FuncType) sym.getScope(funcName).get("__init__")).parameters;
            params = params.subList(1, params.size());
            rtnType = new ClassValueType(funcType.className());
        } else {
            err(callExpr,
                "Not a function or class: %s",
                funcName);
            return callExpr.setInferredType(OBJECT_TYPE);
        }

        checkParametersType(callExpr, params, args);

        return callExpr.setInferredType(rtnType);
    }

    @Override
    public SymbolType analyze(ReturnStmt s) {
        SymbolType t = null;
        SymbolType exp = stk.peek().get("return");
        if (s.value != null) {
            t = s.value.dispatch(this);
        }
        if (!isAncestor(exp, t)) {
            err(s,
                    "Expected type `%s`; got %s",
                    exp, t == null ? "`None`" : "type `" + t + "`");
        }
        return null;
    }

    @Override
    public SymbolType analyze(IntegerLiteral i) {
        return i.setInferredType(INT_TYPE);
    }

    @Override
    public SymbolType analyze(StringLiteral i) {
        return i.setInferredType(STR_TYPE);
    }

    @Override
    public SymbolType analyze(BooleanLiteral i) {
        return i.setInferredType(BOOL_TYPE);
    }

    @Override
    public SymbolType analyze(NoneLiteral i) {
        return i.setInferredType(NONE_TYPE);
    }

    @Override
    public SymbolType analyze(ListExpr listExpr) {
        if (listExpr.elements == null || listExpr.elements.size() == 0) {
            return listExpr.setInferredType(EMPTY_TYPE);
        }
        SymbolType commonType = null;
        for (Expr e : listExpr.elements) {
            SymbolType eleType = e.dispatch(this);
            commonType = getCommonAncestor(commonType, eleType);
        }
        return listExpr.setInferredType(new ListValueType(commonType));
    }

    private SymbolType getCommonAncestor(SymbolType t1, SymbolType t2) {
        if (t1 == null) {
            return t2;
        }
        Stack<String> stk1 = getAllAncestors(t1);
        Stack<String> stk2 = getAllAncestors(t2);
        String ancestorName = null;
        while (!stk1.empty() && !stk2.empty()) {
            String s1 = stk1.pop();
            String s2 = stk2.pop();
            if (s1.equals(s2)) {
                ancestorName = s1;
            }
        }
        return new ClassValueType(ancestorName);
    }

    private Stack<String> getAllAncestors(SymbolType t) {
        Stack<String> stk = new Stack<>();
        String className = t.className();
        while (className != null) {
            stk.push(className);
            className = ((DefinedClassType) sym.get(className)).superClassName();
        }
        return stk;
    }

    
    @Override
    public SymbolType analyze(WhileStmt whileStmt) {
        whileStmt.condition.dispatch(this);
        for (Stmt stmt : whileStmt.body) {
            stmt.dispatch(this);
        }
        return null;
    }

    
    @Override
    public SymbolType analyze(ForStmt forStmt) {
        forStmt.identifier.dispatch(this);
        forStmt.iterable.dispatch(this);
        for (Stmt stmt : forStmt.body) {
            stmt.dispatch(this);
        }
        return null;
    }

    @Override
    public SymbolType analyze(IfStmt ifStmt) {
        ifStmt.condition.dispatch(this);
        for (Stmt stmt : ifStmt.elseBody) {
            stmt.dispatch(this);
        }
        for (Stmt stmt : ifStmt.thenBody) {
            stmt.dispatch(this);
        }
        return null;
    }

    @Override
    public SymbolType analyze(AssignStmt assignStmt) {
        SymbolType t1 = null;
        SymbolType t2 = assignStmt.value.dispatch(this);
        for (Expr target : assignStmt.targets) {
            t1 = target.dispatch(this);
            if (target instanceof IndexExpr && t1.equals(STR_TYPE)) {
                err(target,
                        "`%s` is not a list type",
                        t1);
            }
            if (!isTypeCompatible(t1, t2)) {
                err(assignStmt, 
                    "Expected type `%s`; got type `%s`", 
                    t1, t2);
            }
        }
        
        return null;
    }

    private boolean isTypeCompatible(SymbolType target, SymbolType value) {
//        System.out.println("target: " + target);
//        System.out.println("value: " + value);
        if (target == null || value == null) {
            return false;
        }
        if (isAncestor(target, value)) {
            return true;
        }
        if (!target.isSpecialType() && value.equals(NONE_TYPE)) {
            return true;
        }
        if (target.isListType() && !target.elementType().isListType()
                && value.equals(EMPTY_TYPE)) {
            return true;
        }
        if (target.isListType() && value.isListType()) {
            SymbolType t1 = target.elementType();
            SymbolType t2 = value.elementType();
            return !t1.isSpecialType() && t2.equals(NONE_TYPE);
        }
        return false;
    }

    private boolean isAncestor(SymbolType parent, SymbolType child) {
        if (parent == null || child == null) {
            return false;
        }
        //Special type check
        if (parent.equals(OBJECT_TYPE) &&
                (child.equals(EMPTY_TYPE)
                        || child.equals(NONE_TYPE)
                        || child.isListType())) {
            return true;
        }
        if (parent.isListType() && child.isListType()) {
            return parent.elementType().equals(child.elementType());
        }
        if (!(parent instanceof ClassValueType)
                || !(child instanceof ClassValueType)) {
            return false;
        }
        String pName = parent.className();
        String cName = child.className();
        while (!pName.equals(cName)) {
            if (pName == null || cName == null || sym.get(cName) == null) {
                return false;
            }
            cName = ((DefinedClassType) sym.get(cName)).superClassName();
        }
        return true;
    }

    @Override
    public SymbolType analyze(MemberExpr memberExpr) {
        SymbolType objType = memberExpr.object.dispatch(this);
//        System.out.println(objType);

        String className = objType.className();
        SymbolTable<SymbolType> classSym = sym.getScope(className);
        String attrName = memberExpr.member.name;
        if (classSym == null || classSym.get(attrName) == null) {
            err(memberExpr,
                "There is no attribute named `%s` in class `%s`",
                attrName, className);
            return OBJECT_TYPE;
        }
        SymbolType attrType = classSym.get(attrName);
        return memberExpr.setInferredType(attrType);
    }

    @Override
    public SymbolType analyze(IndexExpr indexExpr) {
        SymbolType t1 = indexExpr.list.dispatch(this);
        SymbolType t2 = indexExpr.index.dispatch(this);

        if (!t2.equals(INT_TYPE)) {
            err(indexExpr,
                "Index is of non-integer type `%s`",
                t2);
        }

        if (t1.isListType()) {
            t1 = ((ListValueType) t1).elementType;
        } else if (!t1.equals(STR_TYPE)) {
            err(indexExpr,
                    "Cannot index into type `%s`",
                    t1);
            return indexExpr.setInferredType(OBJECT_TYPE);
        }
        return indexExpr.setInferredType(t1);
    }

    @Override
    public SymbolType analyze(IfExpr e) {
        e.condition.dispatch(this);
        SymbolType t1 = e.thenExpr.dispatch(this);
        SymbolType t2 = e.elseExpr.dispatch(this);
        SymbolType commonType = getCommonAncestor(t1, t2);
        return e.setInferredType(commonType);
    }

    @Override
    public SymbolType analyze(BinaryExpr e) {
        SymbolType t1 = e.left.dispatch(this);
        SymbolType t2 = e.right.dispatch(this);

        switch (e.operator) {
        case "is":
            if (!t1.isSpecialType() && !t2.isSpecialType()) {
                return e.setInferredType(BOOL_TYPE);
            } else {
                err(e, "Cannot apply operator `%s` on types `%s` and `%s`",
                    e.operator, t1, t2);
                return e.setInferredType(BOOL_TYPE);
            }
        case "or":
        case "and":
            if (BOOL_TYPE.equals(t1) && BOOL_TYPE.equals(t2)) {
                return e.setInferredType(BOOL_TYPE);
            } else {
                err(e, "Cannot apply operator `%s` on types `%s` and `%s`",
                    e.operator, t1, t2);
                return e.setInferredType(BOOL_TYPE);
            }
        case "<":
        case ">":
        case "<=":
        case ">=":
            if (INT_TYPE.equals(t1) && INT_TYPE.equals(t2)) {
                return e.setInferredType(BOOL_TYPE);
            } else {
                err(e, "Cannot apply operator `%s` on types `%s` and `%s`",
                    e.operator, t1, t2);
                return e.setInferredType(BOOL_TYPE);
            }
        case "==":
        case "!=":
            if (INT_TYPE.equals(t1) && INT_TYPE.equals(t2)) {
                return e.setInferredType(BOOL_TYPE);
            } else if (BOOL_TYPE.equals(t1) && BOOL_TYPE.equals(t2)) {
                return e.setInferredType(BOOL_TYPE);
            } else if (STR_TYPE.equals(t1) && STR_TYPE.equals(t2)){
                return e.setInferredType(BOOL_TYPE);
            } else {
                err(e, "Cannot apply operator `%s` on types `%s` and `%s`",
                    e.operator, t1, t2);
                return e.setInferredType(BOOL_TYPE);
            }
        case "-":
        case "*":
        case "//":
        case "%":
            if (INT_TYPE.equals(t1) && INT_TYPE.equals(t2)) {
                return e.setInferredType(INT_TYPE);
            } else {
                err(e, "Cannot apply operator `%s` on types `%s` and `%s`",
                    e.operator, t1, t2);
                return e.setInferredType(INT_TYPE);
            }
        case "+":
            if (INT_TYPE.equals(t1) && INT_TYPE.equals(t2)) {
                return e.setInferredType(INT_TYPE);
            } else if (STR_TYPE.equals(t1) && STR_TYPE.equals(t2)){
                return e.setInferredType(STR_TYPE);
            } else if (t1.isListType() && t2.isListType()){
                SymbolType ele1 = t1.elementType();
                SymbolType ele2 = t2.elementType();
                SymbolType resType = getCommonAncestor(ele1, ele2);
                return e.setInferredType(new ListValueType(resType));
            } else if (INT_TYPE.equals(t1) || INT_TYPE.equals(t2)){
                err(e, "Cannot apply operator `%s` on types `%s` and `%s`",
                    e.operator, t1, t2);
                return e.setInferredType(INT_TYPE);
            } else {
                err(e, "Cannot apply operator `%s` on types `%s` and `%s`",
                    e.operator, t1, t2);
                return e.setInferredType(OBJECT_TYPE);
            }
        default:
            return e.setInferredType(OBJECT_TYPE);
        }

    }

    @Override
    public SymbolType analyze(UnaryExpr e) {
        SymbolType t = e.operand.dispatch(this);
        switch (e.operator) {
        case "-":
            if (!INT_TYPE.equals(t)) {
                err(e, "Cannot apply operator `%s` on type `%s`",
                    e.operator, t);
            }
            return e.setInferredType(INT_TYPE);
        case "not":
            if (!BOOL_TYPE.equals(t)) {
                err(e, "Cannot apply operator `%s` on type `%s`",
                    e.operator, t);
            }
            return e.setInferredType(BOOL_TYPE);
        default:
            return e.setInferredType(OBJECT_TYPE);
        }
    }

    @Override
    public SymbolType analyze(Identifier id) {
        SymbolTable<SymbolType> curSym = stk.peek();
        String varName = id.name;
        SymbolType varType = curSym.get(varName);

        if (varType != null) {
            if (varType instanceof DefinedClassType) {
                varType = SymbolType.OBJECT_TYPE;
            }
            return id.setInferredType(varType);
        }

        err(id, "Not a variable: %s", varName);
        return id.setInferredType(ValueType.OBJECT_TYPE);
    }
}
