package net.sourceforge.pmd.lang.java.rule.comments;

import net.sourceforge.pmd.lang.java.ast.ASTClassOrInterfaceDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
import net.sourceforge.pmd.lang.java.ast.ASTConstructorDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTEnumDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTFieldDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.ast.AbstractJavaAccessNode;
import net.sourceforge.pmd.lang.java.javadoc.checker.CommentChecker;
import net.sourceforge.pmd.lang.rule.properties.BooleanProperty;
import net.sourceforge.pmd.lang.rule.properties.IntegerProperty;

/**
 *
 * This class parses and validates JavaDoc.
 *
 * @author Igniti GmbH <igniti-open@igniti.de>
 * @since Apr 9, 2014
 * 
 */
public class JavaDocCommentRule extends AbstractCommentRule {
	
	public static final IntegerProperty MINIMUM_CONTENT_LENGTH_DESCRIPTOR = new IntegerProperty(
			"minimumContentLength", "The minimum number of actual comment characters",
			0, 10000, 10, 1.0f);

	public static final BooleanProperty CHECK_REFERENCES_ENABLED_DESCRIPTOR = new BooleanProperty(
			"checkReferences", "Checks JavaDoc references if enabled", true, 2);
	
	public JavaDocCommentRule() {		
		definePropertyDescriptor(MINIMUM_CONTENT_LENGTH_DESCRIPTOR);
		definePropertyDescriptor(CHECK_REFERENCES_ENABLED_DESCRIPTOR);
	}
	
    @Override
    public Object visit(ASTCompilationUnit node, Object data) {

    	assignCommentsToDeclarations(node);
        return super.visit(node, data);
    }
    
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
        checker.setMinimumCommentLength(getProperty(MINIMUM_CONTENT_LENGTH_DESCRIPTOR).intValue());
        checker.setCheckReferences(getProperty(CHECK_REFERENCES_ENABLED_DESCRIPTOR).booleanValue());
        checker.check();
    }
}
