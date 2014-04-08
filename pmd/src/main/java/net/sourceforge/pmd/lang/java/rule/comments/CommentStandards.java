package net.sourceforge.pmd.lang.java.rule.comments;

import net.sourceforge.pmd.lang.java.ast.ASTClassOrInterfaceDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTConstructorDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTEnumDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTFieldDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.ast.AbstractJavaAccessNode;
import net.sourceforge.pmd.lang.java.javadoc.checker.CommentChecker;

/**
 *
 * This class parses and validates JavaDoc.
 *
 */
public class CommentStandards extends AbstractCommentRule {

    @Override
    public Object visit(ASTClassOrInterfaceDeclaration node, Object data) {

        checkComment(node, data);
        return super.visit(node, data);
    }

    @Override
    public Object visit(ASTEnumDeclaration node, Object data) {

        checkComment(node, data);
        return super.visit(node, data);
    }

    @Override
    public Object visit(ASTConstructorDeclaration node, Object data) {

        checkComment(node, data);
        return super.visit(node, data);
    }

    @Override
    public Object visit(ASTMethodDeclaration node, Object data) {

        checkComment(node, data);
        return super.visit(node, data);
    }

    @Override
    public Object visit(ASTFieldDeclaration node, Object data) {

        checkComment(node, data);
        return super.visit(node, data);
    }

    private void checkComment(AbstractJavaAccessNode node, Object data) {

        if (node.comment() == null) {
            return;
        }

        CommentChecker checker = new CommentChecker(this, node, data);
        checker.check();
    }
}
