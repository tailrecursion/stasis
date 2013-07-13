/*
 * lambdadrop.java: Once we've lifted and typed closures and method
 * calls, we may choose to fold them together in a subsequent compiler
 * pass.
 */

public class lambdadrop {

  public static void main(String[] args) {
    say_hello((args.length > 0 ? args[0] : null));
  }

  public static void say_hello(String s) {
    System.out.println(s);
  }
}
