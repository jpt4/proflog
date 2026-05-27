(ns proflog.pelletier-test
  (:require [clojure.test :refer [deftest is testing]]
            [proflog.ast :as ast]
            [proflog.kernel :as kernel]
            [proflog.normalize :as normalize]))

;; Upstream source of record:
;; https://github.com/namin/leanTAP/blob/master/cljtap/test/cljtap/test/alphaleantap.clj
;;
;; The formulas below are proved as ordinary closed-tableau obligations:
;; conjoin the upstream axioms, negate the theorem, normalize to NNF, and ask
;; the greenfield kernel to close that pure first-order branch. No Proflog
;; clauses or theorem-specific overlays are involved.

(def status-values
  #{:ported-passing
    :ported-too-slow
    :not-yet-ported
    :requires-kernel-work})

(defn term
  [sym]
  (ast/app-term sym))

(defn v
  [nom]
  (ast/var-term nom))

(defn pred
  [relation & args]
  (ast/pos-lit (apply ast/app-term relation args)))

(defn not*
  [formula]
  (ast/not-form formula))

(defn and2
  [left right]
  (ast/and-form left right))

(defn or2
  [left right]
  (ast/or-form left right))

(defn implies
  [antecedent consequent]
  (ast/implies-form antecedent consequent))

(defn iff
  [left right]
  (and2 (implies left right)
        (implies right left)))

(defn forall
  [nom body]
  (ast/forall-form nom body))

(defn exists
  [nom body]
  (ast/exists-form nom body))

(defn conjoin
  [formulas]
  (reduce and2 formulas))

(defn theorem-branch
  [{:keys [axioms theorem]}]
  (normalize/to-nnf
    (conjoin (concat axioms [(not* theorem)]))))

(defn proposition
  [sym]
  (pred sym))

(def p (proposition 'p))
(def q (proposition 'q))
(def r (proposition 'r))
(def s (proposition 's))

(defn empty-theorem
  [theorem]
  {:axioms []
   :theorem theorem})

(defn problem-1 []
  (empty-theorem
    (iff (implies p q)
         (implies (not* q) (not* p)))))

(defn problem-2 []
  (empty-theorem
    (implies (not* (not* p)) p)))

(defn problem-3 []
  (empty-theorem
    (implies (not* (implies p q))
             (implies q p))))

(defn problem-4 []
  (empty-theorem
    (iff (implies (not* p) q)
         (implies (not* q) p))))

(defn problem-5 []
  (empty-theorem
    (implies (implies (or2 p q)
                      (or2 p r))
             (or2 p (implies q r)))))

(defn problem-6 []
  (empty-theorem
    (or2 p (not* p))))

(defn problem-7 []
  (empty-theorem
    (or2 p (not* (not* (not* p))))))

(defn problem-8 []
  (empty-theorem
    (implies (implies (implies p q) p)
             p)))

(defn problem-9 []
  (empty-theorem
    (implies
      (and2 (or2 p q)
            (and2 (or2 (not* p) q)
                  (or2 p (not* q))))
      (not* (or2 (not* p) (not* q))))))

(defn problem-10 []
  {:axioms [(implies q r)
            (implies r (and2 p q))
            (implies p (or2 q r))]
   :theorem (iff p q)})

(defn problem-11 []
  (empty-theorem
    (iff p p)))

(defn problem-12 []
  (empty-theorem
    (iff (iff (iff p q) r)
         (iff p (iff q r)))))

(defn problem-13 []
  (empty-theorem
    (iff (or2 p (and2 q r))
         (and2 (or2 p q)
               (or2 p r)))))

(defn problem-14 []
  (empty-theorem
    (iff (iff p q)
         (and2 (or2 q (not* p))
               (or2 (not* q) p)))))

(defn problem-15 []
  (empty-theorem
    (iff (implies p q)
         (or2 (not* p) q))))

(defn problem-16 []
  (empty-theorem
    (or2 (implies p q)
         (implies q p))))

(defn problem-17 []
  (empty-theorem
    (iff (implies (and2 p (implies q r)) s)
         (and2 (or2 (not* p) (or2 q s))
               (or2 (not* p) (or2 (not* r) s))))))

(defn problem-18 []
  (ast/nom y x
    (empty-theorem
      (exists y
              (forall x
                      (implies (pred 'f (v y))
                               (pred 'f (v x))))))))

(defn problem-19 []
  (ast/nom x y z
    (empty-theorem
      (exists x
              (forall y
                      (forall z
                              (implies
                                (implies (pred 'p (v y))
                                         (pred 'q (v z)))
                                (implies (pred 'p (v x))
                                         (pred 'q (v x))))))))))

(defn problem-20 []
  (ast/nom x y z w x2 y2 z2
    (empty-theorem
      (forall x
              (forall y
                      (exists z
                              (forall w
                                      (implies
                                        (implies
                                          (and2 (pred 'p (v x))
                                                (pred 'q (v y)))
                                          (and2 (pred 'r (v z))
                                                (pred 's (v w))))
                                        (exists x2
                                                (exists y2
                                                        (implies
                                                          (and2 (pred 'p (v x2))
                                                                (pred 'q (v y2)))
                                                          (exists z2
                                                                  (pred 'r (v z2))))))))))))))

(defn problem-21 []
  (ast/nom ax1 ax2 tx
    {:axioms [(exists ax1
                      (implies p
                               (pred 'f (v ax1))))
              (exists ax2
                      (implies (pred 'f (v ax2))
                               p))]
     :theorem (exists tx
                      (iff p
                           (pred 'f (v tx))))}))

(defn problem-22 []
  (ast/nom x y
    (empty-theorem
      (implies (forall x
                       (iff p
                            (pred 'f (v x))))
               (iff p
                    (forall y
                            (pred 'f (v y))))))))

(defn problem-23 []
  (ast/nom x y
    (empty-theorem
      (iff (forall x
                   (or2 p
                        (pred 'f (v x))))
           (or2 p
                (forall y
                        (pred 'f (v y))))))))

(defn problem-24 []
  (ast/nom x1 x2 x3 x4 x5 x6
    {:axioms [(not* (exists x1
                            (and2 (pred 's (v x1))
                                  (pred 'q (v x1)))))
              (forall x2
                      (implies (pred 'p (v x2))
                               (or2 (pred 'q (v x2))
                                    (pred 'r (v x2)))))
              (not* (implies (exists x3
                                      (pred 'p (v x3)))
                             (exists x4
                                     (pred 'q (v x4)))))
              (forall x5
                      (implies (or2 (pred 'q (v x5))
                                    (pred 'r (v x5)))
                               (pred 's (v x5))))]
     :theorem (exists x6
                      (and2 (pred 'p (v x6))
                            (pred 'r (v x6))))}))

(defn problem-25 []
  (ast/nom x1 x2 x3 x4 x5 x6
    {:axioms [(exists x1
                      (pred 'p (v x1)))
              (forall x2
                      (implies (pred 'f (v x2))
                               (and2 (not* (pred 'g (v x2)))
                                     (pred 'r (v x2)))))
              (forall x3
                      (implies (pred 'p (v x3))
                               (and2 (pred 'g (v x3))
                                     (pred 'f (v x3)))))
              (or2 (forall x4
                           (implies (pred 'p (v x4))
                                    (pred 'r (v x4))))
                   (exists x5
                           (and2 (pred 'p (v x5))
                                 (pred 'r (v x5)))))]
     :theorem (exists x6
                      (and2 (pred 'p (v x6))
                            (pred 'r (v x6))))}))

(defn problem-26 []
  (ast/nom x1 x2 x3 y3 x4 x5
    {:axioms [(iff (exists x1
                           (pred 'p (v x1)))
                   (exists x2
                           (pred 'q (v x2))))
              (forall x3
                      (forall y3
                              (implies (and2 (pred 'p (v x3))
                                             (pred 'q (v y3)))
                                       (iff (pred 'r (v x3))
                                            (pred 's (v y3))))))]
     :theorem (iff (forall x4
                           (implies (pred 'p (v x4))
                                    (pred 'r (v x4))))
                   (forall x5
                           (implies (pred 'q (v x5))
                                    (pred 's (v x5)))))}))

(defn problem-27 []
  (ast/nom x1 x2 x3 x4 x5 x6
    {:axioms [(exists x1
                      (and2 (pred 'f (v x1))
                            (not* (pred 'g (v x1)))))
              (forall x2
                      (implies (pred 'f (v x2))
                               (pred 'h (v x2))))
              (forall x3
                      (implies (and2 (pred 'j (v x3))
                                     (pred 'i (v x3)))
                               (pred 'f (v x3))))
              (implies (exists x4
                               (and2 (pred 'h (v x4))
                                     (not* (pred 'g (v x4)))))
                       (forall x5
                               (implies (pred 'i (v x5))
                                        (not* (pred 'h (v x5))))))]
     :theorem (forall x6
                      (implies (pred 'j (v x6))
                               (not* (pred 'i (v x6)))))}))

(defn problem-28 []
  (ast/nom x1 x2 x3 x4 x5 x6 x7
    {:axioms [(forall x1
                      (implies (pred 'p (v x1))
                               (forall x2
                                       (pred 'q (v x2)))))
              (implies (forall x3
                               (or2 (pred 'q (v x3))
                                    (pred 'r (v x3))))
                       (exists x4
                               (and2 (pred 'q (v x4))
                                     (pred 's (v x4)))))
              (implies (exists x5
                               (pred 's (v x5)))
                       (forall x6
                               (implies (pred 'f (v x6))
                                        (pred 'g (v x6)))))]
     :theorem (forall x7
                      (implies (and2 (pred 'p (v x7))
                                     (pred 'f (v x7)))
                               (pred 'g (v x7))))}))

(defn problem-29 []
  (ast/nom x1 x2 x3 x4 x5 y5
    {:axioms [(and2 (exists x1
                            (pred 'f (v x1)))
                    (exists x2
                            (pred 'g (v x2))))]
     :theorem (iff
                (and2
                  (forall x3
                          (implies (pred 'f (v x3))
                                   (pred 'h (v x3))))
                  (forall x4
                          (implies (pred 'g (v x4))
                                   (pred 'j (v x4)))))
                (forall x5
                        (forall y5
                                (implies (and2 (pred 'f (v x5))
                                               (pred 'g (v y5)))
                                         (and2 (pred 'h (v x5))
                                               (pred 'j (v y5)))))))}))

(defn problem-30 []
  (ast/nom x1 x2 x3
    {:axioms [(forall x1
                      (implies (or2 (pred 'f (v x1))
                                    (pred 'g (v x1)))
                               (not* (pred 'h (v x1)))))
              (forall x2
                      (implies (implies (pred 'g (v x2))
                                        (not* (pred 'i (v x2))))
                               (and2 (pred 'f (v x2))
                                     (pred 'h (v x2)))))]
     :theorem (forall x3
                      (pred 'i (v x3)))}))

(defn problem-31 []
  (ast/nom x1 x2 x3 x4
    {:axioms [(not* (exists x1
                            (and2 (pred 'f (v x1))
                                  (or2 (pred 'g (v x1))
                                       (pred 'h (v x1))))))
              (exists x2
                      (and2 (pred 'i (v x2))
                            (pred 'f (v x2))))
              (forall x3
                      (implies (not* (pred 'h (v x3)))
                               (pred 'j (v x3))))]
     :theorem (exists x4
                      (and2 (pred 'i (v x4))
                            (pred 'j (v x4))))}))

(defn problem-32 []
  (ast/nom x1 x2 x3 x4
    {:axioms [(forall x1
                      (implies (and2 (pred 'f (v x1))
                                     (or2 (pred 'g (v x1))
                                          (pred 'h (v x1))))
                               (pred 'i (v x1))))
              (forall x2
                      (implies (and2 (pred 'i (v x2))
                                     (pred 'h (v x2)))
                               (pred 'j (v x2))))
              (forall x3
                      (implies (pred 'k (v x3))
                               (pred 'h (v x3))))]
     :theorem (forall x4
                      (implies (and2 (pred 'f (v x4))
                                     (pred 'k (v x4)))
                               (pred 'j (v x4))))}))

(defn problem-33 []
  (ast/nom x1 x2
    (let [a (term 'a)
          b (term 'b)
          c (term 'c)]
      (empty-theorem
        (iff
          (forall x1
                  (implies (and2 (pred 'p a)
                                 (implies (pred 'p (v x1))
                                          (pred 'p b)))
                           (pred 'p c)))
          (forall x2
                  (and2
                    (or2 (not* (pred 'p a))
                         (or2 (pred 'p (v x2))
                              (pred 'p c)))
                    (or2 (not* (pred 'p a))
                         (or2 (not* (pred 'p b))
                              (pred 'p c))))))))))

(defn problem-34 []
  (ast/nom x1 y1 x2 y2 x3 y3 x4 y4
    (empty-theorem
      (iff
        (iff (exists x1
                     (forall y1
                             (iff (pred 'p (v x1))
                                  (pred 'p (v y1)))))
             (iff (exists x2
                          (pred 'q (v x2)))
                  (forall y2
                          (pred 'q (v y2)))))
        (iff (exists x3
                     (forall y3
                             (iff (pred 'q (v x3))
                                  (pred 'q (v y3)))))
             (iff (exists x4
                          (pred 'p (v x4)))
                  (forall y4
                          (pred 'p (v y4)))))))))

(defn problem-35 []
  (ast/nom x1 y1 x2 y2
    (empty-theorem
      (exists x1
              (exists y1
                      (implies (pred 'p (v x1) (v y1))
                               (forall x2
                                       (forall y2
                                               (pred 'p (v x2) (v y2))))))))))

(defn problem-36 []
  (ast/nom x1 y1 x2 y2 x3 y3 z3 x4 y4
    {:axioms [(forall x1
                      (exists y1
                              (pred 'f (v x1) (v y1))))
              (forall x2
                      (exists y2
                              (pred 'g (v x2) (v y2))))
              (forall x3
                      (forall y3
                              (implies (or2 (pred 'f (v x3) (v y3))
                                            (pred 'g (v x3) (v y3)))
                                       (forall z3
                                               (implies (or2 (pred 'f (v y3) (v z3))
                                                             (pred 'g (v y3) (v z3)))
                                                        (pred 'h (v x3) (v z3)))))))]
     :theorem (forall x4
                      (exists y4
                              (pred 'h (v x4) (v y4))))}))

(defn problem-37 []
  (ast/nom z1 w1 x1 y1 u1 x2 z2 y2 x3 y3 x4 x5 y5
    {:axioms [(forall z1
                      (exists w1
                              (forall x1
                                      (exists y1
                                              (and2
                                                (implies (pred 'p (v x1) (v z1))
                                                         (pred 'p (v y1) (v w1)))
                                                (and2
                                                  (pred 'p (v y1) (v z1))
                                                  (implies (pred 'p (v y1) (v w1))
                                                           (exists u1
                                                                   (pred 'q (v u1) (v w1))))))))))
              (forall x2
                      (forall z2
                              (implies (not* (pred 'p (v x2) (v z2)))
                                       (exists y2
                                               (pred 'q (v y2) (v z2))))))
              (implies (exists x3
                               (exists y3
                                       (pred 'q (v x3) (v y3))))
                       (forall x4
                               (pred 'r (v x4) (v x4))))]
     :theorem (forall x5
                      (exists y5
                              (pred 'r (v x5) (v y5))))}))

(defn problem-38 []
  (ast/nom x1 y1 z1 w1 x2 z2 w2 y2 z3 w3
    (let [a (term 'a)]
      (empty-theorem
        (iff
          (forall x1
                  (implies
                    (and2 (pred 'p a)
                          (implies (pred 'p (v x1))
                                   (exists y1
                                           (and2 (pred 'p (v y1))
                                                 (pred 'r (v x1) (v y1))))))
                    (exists z1
                            (exists w1
                                    (and2 (pred 'p (v z1))
                                          (and2 (pred 'r (v x1) (v w1))
                                                (pred 'r (v w1) (v z1))))))))
          (forall x2
                  (and2
                    (or2 (not* (pred 'p a))
                         (or2 (pred 'p (v x2))
                              (exists z2
                                      (exists w2
                                              (and2 (pred 'p (v z2))
                                                    (and2 (pred 'r (v x2) (v w2))
                                                          (pred 'r (v w2) (v z2))))))))
                    (or2 (not* (pred 'p a))
                         (or2 (not* (exists y2
                                             (and2 (pred 'p (v y2))
                                                   (pred 'r (v x2) (v y2)))))
                              (exists z3
                                      (exists w3
                                              (and2 (pred 'p (v z3))
                                                    (and2 (pred 'r (v x2) (v w3))
                                                          (pred 'r (v w3) (v z3)))))))))))))))

(defn problem-39 []
  (ast/nom x y
    (empty-theorem
      (not* (exists x
                    (forall y
                            (iff (pred 'f (v y) (v x))
                                 (not* (pred 'f (v y) (v y))))))))))

(defn problem-40 []
  (ast/nom y1 x1 x2 y2 z2
    (empty-theorem
      (implies (exists y1
                       (forall x1
                               (iff (pred 'f (v x1) (v y1))
                                    (pred 'f (v x1) (v x1)))))
               (not* (forall x2
                              (exists y2
                                      (forall z2
                                              (iff (pred 'f (v z2) (v y2))
                                                   (not* (pred 'f (v z2) (v x2))))))))))))

(defn problem-41 []
  (ast/nom z1 y1 x1 z2 x2
    {:axioms [(forall z1
                      (exists y1
                              (forall x1
                                      (iff (pred 'f (v x1) (v y1))
                                           (and2 (pred 'f (v x1) (v z1))
                                                 (not* (pred 'f (v x1) (v x1))))))))]
     :theorem (not* (exists z2
                            (forall x2
                                    (pred 'f (v x2) (v z2)))))}))

(defn problem-42 []
  (ast/nom y x z
    (empty-theorem
      (not* (exists y
                    (forall x
                            (iff (pred 'f (v x) (v y))
                                 (not* (exists z
                                               (and2 (pred 'f (v x) (v z))
                                                     (pred 'f (v z) (v x))))))))))))

(defn problem-43 []
  (ast/nom x1 y1 z1 x2 y2
    {:axioms [(forall x1
                      (forall y1
                              (iff (pred 'q (v x1) (v y1))
                                   (forall z1
                                           (iff (pred 'f (v z1) (v x1))
                                                (pred 'f (v z1) (v y1)))))))]
     :theorem (forall x2
                      (forall y2
                              (iff (pred 'q (v x2) (v y2))
                                   (pred 'q (v y2) (v x2)))))}))

(defn problem-44 []
  (ast/nom x1 y1 y2 x2 y3 x3
    {:axioms [(forall x1
                      (and2 (implies (pred 'f (v x1))
                                     (exists y1
                                             (and2 (pred 'g (v y1))
                                                   (pred 'h (v x1) (v y1)))))
                            (exists y2
                                    (and2 (pred 'g (v y2))
                                          (not* (pred 'h (v x1) (v y2)))))))
              (exists x2
                      (and2 (pred 'j (v x2))
                            (forall y3
                                    (implies (pred 'g (v y3))
                                             (pred 'h (v x2) (v y3))))))]
     :theorem (exists x3
                      (and2 (pred 'j (v x3))
                            (not* (pred 'f (v x3)))))}))

(defn problem-45 []
  (ast/nom x1 y1 y2 y3 x2 y4 y5 x3 y6
    {:axioms [(forall x1
                      (and2 (pred 'f (v x1))
                            (forall y1
                                    (implies
                                      (and2 (pred 'g (v y1))
                                            (implies (pred 'h (v x1) (v y1))
                                                     (pred 'j (v x1) (v y1))))
                                      (forall y2
                                              (and2 (pred 'g (v y2))
                                                    (implies (pred 'h (v x1) (v y2))
                                                             (pred 'k (v y2)))))))))
              (not* (exists y3
                            (and2 (pred 'l (v y3))
                                  (pred 'k (v y3)))))
              (exists x2
                      (and2
                        (and2 (pred 'f (v x2))
                              (forall y4
                                      (implies (pred 'h (v x2) (v y4))
                                               (pred 'l (v y4)))))
                        (forall y5
                                (and2 (pred 'g (v y5))
                                      (implies (pred 'h (v x2) (v y5))
                                               (pred 'j (v x2) (v y5)))))))]
     :theorem (exists x3
                      (and2 (pred 'f (v x3))
                            (not* (exists y6
                                          (and2 (pred 'g (v y6))
                                                (pred 'h (v x3) (v y6)))))))}))

(defn problem-46 []
  (ast/nom x1 y1 x2 x3 y2 x4 y3 x5
    {:axioms [(forall x1
                      (implies (and2 (pred 'f (v x1))
                                     (forall y1
                                             (implies (and2 (pred 'f (v y1))
                                                            (pred 'h (v y1) (v x1)))
                                                      (pred 'g (v y1)))))
                               (pred 'g (v x1))))
              (implies (exists x2
                               (and2 (pred 'f (v x2))
                                     (not* (pred 'g (v x2)))))
                       (exists x3
                               (and2 (pred 'f (v x3))
                                     (and2 (not* (pred 'g (v x3)))
                                           (forall y2
                                                   (implies (and2 (pred 'f (v y2))
                                                                  (not* (pred 'g (v y2))))
                                                            (pred 'j (v x3) (v y2))))))))
              (forall x4
                      (forall y3
                              (implies (and2 (pred 'f (v x4))
                                             (and2 (pred 'f (v y3))
                                                   (pred 'h (v x4) (v y3))))
                                       (not* (pred 'j (v y3) (v x4))))))]
     :theorem (forall x5
                      (implies (pred 'f (v x5))
                               (pred 'g (v x5))))}))

(def problem-catalog
  [{:id 1 :status :ported-passing :builder problem-1}
   {:id 2 :status :ported-passing :builder problem-2}
   {:id 3 :status :ported-passing :builder problem-3}
   {:id 4 :status :ported-passing :builder problem-4}
   {:id 5 :status :ported-passing :builder problem-5}
   {:id 6 :status :ported-passing :builder problem-6}
   {:id 7 :status :ported-passing :builder problem-7}
   {:id 8 :status :ported-passing :builder problem-8}
   {:id 9 :status :ported-passing :builder problem-9}
   {:id 10 :status :ported-passing :builder problem-10}
   {:id 11 :status :ported-passing :builder problem-11}
   {:id 12 :status :ported-passing :builder problem-12}
   {:id 13 :status :ported-passing :builder problem-13}
   {:id 14 :status :ported-passing :builder problem-14}
   {:id 15 :status :ported-passing :builder problem-15}
   {:id 16 :status :ported-passing :builder problem-16}
   {:id 17 :status :ported-passing :builder problem-17}
   {:id 18 :status :ported-passing :builder problem-18}
   {:id 19 :status :ported-passing :builder problem-19}
   {:id 20 :status :ported-passing :builder problem-20}
   {:id 21 :status :ported-passing :builder problem-21}
   {:id 22 :status :ported-passing :builder problem-22}
   {:id 23 :status :ported-passing :builder problem-23}
   {:id 24 :status :ported-passing :builder problem-24}
   {:id 25 :status :ported-passing :builder problem-25}
   {:id 26 :status :ported-passing :builder problem-26}
   {:id 27 :status :ported-passing :builder problem-27}
   {:id 28 :status :ported-passing :builder problem-28}
   {:id 29 :status :ported-passing :builder problem-29}
   {:id 30 :status :ported-passing :builder problem-30}
   {:id 31 :status :ported-passing :builder problem-31}
   {:id 32 :status :ported-passing :builder problem-32}
   {:id 33 :status :ported-passing :builder problem-33}
   {:id 34 :status :ported-passing :builder problem-34}
   {:id 35 :status :ported-passing :builder problem-35}
   {:id 36 :status :ported-passing :builder problem-36}
   {:id 37 :status :ported-passing :builder problem-37}
   {:id 38 :status :ported-passing :builder problem-38}
   {:id 39 :status :ported-passing :builder problem-39}
   {:id 40 :status :ported-passing :builder problem-40}
   {:id 41 :status :ported-passing :builder problem-41}
   {:id 42 :status :ported-passing :builder problem-42}
   {:id 43 :status :ported-passing :builder problem-43}
   {:id 44 :status :ported-passing :builder problem-44}
   {:id 45 :status :ported-passing :builder problem-45}
   {:id 46 :status :ported-passing :builder problem-46}])

(def problem-by-id
  (into {} (map (juxt :id identity) problem-catalog)))

(def prompt-passing-ids
  [1 2 3 4 5 6 7 8 9 11 12 13 14 15 16 18 19 20
   21 22 23 24 25 26 27 28 29 30 31 32 34 35 36 37
   38 39 41 43 44 45 46])

(def slow-passing-ids
  [10 17 33 40 42])

(def ported-too-slow-ids
  [])

(def requires-kernel-work-ids
  [])

(def ported-passing-ids
  (into prompt-passing-ids slow-passing-ids))

(defn proof-for
  [id]
  (let [{:keys [builder]} (problem-by-id id)]
    (kernel/prove (theorem-branch (builder)) 1)))

(deftest ^:pelletier-catalog catalog-covers-upstream-pelletier-problems
  (testing "every upstream Pelletier problem 1 through 46 has an explicit local status"
    (is (= (set (range 1 47))
           (set (map :id problem-catalog))))
    (is (every? status-values
                (map :status problem-catalog)))))

(deftest ^{:pelletier-prompt true
           :pelletier-passing true}
  legacy-mirrored-pelletier-slice-closes
  (testing "the greenfield kernel proves the Pelletier slice already mirrored by the legacy tests"
    (doseq [id [1 2 18]]
      (is (seq (proof-for id))
          (str "Pelletier Problem " id " should close")))))

(deftest ^{:pelletier-prompt true
           :pelletier-passing true}
  prompt-passing-pelletier-tranche-closes
  (testing "the prompt subset of the initial ADR-0022 tranche closes in the pure kernel"
    (doseq [id prompt-passing-ids]
      (is (seq (proof-for id))
          (str "Pelletier Problem " id " should close")))))

(deftest ^:pelletier-passing slow-passing-pelletier-problems-close
  (testing "ported slow problems close, but remain outside the prompt selector"
    (doseq [id slow-passing-ids]
      (is (seq (proof-for id))
          (str "Pelletier Problem " id " should close")))))

(deftest ^:pelletier-exploratory nonpassing-ported-problems-are-classified
  (testing "ported formulas that are not committed as passing regressions stay visible"
    (doseq [id ported-too-slow-ids
            :let [{:keys [status builder]} (problem-by-id id)]]
      (is (= :ported-too-slow status)
          (str "Pelletier Problem " id " should be classified as too slow"))
      (is builder
          (str "Pelletier Problem " id " should keep its ported builder"))))
  (testing "no ported propositional problem currently requires kernel work"
    (is (empty? requires-kernel-work-ids))))
