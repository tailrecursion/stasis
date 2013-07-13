/*
 * lambdalift.java: What could a static Clojure be like?
 *
 * This example shows what an optimizing Clojure to Java compiler
 * might do with a short program.
 * 
 * Demonstrated are "lambda lifting" closures and lazy sequence
 * elimination via type inference.
 *
 * The resulting compiled Java program is much faster to start and run
 * than the equivalent Clojure one, but lacks dynamic features like a
 * REPL.  A degree of dynamicity might still be possible with runtime,
 * in-process Java compilation and loading.
 *
 * The smaller compiled code footprint and faster startup would make
 * Clojure more tolerable for use in command-line scripts and embedded
 * or Android applications.
 */

/*
;; lambdalift.clj:

(ns lambdalift)

(defn say-hello [s]
  (. System/out (println s)))
  
(defn -main [& args]
  (let [x (first args)]
    (say-hello x)))
    
;; On my machine, lambdalift.clj takes about 800ms to start and run the conventional way.    
*/

public class lambdalift {
  
  // Our compiler understands -main as the "main" method of this
  // class, borrowing the prefix convention from gen-class.  It also
  // knows Java main methods must have the signature (String[] args),
  // so is able to type args a String[].
  public static void main(String[] args) {
    // args is then passed to let4213, a private static method on this
    // class.  The process of converting a closure to a function (or,
    // in this case, method) is known as "lambda lifting". (2)
    let4213(args);
  }
  
  // Since let4213 is only ever invoked with the args parameter of main,
  // and the type of its parameter, args, is known to be String[], we
  // know the parameter type here too.
  private static Object let4213(String[] args) {
    // In Clojure today, (first arr), where arr is an array, means a
    // call to clojure.lang.RT/seq and the creation of a
    // clojure.lang.ArraySeq object, the .first method of which is
    // then called.
    
    // Our compiler knows that (first arr) is equivalent to (aget arr
    // 0) on arrays, and because we've tracked the type we can skip
    // the intermediate ArraySeq creation and do direct array access.
    String x = (args.length > 0 ? args[0] : null);
    return say_hello(x);
  }
  
  // Because args was a String[], we know the only call to this method
  // is made with a String.  If we invoked any methods of s, we
  // wouldn't have to use reflection.
  public static Object say_hello(String s) {
    return do_void4208(s);
  }
  
  // The compiler does compile-time reflection on Java methods to
  // determine if they are void.  If they are, calls to them can't be
  // in "expression position".  Our solution is to wrap them in a
  // method that explicitly returns null.
  private static Object do_void4208(String s) {
    System.out.println(s);
    return null;
  }
}

/*
 * On my machine, after compiling with javac, this file takes about
 * 70ms to start and run.
 *
 * Footnotes
 * 
 * 1. http://docs.oracle.com/javase/specs/jls/se7/html/jls-12.html#jls-12.1.4
 * 2. http://en.wikipedia.org/wiki/Lambda_lifting
 */
