# stasis

An experimental, totally unusable proof-of-concept Clojure to Java quasi-compiler.

This:

``clojure
(ns test)

(defn -main [& args]
  (println "hello world"))
```

Becomes this:

```java
public class test {
  public static void main(String... args) {
    (new Object() {
        public Object G__1009() {
          return (new Object() {
              public Object G__1010() {
                System.out.println("hello world");
                return null;
              }
            }).G__1010();
        }
      }).G__1009();
  }
}
```

## todos and ideas

* implement the special forms.
  * apply becomes a special when compiling statically
  * need static stubs for all the Namespace, Var functions (binding needs to be figured out too)
  * anonymous functions need to be compiled into methods and hung off a class
* figure out a good intermediate representation that serves efficient name resolution
* use tools.namespace and jvm.tools.analyzer to only compile referenced names and their dependencies instead of whole namespaces
* use in conjunction with javax.tools.JavaCompiler to provide eval and a REPL

The goal here is to be able to compile clojure.core some day and reap
the startup time and memory efficiency benefits of that arrangement.

## Usage

    lein run resources/test.clj && javac test.java && java test

## License

Copyright © 2013 Alan Dipert

Distributed under the Eclipse Public License, the same as Clojure.
