package de.deutschepost.postcard.pmd.util.comment;

/**
 *
 * <p>Helper class with shortcuts for reporting rule violations.</p>
 *
 */
public class CommentReporter {

    private final CommentChecker checker;

    /**
     *
     * <p>Initializes the {@link CommentReporter}.</p>
     *
     * @param checker The {@link CommentChecker} instance.
     *
     */
    public CommentReporter(CommentChecker checker) {

        this.checker = checker;
    }

    /**
     *
     * <p>Adds a malformed parameter violation.</p>
     *
     * @param tagLine The line in the comment where the tag was specified.
     * @param tagText The tag text as specified in the JavaDoc.
     *
     */
    public void addParameterMalformedViolation(int tagLine, String tagText) {

        addViolation("Malformed @param tag '" + tagText + "', needs to have parameter name and description", tagLine);
    }

    /**
     *
     * <p>Adds a malformed return violation.</p>
     *
     * @param tagLine The line in the comment where the tag was specified.
     *
     */
    public void addReturnMalformedViolation(int tagLine) {

        addViolation("Malformed @return tag, needs to have a description", tagLine);
    }

    /**
     *
     * <p>Adds a violation message. Offsets the <code>tagLine</code> parameter so that
     * it is located in the source file.</p>
     *
     * @param message The message to report.
     * @param tagLine The line in the comment where the tag was specified.
     *
     */
    public void addViolation(String message, int tagLine) {

        // comment-local line to line in source file
        int sourceLine = checker.getNode().comment().getBeginLine() + tagLine;
        checker.getRule().addViolationWithMessage(checker.getData(), checker.getNode(), message, sourceLine, sourceLine);
    }
}
