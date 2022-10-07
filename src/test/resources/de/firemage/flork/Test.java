public class Test {
    public int foo(int x) {
        return bar().xyz();
    }
    
    public static Foo bar() {
        return XYZ.x();
    }

    public void baz() {

    }
}

class Foo {
    public int xyz() {
        return 1;
    }
}

class Bar extends Foo {
    @Override
    public int xyz() {
        return 2;
    }
}
