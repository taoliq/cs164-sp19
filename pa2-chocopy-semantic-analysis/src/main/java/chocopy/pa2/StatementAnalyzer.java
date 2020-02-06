package chocopy.pa2;

import chocopy.common.analysis.AbstractNodeAnalyzer;
// import chocopy.common.analysis.SymbolTable;
import chocopy.common.analysis.types.ClassValueType;
import chocopy.common.analysis.types.DefinedClassType;
import chocopy.common.analysis.types.FuncType;
import chocopy.common.analysis.types.SymbolType;
import chocopy.common.analysis.types.ValueType;
import chocopy.common.astnodes.*;

import java.net.http.WebSocket.Listener;
import java.util.Stack;


/** Analyzer that performs ChocoPy type checks on all nodes.  Applied after
 *  collecting declarations. */
public class StatementAnalyzer extends AbstractNodeAnalyzer<SymbolType> {

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
    public StatementAnalyzer(SymbolTable<SymbolType> globalSymbols, Errors errors0) {
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
        for (Stmt stmt : program.statements) {
            stmt.dispatch(this);
        }
        stk.pop();
        return null;
    }

    @Override
    public SymbolType analyze(ExprStmt stmt) {
        stmt.expr.dispatch(this);
        return null;
    }

    @Override
    public SymbolType analyze(ReturnStmt returnStmt) {
        err(returnStmt,
            "Return statement cannot appear at the top level");
        return null;
    }
    
    @Override
    public SymbolType analyze(CallExpr callExpr) {
        return null;
    }

}
