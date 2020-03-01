package chocopy.common.analysis;

import chocopy.common.astnodes.*;

/**
 * An empty implementation of the {@link NodeAnalyzer} that
 * simply returns does nothing and returns null for every
 * AST node type.
 *
 * T is the type of analysis result.
 */
public class AbstractNodeAnalyzer<T> implements NodeAnalyzer<T> {
    @Override
    public T analyze(AssignStmt node) {
        return defaultAction(node);
    }

    @Override
    public T analyze(BinaryExpr node) {
        return defaultAction(node);
    }

    @Override
    public T analyze(BooleanLiteral node) {
        return defaultAction(node);
    }

    @Override
    public T analyze(CallExpr node) {
        return defaultAction(node);
    }

    @Override
    public T analyze(ClassDef node) {
        return defaultAction(node);
    }

    @Override
    public T analyze(ClassType node) {
        return defaultAction(node);
    }

    @Override
    public T analyze(CompilerError node) {
        return defaultAction(node);
    }

    @Override
    public T analyze(Errors node) {
        return defaultAction(node);
    }

    @Override
    public T analyze(ExprStmt node) {
        return defaultAction(node);
    }

    @Override
    public T analyze(ForStmt node) {
        return defaultAction(node);
    }

    @Override
    public T analyze(FuncDef node) {
        return defaultAction(node);
    }

    @Override
    public T analyze(GlobalDecl node) {
        return defaultAction(node);
    }

    @Override
    public T analyze(Identifier node) {
        return defaultAction(node);
    }

    @Override
    public T analyze(IfExpr node) {
        return defaultAction(node);
    }

    @Override
    public T analyze(IfStmt node) {
        return defaultAction(node);
    }

    @Override
    public T analyze(IndexExpr node) {
        return defaultAction(node);
    }

    @Override
    public T analyze(IntegerLiteral node) {
        return defaultAction(node);
    }

    @Override
    public T analyze(ListExpr node) {
        return defaultAction(node);
    }

    @Override
    public T analyze(ListType node) {
        return defaultAction(node);
    }

    @Override
    public T analyze(MemberExpr node) {
        return defaultAction(node);
    }

    @Override
    public T analyze(MethodCallExpr node) {
        return defaultAction(node);
    }

    @Override
    public T analyze(NoneLiteral node) {
        return defaultAction(node);
    }

    @Override
    public T analyze(NonLocalDecl node) {
        return defaultAction(node);
    }

    @Override
    public T analyze(Program node) {
        return defaultAction(node);
    }

    @Override
    public T analyze(ReturnStmt node) {
        return defaultAction(node);
    }

    @Override
    public T analyze(StringLiteral node) {
        return defaultAction(node);
    }

    @Override
    public T analyze(TypedVar node) {
        return defaultAction(node);
    }

    @Override
    public T analyze(UnaryExpr node) {
        return defaultAction(node);
    }

    @Override
    public T analyze(VarDef node) {
        return defaultAction(node);
    }

    @Override
    public T analyze(WhileStmt node) {
        return defaultAction(node);
    }

    @Override
    public void setDefault(T value) {
        defaultValue = value;
    }

    @Override
    public T defaultAction(Node node) {
        return defaultValue;
    }

    /** Default value for non-overridden methods. */
    private T defaultValue = null;

}
