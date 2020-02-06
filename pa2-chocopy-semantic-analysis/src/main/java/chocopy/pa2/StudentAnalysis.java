package chocopy.pa2;

// import chocopy.common.analysis.SymbolTable;
import chocopy.common.analysis.types.SymbolType;
import chocopy.common.astnodes.Program;

/** Top-level class for performing semantic analysis. */
public class StudentAnalysis {

    /** Perform semantic analysis on PROGRAM, adding error messages and
     *  type annotations. Provide debugging output iff DEBUG. Returns modified
     *  tree. */
    public static Program process(Program program, boolean debug) {
        if (program.hasErrors()) {
            return program;
        }

        DeclarationAnalyzer declarationAnalyzer =
            new DeclarationAnalyzer(program.errors);
        program.dispatch(declarationAnalyzer);
        SymbolTable<SymbolType> globalSym =
            declarationAnalyzer.getGlobals();

        if (!program.hasErrors()) {
            StatementAnalyzer stmtAnalyzer =
                new StatementAnalyzer(globalSym, program.errors);
            program.dispatch(stmtAnalyzer);
        }

        if (!program.hasErrors()) {
            // System.out.println("--begin type check----");
            // for (String name : globalSym.getDeclaredSymbols()) {
            //     System.out.println(name + " : " + globalSym.get(name));
            // }
            TypeChecker typeChecker =
                new TypeChecker(globalSym, program.errors);
            program.dispatch(typeChecker);
        }

        return program;
    }
}
