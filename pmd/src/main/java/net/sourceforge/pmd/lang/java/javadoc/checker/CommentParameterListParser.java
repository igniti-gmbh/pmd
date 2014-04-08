package net.sourceforge.pmd.lang.java.javadoc.checker;

/**
 *
 * <p>This class parses the parameter list of JavaDoc references, e.g. "(int name, float, String)".</p>
 *
 */
public class CommentParameterListParser {

    private String paramList;
    private int offset;

    /**
     *
     * <p>Initializes the parameter list parser.</p>
     *
     * @param paramList The parameter list string, e.g. "(int name, float, String)".
     *
     */
    public CommentParameterListParser(String paramList) {

        this.paramList = paramList;
    }

    /**
     *
     * <p>Parses the next parameter name.</p>
     *
     * @return The next parameter name or null if at end.
     *
     */
    public String nextParameterTypeName() {

        if (offset >= paramList.length()) {
            return null;
        }

        // eat leading space
        skipSpace();

        // read type
        StringBuilder sb = new StringBuilder();
        for ( ; offset<paramList.length(); offset++) {

            char ch = paramList.charAt(offset);
            if (ch == ',' || ch == ')') {
                offset++;
                return sb.toString();
            }

            if (!Character.isWhitespace(ch)) {
                sb.append(ch);
            } else {
                break;
            }
        }

        // skip trailing space
        skipSpace();

        // skip parameter name
        for ( ; offset<paramList.length(); offset++) {

            char ch = paramList.charAt(offset);
            if (Character.isWhitespace(ch) || ch == ')' || ch == ',') {
                offset++;
                break;
            }
        }

        return (sb.length() > 0) ? sb.toString() : null;
    }

    private void skipSpace() {

        for ( ; offset<paramList.length(); offset++) {
            if (!Character.isWhitespace(paramList.charAt(offset))) {
                break;
            }
        }
    }
}
