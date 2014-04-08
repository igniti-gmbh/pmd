package net.sourceforge.pmd.lang.java.javadoc.checker;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
import net.sourceforge.pmd.lang.java.ast.ASTImportDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTResolver;
import net.sourceforge.pmd.lang.java.ast.ASTUtil;

/**
 *
 * <p>Contains methods which resolve types used in JavaDoc tags.</p>
 *
 */
public class CommentTypeResolver {

    private CommentChecker checker;

    private String currentPackageName;
    private String currentOuterClassName;
    private Set<String> imports = new HashSet<>();

    /**
     *
     * <p>Initializes the comment type resolver.</p>
     *
     * @param checker The {@link CommentChecker}.
     *
     */
    public CommentTypeResolver(CommentChecker checker) {

        this.checker = checker;

        this.currentPackageName = ASTUtil.getPackageName(checker.getNode());
        this.currentOuterClassName = ASTUtil.getNodeOuterClassName(checker.getNode());

        collectImports();
    }

    /**
     *
     * <p>Tries to resolve the given type name using the {@link ASTResolver} class.</p>
     *
     * <p>This overload allows clients to cache various parameters over multiple lookups.</p>
     *
     * @param typeRef The type reference, e.g. "my.package.Class" or unqualified "Class".
     *
     * @return The class object or null if the type could not be resolved.
     *
     */
    public Class<?> resolveType(String typeRef) {

        return ASTResolver.resolveType(typeRef, currentPackageName, currentOuterClassName, imports);
    }

    /**
     *
     * <p>Tests if a field or method is contained in a type. Also checks super class and interfaces.</p>
     *
     * @param type The type (class or interface) to check.
     * @param ref The field or method in question, e.g. "my.package.Class#field" or "Class#field".
     *
     * @return Returns true if the field is present in the type hierarchy.
     *
     */
    public boolean isFieldOrMethodInTypeHierarchy(Class<?> type, String ref) {

        if (isFieldOrMethodInType(type, ref)) {
            return true;
        }

        if (type.getSuperclass() != null && isFieldOrMethodInTypeHierarchy(type.getSuperclass(), ref)) {
            return true;
        }

        for (Class<?> iface : type.getInterfaces()) {

            if (isFieldOrMethodInTypeHierarchy(iface, ref)) {
                return true;
            }
        }

        return false;
    }

    // Class#member or #member
    private boolean isFieldOrMethodInType(Class<?> type, String ref) {

        for (Method method : type.getMethods()) {
            if (method.getName().equals(ref)) {
                return true;
            }
        }

        for (Field field : type.getDeclaredFields()) {
            if (field.getName().equals(ref)) {
                return true;
            }
        }

        return false;
    }

    /**
     *
     * <p>Tests if a constructor is contained in a type. Also checks super classes.</p>
     *
     * @param type The type (class or interface) to check.
     * @param paramTypes The types of the constructor's parameters.
     *
     * @return Returns true if the constructor is present in the type hierarchy.
     *
     */
    public boolean isConstructorOverloadInTypeHierarchy(Class<?> type, Class<?>[] paramTypes) {

        if (isConstructorOverloadInType(type, paramTypes)) {
            return true;
        }

        if (type.getSuperclass() != null && isConstructorOverloadInTypeHierarchy(type.getSuperclass(), paramTypes)) {
            return true;
        }

        return false;
    }

    private boolean isConstructorOverloadInType(Class<?> type, Class<?>[] paramTypes) {

        try {
            type.getDeclaredConstructor(paramTypes);
            return true;
        } catch (NoSuchMethodException e) {
            // NOPMD ignored
        }

        return false;
    }

    /**
     *
     * <p>Tests if a method overload is contained in a type. Also checks super classes and interfaces.</p>
     *
     * @param type The type (class or interface) to check.
     * @param methodName The name of the method.
     * @param paramTypes The types of the method's parameters.
     *
     * @return Returns true if the method is present in the type hierarchy.
     *
     */
    public boolean isMethodOverloadInTypeHierarchy(Class<?> type, String methodName, Class<?>[] paramTypes) {

        if (isMethodOverloadInType(type, methodName, paramTypes)) {
            return true;
        }

        if (type.getSuperclass() != null && isMethodOverloadInTypeHierarchy(type.getSuperclass(), methodName, paramTypes)) {
            return true;
        }

        for (Class<?> iface : type.getInterfaces()) {

            if (isMethodOverloadInTypeHierarchy(iface, methodName, paramTypes)) {
                return true;
            }
        }

        return false;
    }

    private boolean isMethodOverloadInType(Class<?> type, String methodName, Class<?>[] paramTypes) {

        try {
            type.getDeclaredMethod(methodName, paramTypes);
            return true;
        } catch (NoSuchMethodException e) {
            // NOPMD ignored
        }

        return false;
    }

    private void collectImports() {

        ASTCompilationUnit unit = checker.getNode().getFirstParentOfType(ASTCompilationUnit.class);
        List<ASTImportDeclaration> importDecls = unit.findChildrenOfType(ASTImportDeclaration.class);

        for (ASTImportDeclaration importDecl : importDecls) {
            imports.add(importDecl.getPackageName());
        }
    }
}
