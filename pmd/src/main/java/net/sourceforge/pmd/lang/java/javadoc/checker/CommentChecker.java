package net.sourceforge.pmd.lang.java.javadoc.checker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.java.ast.ASTClassOrInterfaceDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTConstructorDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTExtendsList;
import net.sourceforge.pmd.lang.java.ast.ASTFieldDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTFormalParameter;
import net.sourceforge.pmd.lang.java.ast.ASTImplementsList;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTName;
import net.sourceforge.pmd.lang.java.ast.ASTNameList;
import net.sourceforge.pmd.lang.java.ast.ASTTypeParameter;
import net.sourceforge.pmd.lang.java.ast.ASTTypeParameters;
import net.sourceforge.pmd.lang.java.ast.ASTUtil;
import net.sourceforge.pmd.lang.java.ast.AbstractJavaAccessNode;
import net.sourceforge.pmd.lang.java.javadoc.parser.JavaDocParser;
import net.sourceforge.pmd.lang.java.javadoc.parser.JavaDocParserCallback;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRule;

/**
 *
 * <p>This class validates JavaDoc comments. The following tests are performed:</p>
 *
 * <ul>
 *     <li>Tags referencing a type (i.e. link and see) may not specify an unknown type</li>
 *     <li>Methods returning void may not have a return tag</li>
 *     <li>Non-void methods must have a return tag</li>
 *     <li>Documented parameters (including generic types) must occur in the method / constructor parameter list or class / interface declaration</li>
 *     <li>All parameters must be documented (including generic types)</li>
 *     <li>Documented throws must occur in the method / constructor throws list</li>
 *     <li>All thrown exceptions must be undocumented</li>
 *     <li>The length of the actual comment text (without JavaDoc tags, but with additional info specified in them)</li>
 *     <li>Methods of types without super class / implemented interfaces may not specify inheritDoc</li>
 *     <li>Checks if tags are valid in the current context (throws, param, return)</li>
 *     <li>Checks that tags are well-formed (type specified, description written, etc)</li>
 * </ul>
 *
 * @author Igniti GmbH <igniti-open@igniti.de>
 * @since Apr 9, 2014
 * 
 */
public class CommentChecker implements JavaDocParserCallback {

    private int minimumCommentLength = 10;    
    private boolean checkReferences = true;

	private boolean preprocessingStep = true;
    private boolean returnDocumented;

    private boolean inheritDocPresent;
    private boolean overrideMethod;

    private final CommentTypeResolver typeResolver;
    private final CommentParameterListResolver parameterListResolver;

    private final CommentReporter reporter;
    private final CommentRefChecker refChecker;

    private final AbstractJavaRule rule;
    private final AbstractJavaAccessNode node;
    private final Object data;

    private int actualCommentCharacterCount;
    private int commentLine;

    private List<String> parameterNames = new LinkedList<String>();
    private List<String> documentedParameters = new LinkedList<String>();

    private List<Class<?>> throwsDecls = new LinkedList<Class<?>>();
    private List<Class<?>> documentedThrows = new LinkedList<Class<?>>();

    /**
     *
     * <p>Initializes the comment checker.</p>
     *
     * @param rule The PMD rule using this checker.
     * @param node The node where the comment is attached.
     * @param data PMD data passed through to {@link AbstractJavaRule#addViolation}.
     * 
     */
    public CommentChecker(AbstractJavaRule rule, AbstractJavaAccessNode node, Object data) {

        this.rule = rule;
        this.node = node;
        this.data = data;

        this.reporter = new CommentReporter(this);
        this.typeResolver = new CommentTypeResolver(this);
        this.parameterListResolver = new CommentParameterListResolver(this);
        this.refChecker =  new CommentRefChecker(this);

        if (node instanceof ASTMethodDeclaration) {
            this.overrideMethod = ASTUtil.isOverrideMethod((ASTMethodDeclaration)node);
        }

        collectParameterNames();
        collectThrows();
    }

    /** 
     * 
     * <p>Sets the minimum length of a comment.</p>
     * 
     * @param minimumCommentLength The minimal number of actual text characters expected to be in the comment.
     * 
     */
    public void setMinimumCommentLength(int minimumCommentLength) {
		this.minimumCommentLength = minimumCommentLength;
	}

	public void setCheckReferences(boolean checkReferences) {
		this.checkReferences = checkReferences;
	}

    /**
     *
     * <p>Performs the comment checks and adds violations to the PMD rule.</p>
     *
     */
    public void check() {

        if (checkPreconditions()) {

            runParser();
            preprocessingStep = false;
            runParser();
        }
    }

    /**
     *
     * <p>Returns the {@link CommentTypeResolver}.</p>
     *
     * @return The {@link CommentTypeResolver}.
     *
     */
    public CommentTypeResolver getTypeResolver() {
        return typeResolver;
    }

    /**
     *
     * <p>Returns the {@link CommentParameterListResolver}.</p>
     *
     * @return The {@link CommentParameterListResolver}.
     *
     */
    public CommentParameterListResolver getParameterListResolver() {
        return parameterListResolver;
    }

    /**
     *
     * <p>Returns the {@link CommentReporter}.</p>
     *
     * @return The {@link CommentReporter}.
     *
     */
    public CommentReporter getReporter() {
        return reporter;
    }

    /**
     *
     * <p>Returns the {@link AbstractJavaRule PMD Rule}.</p>
     *
     * @return The {@link AbstractJavaRule PMD Rule}.
     *
     */
    public AbstractJavaRule getRule() {
        return rule;
    }

    /**
     *
     * <p>Returns the {@link AbstractJavaAccessNode AST Node}.</p>
     *
     * @return The {@link AbstractJavaAccessNode AST Node}.
     *
     */
    public AbstractJavaAccessNode getNode() {
        return node;
    }

    /**
     *
     * <p>Returns the PMD data.</p>
     *
     * @return The PMD data.
     *
     */
    public Object getData() {
        return data;
    }

    @Override
    public void onCommentEnter(int commentLine) {
        this.commentLine = commentLine;
    }

    @Override
    public void onTag(int tagLine, String tagName, String tagArg) {

        if (preprocessingStep) {

            if ("inheritDoc".equals(tagName)) {
                inheritDocPresent = true;
                checkInheritDoc(tagLine);
            }

        } else {

            if ("author".equals(tagName)) {
                checkAuthorTag(tagLine, tagArg);
            } else
            if ("version".equals(tagName)) {
                checkVersionTag(tagLine, tagArg);
            } else
            if ("since".equals(tagName)) {
                checkSinceTag(tagLine, tagArg);
            } else
            if ("return".equals(tagName)) {
                checkReturnTag(tagLine, tagArg);
            } else
            if ("param".equals(tagName)) {
                checkParamTag(tagLine, tagArg);
            } else
            if ("link".equals(tagName)) {
                checkLinkTag(tagLine, tagArg);
            } else
            if ("value".equals(tagName)) {
                checkValueTag(tagLine, tagArg);
            } else
            if ("see".equals(tagName)) {
                checkSeeTag(tagLine, tagArg);
            } else
            if ("throws".equals(tagName) || "exception".equals(tagName)) {
                checkThrowsTag(tagLine, tagName, tagArg);
            } else
            if ("deprecated".equals(tagName)) {
                checkDeprecatedTag(tagLine, tagArg);
            }
        }
    }

	@Override
    public void onText(String text) {
    }

    @Override
    public void onCommentText(String text) {

        if (!preprocessingStep) {
            countActualCommentCharacters(text);
        }
    }

    @Override
    public void onCommentExit() {

        if (preprocessingStep) {
            return;
        }

        // we rely on the base class being properly documented if there was a {@inheritDoc} tag.
        // the base class will be validated independently.
        if (!inheritDocPresent && !overrideMethod) {

            if (actualCommentCharacterCount < minimumCommentLength) {
                reporter.addViolation("Comment is too short: need " + minimumCommentLength + " actual text characters, got " +
                        actualCommentCharacterCount, commentLine);
            }

            if (!returnDocumented && node instanceof ASTMethodDeclaration && !((ASTMethodDeclaration) node).getResultType().isVoid()) {
                reporter.addViolation("Methods returning anything other than void must have a @return tag", commentLine);
            }

            for (String parameterName : parameterNames) {
                if (!documentedParameters.contains(parameterName)) {
                    reporter.addViolation("Parameter '" + parameterName + "' is undocumented", commentLine);
                }
            }

            for (Class<?> throwsDecl : throwsDecls) {
                if (!documentedThrows.contains(throwsDecl)) {
                    reporter.addViolation("Exception '" + throwsDecl.getCanonicalName() + "' is undocumented", commentLine);
                }
            }
        }
    }

    private boolean checkInheritDoc(int tagLine) {

        if (node instanceof ASTConstructorDeclaration) {
            return true;
        }

        if (node instanceof ASTMethodDeclaration && !overrideMethod) {
            reporter.addViolation("@inheritDoc may not be specified on non-override methods", tagLine);
            return false;
        }

        if (node instanceof ASTClassOrInterfaceDeclaration) {
            ASTClassOrInterfaceDeclaration classDecl = (ASTClassOrInterfaceDeclaration)node;
            if (classDecl.getFirstChildOfType(ASTExtendsList.class) == null &&
                classDecl.getFirstChildOfType(ASTImplementsList.class) == null) {
                reporter.addViolation("@inheritDoc may not be specified on classes without a super class or a class implementing interfaces", tagLine);
                return false;
            }
        }
        return true;
    }

    private void checkLinkTag(int tagLine, String tagArg) {

    	if (tagArg.trim().isEmpty()) {
    		reporter.addViolation("Malformed @link tag, must at least specify the linked type.", tagLine);
    	}	
    	
    	if (checkReferences) {
    		refChecker.check(tagLine, "@link", tagArg);
    	}
    }

    private void checkSeeTag(int tagLine, String tagArg) {
    	
    	if (tagArg.trim().isEmpty()) {
    		reporter.addViolation("Malformed @see tag, must at least specify the linked type.", tagLine);
    	}	
    	
    	if (checkReferences) {
    		refChecker.check(tagLine, "@see", tagArg);
    	}
    }

    private void checkAuthorTag(int tagLine, String tagArg) {
    	
    	if (tagArg.trim().isEmpty()) {
    		reporter.addViolation("Malformed @author tag,  must have a author name.", tagLine);
    	}
	}

	private void checkValueTag(int tagLine, String tagArg) {
		
    	if (tagArg.trim().isEmpty()) {
    		reporter.addViolation("Malformed @value tag, must have a JavaDoc reference.", tagLine);
    	}
    	
    	if (checkReferences) {
    		// TODO: this should only allow references to static fields
    		refChecker.check(tagLine, "@value", tagArg);
    	}
	}

	private void checkSinceTag(int tagLine, String tagArg) {
		
    	if (tagArg.trim().isEmpty()) {
    		reporter.addViolation("Malformed @since tag,  must have text.", tagLine);
    	}	
	}

	private void checkVersionTag(int tagLine, String tagArg) {

    	if (tagArg.trim().isEmpty()) {
    		reporter.addViolation("Malformed @version tag,  must have a version specification.", tagLine);
    	}		
	}

    private void checkReturnTag(int tagLine, String text) {

        returnDocumented = true;

        if (!(node instanceof ASTMethodDeclaration)) {
            reporter.addViolation("Illegal @return tag, may only be specified on methods", tagLine);
            return;
        }

        ASTMethodDeclaration method = (ASTMethodDeclaration)node;
        if (method.getResultType().isVoid()) {
            reporter.addViolation("Illegal @return tag, may not be specified on void methods", tagLine);
            return;
        }

        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            reporter.addReturnMalformedViolation(tagLine);
            return;
        }

        countActualCommentCharacters(trimmed);
    }

    private void checkParamTag(int tagLine, String text) {

        if (!checkParamTagAllowedInContext(tagLine, text)) {
            return;
        }

        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            reporter.addParameterMalformedViolation(tagLine, trimmed);
            return;
        }

        int paramEnd = trimmed.indexOf(' ');
        if (paramEnd <= 0) {
            reporter.addParameterMalformedViolation(tagLine, trimmed);
            return;
        }

        int descStart = trimmed.indexOf(' ', paramEnd);
        if (descStart <= 0) {
            reporter.addParameterMalformedViolation(tagLine, trimmed);
            return;
        }

        String paramName = trimmed.substring(0, paramEnd);
        String descText = trimmed.substring(descStart);

        checkParameterExists(tagLine, paramName);
        countActualCommentCharacters(descText);
    }

    private void checkThrowsTag(int tagLine, String tagName, String tagText) {

        if (!(node instanceof ASTMethodDeclaration) && !(node instanceof ASTConstructorDeclaration)) {
            reporter.addViolation("Illegal " + tagName + " tag, may only be specified on methods and constructors", tagLine);
            return;
        }

        if (tagText.trim().isEmpty()) {
            reporter.addViolation("Malformed " + tagName + " tag, must at least specify the exception class", tagLine);
            return;
        }

        String exceptionName;
        int descStart = tagText.indexOf(' ');
        if (descStart != -1) {
            exceptionName = tagText.substring(0, descStart);
            countActualCommentCharacters(tagText.substring(descStart));
        } else {
            exceptionName = tagText;
        }

        if (!checkThrowsTypeExists(tagLine, exceptionName)) {
        	return;
        }

        if (checkReferences) {
        	refChecker.check(tagLine, tagName, tagText);
        }
    }

    private boolean checkThrowsTypeExists(int tagLine, String exceptionName) {

        Class<?> ref = typeResolver.resolveType(exceptionName);
        if (ref == null) {
            reporter.addViolation("Exception '" + exceptionName + "' could not be resolved", tagLine);
            return false;
        }

        if (RuntimeException.class.isAssignableFrom(ref)) {
            // RuntimeException is implicit and doesn't have to be in the throws list
            documentedThrows.add(ref);
            return true;
        }

        for (Class<?> clazz : throwsDecls) {
            if (clazz == ref) { // NOPMD identity compare is intended
                documentedThrows.add(clazz);
                return true;
            }
        }

        reporter.addViolation("Exception '" + exceptionName + "' specified in JavaDoc is not thrown by the documented method", tagLine);
        return false;
    }

    private void checkParameterExists(int tagLine, String paramName) {

        for (String name : parameterNames) {
            if (paramName.equals(name)) {
                // found our parameter
                documentedParameters.add(paramName);
                return;
            }
        }

        reporter.addViolation("Parameter '" + paramName + "' specified in JavaDoc is not a parameter or generic type of the documented item", tagLine);
    }

    private boolean checkParamTagAllowedInContext(int tagLine, String tagText) {


        if (node instanceof ASTMethodDeclaration || node instanceof ASTConstructorDeclaration) {
            // ctor and method are always legal
            return true;
        }

        if (node instanceof ASTClassOrInterfaceDeclaration) {

            // only legal for parameterized class / interface
            ASTClassOrInterfaceDeclaration classDecl = (ASTClassOrInterfaceDeclaration)node;
            if (classDecl.getFirstChildOfType(ASTTypeParameters.class) != null) {
                return true;
            }
        }

        reporter.addViolation("Illegal @param tag '" + tagText + "', may only be specified on methods and constructors or generic types", tagLine);
        return false;
    }

    private void checkDeprecatedTag(int tagLine, String tagArg) {

        if (tagArg.trim().isEmpty()) {
            reporter.addViolation("Malformed @deprecated tag, must have a description", tagLine);
        }
    }

    /**
     *
     * <p>Adds non-whitespace, non-comment characters to the effective character count of
     * the current comment.</p>
     *
     * @param text The text to count the characters of.
     *
     */
    public void countActualCommentCharacters(String text) {

        for (int i=0; i<text.length(); i++) {
            char ch = text.charAt(i);
            if (!Character.isWhitespace(ch) && ch != '/' && ch != '*') {
                actualCommentCharacterCount++;
            }
        }
    }

    private void collectParameterNames() {

        List<ASTFormalParameter> formalParameters = null;
        if (node instanceof ASTMethodDeclaration) {
            formalParameters = ASTUtil.getMethodArgs((ASTMethodDeclaration) node);
        } else
        if (node instanceof ASTConstructorDeclaration) {
            formalParameters = ASTUtil.getConstructorArgs((ASTConstructorDeclaration) node);
        }

        if (formalParameters != null) {
            for (ASTFormalParameter parameter : formalParameters) {
                parameterNames.add(ASTUtil.getMethodArgumentName(parameter));
            }
        }

        ASTTypeParameters genericTypes = node.getFirstChildOfType(ASTTypeParameters.class);
        if (genericTypes != null) {
            for (int i=0; i<genericTypes.jjtGetNumChildren(); i++) {
                ASTTypeParameter genericType = (ASTTypeParameter)genericTypes.jjtGetChild(i);
                parameterNames.add('<' + genericType.getImage() + '>');
            }
        }
    }

    private void collectThrows() {

        if (!(node instanceof ASTMethodDeclaration) && !(node instanceof ASTConstructorDeclaration)) {
            return;
        }

        ASTNameList nameList = node.getFirstChildOfType(ASTNameList.class);
        if (nameList == null) {
            // no throws list
            return;
        }

        for (int i=0; i<nameList.jjtGetNumChildren(); i++) {

            Node child = nameList.jjtGetChild(i);
            if (!(child instanceof ASTName)) {
                continue;
            }

            String exceptionName;
            ASTName nameNode = ((ASTName)child);
            if (nameNode.getNameDeclaration() != null) {
                exceptionName = nameNode.getNameDeclaration().getName();
            } else {
                exceptionName = nameNode.getImage();
            }

            Class<?> clazz = typeResolver.resolveType(exceptionName);
            if (clazz == null) {
                reporter.addViolation("Exception '" + exceptionName + "' could not be resolved", 0);
                return;
            }

            throwsDecls.add(clazz);
        }
    }

    private boolean checkPreconditions() {

        if (node instanceof ASTFieldDeclaration) {

            ASTFieldDeclaration field = (ASTFieldDeclaration)node;
            if (!field.isPublic() && !field.isProtected()) {
                // do not check private fields
                return false;
            }
        }

        if (node instanceof ASTMethodDeclaration) {

            ASTMethodDeclaration method = (ASTMethodDeclaration)node;
            if (!method.isPublic() && !method.isProtected()) {
                // do not check private methods
                return false;
            }
        }

        return true;
    }

    private void runParser() {

        BufferedReader reader = new BufferedReader(new StringReader(node.comment().getImage()));
        JavaDocParser parser = new JavaDocParser(reader, this);
        try {
            parser.parse();
        } catch (IOException e) {
            // PMD doesn't always propagate exceptions
            System.err.println("Exception while parsing JavaDoc");
            e.printStackTrace(System.err);
        }
    }
}
