package net.sourceforge.pmd.lang.java.rule.comments.testclasses;

import java.util.List;

/**
 * 
 * This class is used by the JavaDoc comment references tests which need
 * the link targets to be compiled and loaded into the VM.
 * 
 */
public class JavaDocTestClass {

    public int field1;

    public static final int field2 = 0;

    public void method1() {
    }

    public void overload1(int param1) {
    }

    public void overload1(int param1, int param2) {
    }

    public void overload1(String param1, float param2) {
    }

    public <E extends List<?>> void overload2(E param1) {
    }

    public <E, F> void overload2(E param1, F param2) {
    }
}
