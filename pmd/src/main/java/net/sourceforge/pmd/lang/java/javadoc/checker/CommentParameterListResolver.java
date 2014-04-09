package net.sourceforge.pmd.lang.java.javadoc.checker;

import java.util.LinkedList;
import java.util.List;

/**
 *
 * <p>This class resolves types in JavaDoc references.</p>
 *
 * @author Igniti GmbH <igniti-open@igniti.de>
 * @since Apr 9, 2014
 * 
 */
public class CommentParameterListResolver {

    private final CommentChecker checker;

    /**
     *
     * Initializes the comment parameter list resolver.
     *
     * @param checker The {@link CommentChecker}.
     *
     */
    public CommentParameterListResolver(CommentChecker checker) {
        this.checker = checker;
    }

    /**
     *
     * <p>Parses a JavaDoc method / constructor's parameter list and resolves the specified types.</p>
     *
     * @param paramList The string containing the comma-separated parameters, e.g. "(int name, float, String)".
     * @param tagLine The line in the comment where the current tag was specified.
     *
     * @return Returns a list of parameter types.
     *
     */
    public Class<?>[] resolveParameterTypes(String paramList, int tagLine) {

        List<Class<?>> paramTypes = new LinkedList<Class<?>>();

        String typeName;
        CommentParameterListParser parser = new CommentParameterListParser(paramList);
        while ((typeName = parser.nextParameterTypeName()) != null) {

            if (!resolveParameterType(paramTypes, typeName, tagLine)) {
                return null;
            }
        }

        return paramTypes.toArray(new Class<?>[0]);
    }

    private boolean resolveParameterType(List<Class<?>> paramTypes, String typeName, int tagLine) {

        Class<?> type = checker.getTypeResolver().resolveType(typeName);
        if (type == null) {
            checker.getReporter().addViolation("Could not resolve argument type '" + typeName + "'", tagLine);
            return false;
        }
        paramTypes.add(type);
        return true;
    }
}
