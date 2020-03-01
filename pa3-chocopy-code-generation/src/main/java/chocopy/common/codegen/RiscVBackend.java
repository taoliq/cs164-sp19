package chocopy.common.codegen;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** RISC V assembly-language generation utilities. */
public class RiscVBackend {

    /** Accumulator for assembly code output. */
    protected final StringWriter asmText = new StringWriter();

    /** Allows print, println, and printf of assmebly code. */
    private final PrintWriter out = new PrintWriter(asmText);

    /** The word size in bytes for RISC-V 32-bit. */
    protected static final int WORD_SIZE = 4;

    /** Mappung from @-symbols to values. */
    private HashMap<String, String> defns = new HashMap<>();

    /** The RISC-V registers. */
    public enum Register {

        A0("a0"), A1("a1"), A2("a2"), A3("a3"), A4("a4"), A5("a5"), A6("a6"),
        A7("a7"),
        T0("t0"), T1("t1"), T2("t2"), T3("t3"), T4("t4"), T5("t5"), T6("t6"),
        S1("s1"), S2("s2"), S3("s3"), S4("s4"), S5("s5"),
        S6("s6"), S7("s7"), S8("s8"), S9("s9"), S10("s10"), S11("s11"),
        FP("fp"), SP("sp"), GP("gp"), RA("ra"), ZERO("zero");

        /** The name of the register used in assembly. */
        protected final String name;

        /** This register's code representation is NAME. */
        Register(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return this.name;
        }

    }

    /** Matches a reference to an assembler symbol, and possibly a trailing
     *  +CONST or -CONST. */
    static final Pattern ASM_SYM_REF_PATN =
        Pattern.compile("@(.[a-zA-Z_0-9.$]*)(?:\\s*([-+]\\d+))?|(#.*)|(\r?\n)");
    /** Matches a decimal numeral with optional sign. */
    static final Pattern SIGNED_INT_PATN = Pattern.compile("[-+]?\\d+");

    @Override
    public String toString() {
        return asmText.toString();
    }

    /** Return the accumulated assembly code.  It is an error if the
     *  accumulated code contains any '@' references to undefined symbols. */
    protected String oldToString() {
        int deltaSpace;
        StringBuffer result = new StringBuffer();
        deltaSpace = 0;
        Matcher refs = ASM_SYM_REF_PATN.matcher(asmText.toString());
        while (refs.find()) {
            if (refs.group(3) != null) {
                String repl = refs.group(3);
                refs.appendReplacement(result, "");
                while (deltaSpace < 0) {
                    result.append(" ");
                    deltaSpace += 1;
                }
                result.append(repl);
            } else if (refs.group(4) != null) {
                deltaSpace = 0;
                refs.appendReplacement(result, refs.group(4));
            } else {
                String repl;
                repl = defns.get(refs.group(1));
                if (repl == null) {
                    throw new IllegalStateException("undefined symbol: @"
                                                    + refs.group(1));
                }
                if (refs.group(2) != null) {
                    if (SIGNED_INT_PATN.matcher(repl).matches()) {
                        int val = Integer.parseInt(repl)
                            + Integer.parseInt(refs.group(2));
                        repl = Integer.toString(val);
                    } else {
                        repl += refs.group(2);
                    }
                }
                refs.appendReplacement(result, repl);
                deltaSpace += repl.length() - refs.end(0) + refs.start(0);
            }
        }
        refs.appendTail(result);

        return result.toString();
    }

    /** Define @NAME to have the value VALUE.  Here, NAME is assumed to be
     *  an identifier consisting of letters, digits, underscores, and any of
     *  the charcters '$' or '.', and that does not start with a digit.  Value
     *  may be a numeral or another symbol.
     */
    public void defineSym(String name, String value) {
        if (name.startsWith("@")) {
            emitInsn(String.format(".equiv %s, %s", name, value), null);
        } else {
            emitInsn(String.format(".equiv @%s, %s", name, value), null);
        }
    }

    /** Define @NAME to have the value VALUE, where value is converted to
     *  a string.  See {@link #defineSym(java.lang.String, java.lang.String)}.
     */
    public void defineSym(String name, int value) {
        defineSym(name, Integer.toString(value));
    }

    /**
     * Returns the word size in bytes.
     *
     * This method is used instead of directly accessing the
     * static field {@link #WORD_SIZE}, so that this class
     * may be extended with alternate word sizes.
     */
    public int getWordSize() {
        return WORD_SIZE;
    }

    /**
     * Emit the text STR to the output stream verbatim.  STR should have no
     * trailing newline.
     */
    protected void emit(String str) {
        out.println(str);
    }

    /**
     * Emit instruction or directive INSN along with COMMENT as a one-line
     * comment, if non-null.
     */
    public void emitInsn(String insn, String comment) {
        if (comment != null) {
            emit(String.format("  %-40s # %s", insn, comment));
        } else {
            emitInsn(insn);
        }
    }

    /**
     * Emit instruction or directive INSN without a comment.
     */
    protected void emitInsn(String insn) {
        emit(String.format("  %s", insn));
    }

    /**
     * Emit a local label marker for LABEL with one-line comment COMMENT (null
     * if missing).  Invoke only once per unique label.
     */
    public void emitLocalLabel(Label label, String comment) {
        if (comment != null) {
            emit(String.format("%-42s # %s", label + ":", comment));
        } else {
            emit(String.format("%s:", label + ":"));
        }
    }

    /**
     * Emit a global label marker for LABEL. Invoke only once per
     * unique label.
     */
    public void emitGlobalLabel(Label label) {
        emit(String.format("\n.globl %s", label));
        emit(String.format("%s:", label));
    }

    /**
     * Emit a data word containing VALUE as an integer value.  COMMENT is
     * a emitted as a one-line comment, if non-null.
     */
    public void emitWordLiteral(Integer value, String comment) {
        emitInsn(String.format(".word %s", value), comment);
    }

    /**
     * Emit a data word containing the address ADDR, or 0 if LABEL is null.
     * COMMENT is a emitted as a one-line comment, if non-null.
     */
    public void emitWordAddress(Label addr, String comment) {
        if (addr == null) {
            emitWordLiteral(0, comment);
        } else {
            emitInsn(String.format(".word %s", addr), comment);
        }
    }


    /**
     * Emit VALUE as an ASCII null-terminated string constant, with
     * COMMENT as its one-line comment, if non-null.
     */
    public void emitString(String value, String comment) {
        String quoted = value
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\t", "\\t")
                .replace("\"", "\\\"");
        emitInsn(String.format(".string \"%s\"", quoted), comment);
    }

    /**
     * Mark the start of a data section.
     */
    public void startData() {
        emit("\n.data");
    }

    /**
     * Mark the start of a code/text section.
     */
    public void startCode() {
        emit("\n.text");
    }

    /**
     * Align the next instruction/word in memory to
     * a multiple of 2**POW bytes.
     */
    public void alignNext(int pow) {
        emitInsn(String.format(".align %d", pow));
    }

    /**
     * Emit an ecall instruction, with one-line comment COMMENT,
     * if non-null.
     */
    public void emitEcall(String comment) {
        emitInsn("ecall", comment);
    }

    /**
     * Emit a load-address instruction with destination RD and source
     * LABEL.  COMMENT is an optional one-line comment (null if missing).
     */
    public void emitLA(Register rd, Label label, String comment) {
        emitInsn(String.format("la %s, %s", rd, label), comment);
    }

    /**
     * Emit a load-immediate pseudo-op to set RD to IMM.
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitLI(Register rd, Integer imm, String comment) {
        emitInsn(String.format("li %s, %d", rd, imm), comment);
    }

    /**
     * Emit a load-upper-immediate instruction to set the upper 20 bits
     * of RD to IMM, where 0 <= IMM < 2**20. COMMENT is an optional
     * one-line comment (null if missing).
     */
    public void emitLUI(Register rd, Integer imm, String comment) {
        emitInsn(String.format("lui %s, %d", rd, imm), comment);
    }

    /**
     * Emit a move instruction to set RD to the contents of RS.
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitMV(Register rd, Register rs, String comment) {
        emitInsn(String.format("mv %s, %s", rd, rs), comment);
    }

    /**
     * Emit a jump-register (computed jump) instruction to the address in
     * RS.  COMMENT is an optional one-line comment (null if missing).
     */
    public void emitJR(Register rs, String comment) {
        emitInsn(String.format("jr %s", rs), comment);
    }

    /**
     * Emit a jump (unconditional jump) instruction to LABEL.
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitJ(Label label, String comment) {
        emitInsn(String.format("j %s", label), comment);
    }


    /**
     * Emit a jump-and-link instruction to LABEL.
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitJAL(Label label, String comment) {
        emitInsn(String.format("jal %s", label), comment);
    }

    /**
     * Emit a computed-jump-and-link instruction to the address in RS.
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitJALR(Register rs, String comment) {
        emitInsn(String.format("jalr %s", rs), comment);
    }

    /**
     * Emit an add-immediate instruction performing RD = RS + IMM.
     * Requires -2048 <= IMM < 2048. COMMENT is an optional one-line
     * comment (null if missing).
     */
    public void emitADDI(Register rd, Register rs, Integer imm,
                         String comment) {
        emitInsn(String.format("addi %s, %s, %d", rd, rs, imm), comment);
    }

    /**
     * Emit an add-immediate instruction performing RD = RS + IMM.
     * Here, IMM is a string generally containing a symbolic assembler
     * constant (see defineSym) representing an integer value, or an
     * expression of the form @NAME+NUM or @NAME-NUM.
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitADDI(Register rd, Register rs, String imm,
                         String comment) {
        emitInsn(String.format("addi %s, %s, %s", rd, rs, imm), comment);
    }

    /**
     * Emit an add instruction performing RD = RS1 + RS2 mod 2**32.
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitADD(Register rd, Register rs1, Register rs2,
                        String comment) {
        emitInsn(String.format("add %s, %s, %s", rd, rs1, rs2), comment);
    }

    /**
     * Emit a subtract instruction performing RD = RS1 - RS2 mod 2**32.
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitSUB(Register rd, Register rs1, Register rs2,
                        String comment) {
        emitInsn(String.format("sub %s, %s, %s", rd, rs1, rs2), comment);
    }

    /**
     * Emit a multiply instruction performing RD = RS1 * RS2 mod 2**32.
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitMUL(Register rd, Register rs1, Register rs2,
                        String comment) {
        emitInsn(String.format("mul %s, %s, %s", rd, rs1, rs2), comment);
    }

    /**
     * Emit a signed integer divide instruction performing
     * RD = RS1 / RS2 mod 2**32, rounding the result toward 0.
     * If RS2 == 0, sets RD to -1. If RS1 == -2**31 and RS2 == -1,
     * sets RD to -2**31.
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitDIV(Register rd, Register rs1, Register rs2,
                        String comment) {
        emitInsn(String.format("div %s, %s, %s", rd, rs1, rs2), comment);
    }

    /**
     * Emit a remainder instruction: RD = RS1 rem RS2 defined so that
     * (RS1 / RS2) * RS2 + (RS1 rem RS2) == RS1, where / is as for
     * emitDIV.  COMMENT is an optional one-line comment (null if missing).
     */
    public void emitREM(Register rd, Register rs1, Register rs2,
                        String comment) {
        emitInsn(String.format("rem %s, %s, %s", rd, rs1, rs2), comment);
    }

    /**
     * Emit an xor instruction: RD = RS1 ^ RS2. COMMENT is an optional
     * one-line comment (null if missing).
     */
    public void emitXOR(Register rd, Register rs1, Register rs2,
                        String comment) {
        emitInsn(String.format("xor %s, %s, %s", rd, rs1, rs2), comment);
    }

    /**
     * Emit an xor-immediate instruction: RD = RS ^ IMM, where
     * -2048 <= IMM < 2048.  COMMENT is an optional
     * one-line comment (null if missing).
     */
    public void emitXORI(Register rd, Register rs, Integer imm,
                         String comment) {
        emitInsn(String.format("xori %s, %s, %d", rd, rs, imm), comment);
    }

    /**
     * Emit a bitwise and instruction: RD = RS1 & RS2.
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitAND(Register rd, Register rs1, Register rs2,
                        String comment) {
        emitInsn(String.format("and %s, %s, %s", rd, rs1, rs2), comment);
    }

    /**
     * Emit a bitwise and-immediate instruction: RD = RS & IMM, where
     * -2048 <= IMM < 2048.
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitANDI(Register rd, Register rs, Integer imm,
                         String comment) {
        emitInsn(String.format("andi %s, %s, %d", rd, rs, imm), comment);
    }

    /**
     * Emit a bitwise or instruction: RD = RS1 | RS2.
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitOR(Register rd, Register rs1, Register rs2,
                       String comment) {
        emitInsn(String.format("or %s, %s, %s", rd, rs1, rs2), comment);
    }

    /**
     * Emit a bitwise or-immediate instruction: RD = RS | IMM, where
     * -2048 <= IMM < 2048.
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitORI(Register rd, Register rs, Integer imm,
                        String comment) {
        emitInsn(String.format("ori %s, %s, %d", rd, rs, imm), comment);
    }

    /**
     * Emit a logical left shift instruction: RD = RS1 << (RS2 & 0x31).
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitSLL(Register rd, Register rs1, Register rs2,
                        String comment) {
        emitInsn(String.format("sll %s, %s, %s", rd, rs1, rs2), comment);
    }

    /**
     * Emit a logical left shift instruction: RD = RS << (IMM & 0x31).
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitSLLI(Register rd, Register rs, Integer imm,
                        String comment) {
        emitInsn(String.format("slli %s, %s, %d", rd, rs, imm), comment);
    }

    /**
     * Emit a logical right shift instruction: RD = RS1 >>> (RS2 & 0x31).
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitSRL(Register rd, Register rs1, Register rs2,
                        String comment) {
        emitInsn(String.format("srl %s, %s, %s", rd, rs1, rs2), comment);
    }

    /**
     * Emit a logical right shift instruction: RD = RS >>> (IMM & 0x31).
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitSRLI(Register rd, Register rs, Integer imm,
                        String comment) {
        emitInsn(String.format("srli %s, %s, %d", rd, rs, imm), comment);
    }

    /**
     * Emit an arithmetic right shift instruction: RD = RS1 >> (RS2 & 0x31).
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitSRA(Register rd, Register rs1, Register rs2,
                        String comment) {
        emitInsn(String.format("sra %s, %s, %s", rd, rs1, rs2), comment);
    }

    /**
     * Emit an arithmetic right shift instruction: RD = RS >> (IMM & 0x31).
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitSRAI(Register rd, Register rs, Integer imm,
                        String comment) {
        emitInsn(String.format("srai %s, %s, %d", rd, rs, imm), comment);
    }

    /**
     * Emit a load-word instruction: RD = MEMORY[RS + IMM]:4, where
     * -2048 <= IMM < 2048.
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitLW(Register rd, Register rs, Integer imm,
                       String comment) {
        emitInsn(String.format("lw %s, %d(%s)", rd, imm, rs), comment);
    }

    /**
     * Emit a load-word instruction: RD = MEMORY[RS + IMM]:4, where
     * -2048 <= IMM < 2048.  Here, IMM is symbolic constant expression
     * (see emitADDI).  COMMENT is an optional one-line
     * comment (null if missing).
     */
    public void emitLW(Register rd, Register rs, String imm,
                       String comment) {
        emitInsn(String.format("lw %s, %s(%s)", rd, imm, rs), comment);
    }

    /**
     * Emit a store-word instruction: MEMORY[RS1 + IMM]:4 = RS2, where
     * -2048 <= IMM < 2048.
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitSW(Register rs2, Register rs1, Integer imm,
                       String comment) {
        emitInsn(String.format("sw %s, %d(%s)", rs2, imm, rs1), comment);
    }

    /**
     * Emit a store-word instruction: MEMORY[RS1 + IMM]:4 = RS2, where
     * -2048 <= IMM < 2048.  Here, IMM is symbolic constant expression
     * (see emitADDI).  COMMENT is an optional one-line
     * comment (null if missing).
     */
    public void emitSW(Register rs2, Register rs1, String imm,
                       String comment) {
        emitInsn(String.format("sw %s, %s(%s)", rs2, imm, rs1), comment);
    }

    /**
     * Emit a load-word instruction for globals: RD = MEMORY[LABEL]:4.
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitLW(Register rd, Label label, String comment) {
        emitInsn(String.format("lw %s, %s", rd, label), comment);
    }

    /**
     * Emit a store-word instruction for globals: MEMORY[LABEL]:4 = RS,
     * using TMP as a temporary register.
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitSW(Register rs, Label label, Register tmp,
                       String comment) {
        emitInsn(String.format("sw %s, %s, %s", rs, label, tmp), comment);
    }

    /**
     * Emit a load-byte instruction: RD = MEMORY[RS + IMM]:1, where
     * -2048 <= IMM < 2048.  Sign extends the byte loaded.
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitLB(Register rd, Register rs, Integer imm,
                       String comment) {
        emitInsn(String.format("lb %s, %d(%s)", rd, imm, rs), comment);
    }

    /**
     * Emit a load-byte-unsigned instruction: RD = MEMORY[RS + IMM]:1, where
     * -2048 <= IMM < 2048.  Zero-extends the byte loaded.
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitLBU(Register rd, Register rs, Integer imm,
                        String comment) {
        emitInsn(String.format("lbu %s, %d(%s)", rd, imm, rs), comment);
    }

    /**
     * Emit a store-byte instruction: MEMORY[RS1 + IMM]:1 = RS2, where
     * -2048 <= IMM < 2048.  Assigns the low-order byte of RS2 to memory.
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitSB(Register rs2, Register rs1, Integer imm,
                       String comment) {
        emitInsn(String.format("sb %s, %d(%s)", rs2, imm, rs1), comment);
    }

    /**
     * Emit a branch-if-equal instruction: if RS1 == RS2 goto LABEL.
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitBEQ(Register rs1, Register rs2, Label label,
                        String comment) {
        emitInsn(String.format("beq %s, %s, %s", rs1, rs2, label), comment);
    }

    /**
     * Emit a branch-if-unequal instruction: if RS1 != RS2 goto LABEL.
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitBNE(Register rs1, Register rs2, Label label,
                        String comment) {
        emitInsn(String.format("bne %s, %s, %s", rs1, rs2, label), comment);
    }

    /**
     * Emit a branch-if-greater-or-equal (signed) instruction:
     * if RS1 >= RS2 goto LABEL.
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitBGE(Register rs1, Register rs2, Label label,
                         String comment) {
        emitInsn(String.format("bge %s, %s, %s", rs1, rs2, label), comment);
    }

    /**
     * Emit a branch-if-greater-or-equal (unsigned) instruction:
     * if RS1 >= RS2 goto LABEL.
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitBGEU(Register rs1, Register rs2, Label label,
                         String comment) {
        emitInsn(String.format("bgeu %s, %s, %s", rs1, rs2, label), comment);
    }

    /**
     * Emit a branch-if-less-than (signed) instruction:
     * if RS1 < RS2 goto LABEL.
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitBLT(Register rs1, Register rs2, Label label,
                         String comment) {
        emitInsn(String.format("blt %s, %s, %s", rs1, rs2, label), comment);
    }

    /**
     * Emit a branch-if-less-than (unsigned) instruction:
     * if RS1 < RS2 goto LABEL.
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitBLTU(Register rs1, Register rs2, Label label,
                         String comment) {
        emitInsn(String.format("bltu %s, %s, %s", rs1, rs2, label), comment);
    }

    /**
     * Emit a branch-if-zero instruction: if RS == 0 goto LABEL.
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitBEQZ(Register rs, Label label, String comment) {
        emitInsn(String.format("beqz %s, %s", rs, label), comment);
    }

    /**
     * Emit a branch-if-not-zero instruction: if RS != 0 goto LABEL.
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitBNEZ(Register rs, Label label, String comment) {
        emitInsn(String.format("bnez %s, %s", rs, label), comment);
    }

    /**
     * Emit a branch-if-less-than-zero instruction: if RS < 0 goto LABEL.
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitBLTZ(Register rs, Label label, String comment) {
        emitInsn(String.format("bltz %s, %s", rs, label), comment);
    }

    /**
     * Emit a branch-if-greater-than-zero instruction: if RS > 0 goto LABEL.
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitBGTZ(Register rs, Label label, String comment) {
        emitInsn(String.format("bgtz %s, %s", rs, label), comment);
    }

    /**
     * Emit a branch-if-less-than-equal-to-zero instruction:
     * if RS <= 0 goto LABEL.
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitBLEZ(Register rs, Label label, String comment) {
        emitInsn(String.format("blez %s, %s", rs, label), comment);
    }

    /**
     * Emit a branch-if-greater-than-equal-to-zero instruction:
     * if RS >= 0 goto LABEL.
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitBGEZ(Register rs, Label label, String comment) {
        emitInsn(String.format("bgez %s, %s", rs, label), comment);
    }

    /**
     * Emit a set-less-than instruction: RD = 1 if RS1 < RS2 else 0.
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitSLT(Register rd, Register rs1, Register rs2,
                        String comment) {
        emitInsn(String.format("slt %s, %s, %s", rd, rs1, rs2), comment);
    }

    /**
     * Emit a set-if-zero instruction: RD = 1 if RS == 0 else 0.
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitSEQZ(Register rd, Register rs, String comment) {
        emitInsn(String.format("seqz %s, %s", rd, rs), comment);
    }

    /**
     * Emit a set-if-not-zero instruction: RD = 1 if RS != 0 else 0.
     * COMMENT is an optional one-line comment (null if missing).
     */
    public void emitSNEZ(Register rd, Register rs, String comment) {
        emitInsn(String.format("snez %s, %s", rd, rs), comment);
    }

}
