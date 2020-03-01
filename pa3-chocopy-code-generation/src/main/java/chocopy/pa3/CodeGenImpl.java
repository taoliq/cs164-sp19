package chocopy.pa3;

import java.util.List;

import chocopy.common.analysis.SymbolTable;
import chocopy.common.analysis.AbstractNodeAnalyzer;
import chocopy.common.astnodes.*;
import chocopy.common.codegen.CodeGenBase;
import chocopy.common.codegen.FuncInfo;
import chocopy.common.codegen.Label;
import chocopy.common.codegen.RiscVBackend;
import chocopy.common.codegen.SymbolInfo;

import static chocopy.common.codegen.RiscVBackend.Register.*;

/**
 * This is where the main implementation of PA3 will live.
 *
 * A large part of the functionality has already been implemented
 * in the base class, CodeGenBase. Make sure to read through that
 * class, since you will want to use many of its fields
 * and utility methods in this class when emitting code.
 *
 * Also read the PDF spec for details on what the base class does and
 * what APIs it exposes for its sub-class (this one). Of particular
 * importance is knowing what all the SymbolInfo classes contain.
 */
public class CodeGenImpl extends CodeGenBase {

    /** A code generator emitting instructions to BACKEND. */
    public CodeGenImpl(RiscVBackend backend) {
        super(backend);
    }

    /** Operation on None. */
    private final Label errorNone = new Label("error.None");
    /** Division by zero. */
    private final Label errorDiv = new Label("error.Div");
    /** Index out of bounds. */
    private final Label errorOob = new Label("error.OOB");

    /**
     * Emits the top level of the program.
     *
     * This method is invoked exactly once, and is surrounded
     * by some boilerplate code that: (1) initializes the heap
     * before the top-level begins and (2) exits after the top-level
     * ends.
     *
     * You only need to generate code for statements.
     *
     * @param statements top level statements
     */
    protected void emitTopLevel(List<Stmt> statements) {
        StmtAnalyzer stmtAnalyzer = new StmtAnalyzer(null);
        backend.emitADDI(SP, SP, -2 * backend.getWordSize(),
                         "Saved FP and saved RA (unused at top level).");
        backend.emitSW(ZERO, SP, 0, "Top saved FP is 0.");
        backend.emitSW(ZERO, SP, 4, "Top saved RA is 0.");
        backend.emitADDI(FP, SP, 2 * backend.getWordSize(),
                         "Set FP to previous SP.");

        for (Stmt stmt : statements) {
            stmt.dispatch(stmtAnalyzer);
        }
        backend.emitLI(A0, EXIT_ECALL, "Code for ecall: exit");
        backend.emitEcall(null);
    }

    /**
     * Emits the code for a function described by FUNCINFO.
     *
     * This method is invoked once per function and method definition.
     * At the code generation stage, nested functions are emitted as
     * separate functions of their own. So if function `bar` is nested within
     * function `foo`, you only emit `foo`'s code for `foo` and only emit
     * `bar`'s code for `bar`.
     */
    protected void emitUserDefinedFunction(FuncInfo funcInfo) {
        backend.emitGlobalLabel(funcInfo.getCodeLabel());
        StmtAnalyzer stmtAnalyzer = new StmtAnalyzer(funcInfo);

        backend.emitADDI(SP, SP, -2 * backend.getWordSize(),
                "Saved FP and saved RA.");
        backend.emitSW(FP, SP, 0, "Save FP.");
        backend.emitSW(RA, SP, 4, "Save RA.");
        backend.emitADDI(FP, SP, 2 * backend.getWordSize(),
                "Set FP to previous SP.");

        for (Stmt stmt : funcInfo.getStatements()) {
            stmt.dispatch(stmtAnalyzer);
        }

        backend.emitMV(A0, ZERO, "Returning None implicitly");
        backend.emitLocalLabel(stmtAnalyzer.epilogue, "Epilogue");

        // FIXME: {... reset fp etc. ...}
        backend.emitLW(RA, FP, -4, "Reset RA.");
        backend.emitMV(SP, FP, "Reset SP.");
        backend.emitLW(FP, SP, -8, "Reset FP.");
        backend.emitJR(RA, "Return to caller");
    }

    /** An analyzer that encapsulates code generation for statments. */
    private class StmtAnalyzer extends AbstractNodeAnalyzer<Void> {
        /*
         * The symbol table has all the info you need to determine
         * what a given identifier 'x' in the current scope is. You can
         * use it as follows:
         *   SymbolInfo x = sym.get("x");
         *
         * A SymbolInfo can be one the following:
         * - ClassInfo: a descriptor for classes
         * - FuncInfo: a descriptor for functions/methods
         * - AttrInfo: a descriptor for attributes
         * - GlobalVarInfo: a descriptor for global variables
         * - StackVarInfo: a descriptor for variables allocated on the stack,
         *      such as locals and parameters
         *
         * Since the input program is assumed to be semantically
         * valid and well-typed at this stage, you can always assume that
         * the symbol table contains valid information. For example, in
         * an expression `foo()` you KNOW that sym.get("foo") will either be
         * a FuncInfo or ClassInfo, but not any of the other infos
         * and never null.
         *
         * The symbol table in funcInfo has already been populated in
         * the base class: CodeGenBase. You do not need to add anything to
         * the symbol table. Simply query it with an identifier name to
         * get a descriptor for a function, class, variable, etc.
         *
         * The symbol table also maps nonlocal and global vars, so you
         * only need to lookup one symbol table and it will fetch the
         * appropriate info for the var that is currently in scope.
         */

        /** Symbol table for my statements. */
        private SymbolTable<SymbolInfo> sym;

        /** Label of code that exits from procedure. */
        protected Label epilogue;

        /** The descriptor for the current function, or null at the top
         *  level. */
        private FuncInfo funcInfo;

        /** An analyzer for the function described by FUNCINFO0, which is null
         *  for the top level. */
        StmtAnalyzer(FuncInfo funcInfo0) {
            funcInfo = funcInfo0;
            if (funcInfo == null) {
                sym = globalSymbols;
            } else {
                sym = funcInfo.getSymbolTable();
            }
            epilogue = generateLocalLabel();
        }

        // FIXME: Example of statement.
        @Override
        public Void analyze(ReturnStmt stmt) {
            // FIXME: Here, we emit an instruction that does nothing. Clearly,
            // this is wrong, and you'll have to fix it.
            // This is here just to demonstrate how to emit a
            // RISC-V instruction.
            if (stmt.value == null) {
                backend.emitMV(A0, ZERO, "Returning None implicitly");
            } else {
                stmt.value.dispatch(this);
//                backend.emitLW(A0, SP, 0, "Load label to register");
            }
            backend.emitJ(epilogue, "Go to return");
            return null;
        }

        // FIXME: More, of course.
        @Override
        public Void analyze(ExprStmt exprStmt) {
            exprStmt.expr.dispatch(this);
            return null;
        }

        @Override
        public Void analyze(CallExpr callExpr) {
            String callName = callExpr.function.name;
            FuncInfo info = (FuncInfo) sym.get(callName);

            // TODO: delete arguments when finished call function
            for (Expr e : callExpr.args) {
                e.dispatch(this);
                backend.emitADDI(SP, SP, -1 * backend.getWordSize(),
                        "Move SP to save argument.");
                backend.emitSW(A0, SP, 0, "Load argument to stack");
            }

            backend.emitJAL(info.getCodeLabel(), "Invoke function " + callName);
            return null;
        }

        @Override
        public Void analyze(Identifier node) {
            String varName = node.name;
            int id = funcInfo.getVarIndex(varName);
            // offset based current FP position (argument n-1, lastest argument)
            int offset = funcInfo.getParams().size() - 1 - id;
            backend.emitLW(A0, FP, offset * backend.getWordSize(),
                    "Move argument to reg.");
            return null;
        }

        @Override
        public Void analyze(BooleanLiteral booleanLiteral) {
            Label boolLabel = constants.getBoolConstant(booleanLiteral.value);
//            backend.emitADDI(SP, SP, -1 * backend.getWordSize(),
//                    "Move SP to save bool literal");
            backend.emitLA(A0, boolLabel, "Load bool label");
//            backend.emitSW(T0, SP, 0, "Load label to stack");
            return null;
        }

        @Override
        public Void analyze(IntegerLiteral integerLiteral) {
            Label intLabel = constants.getIntConstant(integerLiteral.value);
//            backend.emitADDI(SP, SP, -1 * backend.getWordSize(),
//                    "Move SP to save int literal");
            backend.emitLA(A0, intLabel, "Load int label");
//            backend.emitSW(T0, SP, 0, "Load label to stack");
            return null;
        }

        @Override
        public Void analyze(StringLiteral stringLiteral) {
            Label strLabel = constants.getStrConstant(stringLiteral.value);
//            backend.emitADDI(SP, SP, -1 * backend.getWordSize(),
//                    "Move SP to save string literal");
            backend.emitLA(A0, strLabel, "Load string label");
//            backend.emitSW(T0, SP, 0, "Load label to stack");
            return null;
        }
    }

    /**
     * Emits custom code in the CODE segment.
     *
     * This method is called after emitting the top level and the
     * function bodies for each function.
     *
     * You can use this method to emit anything you want outside of the
     * top level or functions, e.g. custom routines that you may want to
     * call from within your code to do common tasks. This is not strictly
     * needed. You might not modify this at all and still complete
     * the assignment.
     *
     * To start you off, here is an implementation of three routines that
     * will be commonly needed from within the code you will generate
     * for statements.
     *
     * The routines are error handlers for operations on None, index out
     * of bounds, and division by zero. They never return to their caller.
     * Just jump to one of these routines to throw an error and
     * exit the program. For example, to throw an OOB error:
     *   backend.emitJ(errorOob, "Go to out-of-bounds error and abort");
     *
     */
    protected void emitCustomCode() {
        emitErrorFunc(errorNone, "Operation on None");
        emitErrorFunc(errorDiv, "Divison by zero");
        emitErrorFunc(errorOob, "Index out of bounds");
    }

    /** Emit an error routine labeled ERRLABEL that aborts with message MSG. */
    private void emitErrorFunc(Label errLabel, String msg) {
        backend.emitGlobalLabel(errLabel);
        backend.emitLI(A0, ERROR_NONE, "Exit code for: " + msg);
        backend.emitLA(A1, constants.getStrConstant(msg),
                       "Load error message as str");
        backend.emitADDI(A1, A1, getAttrOffset(strClass, "__str__"),
                         "Load address of attribute __str__");
        backend.emitJ(abortLabel, "Abort");
    }
}
