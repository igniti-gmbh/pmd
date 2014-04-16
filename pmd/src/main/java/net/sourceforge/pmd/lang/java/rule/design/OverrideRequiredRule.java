package net.sourceforge.pmd.lang.java.rule.design;

import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.ast.util.ASTUtil;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRule;

/**
 *
 * This rule enforces the presence of @Override for methods overriding base class
 * methods and methods which implement interface methods.
 *
 */
public class OverrideRequiredRule extends AbstractJavaRule {

    public OverrideRequiredRule() {
    }

    @Override
    public Object visit(ASTMethodDeclaration node, Object data) {

	if (ASTUtil.isOverrideMethod(node) && !ASTUtil.hasOverrideAnnotation(node)) {
	    addViolationWithMessage(data, node, getMessage());
	}

	return super.visit(node, data);
    }
}
