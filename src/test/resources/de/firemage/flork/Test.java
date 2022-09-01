public class Test {
    public Test foo(int x) {
        return bar(x, new Test());
    }
    
    public static Test bar(int x, Test test) {
        if (x * 2 >= 0) {
            return test;
        } else {
            return null;
        }
    }

    public void baz() {

    }
}
