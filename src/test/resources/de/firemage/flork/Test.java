public class Test {
    private Foo x;
    
    private int foo(int x) {
        int foo = this.bar(x);
        if (null != this.x) {
            return 3;
        }
        return foo;
    }
    
    private int bar(int x) {
        if (this.x.y == 1) {
            return 0;
        } else {
            return -1;
        }
    }
}

class Foo {
    int y;
}
