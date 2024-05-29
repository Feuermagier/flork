// public class Test {
//     public int foo(int a) {
//         // int x = 0
//         // if (a < 0) {
//         //     x = 2;
//         // }
//         // if (x < 1) {
//         //     return -1;
//         // } else {
//         //     return 1;
//         // }
//         int x = 0;
//         if (a < 0) {
//             x = 2;
//         }
//         while (x < 100) {
//             x = x + 1;
//         }
//         return x;
//     }
// }

class Foo {
    private Foo x;

    public int foo(int x) {
        int foo = this.bar(x);
        return foo;
    }

    public int bar(int x) {
        if (x == 1) {
            return 0;
        } else {
            return -1;
        }
    }
}

class Bar extends Foo {
    @Override
    public int bar2(int x) {
        if (x == 1) {
            return 1;
        } else {
            return 2;
        }
    }
}
