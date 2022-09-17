public class Test {
    public Test foo(int x) {
        System.out.println(1);
        return bar(x, new Test());
    }
    
    public static Test bar(int x, Test test) {
        if (x >= 0) {
            return new Test();
        } else {
            return null;
        }
    }

    public void baz() {

    }
}
