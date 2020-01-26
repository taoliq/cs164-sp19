package chocopy.pa2;

import chocopy.common.analysis.AbstractNodeAnalyzer;
import chocopy.common.analysis.SymbolTable;
import chocopy.common.analysis.types.SymbolType;
import chocopy.common.analysis.types.ValueType;
import chocopy.common.astnodes.BinaryExpr;
import chocopy.common.astnodes.Declaration;
import chocopy.common.astnodes.Errors;
import chocopy.common.astnodes.ExprStmt;
import chocopy.common.astnodes.Identifier;
import chocopy.common.astnodes.IntegerLiteral;
import chocopy.common.astnodes.Node;
import chocopy.common.astnodes.Program;
import chocopy.common.astnodes.Stmt;

import static chocopy.common.analysis.types.SymbolType.INT_TYPE;
import static chocopy.common.analysis.types.SymbolType.OBJECT_TYPE;

/** Analyzer that performs ChocoPy type checks on all nodes.  Applied after
 *  collecting declarations. */
public class TypeChecker extends AbstractNodeAnalyzer<SymbolType> {

    /** The current symbol table (changes depending on the function
     *  being analyzed). */
    private SymbolTable<SymbolType> sym;
    /** Collector for errors. */
    private Errors errors;


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
        for (Declaration decl : program.declarations) {
            decl.dispatch(this);
        }
        for (Stmt stmt : program.statements) {
            stmt.dispatch(this);
        }
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
    public SymbolType analyze(BinaryExpr e) {
        SymbolType t1 = e.left.dispatch(this);
        SymbolType t2 = e.right.dispatch(this);

        switch (e.operator) {
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
