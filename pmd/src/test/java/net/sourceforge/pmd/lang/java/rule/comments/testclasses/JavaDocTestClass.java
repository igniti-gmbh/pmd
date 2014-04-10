package net.sourceforge.pmd.lang.java.rule.comments.testclasses;

/**
 * 
 * This class is used by the JavaDoc comment link tests which need
 * the link targets to be compiled and loaded into the VM.
 *
 * <p>A link to the {@link net.sourceforge.pmd.lang.java.rule.comments.testclasses.JavaDocTestClass test class}.</p>
 * <p>A link to a {@link JavaDocTestClass#method1() method}.</p>
 * <p>A link to the {@link JavaDocTestClass#overload1 overload method}.</p>
 * <p>A link to the first {@link JavaDocTestClass#overload1(int param1) overload method}.</p>
 * <p>A link to the second {@link JavaDocTestClass#overload1(int, int) overload method}.</p>
 * <p>A link to the third {@link JavaDocTestClass#overload1(String, float) overload method}.</p>
 * <p>A link to the fourth {@link JavaDocTestClass#overload2(E) overload method}.</p>
 * <p>A link to the fifth {@link JavaDocTestClass#overload2(E, F) overload method}.</p>
 * 
 */
public class JavaDocTestClass {

	public void method1() {}
	
	public void overload1(int param1) {}
	
	public void overload1(int param1, int param2) {}

	public void overload1(String param1, float param2) {}

	public <E> void overload2(E param1) {}
	
	public <E, F> void overload2(E param1, F param2) {}
}
