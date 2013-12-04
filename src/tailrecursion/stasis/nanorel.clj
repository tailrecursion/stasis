(ns tailrecursion.stasis.nanorel
  (:require [clojure.core.logic :refer [run* run conde conda == conso lvaro defna
                                        defne matche project fresh featurec
                                        unify membero firsto resto] :as cl]
            [clojure.core.logic.fd :as fd]
            [clojure.core.logic.protocols :as cp]
            [clojure.pprint :refer [pprint]]
            [clojure.repl :refer :all]
            [alandipert.interpol8 :refer [interpolating]])
  (:refer-clojure :exclude [==]))

;;; analysis

(defn classo [x klass]
  (project [x] (== (class x) klass)))

(defn instanceo [x k]
  (project [x k] (== (instance? k x) true)))

(defn analyze-scalar [x a]
  (fresh [klass val]
    (classo x klass)
    (project [x] (== val x))
    (conda
      [(membero klass [Long Double])
       (== {:op :number :val val} a)]
      [(== klass String)
       (== {:op :string :val val} a)]
      [(== klass Boolean)
       (== {:op :boolean :val val} a)]
      [(== klass nil)
       (== {:op :nil :val nil} a)])))

(defn ntho [n* l a]
  (fresh [n n2 decn more]
    (fd/in n n2 (fd/interval 0 n*))
    (conda
      [(fd/> n 0)
       (fd/- n 1 n2)
       (resto l more)
       (ntho (dec n*) more a)]
      [(firsto l a)])))

(declare analyze*)

(defn analyze-if [x a]
  (conda
    [(instanceo x clojure.lang.ISeq)
     (conda
       [(firsto x 'if)
        (fresh [test  then  else
                atest athen aelse]
          (ntho 1 x test)
          (ntho 2 x then)
          (conda [(ntho 3 x else)] [(== else nil)])
          (analyze* test atest)
          (analyze* then athen)
          (analyze* else aelse)
          (== {:op :if :test atest :then athen :else aelse} a))])]))

(defn analyze* [x a]
  (conda
    [(analyze-scalar x a)]
    [(analyze-if x a)]
    [(project [x]
       (throw (RuntimeException. (str "Analyze: unrecognized form: " (pr-str x)))))]))

(defn analyze [expr]
  (first (run 1 [q] (analyze* expr q))))

;;; simple optimizations

(declare optimize*)

(defn optimize-if-test-falsy [ast o]
  (project [ast]
    (conda
      [(fresh [v]
         (featurec ast {:test {:val v}})
         (membero v [nil false]))
       (fresh [else]
         (featurec ast {:else else})
         (optimize* else o))])))

(defn optimize-if-test-true [ast o]
  (project [ast]
    (conda
      [(featurec ast {:test {:val true}})
       (fresh [then]
         (featurec ast {:then then})
         (optimize* then o))])))

(defn optimize* [ast o]
  (conda
   [(optimize-if-test-falsy ast o)]
   [(optimize-if-test-true ast o)]
   [(== ast o)]))

(defn optimize [ast]
  (run* [q] (optimize* ast q)))

;;; emission

(declare emit*)

(defn emit-nil [ast o]
  (conda [(featurec ast {:op :nil})
          (== o "null")]))

(defn emit-scalar [ast o]
  (project [ast]
    (fresh [val]
      (conda
        [(emit-nil ast o)]
        [(featurec ast {:val val})
         (project [val]
           (== o (pr-str val)))]))))

(defn truth [x]
  (interpolating
   "((function(x){return (x != null && x !== false);})(#{x}))"))

(defn emit-if [ast o]
  (project [ast]
    (fresh [test-ast test-o
            then-ast then-o
            else-ast else-o]
      (conda [(featurec ast {:test test-ast :then then-ast :else else-ast})
              (emit* test-ast test-o)
              (emit* then-ast then-o)
              (emit* else-ast else-o)
              (project [test-o then-o else-o]
                (== o (interpolating
                       "(#{(truth test-o)}?#{then-o}:#{else-o})")))]))))

(defn emit* [ast o]
  (conda
    [(emit-scalar ast o)]
    [(emit-if ast o)]
    [(project [ast]
       (throw (RuntimeException. (str "Emit: unrecognized AST: " (pr-str ast)))))]))

(defn emit [ast]
  (first (run 1 [q] (emit* ast q))))

(comment
  (analyze 123)
  ;; {:op :number, :val 123}

  (analyze '(if true 1 "never"))
  ;; {:op :if,
  ;;  :test {:op :boolean, :val true},
  ;;  :then {:op :number, :val 1},
  ;;  :else {:op :string, :val "never"}}

  (optimize (analyze '(if true 1 "never")))
  ;; ({:op :number, :val 1})

  (analyze '(if nil "no" (if true (if false 1 2))))
  ;; {:op :if,
  ;;  :test {:op :nil, :val nil},
  ;;  :then {:op :string, :val "no"},
  ;;  :else
  ;;  {:op :if,
  ;;   :test {:op :boolean, :val true},
  ;;   :then
  ;;   {:op :if,
  ;;    :test {:op :boolean, :val false},
  ;;    :then {:op :number, :val 1},
  ;;    :else {:op :number, :val 2}},
  ;;   :else {:op :nil, :val nil}}}

  (optimize (analyze '(if nil "no" (if true (if false 1 2)))))
  ;; ({:op :number, :val 2})

  (emit (first (optimize (analyze '(if nil "no" (if true (if false 1 2)))))))
  ;; "2"

  (emit (analyze '(if true 1 2)))
  ;; "((function(x){return (x != null && x !== false);})(true))?1:2"

  (emit (first (optimize (analyze '(if true 1 2)))))
  ;; "1"
  )
