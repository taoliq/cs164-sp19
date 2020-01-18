package chocopy.pa1;
import java_cup.runtime.*;
import java.util.*;

%%

/*** Do not change the flags below unless you know what you are doing. ***/

%unicode
%line
%column

%class ChocoPyLexer
%public

%cupsym ChocoPyTokens
%cup
%cupdebug

%eofclose false

/*** Do not change the flags above unless you know what you are doing. ***/

/* The following code section is copied verbatim to the
 * generated lexer class. */
%{
    /* The code below includes some convenience methods to create tokens
     * of a given type and optionally a value that the CUP parser can
     * understand. Specifically, a lot of the logic below deals with
     * embedded information about where in the source code a given token
     * was recognized, so that the parser can report errors accurately.
     * (It need not be modified for this project.) */

    /** Producer of token-related values for the parser. */
    final ComplexSymbolFactory symbolFactory = new ComplexSymbolFactory();

    /** Return a terminal symbol of syntactic category TYPE and no
     *  semantic value at the current source location. */
    private Symbol symbol(int type) {
        return symbol(type, yytext());
    }

    /** Return a terminal symbol of syntactic category TYPE and semantic
     *  value VALUE at the current source location. */
    private Symbol symbol(int type, Object value) {
        return symbolFactory.newSymbol(ChocoPyTokens.terminalNames[type], type,
            new ComplexSymbolFactory.Location(yyline + 1, yycolumn + 1),
            new ComplexSymbolFactory.Location(yyline + 1, yycolumn + yylength()),
            value);
    }

    /** Return a terminal symbol of syntactic category TYPE and semantic
     *  value VALUE at the specified location. *
     *  For INDENT tokens.*/
    private Symbol symbol(int type, int x0, int y0, int len) {
        return symbolFactory.newSymbol(ChocoPyTokens.terminalNames[type], type,
            new ComplexSymbolFactory.Location(x0 + 1, y0 + 1),
            new ComplexSymbolFactory.Location(x0 + 1, y0 + len),
            yytext());
    }

    /** Store string literals. */
    StringBuffer string = new StringBuffer();

    Stack<Integer> stk = new Stack<Integer>();
    int currentIndent = 0;
    final int TAB_WIDTH = 8;
%}

%init{
    stk.push(0);
%init}

%eofval{
    // System.out.println("top of stk is " + stk.peek());
    if (stk.size() > 1) {
        stk.pop();
        return symbol(ChocoPyTokens.DEDENT);
    } else {
        return symbol(ChocoPyTokens.EOF);
    }
%eofval}

/* Macros (regexes used in rules below) */

WhiteSpace = [ \t]
LineBreak  = \r|\n|\r\n
InputCharacter = [^\r\n]

Comment = "#" {InputCharacter}*

Identifier = [a-zA-Z_] \w*

IntegerLiteral = 0 | [1-9][0-9]*

%state STRING
%state VALID

%%

<YYINITIAL> {
  /* handle indent, dedent and blank lines. */
  " "                         { currentIndent++; }
  "\t"                        { currentIndent += TAB_WIDTH; }
  {LineBreak}                 { currentIndent = 0; }
  {Comment}                   { /* ignore */ }
  {InputCharacter}            { yypushback(1);
                                if (currentIndent > stk.peek()) {
                                  yybegin(VALID);
                                  int y0 = stk.peek();
                                  int len = currentIndent - y0;
                                  stk.push(currentIndent);
                                  return symbol(ChocoPyTokens.INDENT, yyline, y0, len);
                                } else if (currentIndent == stk.peek()) {
                                  yybegin(VALID);
                                } else {
                                  stk.pop();
                                  return symbol(ChocoPyTokens.DEDENT);
                                } }
  // <<EOF>>                     { }//yybegin(YYINITIAL);}// return symbol(ChocoPyTokens.EOF); }

}

<VALID> {
  /* keywords */
  "False"                     { return symbol(ChocoPyTokens.FALSE); }
  "None"                      { return symbol(ChocoPyTokens.NONE); }
  "True"                      { return symbol(ChocoPyTokens.TRUE); }
  "and"                       { return symbol(ChocoPyTokens.AND); }
  "as"                        { return symbol(ChocoPyTokens.AS); }
  "assert"                    { return symbol(ChocoPyTokens.ASSERT); }
  "async"                     { return symbol(ChocoPyTokens.ASYNC); }
  "await"                     { return symbol(ChocoPyTokens.AWAIT); }
  "break"                     { return symbol(ChocoPyTokens.BREAK); }
  "class"                     { return symbol(ChocoPyTokens.CLASS); }
  "continue"                  { return symbol(ChocoPyTokens.CONTINUE); }
  "def"                       { return symbol(ChocoPyTokens.DEF); }
  "del"                       { return symbol(ChocoPyTokens.DEL); }
  "elif"                      { return symbol(ChocoPyTokens.ELIF); }
  "else"                      { return symbol(ChocoPyTokens.ELSE); }
  "except"                    { return symbol(ChocoPyTokens.EXCEPT); }
  "finally"                   { return symbol(ChocoPyTokens.FINALLY); }
  "for"                       { return symbol(ChocoPyTokens.FOR); }
  "from"                      { return symbol(ChocoPyTokens.FROM); }
  "global"                    { return symbol(ChocoPyTokens.GLOBAL); }
  "if"                        { return symbol(ChocoPyTokens.IF); }
  "import"                    { return symbol(ChocoPyTokens.IMPORT); }
  "in"                        { return symbol(ChocoPyTokens.IN); }
  "is"                        { return symbol(ChocoPyTokens.IS, yytext()); }
  "lambda"                    { return symbol(ChocoPyTokens.LAMBDA); }
  "nonlocal"                  { return symbol(ChocoPyTokens.NONLOCAL); }
  "not"                       { return symbol(ChocoPyTokens.NOT); }
  "or"                        { return symbol(ChocoPyTokens.OR); }
  "pass"                      { return symbol(ChocoPyTokens.PASS); }
  "raise"                     { return symbol(ChocoPyTokens.RAISE); }
  "return"                    { return symbol(ChocoPyTokens.RETURN); }
  "try"                       { return symbol(ChocoPyTokens.TRY); }
  "while"                     { return symbol(ChocoPyTokens.WHILE); }
  "with"                      { return symbol(ChocoPyTokens.WITH); }
  "yield"                     { return symbol(ChocoPyTokens.YIELD); }

  /* Identifiers. */
  {Identifier}                { return symbol(ChocoPyTokens.IDENTIFIER, yytext()); }

  /* Delimiters. */
  {LineBreak}                 { currentIndent = 0; 
                                yybegin(YYINITIAL); 
                                return symbol(ChocoPyTokens.NEWLINE); }

  /* Literals. */
  {IntegerLiteral}            { return symbol(ChocoPyTokens.INTEGER,
                                                 Integer.parseInt(yytext())); }
  // "\""                        { string.setLength(0); yybegin(STRING);}
  \"{Identifier}\"            { String matched = yytext();
                                return symbol(ChocoPyTokens.IDSTRING, 
                                              matched.substring(1, yylength() - 1)); }
  
  /* this is not correct. It can not work with escape sequences.
   * I think the string litral should be resolved in the STRING state below.
   */ 
  \"[^\"]*\"                  { String matched = yytext();
                                return symbol(ChocoPyTokens.STRING, 
                                              matched.substring(1, yylength() - 1)); }   

  /* Operators. */
  "+"                         { return symbol(ChocoPyTokens.PLUS,        yytext()); }
  "-"                         { return symbol(ChocoPyTokens.MINUS,       yytext()); }
  "*"                         { return symbol(ChocoPyTokens.TIMES,       yytext()); }
  "//"                        { return symbol(ChocoPyTokens.DIVIDE,      yytext()); }
  "%"                         { return symbol(ChocoPyTokens.MOD,         yytext()); }
  "<"                         { return symbol(ChocoPyTokens.LESSTHAN,    yytext()); }
  ">"                         { return symbol(ChocoPyTokens.GREATERTHAN, yytext()); }
  "<="                        { return symbol(ChocoPyTokens.LESSEQ,      yytext()); }
  ">="                        { return symbol(ChocoPyTokens.GREATEREQ,   yytext()); }
  "=="                        { return symbol(ChocoPyTokens.EQEQ,        yytext()); }
  "!="                        { return symbol(ChocoPyTokens.NOTEQ,       yytext()); }
  "="                         { return symbol(ChocoPyTokens.EQ,          yytext()); }
  "("                         { return symbol(ChocoPyTokens.LPAREN,      yytext()); }
  ")"                         { return symbol(ChocoPyTokens.RPAREN,      yytext()); }
  "["                         { return symbol(ChocoPyTokens.LBRACKET,    yytext()); }
  "]"                         { return symbol(ChocoPyTokens.RBRACKET,    yytext()); }
  ","                         { return symbol(ChocoPyTokens.COMMA,       yytext()); }
  ":"                         { return symbol(ChocoPyTokens.COLON,       yytext()); }
  "."                         { return symbol(ChocoPyTokens.DOT,         yytext()); }
  "->"                        { return symbol(ChocoPyTokens.ARROW,       yytext()); }

  /* Comments. */
  {Comment}                   { /* ignore */ }


  /* Whitespace. */
  {WhiteSpace}                { /* ignore */ }
}

<STRING> {
  "\""                        { yybegin(VALID); 
                                return symbol(ChocoPyTokens.STRING, string.toString()); }
                                  
  [^\n\r\"\\]+                { string.append( yytext() ); }
  \\t                         { string.append('\t'); }
  \\n                         { string.append('\n'); }
  \\r                         { string.append('\r'); }
  \\\"                        { string.append('\"'); }
  \\                          { string.append('\\'); }
}

// <<EOF>>                       { return symbol(ChocoPyTokens.EOF); }

/* Error fallback. */
[^]                           { return symbol(ChocoPyTokens.UNRECOGNIZED); }
