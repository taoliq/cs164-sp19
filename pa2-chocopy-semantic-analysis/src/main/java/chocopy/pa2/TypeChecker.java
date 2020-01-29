package chocopy.pa2;

// import chocopy.common.analysis.SymbolTable;
import chocopy.common.analysis.types.SymbolType;
import chocopy.common.analysis.types.ValueType;
import chocopy.common.astnodes.AssignStmt;
import chocopy.common.astnodes.BinaryExpr;
import chocopy.common.astnodes.BooleanLiteral;
import chocopy.common.astnodes.Declaration;
import chocopy.common.astnodes.Errors;
import chocopy.common.astnodes.ExprStmt;
import chocopy.common.astnodes.FuncDef;
import chocopy.common.astnodes.Identifier;
import chocopy.common.astnodes.IntegerLiteral;
import chocopy.common.astnodes.ListExpr;
import chocopy.common.astnodes.Node;
import chocopy.common.astnodes.NoneLiteral;
import chocopy.common.astnodes.Program;
import chocopy.common.astnodes.Stmt;
import chocopy.common.astnodes.StringLiteral;
import chocopy.common.astnodes.UnaryExpr;
import chocopy.common.astnodes.IfExpr;
import chocopy.common.analysis.AbstractNodeAnalyzer;
import chocopy.common.analysis.types.*;
import static chocopy.common.analysis.types.SymbolType.*;

import java.net.http.WebSocket.Listener;
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
    public SymbolType analyze(ExprStmt s) {
        s.expr.dispatch(this);
        return null;
    }

    @Override
    public SymbolType analyze(IntegerLiteral i) {
        return i.setInferredType(SymbolType.INT_TYPE);
    }

    @Override
    public SymbolType analyze(StringLiteral i) {
        return i.setInferredType(SymbolType.STR_TYPE);
    }

    @Override
    public SymbolType analyze(BooleanLiteral i) {
        return i.setInferredType(SymbolType.BOOL_TYPE);
    }

    @Override
    public SymbolType analyze(NoneLiteral i) {
        return i.setInferredType(SymbolType.NONE_TYPE);
    }

    @Override
    public SymbolType analyze(ListExpr e) {
        if (e.elements == null || e.elements.size() == 0) {
            return e.setInferredType(SymbolType.EMPTY_TYPE);
        }

        SymbolType eleType = e.elements.get(0).dispatch(this);
        return e.setInferredType(new ListValueType(eleType));
    }

    @Override
    public SymbolType analyze(AssignStmt e) {
        e.value.dispatch(this);
        return null;
    }

    @Override
    public SymbolType analyze(IfExpr e) {
        e.condition.dispatch(this);
        SymbolType t = e.thenExpr.dispatch(this);
        e.elseExpr.dispatch(this);
        return e.setInferredType(t);
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
                // TODO
                // need more check
                return e.setInferredType(t1);
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
                err(e, "Cannot apply operator `%s` on types `%s`",
                    e.operator, t);
            }
            return e.setInferredType(INT_TYPE);
        case "not":
            if (!BOOL_TYPE.equals(t)) {
                err(e, "Cannot apply operator `%s` on types `%s`",
                    e.operator, t);
            }
            return e.setInferredType(BOOL_TYPE);
        default:
            return e.setInferredType(OBJECT_TYPE);
        }
    }

    @Override
    public SymbolType analyze(Identifier id) {
        String varName = id.name;
        SymbolType varType = sym.get(varName);

        if (varType != null && varType.isValueType()) {
            return id.setInferredType(varType);
        }

        err(id, "Not a variable: %s", varName);
        return id.setInferredType(ValueType.OBJECT_TYPE);
    }
}
