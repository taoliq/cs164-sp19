package chocopy.pa3;

import java.util.List;

import chocopy.common.analysis.SymbolTable;
import chocopy.common.analysis.AbstractNodeAnalyzer;
import chocopy.common.analysis.types.SymbolType;
import chocopy.common.analysis.types.ValueType;
import chocopy.common.astnodes.*;
import chocopy.common.codegen.*;

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

        for (StackVarInfo localVar : funcInfo.getLocals()) {
            ValueType type = localVar.getVarType();
            Literal value = localVar.getInitialValue();
            if (type != null && type.equals(SymbolType.INT_TYPE)) {
                backend.emitLI(T0, ((IntegerLiteral) value).value,
                        "Load integer literal" + ((IntegerLiteral) value).value);
            } else if (type != null && type.equals(SymbolType.BOOL_TYPE)) {
                backend.emitLI(T0, ((BooleanLiteral) value).value ? 1 : 0,
                        "Load boolean literal" + ((BooleanLiteral) value).value);
            }
            backend.emitADDI(SP, SP, -1 * backend.getWordSize(),
                    "Move SP to save local variable.");
            backend.emitSW(T0, SP, 0,
                    "local variable " + localVar.getVarName());
        }

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
        public Void analyze(AssignStmt assignStmt) {
            assignStmt.value.dispatch(this);

            for (Expr tar : assignStmt.targets) {
                String varName = ((Identifier) tar).name;
                SymbolInfo symbolInfo = sym.get(varName);

                // TODO: need box when target is object and value is int/bool
                if (symbolInfo instanceof StackVarInfo) {
                    SymbolTable<SymbolInfo> curSym = sym;
                    FuncInfo curFuncInfo = funcInfo;
                    backend.emitMV(T0, FP, "Save FP for iteration.");
                    // Find the variable by recursion
                    while (!curSym.declares(varName)) {
                        int paramNum = curFuncInfo.getParams().size();
                        curSym = curSym.getParent();
                        curFuncInfo = curFuncInfo.getParentFuncInfo();
                        backend.emitLW(T0, T0, paramNum * backend.getWordSize(),
                                "Load parent function scope.");
                    }

                    int id = curFuncInfo.getVarIndex(varName);
                    // offset based current FP position (argument n-1, lastest argument)
                    int offset = curFuncInfo.getParams().size() - 1 - id;
                    backend.emitSW(A0, T0, offset * backend.getWordSize(),
                            "Store local var: " + varName);
                }
                if (symbolInfo instanceof GlobalVarInfo) {
                    backend.emitSW(A0, ((GlobalVarInfo) symbolInfo).getLabel(), T6,
                            "Store global var: " + varName);
                }
            }
            return null;
        }

        @Override
        public Void analyze(CallExpr callExpr) {
            String callName = callExpr.function.name;
            FuncInfo callFuncInfo = (FuncInfo) sym.get(callName);

            if (funcInfo != null) {
                // Get static link for call
                FuncInfo curFuncInfo = funcInfo;
                int curDepth = funcInfo.getDepth();
                int callFuncDepth = callFuncInfo.getDepth();
                int hop = curDepth - callFuncDepth + 1;
                backend.emitMV(T0, FP, "Save the current FP.");
                for (int i = 0; i < hop; i++) {
                    assert curFuncInfo != null : "current function can not be NULL";
                    int paramNum = curFuncInfo.getParams().size();
                    backend.emitLW(T0, T0, paramNum * backend.getWordSize(),
                            "Load parent function scope.");
                    curFuncInfo = curFuncInfo.getParentFuncInfo();
                }

                backend.emitADDI(SP, SP, -1 * backend.getWordSize(),
                        "Move SP to save static link.");
                backend.emitSW(T0, SP, 0, "Load static link.");

            }

            // TODO: delete arguments when finished call function
            for (int i = 0; i < callExpr.args.size(); i++) {
                Expr e = callExpr.args.get(i);
                String paramName = callFuncInfo.getParams().get(i);
                StackVarInfo paramInfo = (StackVarInfo) callFuncInfo.getSymbolTable().get(paramName);

                e.dispatch(this);
                if (e.getInferredType().equals(SymbolType.INT_TYPE)
                        && paramInfo.getVarType().equals(SymbolType.OBJECT_TYPE)) {
                    backend.emitInsn("jal makeint", "Box integer");
                }
                if (e.getInferredType().equals(SymbolType.BOOL_TYPE)
                        && paramInfo.getVarType().equals(SymbolType.OBJECT_TYPE)) {
                    backend.emitInsn("jal makebool", "Box boolean");
                }
                backend.emitADDI(SP, SP, -1 * backend.getWordSize(),
                        "Move SP to save argument.");
                backend.emitSW(A0, SP, 0, "Load argument to stack");
            }

            backend.emitJAL(callFuncInfo.getCodeLabel(), "Invoke function " + callName);
            return null;
        }

        @Override
        public Void analyze(Identifier node) {
            String varName = node.name;
            SymbolInfo symbolInfo = sym.get(varName);

            if (symbolInfo instanceof StackVarInfo) {
                SymbolTable<SymbolInfo> curSym = sym;
                FuncInfo curFuncInfo = funcInfo;
                backend.emitMV(T0, FP, "Save FP for iteration.");
                //find the variable by recursion
                while (!curSym.declares(varName)) {
                    int paramNum = curFuncInfo.getParams().size();
                    curSym = curSym.getParent();
                    curFuncInfo = curFuncInfo.getParentFuncInfo();
                    backend.emitLW(T0, T0, paramNum * backend.getWordSize(),
                            "Load parent function scope.");
                }
                int id = curFuncInfo.getVarIndex(varName);
                // offset based current FP position (argument n-1, lastest argument)
                int offset = curFuncInfo.getParams().size() - 1 - id;
                backend.emitLW(A0, T0, offset * backend.getWordSize(),
                        "Load local var: " + varName);
            }
            if (symbolInfo instanceof GlobalVarInfo) {
                backend.emitLW(A0, ((GlobalVarInfo) symbolInfo).getLabel(),
                        "Load global var: " + varName);
            }

            return null;
        }

        @Override
        public Void analyze(BooleanLiteral booleanLiteral) {
//            Label boolLabel = constants.getBoolConstant(booleanLiteral.value);
//            backend.emitLA(A0, boolLabel, "Load bool label");
            backend.emitLI(A0, booleanLiteral.value ? 1 : 0,
                    "Load boolean literal " + booleanLiteral.value);
            return null;
        }

        @Override
        public Void analyze(IntegerLiteral integerLiteral) {
//            Label intLabel = constants.getIntConstant(integerLiteral.value);
//            backend.emitLA(A0, intLabel, "Load int label");

            backend.emitLI(A0, integerLiteral.value,
                    "Load integer literal " + integerLiteral.value);
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

        @Override
        public Void analyze(BinaryExpr binaryExpr) {
            if (!binaryExpr.operator.equals("and")
                    && !binaryExpr.operator.equals("or")) {
                binaryExpr.left.dispatch(this);
                backend.emitSW(A0, SP, -1 * backend.getWordSize(),
                        "Save left expression value.");

                binaryExpr.right.dispatch(this);
                backend.emitSW(A0, SP, -2 * backend.getWordSize(),
                        "Save right expression value.");

                backend.emitLW(T0, SP, -1 * backend.getWordSize(),
                        "Load left expression value.");
                backend.emitLW(T1, SP, -2 * backend.getWordSize(),
                        "Load right expression value.");
            }

            Label compareBranch = null;
            Label compareFinish = null;

            switch (binaryExpr.operator) {
                case "+":
                    backend.emitADD(A0, T0, T1, "add");
                    break;
                case "-":
                    backend.emitSUB(A0, T0, T1, "sub");
                    break;
                case "*":
                    backend.emitMUL(A0, T0, T1, "mul");
                    break;
                case "//":
                    backend.emitDIV(A0, T0, T1, "div");
                    break;
                case "%":
                    backend.emitREM(A0, T0, T1, "remainer");
                    break;

                case "==":
                    compareBranch = generateLocalLabel();
                    compareFinish = generateLocalLabel();

                    backend.emitBEQ(T0, T1, compareBranch, "Go to equal branch");
                    backend.emitLI(A0, 0, "Load integer of False");
                    backend.emitJ(compareFinish, null);

                    backend.emitLocalLabel(compareBranch, "Equal branch");
                    backend.emitLI(A0, 1, "Load integer of True");

                    backend.emitLocalLabel(compareFinish, "compare finish");
                    break;
                case "!=":
                    compareBranch = generateLocalLabel();
                    compareFinish = generateLocalLabel();

                    backend.emitBNE(T0, T1, compareBranch,
                            "Go to not equal branch");
                    backend.emitLI(A0, 0, "Load integer of False");
                    backend.emitJ(compareFinish, null);

                    backend.emitLocalLabel(compareBranch, "not equal branch");
                    backend.emitLI(A0, 1, "Load integer of True");

                    backend.emitLocalLabel(compareFinish, "compare finish");
                    break;
                case ">":
                    compareBranch = generateLocalLabel();
                    compareFinish = generateLocalLabel();

                    backend.emitBLT(T1, T0, compareBranch,
                            "Go to greater branch");
                    backend.emitLI(A0, 0, "Load integer of False");
                    backend.emitJ(compareFinish, null);

                    backend.emitLocalLabel(compareBranch, "greater branch");
                    backend.emitLI(A0, 1, "Load integer of True");

                    backend.emitLocalLabel(compareFinish, "compare finish");
                    break;
                case ">=":
                    compareBranch = generateLocalLabel();
                    compareFinish = generateLocalLabel();

                    backend.emitBGE(T0, T1, compareBranch,
                            "Go to greater or equal branch");
                    backend.emitLI(A0, 0, "Load integer of False");
                    backend.emitJ(compareFinish, null);

                    backend.emitLocalLabel(compareBranch, "greater or equal branch");
                    backend.emitLI(A0, 1, "Load integer of True");

                    backend.emitLocalLabel(compareFinish, "compare finish");
                    break;
                case "<":
                    compareBranch = generateLocalLabel();
                    compareFinish = generateLocalLabel();

                    backend.emitBLT(T0, T1, compareBranch,
                            "Go to less branch");
                    backend.emitLI(A0, 0, "Load integer of False");
                    backend.emitJ(compareFinish, null);

                    backend.emitLocalLabel(compareBranch, "less branch");
                    backend.emitLI(A0, 1, "Load integer of True");

                    backend.emitLocalLabel(compareFinish, "compare finish");
                    break;
                case "<=":
                    compareBranch = generateLocalLabel();
                    compareFinish = generateLocalLabel();

                    backend.emitBGE(T1, T0, compareBranch,
                            "Go to less or equal branch");
                    backend.emitLI(A0, 0, "Load integer of False");
                    backend.emitJ(compareFinish, null);

                    backend.emitLocalLabel(compareBranch, "less or equal branch");
                    backend.emitLI(A0, 1, "Load integer of True");

                    backend.emitLocalLabel(compareFinish, "compare finish");
                    break;

                case "and":
                case "or":
                    compareFinish = generateLocalLabel();
                    int shortCircuitValue = binaryExpr.operator.equals("and") ? 0 : 1;

                    binaryExpr.left.dispatch(this);
                    backend.emitLI(T0, shortCircuitValue,
                            "Load short-circuit value " + shortCircuitValue);
                    backend.emitBEQ(A0, T0, compareFinish, "short-circuit");

                    binaryExpr.right.dispatch(this);
                    backend.emitLocalLabel(compareFinish, "binary logical finish");
                    break;

                default:
                    break;
            }

            return null;
        }

        @Override
        public Void analyze(UnaryExpr e) {
            e.operand.dispatch(this);
            switch (e.operator) {
                case "-":
                    backend.emitSUB(A0, ZERO, A0, "Get negative number.");
                    break;
                case "not":
                    backend.emitXORI(A0, A0, 1, "Flip the expr value");
                    break;
                default:
                    break;
            }
            return null;
        }

        @Override
        public Void analyze(IfExpr node) {
            return super.analyze(node);
        }

        @Override
        public Void analyze(IfStmt ifStmt) {
            Label branch = generateLocalLabel();
            Label finish = generateLocalLabel();

            ifStmt.condition.dispatch(this);
            backend.emitBEQZ(A0, branch, "Jump when condition is false.");
            for (Stmt stmt : ifStmt.thenBody) {
                stmt.dispatch(this);
            }
            backend.emitJ(finish, null);
            backend.emitLocalLabel(branch, "else body begin");
            for (Stmt stmt : ifStmt.elseBody) {
                stmt.dispatch(this);
            }
            backend.emitLocalLabel(finish, null);
            return null;
        }

        @Override
        public Void analyze(WhileStmt whileStmt) {
            Label entrance = generateLocalLabel();
            Label quit = generateLocalLabel();
            backend.emitLocalLabel(entrance, "Entrance for while loop.");
            whileStmt.condition.dispatch(this);
            backend.emitBEQZ(A0, quit, "Jump out when condition is false.");
            for (Stmt stmt : whileStmt.body) {
                stmt.dispatch(this);
            }
            backend.emitJ(entrance, "Go back to while loop entrance");
            backend.emitLocalLabel(quit, "Finish while loop.");
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
        emitMakeInt();
        emitMakeBool();

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

    private void emitMakeInt() {
        Label label = new Label("makeint");
        backend.emitGlobalLabel(label);
        backend.emitADDI(SP, SP, -8, null);
        backend.emitSW(RA, SP, 4, null);
        backend.emitSW(A0, SP, 0, null);
        ClassInfo intClass = (ClassInfo) globalSymbols.get("int");
        backend.emitLA(A0, intClass.getPrototypeLabel(), null);
        backend.emitJAL(new Label("alloc"), null);
        backend.emitLW(T0, SP, 0, null);
        backend.emitSW(T0, A0, getAttrOffset(intClass, "__int__"), null);
        backend.emitLW(RA, SP, 4, null);
        backend.emitADDI(SP, SP, 8, null);
        backend.emitJR(RA, null);
    }

    private void emitMakeBool() {
        Label funcLabel = new Label("makebool");
        Label falseBranch = generateLocalLabel();
        Label trueConstantLabel = constants.getBoolConstant(true);
        Label falseConstantLabel = constants.getBoolConstant(false);

        backend.emitGlobalLabel(funcLabel);
        backend.emitLI(T0, 0, "Load integer of False");
        backend.emitBEQ(A0, T0, falseBranch, "Go to False branch");
        backend.emitLA(A0, trueConstantLabel, "Load True constant");
        backend.emitJR(RA, null);

        backend.emitLocalLabel(falseBranch, "False branch");
        backend.emitLA(A0, falseConstantLabel, "Load False constant");
        backend.emitJR(RA, null);
    }
}
