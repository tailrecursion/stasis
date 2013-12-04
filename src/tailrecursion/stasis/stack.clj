(ns tailrecursion.stasis.stack
  (:require [clojure.core.logic :refer [run* run conde conda == conso lvaro defna
                                        defne matche project fresh featurec
                                        unify membero]]
            [clojure.core.logic.fd :as fd]
            [clojure.core.logic.protocols :as cp]
            [clojure.pprint :refer [pprint]]
            [clojure.repl :refer :all])
  (:refer-clojure :exclude [compile ==]))

;;; stack machine

(defmulti evop (fn [stack [op arg]] op))

(defmethod evop 'push [stack [_ val]] (conj stack val))

(defmethod evop 'drop [stack _] (pop stack))

(defmethod evop 'dup [stack _] (conj stack (peek stack)))

(defmethod evop 'roll [stack [_ n]]
  (let [top (subvec stack (- (count stack) n))
        bottom (subvec stack 0 (- (count stack) n))]
    (into bottom (conj (subvec top 1) (nth top 0)))))

(defmethod evop :default [stack [op _]]
  (conj (pop (pop stack)) (apply (resolve op) [(peek (pop stack)) (peek stack)])))

(defn exec [stack ops & {:keys [iter]}]
  ((if iter reductions reduce) evop stack ops))

;;; compiler

(defne shuffleo [env bindings out]
  ([[x]      [x x] '[[dup]]])
  ([[x y]    [y x] '[[roll 2]]])
  ([bindings env   '[]]))

(defn shuffle [env bindings]
  (first (run 1 [q] (shuffleo env bindings q))))

(def listy? (partial instance? clojure.lang.ISeq))

(defn compile
  ([expr] (compile [] expr))
  ([env expr]
     (let [[op & args] expr]
       (if (listy? op)
         (into (mapv #(vector 'push %) args) (compile env op))
         (case op
           'fn (let [[bindings body] args]
                 (compile bindings body))
           (into (shuffle env args) [[op]]))))))

(comment
  (compile '(fn [x] (* x x)))
  ;=> [[dup] [*]]

  (compile '((fn [x] (* x x)) 2))
  ;=> [[push 2] [dup] [*]]

  (compile '((fn [x y] (- y x)) 5 10))
  ;=> [[push 5] [push 10] [roll 2] [-]]

  (exec [] (compile '((fn [x y] (- y x)) 5 10)) :iter true)
  ;=> ([] [5] [5 10] [10 5] [5])

  (exec [] (compile '((fn [x] (* x x)) 5)) :iter true)
  ;=> ([] [5] [5 5] [25])
)
