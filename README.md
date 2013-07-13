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

## braindump

```org
* rationale
** faster startup
*** make clojure suitable for 'scripting': CLI/glue programs
*** win on android
*** currently
**** clj hello world: 1800ms
**** clj hello world (AOT main class): 700ms
***** primary overhead: loading clojure.core
***** AOT not transitive
**** java hello world: < 100ms
***** competitive with scriptlang (not including compilation)
***** could be on par with go if compilation is snappy
*** why not cljs/node/lua/ruby/python/llvm
**** others are working on them
**** persistent data structures on jvm are done
***** so is CAS, STM
**** want to work with existing clj jvm code
***** could use clj jvm for repl/test/debug, this for prod/deploy
** lighter runtime footprint
*** fewer classes
**** hunch: lambda lifting easier on GC
**** less bytecode overhead
*** easier on mobile, embedded JVMs
*** maybe no metadata for further mem efficiency
*** tools eg proguard more effective when there are more methods than classes?
**** TODO need to test
** tighter interop
*** defs, defns are class fields and methods
*** direct call instead of RT.var + invoke
**** implies no dynamic NS: can we have both?
***** TODO maybe via class redefinition, testing needed
**** in lieu of IFn.invoke apply becomes a special
***** and will always need reflection
**** without intermediate objects, where will metadata go?
***** hybrid system involving a prelude/runtime + lifting?
* thoughts and questions
** scopes?
*** def doesn't have to be top level, so what happens when you close over it?
**** if an inner defn inside a let becomes a method, how is the let's scope visible?
***** explicit scope/env parameter to every method call
****** lots of copying, but maybe ok because of persistence
****** Java callers would have to provide one, seems like a drag
***** single global table
****** implies a runtime of at least a place to put such 'superglobals'
***** scopes hung on fields of ns/class
****** maybe ok if you can't define something in a namespace you're not currently in
****** makes the most sense ATM - situation mirrors what would happen to anonymous functions
******* they're lifted to gensym'd methods and substituted for calls to these methods
******* is there a performance benefit to methods calling eachother being in the same class
******* TODO research this
** macros?
*** need'em
*** run in the compiler's clojure a la cljs?
**** alternative requires eval
** generate java or bytecode?
*** know java, don't know bytecode or tools very well
*** java
**** compatible with many jvms regardless of bytecode format
***** e.g. dalvik
***** maybe a non-benefit depending on bytecode transform tools
***** TODO look into the state of these tools
**** have to appease the java compiler, which sucks
***** does every expression need to boil into an inner class that returns an Object?
****** if so this makes primitives across call boundaries a hairy proposition
****** code would be a mixture of object/primitive consumption, emission
******* need to trace primitive paths and emit code that touches appropriately
**** could generate 'idiomatic' java
***** definitely would require whole-program analysis and heuristics
***** AI project, out-of-scope sorry fogus :-(
**** can dynamically compile, load Java sources in-process via compiler tools
***** maybe enabling REPL, eval, dynamic definitions
***** probably slower than our own compile + load
****** TODO but by how much? test
**** compiler is easier to port, extend, debug for most people
***** even without source maps, but they'd be nice too
***** is portability a goal?
****** probably not
*** bytecode
**** normal $JVM_LANG choice
***** because most alt langs are dynamic and do runtime code loading
***** compile into bytecode and then classload
***** this is the thing that is slow
**** would need multiple backends to support different JVMs
***** how different would these backends be?
***** TODO survey different vm bytecode formats
** eval & repl?
*** if targeting java
**** tricky
**** involves compiling to java then using compiler tools to compile/load in-process
***** java compile step slows down dynamism, but maybe we don't care
***** complicates matter of namespaces
****** are loaded classes themselves dynamic enough to be OK namespaces?
****** TODO investigate class redefinition, see above
*** either way
**** what exactly needs to get compiled and/or loaded when an expression is evaluated?
***** depends mostly on
****** specifics of lambda lifting
****** dynamism of loaded classes
******* maybe possible w/ 3rd party introspection/debug tools
******* unlikely these tools work across jvms
******* TODO look into tools for redefining, adding methods to classes at runtime
* misc notes
** implement the special forms.
** apply in lieu of IFn.invoke is a special that necessitates reflection
** need static stubs for all the Namespace, Var stuff
*** depends on dynamic limits of regular classes, see above
*** also depends on how dynamic we want/need to be
**** esp. considering we may only complement clj as a prod not dev compiler
* anonymous functions need to be compiled into methods and hung off a class
** yes, lambda lifting, see above
* figure out a good intermediate representation that serves efficient name resolution
** see above notes re: static/prelude/hybrid resolution
** related to degree of runtime dynamism desired, itself related to state of various tools
* tricks
** turn statements into expressions with anon. inner classes
** free variables need to be static if captured by inner class
*** java copies the final, maybe a problem if it's big?
*** TODO compare with java8 lambdas
** void method calls can't be returned
*** also can't be assigned to anything
*** trick: put in statement context and wrap entire thing in an inner class
*** then return null explicitly at the end
**** see do-void
```

## Usage

    lein run resources/test.clj && javac test.java && java test

## License

Copyright © 2013 Alan Dipert

Distributed under the Eclipse Public License, the same as Clojure.
