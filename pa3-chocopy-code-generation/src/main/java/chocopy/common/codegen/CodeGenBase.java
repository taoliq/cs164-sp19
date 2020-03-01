package chocopy.common.codegen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import chocopy.common.analysis.SymbolTable;
import chocopy.common.analysis.types.SymbolType;
import chocopy.common.analysis.types.ValueType;
import chocopy.common.astnodes.BooleanLiteral;
import chocopy.common.astnodes.ClassDef;
import chocopy.common.astnodes.Declaration;
import chocopy.common.astnodes.FuncDef;
import chocopy.common.astnodes.GlobalDecl;
import chocopy.common.astnodes.IntegerLiteral;
import chocopy.common.astnodes.Literal;
import chocopy.common.astnodes.NonLocalDecl;
import chocopy.common.astnodes.Program;
import chocopy.common.astnodes.Stmt;
import chocopy.common.astnodes.TypedVar;
import chocopy.common.astnodes.VarDef;
import chocopy.common.analysis.AbstractNodeAnalyzer;

import static chocopy.common.Utils.*;
import static chocopy.common.codegen.RiscVBackend.Register.*;

/**
 * The code generator for a ChocoPy program.
 *
 * This class implements logic to analyze all declarations
 * in a program and create descriptors for classes, functions,
 * methods, variables (global and local), and attributes. This
 * logic also builds symbol tables for globals and individual functions.
 *
 * This class also implements logic to emit global variables, object
 * prototypes and dispatch tables, as well as int/str/bool constants.
 *
 * However, this class is abstract because it does not implement logic
 * for emitting executable code in bodies of user-defined functions
 * as well as in top-level statements. This class should be extended with
 * implementations for such logic.
 *
 * All non-public members of this class are `protected`, and can be
 * overridden by sub-classes to extend change functionality.
 *
 * The SymbolInfo classes can also be overridden. If say you want to use
 * your own extended FuncInfo called MyFuncInfo (that extends FuncInfo),
 * then override the makeFuncInfo() method of this class to
 * `return new MyFuncInfo(...)` instead. This is probably not needed, though.
 */
public abstract class CodeGenBase {

    /** The location of the text resources containing common library code. */
    protected static final String LIBRARY_CODE_DIR
        = "chocopy/common/codegen/asm/";

    /** The backend that emits assembly. */
    protected final RiscVBackend backend;

    /** Convenience variable: the word size for the current back end. */
    protected final int wordSize;

    /** A counter for generating unique class type tags. */
    protected int nextTypeTag = 0;

    /** A counter used to generate unique local label names. */
    protected int nextLabelSuffix = 0;

    /** Predefined classes. The list "class" is a fake class; we use it only
     *  to emit a prototype object for empty lists. */
    protected ClassInfo
        objectClass, intClass, boolClass, strClass, listClass;

    /** Predefined functions. */
    protected FuncInfo printFunc, lenFunc, inputFunc;

    /**
     * A list of global variables, whose initial values are
     * emitted in the backend.
     */
    protected final List<GlobalVarInfo> globalVars = new ArrayList<>();

    /**
     * A list of program classes, whose prototype objects and dispatch
     * tables are emitted in the backend.
     */
    protected final List<ClassInfo> classes = new ArrayList<>();

    /**
     * A list of functions (including methods and nested functions) whose
     * bodies are emitted in the backend.
     */
    protected final List<FuncInfo> functions = new ArrayList<>();

    /** Label for built-in routine: alloc. */
    protected final Label objectAllocLabel = new Label("alloc");

    /** Label for built-in routine: alloc2. */
    protected final Label objectAllocResizeLabel = new Label("alloc2");

    /** Label for built-in routine: abort. */
    protected final Label abortLabel = new Label("abort");

    /** Label for built-in routine: heap.init. */
    protected final Label heapInitLabel = new Label("heap.init");

    /** Error codes. */
    protected final int ERROR_ARG = 1, ERROR_DIV_ZERO = 2, ERROR_OOB = 3,
        ERROR_NONE = 4, ERROR_OOM = 5, ERROR_NYI = 6;

    /** Size of heap memory. */
    protected final int HEAP_SIZE_BYTES = 1024 * 1024 * 32;

    /** Ecall numbers for intrinsic routines. */
    protected final int
        EXIT_ECALL = 10,
        EXIT2_ECALL = 17,
        PRINT_STRING_ECALL = 4,
        PRINT_CHAR_ECALL = 11,
        PRINT_INT_ECALL = 1,
        SBRK_ECALL = 9;

    /**
     * The symbol table that maps global names to information about
     * the bound global variables, global functions, or classes.
     */
    protected final SymbolTable<SymbolInfo> globalSymbols = new SymbolTable<>();

    /**
     * A utility for caching constants and generating labels for constants.
     */
    protected final Constants constants = new Constants();

    /** The object header size, in words (includes type tag, size,
     *  and dispatch table pointer). */
    public static final int HEADER_SIZE = 3;

    /**
     * Initializes a code generator for ChocoPy that uses BACKEND to emit
     * assembly code.
     *
     * The constructor creates Info objects for predefined functions,
     * classes, methods, and built-in routines.
     */
    public CodeGenBase(RiscVBackend backend) {
        this.backend = backend;
        wordSize = backend.getWordSize();

        initClasses();
        initFunctions();
        initAsmConstants();
    }

    /** Return a fresh type tag. */
    protected int getNextTypeTag() {
        return nextTypeTag++;
    }

    /** Returns the next unique label suffix. */
    protected int getNextLabelSuffix() {
        return nextLabelSuffix++;
    }

    /**
     * Return a fresh label.
     *
     * This label is guaranteed to be unique amongst labels
     * generated by invoking this method. All such labels
     * have a prefix of `label_`.
     *
     * This is useful to generate local labels in
     * function bodies (e.g. for targets of jumps),
     * where the name does not matter in general.
     */
    protected Label generateLocalLabel() {
        return new Label(String.format("label_%d", getNextLabelSuffix()));
    }

    /**
     * Generates assembly code for PROGRAM.
     *
     * This is the main driver that calls internal methods for
     * emitting DATA section (globals, constants, prototypes, etc)
     * as well as the the CODE section (predefined functions, built-in
     * routines, and user-defined functions).
     */
    public void generate(Program program) {
        analyzeProgram(program);

        backend.startData();

        for (ClassInfo classInfo : this.classes) {
            emitPrototype(classInfo);
        }

        for (ClassInfo classInfo : this.classes) {
            emitDispatchTable(classInfo);
        }

        for (GlobalVarInfo global : this.globalVars) {
            backend.emitGlobalLabel(global.getLabel());
            emitConstant(global.getInitialValue(), global.getVarType(),
                         String.format("Initial value of global var: %s",
                                       global.getVarName()));
        }

        backend.startCode();

        Label mainLabel = new Label("main");
        backend.emitGlobalLabel(mainLabel);
        backend.emitLUI(A0, HEAP_SIZE_BYTES >> 12,
                        "Initialize heap size (in multiples of 4KB)");
        backend.emitADD(S11, S11, A0, "Save heap size");
        backend.emitJAL(heapInitLabel, "Call heap.init routine");
        backend.emitMV(GP, A0, "Initialize heap pointer");
        backend.emitMV(S10, GP, "Set beginning of heap");
        backend.emitADD(S11, S10, S11,
                        "Set end of heap (= start of heap + heap size)");
        backend.emitMV(RA, ZERO, "No normal return from main program.");
        backend.emitMV(FP, ZERO, "No preceding frame.");

        emitTopLevel(program.statements);

        for (FuncInfo funcInfo : this.functions) {
            funcInfo.emitBody();
        }

        emitStdFunc("alloc");
        emitStdFunc("alloc2");
        emitStdFunc("abort");
        emitStdFunc("heap.init");

        emitCustomCode();

        backend.startData();
        emitConstants();
    }

    /** Create descriptors and symbols for builtin classes and methods. */
    protected void initClasses() {
        FuncInfo objectInit =
            makeFuncInfo("object.__init__", 0, SymbolType.NONE_TYPE,
                         globalSymbols, null, this::emitStdFunc);
        objectInit.addParam(makeStackVarInfo("self", SymbolType.OBJECT_TYPE,
                                             null, objectInit));
        functions.add(objectInit);

        objectClass = makeClassInfo("object", getNextTypeTag(), null);
        objectClass.addMethod(objectInit);
        classes.add(objectClass);
        globalSymbols.put(objectClass.getClassName(), objectClass);

        intClass = makeClassInfo("int", getNextTypeTag(), objectClass);
        intClass.addAttribute(makeAttrInfo("__int__", null, null));
        classes.add(intClass);
        globalSymbols.put(intClass.getClassName(), intClass);

        boolClass = makeClassInfo("bool", getNextTypeTag(), objectClass);
        boolClass.addAttribute(makeAttrInfo("__bool__", null, null));
        classes.add(boolClass);
        globalSymbols.put(boolClass.getClassName(), boolClass);

        strClass = makeClassInfo("str", getNextTypeTag(), objectClass);
        strClass.addAttribute(makeAttrInfo("__len__", SymbolType.INT_TYPE,
                                           new IntegerLiteral(null, null, 0)));
        strClass.addAttribute(makeAttrInfo("__str__", null, null));
        classes.add(strClass);
        globalSymbols.put(strClass.getClassName(), strClass);

        listClass = makeClassInfo(".list", -1, objectClass);
        listClass.addAttribute(makeAttrInfo("__len__", SymbolType.INT_TYPE,
                                            new IntegerLiteral(null, null, 0)));
        classes.add(listClass);
        listClass.dispatchTableLabel = null;
    }

    /** Create descriptors and symbols for builtin functions. */
    protected void initFunctions() {
        printFunc = makeFuncInfo("print", 0, SymbolType.NONE_TYPE,
                                 globalSymbols, null, this::emitStdFunc);
        printFunc.addParam(makeStackVarInfo("arg", SymbolType.OBJECT_TYPE,
                                            null, printFunc));
        functions.add(printFunc);
        globalSymbols.put(printFunc.getBaseName(), printFunc);

        lenFunc = makeFuncInfo("len", 0, SymbolType.INT_TYPE,
                globalSymbols, null, this::emitStdFunc);
        lenFunc.addParam(makeStackVarInfo("arg", SymbolType.OBJECT_TYPE,
                                          null, lenFunc));
        functions.add(lenFunc);
        globalSymbols.put(lenFunc.getBaseName(), lenFunc);

        inputFunc = makeFuncInfo("input", 0, SymbolType.STR_TYPE,
                                 globalSymbols, null, this::emitStdFunc);
        functions.add(inputFunc);
        globalSymbols.put(inputFunc.getBaseName(), inputFunc);
    }

    /* Symbolic assembler constants defined here (to add others, override
     * initAsmConstants in an extension of CodeGenBase):
     * ecalls:
     *   @print_string
     *   @print_char
     *   @print_int
     *   @exit2
     * Exit codes:
     *   @error_div_zero: Division by 0.
     *   @error_arg: Bad argument.
     *   @error_oob: Out of bounds.
     *   @error_none: Attempt to access attribute of None.
     *   @error_oom: Out of memory.
     *   @error_nyi: Unimplemented operation.
     * Data-structure byte offsets:
     *   @.__obj_size__: Offset of size of object.
     *   @.__len__: Offset of length in chars or words.
     *   @.__str__: Offset of string data.
     *   @.__elts__: Offset of first list item.
     *   @.__int__: Offset of integer value.
     *   @.__bool__: Offset of boolean (1/0) value.
     */

    /** Define @-constants to be used in assembly code. */
    protected void initAsmConstants() {
        backend.defineSym("sbrk", SBRK_ECALL);
        backend.defineSym("print_string", PRINT_STRING_ECALL);
        backend.defineSym("print_char", PRINT_CHAR_ECALL);
        backend.defineSym("print_int", PRINT_INT_ECALL);
        backend.defineSym("exit2", EXIT2_ECALL);

        backend.defineSym(".__obj_size__", 4);
        backend.defineSym(".__len__", 12);
        backend.defineSym(".__int__", 12);
        backend.defineSym(".__bool__", 12);
        backend.defineSym(".__str__", 16);
        backend.defineSym(".__elts__", 16);

        backend.defineSym("error_div_zero", ERROR_DIV_ZERO);
        backend.defineSym("error_arg", ERROR_ARG);
        backend.defineSym("error_oob", ERROR_OOB);
        backend.defineSym("error_none", ERROR_NONE);
        backend.defineSym("error_oom", ERROR_OOM);
        backend.defineSym("error_nyi", ERROR_NYI);
    }

    /*-----------------------------------------------------------*/
    /*                                                           */
    /*          FACTORY METHODS TO CREATE INFO OBJECTS           */
    /*                                                           */
    /*-----------------------------------------------------------*/

    /**
     * A factory method that returns a descriptor for function or method
     * FUNCNAME returning type RETURNTYPE at nesting level DEPTH in the
     * region corresponding to PARENTSYMBOLTABLE.

     * PARENTFUNCINFO is a descriptor of the enclosing function and is null
     * for global functions and methods.

     * EMITTER is a method that emits the function's body (usually a
     * generic emitter for user-defined functions/methods, and a
     * special emitter for pre-defined functions/methods).
     *
     * Sub-classes of CodeGenBase can override this method
     * if they wish to use a sub-class of FuncInfo with more
     * functionality.
     */
    protected FuncInfo makeFuncInfo(String funcName, int depth,
                                    SymbolType returnType,
                                    SymbolTable<SymbolInfo> parentSymbolTable,
                                    FuncInfo parentFuncInfo,
                                    Consumer<FuncInfo> emitter) {
        return new FuncInfo(funcName, depth, returnType, parentSymbolTable,
                            parentFuncInfo, emitter);
    }

    /**
     * Return a descriptor for a class named CLASSNAME having type tag
     * TYPETAG and superclass SUPERCLASSINFO (null for `object' only).
     *
     * Sub-classes of CodeGenBase can override this method
     * if they wish to use a sub-class of ClassInfo with more
     * functionality.
     */
    public ClassInfo makeClassInfo(String className, int typeTag,
                                   ClassInfo superClassInfo) {
        return new ClassInfo(className, typeTag, superClassInfo);
    }

    /**
     * A factory method that returns a descriptor for an attribute named
     * ATTRNAME of type ATTRTYPE and with an initial value specified
     * by INITIALVALUE, which may be null to indicate a default initialization.
     *
     * Sub-classes of CodeGenBase can override this method
     * if they wish to use a sub-class of AttrInfo with more
     * functionality.
     */
    public AttrInfo makeAttrInfo(String attrName, ValueType attrType,
                                 Literal initialValue) {
        return new AttrInfo(attrName, attrType, initialValue);
    }

    /**
     * A factory method that returns a descriptor for a local variable or
     * parameter named VARNAME of type VARTYPE, whose initial value is
     * specified by INITIALVALUE (if non-null) and which is defined
     * immediately within the function given by FUNCINFO.
     *
     * These variables are allocated on the stack in activation
     * frames.
     *
     * Sub-classes of CodeGenBase can override this method
     * if they wish to use a sub-class of StackVarInfo with more
     * functionality.
     *
     */
    public StackVarInfo makeStackVarInfo(String varName, ValueType varType,
                                         Literal initialValue,
                                         FuncInfo funcInfo) {
        return new StackVarInfo(varName, varType, initialValue, funcInfo);
    }

    /**
     * A factory method that returns a descriptor for a global variable with
     * name VARNAME and type VARTYPE, whose initial value is specified by
     * INITIALVALUE (if non-null).
     *
     * Sub-classes of CodeGenBase can override this method
     * if they wish to use a sub-class of GlobalVarInfo with more
     * functionality.
     */
    public GlobalVarInfo makeGlobalVarInfo(String varName, ValueType varType,
                                           Literal initialValue) {
        return new GlobalVarInfo(varName, varType, initialValue);
    }

    /*-----------------------------------------------------------*
     *                                                           *
     *             ANALYSIS OF AST INTO INFO OBJECTS             *
     *   (Students can ignore these methods as all the work has  *
     *    been done and does not need to be modified/extended)   *
     *                                                           *
     *-----------------------------------------------------------*/


    /**
     * Analyze PROGRAM, creating Info objects for all symbols.
     * Populate the global symbol table.
     */
    protected void analyzeProgram(Program program) {
        /* Proceed in phases:
         * 1. Analyze all global variable declarations.
         *    Do this first so that global variables are in the symbol
         *    table before we encounter `global x` declarations.
         * 2. Analyze classes and global functions now that global variables
         *    are in the symbol table.
         */
        for (Declaration decl : program.declarations) {
            if (decl instanceof VarDef) {
                VarDef varDef = (VarDef) decl;
                ValueType varType
                    = ValueType.annotationToValueType(varDef.var.type);
                GlobalVarInfo globalVar =
                    makeGlobalVarInfo(varDef.var.identifier.name, varType,
                                      varDef.value);

                this.globalVars.add(globalVar);

                this.globalSymbols.put(globalVar.getVarName(), globalVar);
            }
        }

        for (Declaration decl : program.declarations) {
            if (decl instanceof ClassDef) {
                ClassDef classDef = (ClassDef) decl;
                ClassInfo classInfo = analyzeClass(classDef);

                this.classes.add(classInfo);

                this.globalSymbols.put(classInfo.getClassName(), classInfo);
            } else if (decl instanceof FuncDef) {
                FuncDef funcDef = (FuncDef) decl;
                FuncInfo funcInfo = analyzeFunction(null, funcDef, 0,
                        globalSymbols, null);

                this.functions.add(funcInfo);

                this.globalSymbols.put(funcInfo.getBaseName(), funcInfo);
            }
        }
    }

    /**
     * Analyze a class definition CLASSDEF and return the resulting
     * Info object. Also creates Info objects for attributes/methods
     * and stores them in the ClassInfo. Methods are recursively
     * analyzed using analyzeFunction().
     */
    protected ClassInfo analyzeClass(ClassDef classDef) {
        String className = classDef.name.name;
        String superClassName = classDef.superClass.name;
        SymbolInfo superSymbolInfo = globalSymbols.get(superClassName);
        assert superSymbolInfo instanceof ClassInfo
            : "Semantic analysis should ensure that super-class is defined";
        ClassInfo superClassInfo = (ClassInfo) superSymbolInfo;
        ClassInfo classInfo = makeClassInfo(className, getNextTypeTag(),
                                            superClassInfo);

        for (Declaration decl : classDef.declarations) {
            if (decl instanceof VarDef) {
                VarDef attrDef = (VarDef) decl;
                ValueType attrType
                    = ValueType.annotationToValueType(attrDef.var.type);
                AttrInfo attrInfo =
                    makeAttrInfo(attrDef.var.identifier.name, attrType,
                                 attrDef.value);

                classInfo.addAttribute(attrInfo);
            } else if (decl instanceof FuncDef) {
                FuncDef funcDef = (FuncDef) decl;
                FuncInfo methodInfo = analyzeFunction(className, funcDef, 0,
                        globalSymbols, null);

                this.functions.add(methodInfo);

                classInfo.addMethod(methodInfo);
            }
        }

        return classInfo;
    }


    /**
     * Analyze a function or method definition FUNCDEF at nesting depth DEPTH
     * and return the resulting Info object.  Analyze any nested functions
     * recursively. The FuncInfo's symbol table is completely populated
     * by analyzing all the params, local vars, global and nonlocal var
     * declarations.
     *
     * CONTAINER is the fully qualified name of the containing function/class,
     * or null for global functions. PARENTSYMBOLTABLE symbol table contains
     * symbols inherited from outer regions (that of the containing
     * function/method for nested function definitions, and the
     * global symbol table for global function / method definitions).
     * PARENTFUNCINFO is the Info object for the parent function/method
     * if this definition is nested, and otherwise null.
     */
    protected FuncInfo
        analyzeFunction(String container, FuncDef funcDef,
                        int depth,
                        SymbolTable<SymbolInfo> parentSymbolTable,
                        FuncInfo parentFuncInfo) {
        /* We proceed in three steps.
         *  1. Create the FuncInfo object to be returned.
         *  2. Populate it by analyzing all the parameters and local var
         *     definitions.
         *  3. Now that the function's symbol table is built up, analyze
         *     nested function definitions.
         *  4. Add the body to the function descriptor for code gen.
         */

        String funcBaseName = funcDef.name.name;
        String funcQualifiedName =
            container != null
            ? String.format("%s.%s", container, funcBaseName)
            : funcBaseName;

        FuncInfo funcInfo =
            makeFuncInfo(funcQualifiedName, depth,
                         ValueType.annotationToValueType(funcDef.returnType),
                         parentSymbolTable, parentFuncInfo,
                         this::emitUserDefinedFunction);

        for (TypedVar param : funcDef.params) {
            ValueType paramType
                = ValueType.annotationToValueType(param.type);

            StackVarInfo paramInfo =
                makeStackVarInfo(param.identifier.name, paramType, null,
                                 funcInfo);

            funcInfo.addParam(paramInfo);
        }

        LocalDeclAnalyzer localDefs = new LocalDeclAnalyzer(funcInfo);

        for (Declaration decl : funcDef.declarations) {
            decl.dispatch(localDefs);
        }

        NestedFuncAnalyzer nestedFuncs = new NestedFuncAnalyzer(funcInfo);

        for (Declaration decl : funcDef.declarations) {
            decl.dispatch(nestedFuncs);
        }

        funcInfo.addBody(funcDef.statements);
        return funcInfo;
    }

    /** Analyzer for local variable declarations in a function. */
    protected class LocalDeclAnalyzer extends AbstractNodeAnalyzer<Void> {
        /** The descriptor for the function being analyzed. */
        private FuncInfo funcInfo;

        /** A new analyzer for a function with descriptor FUNCINFO0. */
        protected LocalDeclAnalyzer(FuncInfo funcInfo0) {
            funcInfo = funcInfo0;
        }

        @Override
        public Void analyze(VarDef localVarDef) {
            ValueType localVarType
                = ValueType.annotationToValueType(localVarDef.var.type);
            StackVarInfo localVar =
                makeStackVarInfo(localVarDef.var.identifier.name,
                                 localVarType, localVarDef.value,
                                 funcInfo);
            funcInfo.addLocal(localVar);
            return null;
        }

        @Override
        public Void analyze(GlobalDecl decl) {
            SymbolInfo symInfo =
                globalSymbols.get(decl.getIdentifier().name);
            assert symInfo instanceof GlobalVarInfo
                : "Semantic analysis should ensure that global var exists";
            GlobalVarInfo globalVar = (GlobalVarInfo) symInfo;
            funcInfo.getSymbolTable().put(globalVar.getVarName(),
                                          globalVar);
            return null;
        }

        @Override
        public Void analyze(NonLocalDecl decl) {
            assert funcInfo.getSymbolTable().get(decl.getIdentifier().name)
                instanceof StackVarInfo
                : "Semantic analysis should ensure nonlocal var exists";
            return null;
        }
    }

    /** Analyzer for nested function declarations in a function. */
    protected class NestedFuncAnalyzer extends AbstractNodeAnalyzer<Void> {
        /** Descriptor for the function being analyzed. */
        private FuncInfo funcInfo;

        /** A new analyzer for a function with descriptor FUNCINFO0. */
        protected NestedFuncAnalyzer(FuncInfo funcInfo0) {
            funcInfo = funcInfo0;
        }

        @Override
        public Void analyze(FuncDef nestedFuncDef) {
            FuncInfo nestedFuncInfo =
                analyzeFunction(funcInfo.getFuncName(), nestedFuncDef,
                                funcInfo.getDepth() + 1,
                                funcInfo.getSymbolTable(),
                                funcInfo);

            functions.add(nestedFuncInfo);

            funcInfo.getSymbolTable().put(nestedFuncInfo.getBaseName(),
                                          nestedFuncInfo);
            return null;
        }
    }


    /*------------------------------------------------------------*
     *                                                            *
     *  EMITING DATA SECTION FOR GLOBALS+PROTOTYPES+CONSTANTS     *
     *   (Students can ignore these methods as all the work has   *
     *    been done and does not need to be modified/extended)    *
     *                                                            *
     *------------------------------------------------------------*/


    /** Emit code to align next data item to word boundary. */
    protected void alignObject() {
        int wordSizeLog2 =
            31 - Integer.numberOfLeadingZeros(wordSize);
        backend.alignNext(wordSizeLog2);
    }

    /** Emit the constant section containing the prototype FOR the class
     *  defined by CLASSINFO. */
    protected void emitPrototype(ClassInfo classInfo) {
        backend.emitGlobalLabel(classInfo.getPrototypeLabel());
        backend.emitWordLiteral(classInfo.getTypeTag(),
                                String.format("Type tag for class: %s",
                                              classInfo.getClassName()));
        backend.emitWordLiteral(classInfo.attributes.size() + HEADER_SIZE,
                                "Object size");
        backend.emitWordAddress(classInfo.getDispatchTableLabel(),
                                "Pointer to dispatch table");
        for (VarInfo attr : classInfo.attributes) {
            String cmnt = String.format("Initial value of attribute: %s",
                                        attr.getVarName());
            emitConstant(attr.getInitialValue(), attr.getVarType(), cmnt);
        }
        alignObject();
    }

    /** Emit a word containing a constant representing VALUE, assuming that
     *  it will be interpreted as a value of static type TYPE. VALUE may be
     *  null, indicating None. TYPE may be null, indicating object.
     *  COMMENT is an optional comment.  */
    protected void emitConstant(Literal value, ValueType type, String comment) {
        if (type != null && type.equals(SymbolType.INT_TYPE)) {
            backend.emitWordLiteral(((IntegerLiteral) value).value, comment);
        } else if (type != null && type.equals(SymbolType.BOOL_TYPE)) {
            backend.emitWordLiteral(((BooleanLiteral) value).value ? 1 : 0,
                                    comment);
        } else {
            backend.emitWordAddress(constants.fromLiteral(value), comment);
        }
    }

    /** Emit code for all constants. */
    protected void emitConstants() {
        backend.emitGlobalLabel(constants.falseConstant);
        backend.emitWordLiteral(boolClass.getTypeTag(),
                                "Type tag for class: bool");
        backend.emitWordLiteral(boolClass.attributes.size() + HEADER_SIZE,
                                "Object size");
        backend.emitWordAddress(boolClass.getDispatchTableLabel(),
                                "Pointer to dispatch table");
        backend.emitWordLiteral(0, "Constant value of attribute: __bool__");
        alignObject();

        backend.emitGlobalLabel(constants.trueConstant);
        backend.emitWordLiteral(boolClass.getTypeTag(),
                                "Type tag for class: bool");
        backend.emitWordLiteral(boolClass.attributes.size() + HEADER_SIZE,
                                "Object size");
        backend.emitWordAddress(boolClass.getDispatchTableLabel(),
                                "Pointer to dispatch table");
        backend.emitWordLiteral(1, "Constant value of attribute: __bool__");
        alignObject();

        for (Map.Entry<String, Label> e : constants.strConstants.entrySet()) {
            String value = e.getKey();
            Label label = e.getValue();
            int numWordsForCharacters =
                value.length() / wordSize + 1;
            backend.emitGlobalLabel(label);
            backend.emitWordLiteral(strClass.getTypeTag(),
                                    "Type tag for class: str");
            backend.emitWordLiteral(3 + 1 + numWordsForCharacters,
                                    "Object size");
            backend.emitWordAddress(strClass.getDispatchTableLabel(),
                                    "Pointer to dispatch table");
            backend.emitWordLiteral(value.length(),
                                    "Constant value of attribute: __len__");
            backend.emitString(value, "Constant value of attribute: __str__");
            alignObject();
        }

        for (Map.Entry<Integer, Label> e : constants.intConstants.entrySet()) {
            Integer value = e.getKey();
            Label label = e.getValue();
            backend.emitGlobalLabel(label);
            backend.emitWordLiteral(intClass.getTypeTag(),
                                    "Type tag for class: int");
            backend.emitWordLiteral(intClass.attributes.size() + HEADER_SIZE,
                                    "Object size");
            backend.emitWordAddress(intClass.getDispatchTableLabel(),
                                    "Pointer to dispatch table");
            backend.emitWordLiteral(value,
                                    "Constant value of attribute: __int__");
            alignObject();
        }
    }


    /** Emit the method dispatching table for CLASSINFO. */
    protected void emitDispatchTable(ClassInfo classInfo) {
        Label dispatchTableLabel = classInfo.getDispatchTableLabel();
        if (dispatchTableLabel == null) {
            return;
        }
        backend.emitGlobalLabel(dispatchTableLabel);
        for (FuncInfo method : classInfo.methods) {
            String cmnt = String.format("Implementation for method: %s.%s",
                                        classInfo.getClassName(),
                                        method.getBaseName());
            backend.emitWordAddress(method.getCodeLabel(), cmnt);
        }
    }

    /*------------------------------------------------------------*
     *                                                            *
     *   UTILITY METHODS TO GET BYTE OFFSETS IN OBJECT LAYOUT     *
     *   (Students will find these methods helpful to use in      *
     *   their sub-class when generating code for expressions)    *
     *                                                            *
     *------------------------------------------------------------*/


    /** Return offset of the type-tag field in an object.  */
    protected int getTypeTagOffset() {
        return 0 * wordSize;
    }

    /** Return offset of the size field in an object. */
    protected int getObjectSizeOffset() {
        return 1 * wordSize;
    }

    /** Return offset of the start of the pointer to the method-dispatching
     *  table in an object. */
    protected int getDispatchTableOffset() {
        return 2 * wordSize;
    }

    /** Return the offset of the ATTRNAME attribute of an object of type
     *  described by CLASSINFO. */
    protected int getAttrOffset(ClassInfo classInfo, String attrName) {
        int attrIndex = classInfo.getAttributeIndex(attrName);
        assert attrIndex >= 0
            : "Type checker ensures that attributes are valid";
        return wordSize * (HEADER_SIZE + attrIndex);
    }

    /** Return the offset of the method named METHODNAME in the
     *  method-dispatching table for the class described by CLASSINFO. */
    protected int getMethodOffset(ClassInfo classInfo, String methodName) {
        int methodIndex = classInfo.getMethodIndex(methodName);
        assert methodIndex >= 0
            : "Type checker ensures that attributes are valid";
        return wordSize * methodIndex;
    }

    /*------------------------------------------------------------*
     *                                                            *
     *        UNIMPLEMENTED METHODS (should be extended)          *
     *                                                            *
     *------------------------------------------------------------*/


    /** Emits code for STATEMENTS, assumed to be at the top level. */
    protected abstract void emitTopLevel(List<Stmt> statements);

    /** Emits code for the body of user-defined function FUNCINFO. */
    protected abstract void emitUserDefinedFunction(FuncInfo funcInfo);

    /**
     * Emits code outside the ChocoPy program.
     *
     * Custom assembly routines (that may be jumpable from
     * program statements) can be emitted here.
     */
    protected abstract void emitCustomCode();

    /*------------------------------------------------------------*
     *                                                            *
     *             PREDEFINED FUNCTIONS AND ROUTINES              *
     *   (Students may find a cursory read of these methods to    *
     *    be useful to get an idea for how code can be emitted)   *
     *                                                            *
     *------------------------------------------------------------*/

    /** Return Risc V assembler code for function NAME from
     *  directory LIB, or null if it does not exist. LIB must end in
     *  '/'. */
    protected String getStandardLibraryCode(String name, String lib) {
        String simpleName = name.replace("$", "") + ".s";
        return getResourceFileAsString(lib + simpleName);
    }

    /** Emit label and body for the function LABEL, taking the
     *  source from directory LIB (must end in '/'). */
    protected void emitStdFunc(Label label, String lib) {
        emitStdFunc(label, label.toString(), lib);
    }

    /** Emit label and body for the function LABEL, taking the
     *  source from SOURCEFILE.s in directory LIB (must end in '/'). */
    protected void emitStdFunc(Label label, String sourceFile, String lib) {
        String source = getStandardLibraryCode(sourceFile, lib);
        if (source == null) {
            throw fatal("Code for %s is missing.", sourceFile);
        }
        backend.emitGlobalLabel(label);
        backend.emit(convertLiterals(source));
    }

    /** Emit label and body for the function LABEL, taking the
     *  source from from the default library directory. */
    protected void emitStdFunc(Label label) {
        emitStdFunc(label, LIBRARY_CODE_DIR);
    }

    /** Emit label and body for the function named NAME, taking the
     *  source from from directory LIB (must end in '/'). */
    protected void emitStdFunc(String name, String lib) {
        emitStdFunc(new Label(name), lib);
    }

    /** Emit label and body for the function NAME, taking the
     *  source from from the default library directory. */
    protected void emitStdFunc(String name) {
        emitStdFunc(name, LIBRARY_CODE_DIR);
    }

    /** Emit label and body for the function described by FUNCINFO, taking the
     *  source from from directory LIB (must end in '/'). */
    protected void emitStdFunc(FuncInfo funcInfo, String lib) {
        emitStdFunc(funcInfo.getCodeLabel(), lib);
    }

    /** Emit label and body for the function described by FUNCINFO, taking the
     *  source from from the default library directory. */
    protected void emitStdFunc(FuncInfo funcInfo) {
        emitStdFunc(funcInfo, LIBRARY_CODE_DIR);
    }

    /** Pattern matching STRING["..."]. */
    private static final Pattern STRING_LITERAL_PATN =
        Pattern.compile("STRING\\[\"(.*?)\"\\]");

    /** Return result of converting STRING["..."] notations in SOURCE to
     *  labels of string constants, adding those constants to the pool. */
    private String convertLiterals(String source) {
        Matcher matcher = STRING_LITERAL_PATN.matcher(source);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String r = constants.getStrConstant(matcher.group(1)).toString();
            matcher.appendReplacement(result,
                                      pad(r, ' ',
                                          matcher.end(0) - matcher.start(0),
                                          false));
        }
        return matcher.appendTail(result).toString();
    }

}
