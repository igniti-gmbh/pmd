package net.sourceforge.pmd.lang.java.ast;

import java.lang.reflect.Array;
import java.util.Set;

/**
 *
 * This class helps resolving java types.
 *
 */
public class ASTResolver {

    private ASTResolver() {}

    /**
     *
     * <p>Tries to resolve the given type name by attempting a direct lookup or by prefixing the type
     * with all import statements in the file (resolves Map => java.util.Map), the current package
     * name or the current outer class name.</p>
     *
     * @param node The AST node for which to resolve the type.
     * @param typeRef The type reference, e.g. "my.package.Class" or unqualified "Class".
     *
     * @return The class object or null if the type could not be resolved.
     *
     */
    public static Class<?> resolveType(AbstractJavaNode node, String typeRef) {

        return resolveType(typeRef, ASTUtil.getPackageName(node), ASTUtil.getNodeOuterClassName(node), ASTUtil.getNodeImports(node));
    }

    /**
     *
     * <p>Tries to resolve the given type name by attempting a direct lookup or by prefixing the type
     * with all import statements in the file (resolves Map => java.util.Map), the current package
     * name or the current outer class name.</p>
     *
     * <p>This overload allows clients to cache various parameters over multiple lookups.</p>
     *
     * @param typeRef The type reference, e.g. "my.package.Class" or unqualified "Class".
     * @param packageName The full qualified package name the current outer class is declared in.
     * @param outerClassName The full qualified outer class name.
     * @param imports Imports of the current compilation unit.
     *
     * @return The class object or null if the type could not be resolved.
     *
     */
    public static Class<?> resolveType(String typeRef, String packageName, String outerClassName, Set<String> imports) {

        // order is important!

        boolean arrayType = typeRef.indexOf(']') > typeRef.indexOf('[');

        Class<?> clazz = lookupType(typeRef, arrayType);
        if (clazz != null) {
            return clazz;
        }

        clazz = lookupPrimitiveType(arrayType ? getArrayComponentTypeName(typeRef) : typeRef, arrayType);
        if (clazz != null) {
            return clazz;
        }

        // my.package.Class.Inner
        int index = typeRef.lastIndexOf('.');
        if (index != -1) {

            String innerRef = typeRef.substring(0, index) + '$' + typeRef.substring(index+1);
            clazz = lookupType(innerRef, arrayType);
            if (clazz != null) {
                return clazz;
            }
        }

        clazz = lookupPrefixed(packageName, typeRef, arrayType);
        if (clazz != null) {
            return clazz;
        }

        clazz = lookupPrefixed(outerClassName, typeRef, arrayType);
        if (clazz != null) {
            return clazz;
        }

        // try to find an exact match in the imports

        for (String imported : imports) {
            if (imported.endsWith(typeRef)) {
                clazz = lookupType(imported, false);
                if (clazz != null) {
                    return clazz;
                }
            }
        }

        // try prefixing packages

        for (String imported : imports) {

            clazz = lookupPrefixed(imported, typeRef, arrayType);
            //clazz = lookupType(imported + '.' + typeRef, arrayType);
            if (clazz != null) {
                return clazz;
            }
        }

        // java.lang is implicit
        clazz = lookupType("java.lang." + typeRef, arrayType);
        if (clazz != null) {
            return clazz;
        }

        return null;
    }

    private static Class<?> lookupPrefixed(String prefix, String typeRef, boolean arrayType) {

        Class<?> clazz;

        // typeRef = OuterClass
        clazz = lookupType(prefix + '.' + typeRef, arrayType);
        if (clazz != null) {
            return clazz;
        }

        // typeRef = OuterClass.InnerClass
        clazz = lookupType(prefix + '.' + typeRef.replace('.', '$'), arrayType);
        if (clazz != null) {
            return clazz;
        }

        // typeRef = InnerClass (in OuterClass)
        clazz = lookupType(prefix + '$' + typeRef, arrayType);
        if (clazz != null) {
            return clazz;
        }

        return null;
    }

    private static Class<?> lookupType(String typeName, boolean arrayType) {

        if (arrayType) {

            Class<?> componentType = lookupType(getArrayComponentTypeName(typeName), false);
            if (componentType == null) {
                return null;
            }
            return Array.newInstance(componentType, 0).getClass();

        } else {
            try {
                return Class.forName(typeName, false, ASTResolver.class.getClassLoader());
            } catch (ClassNotFoundException e1) {
                return null; // NOPMD
            } catch (Throwable t) { // NOPMD
                t.printStackTrace(System.err);
                return null; // NOPMD
            }
        }
    }

    private static Class<?> lookupPrimitiveType(String typeRef, boolean arrayType) {

        switch (typeRef) {

            case "short": return arrayType ? short[].class : short.class; // NOPMD
            case "int": return arrayType ? int[].class : int.class;
            case "long": return arrayType ? long[].class : long.class;
            case "double": return arrayType ? double[].class : double.class;
            case "float": return arrayType ? float[].class : float.class;
            case "boolean": return arrayType ? boolean[].class : boolean.class;
            case "char": return arrayType ? char[].class : char.class;
            case "byte": return arrayType ? byte[].class : byte.class;
            case "void": return void.class;
            default: break;
        }
        return null;
    }

    private static String getArrayComponentTypeName(String typeRef) {
        return typeRef.substring(0, typeRef.indexOf('['));
    }
}
