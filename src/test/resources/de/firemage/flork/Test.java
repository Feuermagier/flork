public class Test {
    private int foo(int x) {
        return new Foo().xyz();
    }
    
    public static Foo bar() {
        return XYZ.x();
    }

    public void baz() {

    }
}

class Foo {
    public int doStuff() {
        return this.xyz();
    }
    public int xyz() {
        return 1;
    }
}

class Bar extends Foo {
    public int xyz() {
        return 2;
    }
}
