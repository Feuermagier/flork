public class Test {
    private Foo x;
    
    private void foo(int x) {
        Foo foo = new Foo();
        int q = foo.y;
    }
}

class Foo {
    int y;
}
