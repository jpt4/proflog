(ns proflog.relational-arithmetic-upstream-test
  "Clojure translations of faster-minikanren/test-numbers.scm.

   These tests exercise both the translated numeric relations and the small
   relational interpreter from the upstream suite. The assertions prefer
   concrete semantic results where core.logic's reified variable names and
   constraint rendering are implementation-specific.

   Adapted from faster-minikanren under the MIT license:
   Copyright (c) 2015 William E. Byrd."
  (:refer-clojure :exclude [==])
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.core.logic :refer [!= == conde fresh lcons run run*]]
            [proflog.minikanren-constraints :as mkc]
            [proflog.relational-arithmetic :as arith]))

(declare eval-expo not-in-envo lookupo)

(defn number-primo
  [exp env val]
  (fresh [n]
    (== (list 'intexp n) exp)
    (== (list 'intval n) val)
    (not-in-envo 'numo env)))

(defn sub1-primo
  [exp env val]
  (fresh [e n n-1]
    (== (list 'sub1 e) exp)
    (== (list 'intval n-1) val)
    (not-in-envo 'sub1 env)
    (eval-expo e env (list 'intval n))
    (arith/minuso n '(1) n-1)))

(defn zero?-primo
  [exp env val]
  (fresh [e n]
    (== (list 'zero? e) exp)
    (conde
      [(arith/zeroo n) (== true val)]
      [(arith/poso n) (== false val)])
    (not-in-envo 'zero? env)
    (eval-expo e env (list 'intval n))))

(defn *-primo
  [exp env val]
  (fresh [e1 e2 n1 n2 n3]
    (== (list '* e1 e2) exp)
    (== (list 'intval n3) val)
    (not-in-envo '* env)
    (eval-expo e1 env (list 'intval n1))
    (eval-expo e2 env (list 'intval n2))
    (arith/*o n1 n2 n3)))

(defn if-primo
  [exp env val]
  (fresh [e1 e2 e3 t]
    (== (list 'if e1 e2 e3) exp)
    (not-in-envo 'if env)
    (eval-expo e1 env t)
    (conde
      [(== true t) (eval-expo e2 env val)]
      [(== false t) (eval-expo e3 env val)])))

(defn boolean-primo
  [exp env val]
  (conde
    [(== true exp) (== true val)]
    [(== false exp) (== false val)]))

(defn eval-expo
  [exp env val]
  (conde
    [(boolean-primo exp env val)]
    [(number-primo exp env val)]
    [(sub1-primo exp env val)]
    [(zero?-primo exp env val)]
    [(*-primo exp env val)]
    [(if-primo exp env val)]
    [(mkc/symbolo exp) (lookupo exp env val)]
    [(fresh [rator rand x body env* a]
       (== (list rator rand) exp)
       (eval-expo rator env (list 'closure x body env*))
       (eval-expo rand env a)
       (eval-expo body (lcons (lcons x a) env*) val))]
    [(fresh [x body]
       (== (list 'lambda (list x) body) exp)
       (mkc/symbolo x)
       (== (list 'closure x body env) val)
       (not-in-envo 'lambda env))]))

(defn not-in-envo
  [x env]
  (conde
    [(fresh [y v rest]
       (== (lcons (lcons y v) rest) env)
       (!= y x)
       (not-in-envo x rest))]
    [(== '() env)]))

(defn lookupo
  [x env t]
  (fresh [rest y v]
    (== (lcons (lcons y v) rest) env)
    (conde
      [(== y x) (== v t)]
      [(!= y x) (lookupo x rest t)])))

(def rel-fact5
  (let [n5 (arith/build-num 5)
        n1 (arith/build-num 1)]
    (list
      (list 'lambda '(f)
            (list (list 'f 'f) (list 'intexp n5)))
      (list 'lambda '(f)
            (list 'lambda '(n)
                  (list 'if
                        (list 'zero? 'n)
                        (list 'intexp n1)
                        (list '* 'n (list (list 'f 'f) (list 'sub1 'n)))))))))

(deftest upstream-multiplication-and-factor-tests
  (testing "test 1: 2 * 3 = 6"
    (is (= (list (arith/build-num 6))
           (let [n2 (arith/build-num 2)
                 n3 (arith/build-num 3)]
             (run* [q]
               (arith/*o n2 n3 q))))))
  (testing "test 2: factor pairs for 6"
    (is (= (list [(arith/build-num 1) (arith/build-num 6)]
                 [(arith/build-num 6) (arith/build-num 1)]
                 [(arith/build-num 2) (arith/build-num 3)]
                 [(arith/build-num 3) (arith/build-num 2)])
           (let [n6 (arith/build-num 6)]
             (run* [q]
               (fresh [n m]
                 (arith/*o n m n6)
                 (== [n m] q))))))))

(deftest upstream-sums-and-factors-tests
  (testing "sums: the first five generic pluso answers match the upstream shape"
    (let [answers (run 5 [q]
                    (fresh [x y z]
                      (arith/pluso x y z)
                      (== [x y z] q)))]
      (is (= 5 (count answers)))
      (is (= [(arith/build-num 1)
              (arith/build-num 1)
              (arith/build-num 2)]
             (nth answers 2)))
      (is (= [(arith/build-num 1)
              (arith/build-num 3)
              (arith/build-num 4)]
             (nth answers 4)))))
  (testing "factors: all factor pairs for 24 are found"
    (let [n24 (arith/build-num 24)]
      (is (= (list [[(arith/build-num 1) n24 n24]]
                   [[n24 (arith/build-num 1) n24]]
                   [[(arith/build-num 2) (arith/build-num 12) n24]]
                   [[(arith/build-num 4) (arith/build-num 6) n24]]
                   [[(arith/build-num 8) (arith/build-num 3) n24]]
                   [[(arith/build-num 3) (arith/build-num 8) n24]]
                   [[(arith/build-num 6) (arith/build-num 4) n24]]
                   [[(arith/build-num 12) (arith/build-num 2) n24]])
             (mapv vector
                   (run* [q]
                     (fresh [x y]
                       (arith/*o x y n24)
                       (== [x y n24] q)))))))))

(deftest upstream-absento-push-down-problems
  (doseq [[label goal]
          [["push-down problems 2"
            (fn [q]
              (fresh [x a d]
                (mkc/absento 'intval x)
                (== 'intval a)
                (== (lcons a d) x)))]
           ["push-down problems 3"
            (fn [q]
              (fresh [x a d]
                (== (lcons a d) x)
                (mkc/absento 'intval x)
                (== 'intval a)))]
           ["push-down problems 4"
            (fn [q]
              (fresh [x a d]
                (== (lcons a d) x)
                (== 'intval a)
                (mkc/absento 'intval x)))]
           ["push-down problems 6"
            (fn [q]
              (fresh [x a d]
                (== 'intval a)
                (== (lcons a d) x)
                (mkc/absento 'intval x)))]
           ["push-down problems 1"
            (fn [q]
              (fresh [x a d]
                (mkc/absento 'intval x)
                (== (lcons a d) x)
                (== 'intval a)))]
           ["push-down problems 5"
            (fn [q]
              (fresh [x a d]
                (== 'intval a)
                (mkc/absento 'intval x)
                (== (lcons a d) x)))]]]
    (testing label
      (is (= '()
             (run* [q]
               (goal q)))))))

(deftest upstream-small-interpreter-forward-tests
  (testing "zero?"
    (let [n1 (arith/build-num 1)]
      (is (= '(true)
             (run 1 [q]
               (eval-expo (list 'zero? (list 'sub1 (list 'intexp n1)))
                          '()
                          q))))))
  (testing "*"
    (let [n2 (arith/build-num 2)
          n3 (arith/build-num 3)
          n6 (arith/build-num 6)]
      (is (= '(:ok)
             (run 1 [q]
               (eval-expo (list '* (list 'intexp n3) (list 'intexp n2))
                          '()
                          (list 'intval n6))
               (== q :ok))))))
  (testing "rel-fact5"
    (let [n120 (arith/build-num 120)]
      (is (= (list (list 'intval n120))
             (run* [q]
               (eval-expo rel-fact5 '() q)))))))

(deftest upstream-small-interpreter-backward-sub1-tests
  (testing "sub1"
    (let [n6 (arith/build-num 6)
          n7 (arith/build-num 7)]
      (is (= (list (list 'sub1 (list 'intexp n7)))
             (run 1 [q]
               (eval-expo q '() (list 'intval n6))
               (== (list 'sub1 (list 'intexp n7)) q))))))
  (testing "sub1 bigger WAIT a minute"
    (let [n6 (arith/build-num 6)
          n8 (arith/build-num 8)]
      (is (= (list (list 'sub1 (list 'sub1 (list 'intexp n8))))
             (run 1 [q]
               (eval-expo q '() (list 'intval n6))
               (== (list 'sub1 (list 'sub1 (list 'intexp n8))) q))))))
  (testing "sub1 biggest WAIT a minute"
    (let [n6 (arith/build-num 6)
          n9 (arith/build-num 9)]
      (is (= (list (list 'sub1 (list 'sub1 (list 'sub1 (list 'intexp n9)))))
             (run 1 [q]
               (eval-expo q '() (list 'intval n6))
               (== (list 'sub1 (list 'sub1 (list 'sub1 (list 'intexp n9)))) q)))))))

(deftest upstream-lots-of-programs-to-make-a-6-test
  (let [n1 (arith/build-num 1)
        n2 (arith/build-num 2)
        n3 (arith/build-num 3)
        n6 (arith/build-num 6)
        n7 (arith/build-num 7)
        n8 (arith/build-num 8)
        answers (run 12 [q]
                  (eval-expo q '() (list 'intval n6)))
        expected-ground-programs
        [(list 'intexp n6)
         (list 'sub1 (list 'intexp n7))
         (list '* (list 'intexp n1) (list 'intexp n6))
         (list '* (list 'intexp n6) (list 'intexp n1))
         (list 'if true (list 'intexp n6) '_0)
         (list '* (list 'intexp n2) (list 'intexp n3))
         (list 'if false '_0 (list 'intexp n6))
         (list 'sub1 (list '* (list 'intexp n1) (list 'intexp n7)))
         (list 'sub1 (list '* (list 'intexp n7) (list 'intexp n1)))
         (list 'sub1 (list 'sub1 (list 'intexp n8)))
         (list 'sub1 (list 'if true (list 'intexp n7) '_0))]]
    (is (= 12 (count answers)))
    (doseq [program expected-ground-programs]
      (is (some #{program} answers)))
    (is (some #(and (seq? %) (some #{':-} %)) answers))))

(deftest upstream-rel-fact5-backwards-test
  (let [n5 (arith/build-num 5)
        n1 (arith/build-num 1)
        n120 (arith/build-num 120)
        program (list
                  (list 'lambda '(f)
                        (list (list 'f 'f) (list 'intexp n5)))
                  (list 'lambda '(f)
                        (list 'lambda '(n)
                              (list 'if
                                    (list 'zero? 'n)
                                    (list 'intexp n1)
                                    (list '* 'n (list (list 'f 'f) (list 'sub1 'n)))))))]
    (is (= '(f)
           (run 1 [q]
             (eval-expo
               (list
                 (list 'lambda '(f)
                       (list (list 'f q) (list 'intexp n5)))
                 (second program))
               '()
               (list 'intval n120)))))))
