public class Test {
    public int foo(int a) {
        // int x = 0
        // if (a < 0) {
        //     x = 2;
        // }
        // if (x < 1) {
        //     return -1;
        // } else {
        //     return 1;
        // }
        int x = 0;
        if (a < 0) {
            x = 2;
        }
        while (x < 100) {
            x = x + 1;
        }
        return x;
    }
}

// public class Test {
//     private Foo x;
//
//     private int foo(int x) {
//         int foo = this.bar(x);
//         if (null != this.x) {
//             return 3;
//         }
//         return foo;
//     }
//
//     private int bar(int x) {
//         if (this.x.y == 1) {
//             return 0;
//         } else {
//             return -1;
//         }
//     }
// }
//
// class Foo {
//     int y;
// }
//
// class Bar extends Foo { }
