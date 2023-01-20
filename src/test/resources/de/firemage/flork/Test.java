public class Test {
    private Foo x;
    
    private void foo(int x) {
        this.x.y = 3;
    }
}

class Foo {
    int y;
}
