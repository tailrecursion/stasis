# stasis

A collection of experiments related to the idea of compiling Clojure to Java.

This:

```clojure
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

## notes and research

See [notes.org](notes.org).

## Usage

    lein run resources/test.clj && javac test.java && java test

## License

Copyright © 2013 Alan Dipert

Distributed under the Eclipse Public License, the same as Clojure.
