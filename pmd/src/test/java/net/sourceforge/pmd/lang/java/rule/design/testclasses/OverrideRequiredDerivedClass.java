package net.sourceforge.pmd.lang.java.rule.design.testclasses;

/**
 * 
 * This class is used by the OverrideRequired tests which need
 * the tested classes to be compiled and loaded into the VM.
 * 
 */
public class OverrideRequiredDerivedClass extends OverrideRequiredBaseClass<String> {

    public void baseClassMethod1() {
    }

    public void baseClassMethod2(int param1, float param2) {
    }

    public void baseClassMethod3(String param) {
    }

    public void baseClassMethod4(String param) {
    }

    public <F> void baseClassMethod5(F param) {
    }
}
