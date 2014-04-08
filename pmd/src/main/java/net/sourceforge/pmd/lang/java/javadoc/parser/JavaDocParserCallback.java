package net.sourceforge.pmd.lang.java.javadoc.parser;

/**
 *
 * <p>Callback interface for the {@link JavaDocParser}.</p>
 *
 * <p>Clients implement this interface if they wish to be informed of parsing events.</p>
 *
 */
public interface JavaDocParserCallback {

    /**
     *
     * Called when a tag was encountered.
     *
     * @param tagLine The line the tag appears within the parsed source.
     * @param tagName The name of the tag.
     * @param tagArg Tag argument text.
     *
     */
    void onTag(int tagLine, String tagName, String tagArg);

    /**
     *
     * Called for text outside of comments.
     *
     * @param text The source text.
     *
     */
    void onText(String text);

    /**
     *
     * Called when the parser enters a comment.
     *
     * @param commentLine The line the comment starts within the parsed source.
     *
     */
    void onCommentEnter(int commentLine);

    /**
     *
     * Called when the parser encounters a tag in the comment or the comment end
     * is reached.
     *
     * @param text The comment text.
     *
     */
    void onCommentText(String text);

    /**
     *
     * Called when the parser leaves a comment.
     *
     */
    void onCommentExit();
}
