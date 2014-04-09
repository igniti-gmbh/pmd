package net.sourceforge.pmd.lang.java.javadoc.checker;

import net.sourceforge.pmd.lang.java.ast.ASTUtil;

/**
 *
 * <p>This class validates links specified in JavaDoc (the see and link tags).</p>
 * 
 * @author Igniti GmbH <igniti-open@igniti.de>
 * @since Apr 9, 2014
 *
 */
public class CommentRefChecker {

    private final CommentChecker checker;

    /**
     *
     * <p>Initializes the reference checker.</p>
     *
     * @param checker The {@link CommentChecker}.
     *
     */
    public CommentRefChecker(CommentChecker checker) {

        this.checker = checker;
    }

    /**
     *
     * <p>Checks a tag reference.</p>
     *
     * @param tagLine The line in the comment where the current tag was specified.
     * @param tagName The name of the current tag.
     * @param tagText The text of the current tag.
     *
     */
    public void check(int tagLine, String tagName, String tagText) {

        checker.countActualCommentCharacters(tagText);

        int separatorPos = tagText.indexOf('#');
        if (separatorPos != -1) {

            String typeName = (separatorPos > 0) ? tagText.substring(0, separatorPos) : ASTUtil.getNodeClassName(checker.getNode());
            Class<?> type = checker.getTypeResolver().resolveType(typeName);
            if (type == null) {
                checker.getReporter().addViolation("Referenced type '" + typeName + "' could not be resolved", tagLine);
                return;
            }

            int paramListStart = tagText.lastIndexOf('(');
            if (paramListStart != -1) {

                // #method(...) or Class#method(...)
                checkMethodOrConstructorOverloadRef(type, tagLine, tagName, tagText, separatorPos, paramListStart);

            } else {

                // #field or Class#field - also methods
                checkFieldOrMethodRef(type, tagLine, tagName, tagText, separatorPos);
            }

        } else {

            // Class

            String className;
            int classNameEnd = tagText.indexOf(' ');
            if (classNameEnd != -1) {
                className = tagText.substring(0, classNameEnd);
            } else {
                className = tagText;
            }

            if (checker.getTypeResolver().resolveType(className) == null) {
                checker.getReporter().addViolation("Specified type '" + className + "' not found", tagLine);
            }
        }
    }

    private void checkFieldOrMethodRef(Class<?> type, int tagLine, String tagName, String tagText, int separatorPos) {

        String refText;
        int refEnd = tagText.indexOf(' ', separatorPos);
        if (refEnd != -1) {
            refText = tagText.substring(separatorPos + 1, refEnd);
        } else {
            refText = tagText.substring(separatorPos + 1);
        }

        if (!checker.getTypeResolver().isFieldOrMethodInTypeHierarchy(type, refText)) {
            checker.getReporter().addViolation("Method or field of tag " + tagName + " '" + tagText + "' not present in type '" + type.getCanonicalName() + "'", tagLine);
        }
    }

    private void checkMethodOrConstructorOverloadRef(Class<?> type, int tagLine, String tagName, String tagText, int separatorPos, int paramListStart) {

        int paramListEnd = tagText.indexOf(')', paramListStart);
        if (paramListEnd == -1) {
            checker.getReporter().addViolation("Malformed overload specification in " + tagName + " tag, missing ')'", tagLine);
            return;
        }

        String memberName = tagText.substring(separatorPos + 1, paramListStart);

        Class<?>[] paramTypes = checker.getParameterListResolver().resolveParameterTypes(tagText.substring(paramListStart + 1, paramListEnd), tagLine);
        if (paramTypes == null) {
            return; // resolveParamTypes has already added a violation
        }

        if (memberName.equals(type.getSimpleName())) {
            if (!checker.getTypeResolver().isConstructorOverloadInTypeHierarchy(type, paramTypes)) {
                checker.getReporter().addViolation("Overloaded constructor '" + tagText.substring(separatorPos + 1) + "' not found in type '" + type.getCanonicalName() + "'", tagLine);
            }
        } else
        if (!checker.getTypeResolver().isMethodOverloadInTypeHierarchy(type, memberName, paramTypes)) {
            checker.getReporter().addViolation("Overloaded method '" + tagText.substring(separatorPos + 1) + "' not found in type '" + type.getCanonicalName() + "'", tagLine);
        }
    }
}
