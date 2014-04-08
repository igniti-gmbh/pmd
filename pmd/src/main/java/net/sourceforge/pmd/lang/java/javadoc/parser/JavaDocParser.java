package net.sourceforge.pmd.lang.java.javadoc.parser;

import java.io.BufferedReader;
import java.io.IOException;

/**
 *
 * <p>A rudimentary event-driven JavaDoc parser.</p>
 *
 * <p>Extracts tags from JavaDoc source and passes them to a {@link JavaDocParserCallback}.</p>
 *
 */
public class JavaDocParser {

    private int currentLine;

    private BufferedReader reader;

    private JavaDocParserCallback callback;

    private StringBuilder text = new StringBuilder(); // NOPMD: the JDocParser is not kept around for a long time

    /**
     *
     * <p>Initializes the parser. Use #parse() to start the parsing process.</p>
     *
     * @param reader The {@link BufferedReader} to read text from.
     * @param callback The {@link JavaDocParserCallback} used to notify clients of parsed text / tokens.
     *
     */
    public JavaDocParser(BufferedReader reader, JavaDocParserCallback callback) {
        this.reader = reader;
        this.callback = callback;
    }

    /**
     *
     * Parses the JavaDoc comment.
     *
     * @throws IOException On IO error
     */
    public void parse() throws IOException {

        while (true) {

            String token = readToken();
            if (token == null) {
                break;
            }

            if (token.startsWith("/**")) {

                textCallback(false);
                text.append(token);
                parseComment();
            } else {
                text.append(token);
            }
        }
        textCallback(false);
    }

    private void parseComment() throws IOException {

        callback.onCommentEnter(currentLine);

        while (true) {

            String token = readToken();
            if (token == null) {
                break;
            }

            if (token.endsWith("*/")) {

                text.append(token);
                textCallback(true);
                break;
            } else
            if ("@".equals(token)) {

                textCallback(true);
                if (!parseSectionTag()) {
                    break;
                }
            } else
            if ("{".equals(token)) {

                String nextToken = readToken();
                if ("@".equals(nextToken)) {
                    textCallback(true);
                    parseInlineTag();
                } else {
                    text.append(token);
                    text.append(nextToken);
                }

            } else {
                text.append(token);
            }
        }

        textCallback(true);

        callback.onCommentExit();
    }

    private void parseInlineTag() throws IOException {

        int tagLine = currentLine;
        String tagName = null;
        StringBuilder tagArg = new StringBuilder();

        while (true) {

            String token = readToken();
            if (token == null) {
                return;
            }

            if ("*".equals(token)) {
                continue;
            }

            boolean whitespace = isWhitespace(token);
            if (tagName == null && !whitespace) {

                tagName = token;
            } else
            if ("}".equals(token)) {

                break;
            } else
            if (tagArg.length() > 0 || !whitespace)
            {
                tagArg.append(token);
            }
        }

        callback.onTag(tagLine, tagName, tagArg.toString());
    }

    private boolean parseSectionTag() throws IOException {

        int tagLine = currentLine;
        String tagName = null;
        StringBuilder tagArg = new StringBuilder();

        boolean commentEnd = false;
        while (true) {

            String token = readToken();
            if (token == null) {
                return false;
            }

            boolean whitespace = isWhitespace(token);
            if (tagName == null && !whitespace) {
                tagName = token;
            } else
            if (containsNewline(token)) {
                if (!whitespace) {
                    tagArg.append(token);
                } else {
                    text.append(token);
                }
                break;
            } else
            if (token.endsWith("*/")) {
                text.append(token);
                commentEnd = true;
                break;
            } else
            if (token.startsWith("{")) {
                String nextToken = readToken();
                if ("@".equals(nextToken)) {
                    textCallback(true);
                    parseInlineTag();
                } else {
                    text.append(token);
                    text.append(nextToken);
                }
            } else
            if (tagArg.length() > 0 || !whitespace) {
                tagArg.append(token);
            }
        }

        callback.onTag(tagLine, tagName, tagArg.toString());

        return !commentEnd;
    }

    private boolean isWhitespace(String s) {

        for (int i=0; i<s.length(); i++) {

            if (!Character.isWhitespace(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean containsNewline(String s) {

        for (int i=0; i<s.length(); i++) {

            if (s.charAt(i) == '\n') {
                return true;
            }
        }
        return false;
    }

    private String readToken() throws IOException {

        reader.mark(1);
        int read = reader.read();
        if (read == -1) {
            return null;
        }

        if (Character.isWhitespace((char)read)) {

            // starts with whitespace, read until first non-whitespace
            return readWhitespaceToken((char)read);

        } else
        if ((char)read == '"') {

            return readQuotedToken((char)read);

        }

        return readRegularToken();
    }

    private String readRegularToken() throws IOException {

        reader.reset();

        StringBuilder sb = new StringBuilder();

        while (true) {

            reader.mark(1);

            int read = reader.read();
            if (read == -1) {
                break;
            }

            char ch = (char)read;
            if (Character.isWhitespace(ch)) {
                reader.reset();
                break;
            }

            if ((char)read == '\n') {
                currentLine++;
            }

            if (ch == '{' || ch == '}' || ch == '@') {

                if (sb.length() > 0) {
                    reader.reset();
                } else {
                    sb.append(ch);
                }
                break;
            }

            sb.append((char)read);
        }

        return sb.toString();
    }

    private String readWhitespaceToken(char ch) throws IOException {

        StringBuilder sb = new StringBuilder();

        if (ch == '\n') {
            currentLine++;
        }

        sb.append(ch);
        while (true) {

            reader.mark(1);

            int read = reader.read();
            if (read == -1) {
                break;
            }

            if (!Character.isWhitespace((char)read)) {
                reader.reset();
                break;
            }

            if ((char)read == '\n') {
                currentLine++;
            }

            sb.append((char)read);
        }

        return sb.toString();
    }

    private String readQuotedToken(char ch) throws IOException {

        StringBuilder sb = new StringBuilder();

        sb.append(ch);
        while (true) {

            int read = reader.read();
            if (read == -1) {
                break;
            }

            sb.append((char)read);

            if ((char)read == '\n') {
                currentLine++;
            }

            if ((char)read == '"') {
                break;
            }
        }

        return sb.toString();
    }

    private void textCallback(boolean inComment) {

        if (text.length() > 0) {

            if (inComment) {
                callback.onCommentText(text.toString());
            } else {
                callback.onText(text.toString());
            }
            text.setLength(0);
        }
    }
}
