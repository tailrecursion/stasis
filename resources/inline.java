/*
 * inline.java: In a final pass, maybe we inline definitions that
 * don't have an export prefix.
 */

public class inline {
  
  public static void main(String[] args) {
    System.out.println((args.length > 0 ? args[0] : null));
  }
}
