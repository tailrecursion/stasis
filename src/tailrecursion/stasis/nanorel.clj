;;;;;; Experimental analyze, optimize, emit via core.logic for Clojure -> JavaScript

;;; I think relational analyze and emit phases here are a bit
;;; indulgent, as they should never produce multiple outputs for the
;;; same input.  I coded them this way to familiarize myself with
;;; core.logic.  They are perfectly suited to being regular functions.

;;; While analysis and emission needn't be relational, I think the
;;; optimization process amenes itself to the tools and techniques of
;;; logic programming.  Unifying an instantiated AST with a pattern
;;; feels like a natural way to express the applicability of a rewrite
;;; and to perform the rewrite itself.

;;; Like any compiler's optimization pass, a relational approach boils
;;; down to a tree search over optimizations and their permutations
;;; and is still susceptible to divergence/cycles - the production of
;;; an infinite number of possible programs.  As opposed to how
;;; optimization passes are usually organized, though, with relations
;;; one has a model upon which to reason and debug at a higher level.

;;; The optimization pass may be significantly improved with the
;;; introduction of a representation that grouped programs into
;;; equivalence classes as described in [1].

;;; With this approach, optimization relations would unify not with
;;; particular ASTs but with "E-PEGS" representing the set of
;;; equivalent ASTs, and would thus be more general.  The property
;;; that multiple semantically equivalent ASTs converge to a single
;;; E-PEG is known in term rewriting as that of confluence. [2]

;;; I suspect the performance benefit of introducing an E-PEG
;;; representation is two-fold.

;;; First, the search for the most optimal program is likely to
;;; produce an acceptable result sooner, because there are fewer
;;; objects over which to search, as N ASTs were collapsed to 1 E-PEG
;;; during "peggification".  While the search space may still be
;;; infinite, the surface of it we are able to practically search
;;; grows manyfold.

;;; Second, relations over E-PEGs might be "tabled", or memoized.  In
;;; this way, work is only done when a genuinely distinct program must
;;; be compiled.  I imagine a smart compiler using these techniques
;;; to very efficiently incrementally compile programs after parts of
;;; them are modified.

;;; So, my current thoughts around how to most awesomely write a compiler are:

;;; 1. Write a function, 'analyze', that reads source code and returns AST.
;;; 2. Write a set of relations, 'peggify', that read AST and return E-PEG - an AST-like structure whose leaves may be multisets of programmatically equivalent AST.
;;; 3. Write a set of relations, 'optimize', that take E-PEG and return (possibly optimized) E-PEG.
;;; 4. Write a function, 'select', that takes optimized E-PEGs and ranks them by one or more program-global selection heuristics.
;;; 4. Write a function, 'emit', that takes the selected E-PEG and produces target code.

;;; 1. http://www.cs.cornell.edu/~ross/publications/eqsat/eqsat_tate_popl09.pdf
;;; 2. http://en.wikipedia.org/wiki/Confluence_(abstract_rewriting)

;;; Footnotes

;;; I see a strong connection between the ideas of "Equality
;;; Saturation" and those presented by John Backus in "From function
;;; level semantics to program transformation and optimization"
;;; http://dl.acm.org/citation.cfm?id=21859

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
