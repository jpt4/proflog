(ns proflog.nim-synthesis-test
  (:require [clojure.test :refer [deftest is testing]]
            [proflog.ast :as ast]
            [proflog.language :as language]
            [proflog.query :as query]))

(def nim-synthesis-language
  (language/language
    {:constants ['zero]
     :functions {'s 1}
     :relations {'win 1}}))

(defn numeral
  [n]
  (if (zero? n)
    (ast/app-term 'zero)
    (ast/app-term 's (numeral (dec n)))))

(defn p2-program
  []
  (ast/nom x y
    (language/compile-program
      nim-synthesis-language
      [(ast/clause 'win [x]
                   (ast/exists-form y
                                    (ast/and-form
                                      (ast/or-form
                                        (ast/eq-lit (ast/var-term x)
                                                    (ast/app-term 's (ast/var-term y)))
                                        (ast/eq-lit (ast/var-term x)
                                                    (ast/app-term 's
                                                                  (ast/app-term 's
                                                                                (ast/var-term y)))))
                                      (ast/neg-lit (ast/app-term 'win (ast/var-term y))))))])))

(defn succeeds-directly?
  [program formula fuel]
  (seq (query/query-succeeds program formula 1 fuel)))

(defn fails-directly?
  [program formula fuel]
  (seq (query/query-fails program formula 1 fuel)))

(defn winning-move-formula
  [position witness]
  (ast/and-form
    (ast/or-form
      (ast/eq-lit position (ast/app-term 's witness))
      (ast/eq-lit position (ast/app-term 's (ast/app-term 's witness))))
    (ast/neg-lit (ast/app-term 'win witness))))

(deftest recursive-nim-winning-move-witnesses-succeed
  (testing "winning positions admit the expected losing successor as a witness"
    (let [program (p2-program)]
      (is (succeeds-directly?
            program
            (winning-move-formula (numeral 1) (numeral 0))
            8))
      (is (succeeds-directly?
            program
            (winning-move-formula (numeral 2) (numeral 0))
            8))
      (is (succeeds-directly?
            program
            (winning-move-formula (numeral 4) (numeral 3))
            16))
      (is (succeeds-directly?
            program
            (winning-move-formula (numeral 5) (numeral 3))
            16)))))

(deftest recursive-nim-wrong-move-witnesses-fail
  (testing "non-winning or impossible successor candidates are refuted directly"
    (let [program (p2-program)]
      (is (fails-directly?
            program
            (winning-move-formula (numeral 1) (numeral 1))
            8))
      (is (fails-directly?
            program
            (winning-move-formula (numeral 4) (numeral 2))
            8)))))

(deftest recursive-nim-deeper-ground-positions-follow-the-pattern
  (testing "deeper ground Nim positions stay semantically correct in the extended suite"
    (let [program (p2-program)]
      (is (succeeds-directly?
            program
            (ast/pos-lit (ast/app-term 'win (numeral 4)))
            16))
      (is (succeeds-directly?
            program
            (ast/pos-lit (ast/app-term 'win (numeral 5)))
            16)))))
