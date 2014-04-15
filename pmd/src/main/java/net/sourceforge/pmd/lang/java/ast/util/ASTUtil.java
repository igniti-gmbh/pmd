package net.sourceforge.pmd.lang.java.ast.util;

import java.io.PrintStream;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.java.ast.ASTAllocationExpression;
import net.sourceforge.pmd.lang.java.ast.ASTAnnotation;
import net.sourceforge.pmd.lang.java.ast.ASTClassOrInterfaceBody;
import net.sourceforge.pmd.lang.java.ast.ASTClassOrInterfaceBodyDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTClassOrInterfaceDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTClassOrInterfaceType;
import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
import net.sourceforge.pmd.lang.java.ast.ASTConstructorDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTEnumDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTFieldDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTFormalParameter;
import net.sourceforge.pmd.lang.java.ast.ASTFormalParameters;
import net.sourceforge.pmd.lang.java.ast.ASTImportDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclarator;
import net.sourceforge.pmd.lang.java.ast.ASTName;
import net.sourceforge.pmd.lang.java.ast.ASTPackageDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTPrimitiveType;
import net.sourceforge.pmd.lang.java.ast.ASTReferenceType;
import net.sourceforge.pmd.lang.java.ast.ASTResultType;
import net.sourceforge.pmd.lang.java.ast.ASTTypeBound;
import net.sourceforge.pmd.lang.java.ast.ASTTypeParameter;
import net.sourceforge.pmd.lang.java.ast.ASTTypeParameters;
import net.sourceforge.pmd.lang.java.ast.ASTVariableDeclaratorId;
import net.sourceforge.pmd.lang.java.ast.AbstractJavaAccessNode;
import net.sourceforge.pmd.lang.java.ast.AbstractJavaNode;
import net.sourceforge.pmd.lang.java.ast.Comment;
import net.sourceforge.pmd.lang.java.symboltable.ClassScope;
import net.sourceforge.pmd.lang.java.symboltable.MethodScope;
import net.sourceforge.pmd.lang.java.typeresolution.ASTResolver;
import net.sourceforge.pmd.lang.symboltable.Scope;

import org.apache.commons.lang3.ClassUtils;

/**
 *
 * <p>Helper class for operating on an Abstract Syntax Tree (AST) provided by PMD.</p>
 *
 * <p>All Java classes referenced in the AST must be available on the PMD class path
 * or they will not be resolved. Methods returning {@link Class} objects will return
 * null if the type cannot be resolved.</p>
 *
 */
public final class ASTUtil {

    private static final String UNRESOLVED_NAME = "<unresolved>";

    private ASTUtil() {}

    /**
     *
     * <p>Returns the Java {@link Class} object representing the class / interface this node is defined in.</p>
     *
     * <p>Note that this method relies on reflection and that any type to be resolved must be in the class path.</p>
     *
     * @param node The node to find the class for.
     *
     * @return A {@link Class} object representing the associated class, or null if the class could not be resolved.
     *
     */
    public static Class<?> getNodeClass(AbstractJavaNode node) {

        if (node instanceof ASTMethodDeclaration && isAnonymousInnerClassMethod((ASTMethodDeclaration) node)) {
            return getAnonymousInnerClass((ASTMethodDeclaration) node, getBodyClass(node.getFirstParentOfType(ASTClassOrInterfaceBody.class)));
        }

        // class Foo {}
        ASTClassOrInterfaceBody body = node.getFirstParentOfType(ASTClassOrInterfaceBody.class);
        if (body != null) {
            return getBodyClass(body);
        }

        ASTEnumDeclaration enumDecl = node.getFirstParentOfType(ASTEnumDeclaration.class);
        if (enumDecl != null) {
            return getEnumClass(enumDecl);
        }

        // try to find the interface / class declared in the CU
        ASTCompilationUnit unitDecl = node.getFirstParentOfType(ASTCompilationUnit.class);
        if (unitDecl != null) {

            body = unitDecl.getFirstDescendantOfType(ASTClassOrInterfaceBody.class);
            if (body != null) {
                return getBodyClass(body);
            }
        }

        return null;
    }

    private static Class<?> getAnonymousInnerClass(ASTMethodDeclaration method, Class<?> superClass) {

        ASTClassOrInterfaceDeclaration classDecl = method.getFirstParentOfType(ASTClassOrInterfaceDeclaration.class);
        if (classDecl == null || classDecl.getType() == null) {
            return null;
        }

        String outerClassName = classDecl.getType().getCanonicalName();
        String packageName = getPackageName(method);
        Set<String> imports = getNodeImports(method);

        for (int i=1; i<100; ++i) {

            Class<?> anonymous = ASTResolver.resolveType(outerClassName + '$' + i, packageName, outerClassName, imports);
            if (anonymous == null) {
                // compiler uses consecutive indices for the names, we can stop as soon as we don't find a
                // inner class
                break;
            }

            if (superClass.isAssignableFrom(anonymous)) {
                return anonymous;
            }
        }

        return null;
    }

    private static Class<?> getBodyClass(ASTClassOrInterfaceBody body) {

        if (body.jjtGetParent() instanceof ASTClassOrInterfaceDeclaration) {
            // method in a top-level class or inner class
            ASTClassOrInterfaceDeclaration decl = (ASTClassOrInterfaceDeclaration)body.jjtGetParent();
            if (decl.getType() != null) {
                return decl.getType();
            }

            return ASTResolver.resolveType(decl, decl.getImage());
        }

        // Foo = new Foo() {}
        if (body.jjtGetParent() instanceof ASTAllocationExpression) {

			// method in a anonymous class
            ASTClassOrInterfaceType type = body.jjtGetParent().getFirstChildOfType(ASTClassOrInterfaceType.class);
            return type.getType() != null ? type.getType() : ASTResolver.resolveType(body, type.getImage());
        }

        return null;
    }

    private static Class<?> getEnumClass(ASTEnumDeclaration enumDecl) {

        if (enumDecl.getType() != null) {
            return enumDecl.getType();
        }

        // PMD 5.1.0 doesn't seem to resolve enum types, manually look up the full qualified
        // name as a fallback

        String fqn = getNodeClassName(enumDecl);
        try {
            return Class.forName(fqn);
        } catch (ClassNotFoundException e) {
            System.err.println("Could not resolve enum class " + getNodeClassName(enumDecl)); // NOPMD PMD doesn't propagate exception
        }

        return null;
    }

    public static boolean isAnonymousInnerClassMethod(ASTMethodDeclaration method) {

        ASTClassOrInterfaceBody body = method.getFirstParentOfType(ASTClassOrInterfaceBody.class);
        return (body != null) && (body.jjtGetParent() instanceof ASTAllocationExpression);
    }

    /**
     *
     * <p>Tests if a method overrides a base class method or implements an interface method.</p>
     *
     * <p>This method does not rely on Override annotations being present. It checks the
     * class hierarchy.</p>
     *
     * @param method The AST node representing the node to test.
     *
     * @return Returns true if the method effectively overrides.
     *
     */
    public static boolean isOverrideMethod(ASTMethodDeclaration method) {

        Class<?> clazz = getNodeClass(method);
        if (clazz == null) {
            System.err.println("Could not resolve class in method override test " + getNodeClassName(method) + "." + method.getMethodName());
            return false;
        }

        return isOverrideMethod(method, clazz);
    }

    private static boolean isOverrideMethod(ASTMethodDeclaration method, Class<?> clazz) {

        if (isMethodInClassInterfaces(method, clazz)) {
            return true;
        }

        Class<?> parent = clazz.getSuperclass();
        while (parent != null) {

            if (isMethodInType(method, parent)) {
                return true;
            }

            if (isMethodInClassInterfaces(method, parent)) {
                return true;
            }

            parent = parent.getSuperclass();
        }

        return false;
    }

    /**
     *
     * <p>Tests if an interface defines a method with the same signature as the AST node.</p>
     *
     * @param method The AST node representing the method.
     * @param clazz The Java {@link Class} object representing the interface to test against.
     *
     * @return Returns true if the method is declared in the interface.
     *
     */
    public static boolean isMethodInClassInterfaces(ASTMethodDeclaration method, Class<?> clazz) {

        for (Class<?> iface : clazz.getInterfaces()) {

            if (isMethodInType(method, iface)) {
                return true;
            }

            if (isMethodInClassInterfaces(method, iface)) {
                return true;
            }
        }

        return false;
    }

    /**
     *
     * <p>Tests if a method is present in a class.</p>
     *
     * @param method The AST node representing the method.
     * @param clazz The Java {@link Class} object representing the class to test against.
     *
     * @return Returns true if the method is defined in the class.
     *
     */
    public static boolean isMethodInType(ASTMethodDeclaration method, Class<?> clazz) {

        for (Method classMethod : clazz.getDeclaredMethods()) {
            if (isMethodEqual(method, classMethod)) {
                return true;
            }
        }
        return false;
    }

    /**
     *
     * <p>Compares a AST method with a Java reflection method object.</p>
     *
     * @param ref The AST node representing the method.
     * @param method The Java {@link Method} object to test against.
     *
     * @return Returns true if the method is defined in the class.
     *
     */
    public static boolean isMethodEqual(ASTMethodDeclaration ref, Method method) {

        if (!ref.getMethodName().equals(method.getName())) {
            return false;
        }

        //System.out.println("isMethodEqual " + ref.getMethodName());

        if (!isMethodVisibilityAcceptable(ref, method)) {
            return false;
        }

        if (!isMethodReturnType(ref, method.getReturnType())) {
            return false;
        }

        return isMethodArgumentListEqual(ref, method);
    }

    private static boolean isMethodVisibilityAcceptable(ASTMethodDeclaration ref, Method method) {

        if ((method.getModifiers() & Modifier.FINAL) != 0) {
            // a final method cannot be overridden
            return false;
        }

        if ((method.getModifiers() & Modifier.PRIVATE) != 0) {
            // a private method cannot be overridden
            return false;
        }

        if ((method.getModifiers() & Modifier.FINAL) != 0) {
            // a final method cannot be overridden
            return false;
        }

        if (ref.isPackagePrivate()) {

            String refPackage = getPackageName(ref);
            String methodPackage = method.getDeclaringClass().getPackage().getName();

            if (!refPackage.equals(methodPackage)) {
                // package private methods must be in the same package
                return false;
            }
        }

        return true;
    }

    private static boolean isParameterOrReturnType(AbstractJavaNode node, Type type, boolean arrayType) {

        //System.out.println("isParameterOrReturnType " + getNodeOuterClassName(node));

        ASTPrimitiveType primitiveType = node.getFirstDescendantOfType(ASTPrimitiveType.class);
        ASTReferenceType refType = node.getFirstDescendantOfType(ASTReferenceType.class);
        if (primitiveType != null) {

            if (arrayType) {

                // ref type + prim type = primitive array
                return isTypeEqual(Array.newInstance(primitiveType.getType(), 0).getClass(), type);
            }

            if (isTypeEqual(primitiveType.getType(), type)) {
                return true;
            }
        }

        if (refType != null) {

            if (refType.getType() == null) {

                // generics don't get a type assigned by PMD

                ASTClassOrInterfaceType classType = refType.getFirstChildOfType(ASTClassOrInterfaceType.class);
                List<Class<?>> generics = resolveGenericType(classType.getImage(), node);
                for (Class<?> generic : generics) {

                    if (refType.isArray()) {

                        if (isTypeEqual(Array.newInstance(generic, 0).getClass(), type)) {
                            return true;
                        }

                    } else
                    if (isTypeEqual(generic, type)) {
                        return true;
                    }
                }

            } else
            if (arrayType) {
                return isTypeEqual(Array.newInstance(refType.getType(), 0).getClass(), type);
            } else
            if (isTypeEqual(refType.getType(), type)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isTypeEqual(Class<?> ref, Type type) {

        if (type instanceof TypeVariable<?>) {

            TypeVariable<?> typeVariable = (TypeVariable<?>)type;
            if (typeVariable.getBounds().length > 0) {

                for (Type bound : typeVariable.getBounds()) {

                    if (ClassUtils.isAssignable((Class<?>)bound, ref, true) || ClassUtils.isAssignable(ref, (Class<?>)bound)) {
                        return true;
                    }
                }
            } else {

                // type is unbounded generic and can be replaced with ref
                return true;
            }
        } else
        if (type instanceof GenericArrayType) {

            GenericArrayType arrayType = (GenericArrayType)type;
            return ref.isArray() && isTypeEqual(ref.getComponentType(), arrayType.getGenericComponentType());

        } else
        if (type instanceof ParameterizedType) {

            ParameterizedType parameterizedType = (ParameterizedType)type;
            return parameterizedType.getRawType() == ref; // NOPMD intentional
        } else
        if (type instanceof Class<?>) {

            return ref == type; // NOPMD intentional
        }

        System.err.println("Unhandled Type implementation " + type.getClass());

        return false;
    }

    private static List<Class<?>> resolveGenericType(String name, AbstractJavaNode node) {

        List<Class<?>> classes = new LinkedList<Class<?>>();

        while (node != null) {

            ASTTypeParameters parameters = node.getFirstChildOfType(ASTTypeParameters.class);
            if (parameters != null) {

                for (int i=0; i<parameters.jjtGetNumChildren(); i++) {

                    ASTTypeParameter parameter = (ASTTypeParameter)parameters.jjtGetChild(i);
                    if (!parameter.getImage().equals(name)) {
                        continue;
                    }

                    ASTTypeBound bound = parameter.getFirstChildOfType(ASTTypeBound.class);
                    if (bound != null) {

                        for (int j=0; j<bound.jjtGetNumChildren(); j++) {

                            ASTClassOrInterfaceType type = (ASTClassOrInterfaceType)bound.jjtGetChild(j);

                            // type erasure, becomes bounded type
                            Class<?> clazz = ASTResolver.resolveType(node, type.getImage());
                            if (clazz == null) {
                                System.err.println("Could not resolve generic type bound '" + bound.getImage() + "'");
                                return null;
                            }

                            classes.add(clazz);
                        }

                    } else {
                        // type erasure, unbounded becomes Object
                        classes.add(Object.class);
                    }

                    // found the type
                    break; // NOPMD
                }
            }

            node = (AbstractJavaNode) node.jjtGetParent();
        }

        return classes;
    }

    private static boolean isMethodReturnType(ASTMethodDeclaration methodDecl, Class<?> clazz) {

        ASTResultType resultType = methodDecl.getFirstChildOfType(ASTResultType.class);
        if (resultType.jjtGetNumChildren() == 0) {
            return void.class == clazz;
        }

        return isParameterOrReturnType(resultType, clazz, resultType.returnsArray());
    }

    /**
     *
     * <p>Tests if the AST method arguments are equal to a Java {@link Method} object's arguments.</p>
     *
     * @param ref The AST node representing the method whose arguments to test.
     * @param method The Java {@link Method} object to test against.
     *
     * @return Returns true if the arguments are equal.
     *
     */
    public static boolean isMethodArgumentListEqual(ASTMethodDeclaration ref, Method method) {

        List<ASTFormalParameter> refParams = getMethodArgs(ref);

        if (refParams.size() != method.getParameterTypes().length) {
            return false;
        }

        for (int i=0; i<refParams.size(); i++) {

            ASTFormalParameter parameter = refParams.get(i);
            if (!isParameterOrReturnType(refParams.get(i), method.getGenericParameterTypes()[i], parameter.isArray())) {
                return false;
            }
        }

        return true;
    }

    /**
     *
     * <p>Returns the name of a method / constructor argument.</p>
     *
     * @param arg The AST node representing the argument to get the name of.
     *
     * @return Returns the name of the argument or null.
     *
     */
    public static String getMethodArgumentName(ASTFormalParameter arg) {

        ASTVariableDeclaratorId varDecl = arg.getFirstChildOfType(ASTVariableDeclaratorId.class);
        if (varDecl != null && varDecl.getNameDeclaration() != null) {
            return varDecl.getNameDeclaration().getName();
        }
        return null;
    }

    /**
     *
     * <p>Returns the arguments of a method as a list.</p>
     *
     * @param method The AST node representing the method to get the arguments of.
     *
     * @return Returns a list of method {@link ASTFormalParameter arguments} (can be empty).
     *
     */
    public static List<ASTFormalParameter> getMethodArgs(ASTMethodDeclaration method) {

        ASTMethodDeclarator decl = method.getFirstChildOfType(ASTMethodDeclarator.class);
        ASTFormalParameters parameters = decl.getFirstChildOfType(ASTFormalParameters.class);
        return parameters.findChildrenOfType(ASTFormalParameter.class);
    }

    /**
     *
     * <p>Returns the arguments of a constructor as a list.</p>
     *
     * @param ctor The AST node representing the constructor to get the arguments of.
     *
     * @return Returns a list of constructor {@link ASTFormalParameter arguments} (can be empty).
     *
     */
    public static List<ASTFormalParameter> getConstructorArgs(ASTConstructorDeclaration ctor) {

        ASTFormalParameters parameters = ctor.getFirstChildOfType(ASTFormalParameters.class);
        return parameters.findChildrenOfType(ASTFormalParameter.class);
    }

    /**
     *
     * <p>Returns the annotations of a method as a list.</p>
     *
     * @param method The AST node representing the method to get the annotations for.
     *
     * @return Returns a list of method {@link ASTAnnotation annotations} (can be empty).
     *
     */
    public static List<ASTAnnotation> getMethodAnnotations(ASTMethodDeclaration method) {

        ASTClassOrInterfaceBodyDeclaration body = method.getFirstParentOfType(ASTClassOrInterfaceBodyDeclaration.class);

        List<ASTAnnotation> annotations = new LinkedList<ASTAnnotation>();
        for (int i=0; i<body.jjtGetNumChildren(); i++) {

            Node child = body.jjtGetChild(i);
            if (child != method) { // NOPMD identity comparison
                continue;
            }

            // found our method node in parent, backtrack as long as we find annotations
            while (--i >= 0) {

                Node sibling = body.jjtGetChild(i);
                if (!(sibling instanceof ASTAnnotation)) {
                    break;
                }

                annotations.add((ASTAnnotation)sibling);
            }

            break; // NOPMD alternative would be deeper nesting
        }

        return annotations;
    }

    /**
     *
     * <p>Checks if a method has an {@link Override @Override} annotation.</p>
     *
     * @param method The method to test.
     *
     * @return Returns true if the method has an {@link Override @Override} annotation.
     *
     */
    public static boolean hasOverrideAnnotation(ASTMethodDeclaration method) {

        List<ASTAnnotation> annotations = getMethodAnnotations(method);
        for (ASTAnnotation annotation : annotations) {

            ASTName name = annotation.getFirstDescendantOfType(ASTName.class);
            if (name.getImage().equals("Override")) {
                return true;
            }
        }
        return false;
    }

    /**
     *
     * <p>Enumeration describing the type of singleton.</p>
     *
     */
    public enum SingletonType {

        /**
         *
         * <p>Not a singleton.</p>
         *
         */
        NONE,
        /**
         *
         * <p>A eager singleton.</p>
         *
         */
        EAGER,
        /**
         *
         * <p>A lazy-init singleton.</p>
         *
         */
        LAZY
    }

    /**
     *
     * <p>Determines whether a class is a singleton and if it is, which type of singleton it is.</p>
     *
     * <p>This test is very rough and doesn't validate all constraints we put on singletons. It
     * tries to figure out what the author had intended.</p>
     *
     * @param classDecl The AST node representing the class.
     *
     * @return The {@link SingletonType}.
     *
     */
    public static SingletonType guessIntendedSingletonType(ASTClassOrInterfaceDeclaration classDecl) {

        if (classDecl.isInterface()) {
            // interfaces cannot be singletons
            return SingletonType.NONE;
        }

        boolean staticMemberFound = false;
        boolean staticMemberHasNewInitializer = false;
        boolean staticAccessorFound = false;

        ASTClassOrInterfaceBody body = classDecl.getFirstChildOfType(ASTClassOrInterfaceBody.class);
        for (int i=0; i<body.jjtGetNumChildren(); i++) {

            Node child = body.jjtGetChild(i);
            if (!(child instanceof ASTClassOrInterfaceBodyDeclaration)) {
                continue;
            }

            ASTClassOrInterfaceBodyDeclaration bodyDecl = (ASTClassOrInterfaceBodyDeclaration)child;

            ASTFieldDeclaration fieldDecl = bodyDecl.getFirstChildOfType(ASTFieldDeclaration.class);
            if (fieldDecl != null && fieldDecl.isStatic() && fieldDecl.getType() == classDecl.getType()) {

                staticMemberFound = true;

                ASTAllocationExpression allocation = fieldDecl.getFirstDescendantOfType(ASTAllocationExpression.class);
                if (allocation != null && allocation.getType() == classDecl.getType()) {
                    staticMemberHasNewInitializer = true;
                }
                continue;
            }

            ASTMethodDeclaration methodDecl = bodyDecl.getFirstChildOfType(ASTMethodDeclaration.class);
            if (methodDecl != null && isSingletonAccessor(methodDecl)) {

                staticAccessorFound = true;
            }
        }

        if (staticMemberFound && staticAccessorFound) {

            return staticMemberHasNewInitializer ? SingletonType.EAGER : SingletonType.LAZY;
        }

        return SingletonType.NONE;
    }

    /**
     *
     * <p>Checks if a method looks like a singleton accessor.</p>
     *
     * @param methodDecl The method declaration to check.
     *
     * @return Returns true if the method looks like a singleton accessor, false otherwise.
     *
     */
    public static boolean isSingletonAccessor(ASTMethodDeclaration methodDecl) {

        Class<?> clazz = getNodeClass(methodDecl);

        if (!methodDecl.isStatic()) {
            // method isn't static, not a singleton accessor
            return false;
        }

        if (!isMethodReturnType(methodDecl, clazz)) {
            // wrong return type, not a singleton accessor
            return false;
        }

        // method may not take any arguments
        return ASTUtil.getMethodArgs(methodDecl).isEmpty();
    }

    /**
     *
     * <p>Determines the package name from any node in the AST.</p>
     *
     * @param node The originating node.
     *
     * @return The package name as specified in the source file.
     *
     */
    public static String getPackageName(AbstractJavaNode node) {

        ASTCompilationUnit cunit = node.getFirstParentOfType(ASTCompilationUnit.class);
        return (cunit != null && cunit.getPackageDeclaration() != null) ? cunit.getPackageDeclaration().getPackageNameImage() : UNRESOLVED_NAME;
    }

    /**
     *
     * <p>Returns a list of imports. This includes imports as specified in the
     * source file and for each import the corresponding package.</p>
     *
     * @param node The node in the AST to return the imports for.
     *
     * @return A list of fully qualified package names.
     *
     */
    public static Set<String> getNodeImports(AbstractJavaNode node) {

        Set<String> imports = new HashSet<String>();
        ASTCompilationUnit unit = node.getFirstParentOfType(ASTCompilationUnit.class);
        for (int i=0; i<unit.jjtGetNumChildren(); i++) {

            Node child = unit.jjtGetChild(i);
            if (child instanceof ASTImportDeclaration) {

                ASTImportDeclaration importDecl = (ASTImportDeclaration) child;
                imports.add(importDecl.getImportedName());
                imports.add(importDecl.getPackageName());
            }
        }

        return imports;
    }

    /**
     *
     * <p>Tries to resolve a fully qualified class name without using reflection. This is used to
     * report unresolvable types. Note that this only works for the compilation unit the class
     * was defined in.</p>
     *
     * <p>Retrieves the name of the class which contains the node, i.e. the inner / anonymous
     * class for inner / anonymous class methods.</p>
     *
     * @param node The {@link AbstractJavaNode} for which to look up the class.
     *
     * @return The full qualified class name or a string containing partial information.
     *
     */
    public static String getNodeClassName(AbstractJavaNode node) {

        StringBuilder fqn = new StringBuilder();

        ASTCompilationUnit cunit = node.getFirstParentOfType(ASTCompilationUnit.class);
        ASTPackageDeclaration packageDecl = cunit.getFirstChildOfType(ASTPackageDeclaration.class);
        if (packageDecl != null) {
            fqn.append(packageDecl.getPackageNameImage());
            fqn.append('.');
        }

        ClassScope classScope = null;
        if (node.getScope() instanceof MethodScope) {

            MethodScope scope = (MethodScope)node.getScope();
            classScope = (ClassScope)scope.getParent();
        } else
        if (node.getScope() instanceof ClassScope) {

            classScope = (ClassScope)node.getScope();
        }

        if (classScope != null) {
            fqn.append(classScope.getClassName());
        }

        return fqn.toString();
    }

    /**
     *
     * <p>Tries to resolve a fully qualified class name without using reflection. This is used to
     * report unresolvable types. Note that this only works for the compilation unit the class
     * was defined in.</p>
     *
     * <p>Retrieves the name of the outermost class which contains the node.</p>
     *
     * @param node The {@link AbstractJavaNode} for which to look up the class.
     *
     * @return The full qualified class name or a string containing partial information.
     *
     */
    public static String getNodeOuterClassName(AbstractJavaNode node) {

        StringBuilder fqn = new StringBuilder();

        ASTCompilationUnit cunit = node.getFirstParentOfType(ASTCompilationUnit.class);
        ASTPackageDeclaration packageDecl = cunit.getFirstChildOfType(ASTPackageDeclaration.class);
        if (packageDecl != null) {
            fqn.append(packageDecl.getPackageNameImage());
            fqn.append('.');
        }

        // find the topmost class scope
        Scope scope = node.getScope();
        ClassScope classScope = (node.getScope() instanceof ClassScope) ? ((ClassScope)node.getScope()) : null;
        while (scope.getParent() != null) {

            scope = scope.getParent();

            if (scope instanceof ClassScope) {
                classScope = (ClassScope) scope;
            }
        }

        if (classScope != null) {
            fqn.append(classScope.getClassName());
        }

        return fqn.toString();
    }

    /**
     *
     * <p>Finds a parent node of a given type. Contrary to the PMD method, this method
     * stops traversing up when it has reached a node of a type.</p>
     *
     * @param node The {@link Node} for which to find the parent node.
     * @param parentClass The node {@link Class} which is to be found.
     * @param stopAt The node {@link Class} to stop the traversal at.
     *
     * @param <T> The type of the parent node.
     *
     * @return The node of type {@code parentClass}.
     *
     */
    @SuppressWarnings("unchecked")
    public static <T extends Node> T findParentOfType(Node node, Class<T> parentClass, Class<? extends Node> stopAt) {

        Node parent = node.jjtGetParent();
        while (parent != null) {

            if (parent.getClass() == stopAt) {
                break;
            }

            if (parent.getClass() == parentClass) {
                return (T)parent;
            }

            parent = parent.jjtGetParent();
        }

        return null;
    }

    /**
     *
     * <p>Finds descendant nodes (direct and indirect children) of a given type. Contrary to the PMD method,
     * this method stops traversing down when it has reached a node of a type.</p>
     *
     * @param node The {@link Node} for which to find child nodes.
     * @param childClass The node {@link Class} which is to be found.
     * @param stopAt The node {@link Class} to stop the traversal at.
     *
     * @param <T> The type of the descendant nodes.
     *
     * @return A list with nodes of type {@code childClass}.
     *
     */
    public static <T extends Node> List<T> findDescendantsOfType(Node node, Class<T> childClass, Class<? extends Node> stopAt) {

        List<T> descendants = new LinkedList<T>();
        collectDescendantsOfType(node, childClass, stopAt, descendants);
        return descendants;
    }

    @SuppressWarnings("unchecked")
    private static <T> void collectDescendantsOfType(Node node, Class<T> childClass, Class<?> stopAt, List<T> descendants) {

        for (int i=0; i<node.jjtGetNumChildren(); i++) {

            Node child = node.jjtGetChild(i);

            if (child.getClass() == childClass) {

                descendants.add((T)child);
            }

            if (child.getClass() != stopAt) {

                collectDescendantsOfType(child, childClass, stopAt, descendants);
            }
        }
    }

    /**
     *
     * <p>Finds descendant nodes (direct and indirect children) of a given type. Contrary to the PMD method,
     * this method stops traversing down when it has reached a node of a type.</p>
     *
     * @param node The {@link Node} for which to find child nodes.
     * @param childClass The node {@link Class} which is to be found.
     * @param stopAt The node {@link Class} to stop the traversal at.
     *
     * @param <T> The type of the descendant node.
     *
     * @return A list with nodes of type {@code childClass}.
     *
     */
    public static <T extends Node> T getFirstDescendantOfType(Node node, Class<T> childClass, Class<? extends Node> stopAt) {

        for (int i=0; i<node.jjtGetNumChildren(); i++) {

            Node child = node.jjtGetChild(i);

            if (child.getClass() == stopAt) {
                return null;
            }

            if (child.getClass() == childClass) {
                return (T)child;
            }
        }

        return null;
    }

    /**
     *
     * <p>Prints a (sub-) tree to a {@link PrintStream}.</p>
     *
     * @param node The node to start from.
     * @param writer The {@link PrintStream} to write to.
     *
     */
    public static void dumpTree(Node node, PrintStream writer) {

        dumpTree(node, writer, 0);
    }

    private static void dumpTree(Node node, PrintStream writer, int indent) {

        for (int i=0; i<indent; i++) {
            writer.print(' ');
        }

        writer.print("Node " + node);

        if (node instanceof ASTFieldDeclaration) {

            writer.print(" " + ((ASTFieldDeclaration) node).getVariableName());
        } else
        if (node instanceof ASTConstructorDeclaration) {

            writer.print(" " + getNodeClassName((ASTConstructorDeclaration) node));
        } else
        if (node instanceof ASTMethodDeclaration) {

            writer.print(" " + ((ASTMethodDeclaration) node).getMethodName());
        } else
        if (node instanceof ASTClassOrInterfaceDeclaration) {

            writer.print(" " + getNodeClassName((ASTClassOrInterfaceDeclaration) node));
        } else
        if (node instanceof ASTClassOrInterfaceType) {

            writer.print(" type: " + ((ASTClassOrInterfaceType)node).getType() + " image: " + node.getImage());
        }
        
        if (node instanceof AbstractJavaNode) {
        	Comment comment = ((AbstractJavaNode)node).comment();
        	if (comment != null) {
        		String text =  comment.getImage().replace('\n', ' ');
        		if (text.length() > 20) {
        			text = text.substring(0, 20) + " ...";
        		}
        		writer.print(" comment '" + text + "'");
        	}
        }

        writer.println();

        for (int i=0; i<node.jjtGetNumChildren(); i++) {

            dumpTree(node.jjtGetChild(i), writer, indent + 1);
        }
    }
}
