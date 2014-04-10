package net.sourceforge.pmd.lang.java.javadoc.checker;

import net.sourceforge.pmd.lang.java.ast.ASTClassOrInterfaceDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTConstructorDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTEnumDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTExtendsList;
import net.sourceforge.pmd.lang.java.ast.ASTFieldDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTImplementsList;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTTypeParameters;

/**
*
* <p>Validates JavaDoc tags.</p>
*
* @author Igniti GmbH <igniti-open@igniti.de>
* @since Apr 9, 2014
*
*/
public class CommentTagChecker {

	private final CommentChecker checker;    
	private final CommentRefChecker refChecker;

	public CommentTagChecker(CommentChecker checker) {
		
		this.checker = checker;
		this.refChecker = new CommentRefChecker(checker);
	}
	
	public CommentRefChecker getRefChecker() {
		return refChecker;
	}
	
    public void checkAuthorTag(int tagLine, String tagArg) {
    	
        if (!(checker.getNode() instanceof ASTClassOrInterfaceDeclaration || checker.getNode() instanceof ASTEnumDeclaration)) { 
            checker.getReporter().addViolation("Illegal @author tag, may only be specified on classes, interfaces and enums", tagLine);
            return;
        }

    	if (tagArg.trim().isEmpty()) {
    		checker.getReporter().addViolation("Malformed @author tag,  must have a author name.", tagLine);
    		return;
    	}

        checker.countActualCommentCharacters(tagArg);
	}

    public void checkDeprecatedTag(int tagLine, String tagArg) {

        if (tagArg.trim().isEmpty()) {
            checker.getReporter().addViolation("Malformed @deprecated tag, must have a description", tagLine);
            return;
        }
        
        checker.countActualCommentCharacters(tagArg);
    }

    public boolean checkInheritDoc(int tagLine) {

        if (checker.getNode() instanceof ASTConstructorDeclaration) {
            return true;
        }

        if (checker.getNode() instanceof ASTMethodDeclaration && !checker.isOverrideMethod()) {
            checker.getReporter().addViolation("@inheritDoc may not be specified on non-override methods", tagLine);
            return false;
        }

        if (checker.getNode() instanceof ASTClassOrInterfaceDeclaration) {
            ASTClassOrInterfaceDeclaration classDecl = (ASTClassOrInterfaceDeclaration)checker.getNode();
            if (classDecl.getFirstChildOfType(ASTExtendsList.class) == null &&
                classDecl.getFirstChildOfType(ASTImplementsList.class) == null) {
                checker.getReporter().addViolation("@inheritDoc may not be specified on classes without a super class or a class implementing interfaces", tagLine);
                return false;
            }
        }
        return true;
    }

    public void checkLinkTag(int tagLine, String tagArg) {

    	if (tagArg.trim().isEmpty()) {
    		checker.getReporter().addViolation("Malformed @link tag, must at least specify the linked type.", tagLine);
    		return;
    	}	

        checker.countActualCommentCharacters(tagArg);

    	if (checker.isCheckReferences()) {
    		refChecker.check(tagLine, "@link", tagArg);
    	}
    }

    public void checkParamTag(int tagLine, String text) {

        if (!checkParamTagAllowedInContext(tagLine, text)) {
            return;
        }

        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            checker.getReporter().addParameterMalformedViolation(tagLine, trimmed);
            return;
        }

        int paramEnd = trimmed.indexOf(' ');
        if (paramEnd <= 0) {
            checker.getReporter().addParameterMalformedViolation(tagLine, trimmed);
            return;
        }

        int descStart = trimmed.indexOf(' ', paramEnd);
        if (descStart <= 0) {
            checker.getReporter().addParameterMalformedViolation(tagLine, trimmed);
            return;
        }

        checker.countActualCommentCharacters(text);

        String paramName = trimmed.substring(0, paramEnd);

        checkParameterExists(tagLine, paramName);
    }

    public boolean checkParamTagAllowedInContext(int tagLine, String tagText) {


        if (checker.getNode() instanceof ASTMethodDeclaration || checker.getNode() instanceof ASTConstructorDeclaration) {
            // ctor and method are always legal
            return true;
        }

        if (checker.getNode() instanceof ASTClassOrInterfaceDeclaration) {

            // only legal for parameterized class / interface
            ASTClassOrInterfaceDeclaration classDecl = (ASTClassOrInterfaceDeclaration)checker.getNode();
            if (classDecl.getFirstChildOfType(ASTTypeParameters.class) != null) {
                return true;
            }
        }

        checker.getReporter().addViolation("Illegal @param tag '" + tagText + "', may only be specified on methods and constructors or generic types", tagLine);
        return false;
    }

    public void checkParameterExists(int tagLine, String paramName) {

        for (String name : checker.getParameterNames()) {
            if (paramName.equals(name)) {
                // found our parameter
                checker.addDocumentedParameter(paramName);
                return;
            }
        }

        checker.getReporter().addViolation("Parameter '" + paramName + "' specified in JavaDoc is not a parameter or generic type of the documented item", tagLine);
    }

    public void checkReturnTag(int tagLine, String tagArg) {

        checker.markReturnDocumented();

        if (!(checker.getNode() instanceof ASTMethodDeclaration)) {
            checker.getReporter().addViolation("Illegal @return tag, may only be specified on methods", tagLine);
            return;
        }

        ASTMethodDeclaration method = (ASTMethodDeclaration)checker.getNode();
        if (method.getResultType().isVoid()) {
            checker.getReporter().addViolation("Illegal @return tag, may not be specified on void methods", tagLine);
            return;
        }

        String trimmed = tagArg.trim();
        if (trimmed.isEmpty()) {
            checker.getReporter().addReturnMalformedViolation(tagLine);
            return;
        }

        checker.countActualCommentCharacters(tagArg);
    }

    public void checkSeeTag(int tagLine, String tagArg) {
		
        if (!(checker.getNode() instanceof ASTClassOrInterfaceDeclaration || checker.getNode() instanceof ASTEnumDeclaration || checker.getNode() instanceof ASTFieldDeclaration || checker.getNode() instanceof ASTMethodDeclaration || checker.getNode() instanceof ASTConstructorDeclaration)) { 
            checker.getReporter().addViolation("Illegal @see tag, may only be specified on classes, interfaces, enums, fields, constructors and methods", tagLine);
            return;
        }

    	if (tagArg.trim().isEmpty()) {
    		checker.getReporter().addViolation("Malformed @see tag, must at least specify the linked type.", tagLine);
    		return;
    	}	
    	
        checker.countActualCommentCharacters(tagArg);

    	if (checker.isCheckReferences()) {
    		refChecker.check(tagLine, "@see", tagArg);
    	}
    }

	public void checkSinceTag(int tagLine, String tagArg) {
		
        if (!(checker.getNode() instanceof ASTClassOrInterfaceDeclaration || checker.getNode() instanceof ASTEnumDeclaration || checker.getNode() instanceof ASTFieldDeclaration || checker.getNode() instanceof ASTMethodDeclaration || checker.getNode() instanceof ASTConstructorDeclaration)) { 
            checker.getReporter().addViolation("Illegal @since tag, may only be specified on classes, interfaces, enums, fields, constructors and methods", tagLine);
            return;
        }

    	if (tagArg.trim().isEmpty()) {
    		checker.getReporter().addViolation("Malformed @since tag, must have text.", tagLine);
    		return;
    	}	
    	
        checker.countActualCommentCharacters(tagArg);
	}

    public void checkThrowsTag(int tagLine, String tagName, String tagText) {

        if (!(checker.getNode() instanceof ASTMethodDeclaration) && !(checker.getNode() instanceof ASTConstructorDeclaration)) {
            checker.getReporter().addViolation("Illegal " + tagName + " tag, may only be specified on methods and constructors", tagLine);
            return;
        }

        if (tagText.trim().isEmpty()) {
            checker.getReporter().addViolation("Malformed " + tagName + " tag, must at least specify the exception class", tagLine);
            return;
        }

        checker.countActualCommentCharacters(tagText);

        int descStart = tagText.indexOf(' ');
        String exceptionName = (descStart != -1) ? tagText.substring(0, descStart) : tagText;

        if (!checkThrowsTypeExists(tagLine, exceptionName)) {
        	return;
        }

        if (checker.isCheckReferences()) {
        	refChecker.check(tagLine, tagName, tagText);
        }
    }

    public boolean checkThrowsTypeExists(int tagLine, String exceptionName) {

        Class<?> ref = checker.getTypeResolver().resolveType(exceptionName);
        if (ref == null) {
            checker.getReporter().addViolation("Exception '" + exceptionName + "' could not be resolved", tagLine);
            return false;
        }

        if (RuntimeException.class.isAssignableFrom(ref)) {
            // RuntimeException is implicit and doesn't have to be in the throws list
            checker.addDocumentedThrows(ref);
            return true;
        }

        for (Class<?> clazz : checker.getThrowsDecls()) {
            if (clazz == ref) { // NOPMD identity compare is intended
            	checker.addDocumentedThrows(clazz);
                return true;
            }
        }

        checker.getReporter().addViolation("Exception '" + exceptionName + "' specified in JavaDoc is not thrown by the documented method", tagLine);
        return false;
    }

	public void checkValueTag(int tagLine, String tagArg) {
		
    	if (tagArg.trim().isEmpty()) {
    		checker.getReporter().addViolation("Malformed @value tag, must have a JavaDoc reference.", tagLine);
    		return;
    	}
    	
        checker.countActualCommentCharacters(tagArg);

    	if (checker.isCheckReferences()) {
    		// TODO: this should only allow references to static fields
    		refChecker.check(tagLine, "@value", tagArg);
    	}
	}

	public void checkVersionTag(int tagLine, String tagArg) {
    	
        if (!(checker.getNode() instanceof ASTClassOrInterfaceDeclaration || checker.getNode() instanceof ASTEnumDeclaration)) { 
            checker.getReporter().addViolation("Illegal @version tag, may only be specified on classes, interfaces and enums", tagLine);
            return;
        }

    	if (tagArg.trim().isEmpty()) {
    		checker.getReporter().addViolation("Malformed @version tag,  must have a version specification.", tagLine);
    		return;
    	}		

        checker.countActualCommentCharacters(tagArg);
	}
}
