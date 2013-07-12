(ns tailrecursion.stasis
  (:require [clojure.tools.analyzer        :as ana]
            [clojure.java.io               :as io]
            [clojure.string                :as str]
            [cljs.analyzer                 :refer [forms-seq]]
            [clojure.tools.namespace.parse :refer [comment? ns-decl?]]
            [clojure.repl                  :refer [source]]
            [clojure.walk                  :refer [macroexpand-all]]
            [alandipert.interpol8          :refer [interpolating]])
  (:import clojure.lang.Compiler))

(def ^:dynamic *java-out*)

(defrecord NSClass [ns                  ;ns-sym
                    java-package        ;package string
                    class-name          ;class-name string
                    imports             ;ns-sym => NSClass
                    variables           ;sym => src
                    methods             ;sym => src
                    ])

(defn nsclass [ns java-package class-name]
  (NSClass. ns java-package class-name {} {} {}))

(declare compile-expr)

(defmulti compile-op (fn [nsc self env [op & args]] op))

(defmethod compile-op 'do [nsc self env [_ & exprs]]
  (let [m (gensym)
        [body ret] ((juxt butlast last) (map (partial compile-expr nsc self env) exprs))]
    (interpolating
     "(new Object() {
        public Object #{m}() {
          #{(str/join \";\" body)}
          return #{ret};
        }
      }).#{m}();")))

(defn do-void [& strs]
  (let [m (gensym)]
    (interpolating
     "(new Object() {
        public Object #{m}() {
          #{(apply str strs)};
          return null;}}).#{m}()")))

(defmethod compile-op 'println [nsc self env [_ & exprs]]
  ;; punting with our own println. really, we need to implement all
  ;; specials and our own apply, binding forms so we can compile
  ;; clojure.core statically
  (do-void "System.out.println("
           (str/join ", " (map (partial compile-expr nsc self env) exprs))
           ")"))

(defmulti compile-literal class)
(defmethod compile-literal String [x] (pr-str x))

(defn compile-expr [nsc self env form]
  (if (seq? form)
    (compile-op nsc self env form)
    (compile-literal form)))

(defn add-method [nsc def]
  (comment todo))

(defn add-main [nsc def]
  (let [[_ _ [_ [[_ argname] & body]]] def
        argv (munge argname)
        env #{argv}]
    (update-in nsc [:methods] assoc '-main
      (interpolating
       "public static void main(String... #{argv}) {
         #{(compile-expr nsc '-main env (cons 'do body))}
       }"))))

(defn add-def [nsc def]
  (let [[_ name v] def]
    (if (and (seq? v) (= (first v) 'fn*))
      (if (= name '-main) (add-main nsc def) (throw (Exception.)))
      (comment add-variable))))

(defn compile-file [file]
  (let [[[_ ns] & defs] (filter (complement comment?) (forms-seq file))
        [package class] ((juxt butlast last) (.split (munge (name ns)) "\\."))
        nsc (nsclass ns package class)]
    (reduce add-def nsc (map macroexpand-all defs))))

(defn emit [nsc]
  (let [package-path (if (:java-package nsc) (.split (:java-package nsc) "\\."))
        out-dir (apply io/file *java-out* package-path)
        methods (str/join \newline (vals (:methods nsc)))]
    (spit (io/file out-dir (str (:class-name nsc) ".java"))
          (interpolating
           "public class #{(:class-name nsc)} {
              #{methods}
            }"))))

(defn -main [& files]
  (binding [*java-out* (System/getProperty "user.dir")]
    (doseq [f files]
      (println (str "Compiling " f))
      (emit (compile-file f)))))
