(ns proflog.relational-maps-probe-test
  (:refer-clojure :exclude [==])
  (:require [clojure.core.logic :refer [== fresh lcons run run*]]
            [clojure.test :refer [deftest is testing]]
            [proflog.relational-maps-probe :as rmap]))

(deftest association-list-lookup-supports-forward-and-reverse-use
  (testing "ground key lookup synthesizes the value"
    (is (= '(2)
           (run* [q]
             (rmap/alist-lookupo :b '([:a 1] [:b 2]) q)))))
  (testing "ground value lookup can synthesize the key"
    (is (= '(:b)
           (run* [q]
             (rmap/alist-lookupo q '([:a 1] [:b 2]) 2)))))
  (testing "lookup can synthesize a matching one-entry alist"
    (is (= '(([:target :value]))
           (run 1 [q]
             (rmap/alist-lookupo :target q :value)
             (== q (lcons [:target :value] '())))))))

(deftest association-list-presence-and-absence-delay-over-open-tails
  (testing "contains-key succeeds when a key is present"
    (is (= '(:ok)
           (run* [q]
             (rmap/alist-contains-keyo :a '([:a 1] [:b 2]))
             (== q :ok)))))
  (testing "absent-key rejects discovered matches"
    (is (= '()
           (run* [q]
             (rmap/alist-absent-keyo :a '([:a 1] [:b 2]))
             (== q :ok)))))
  (testing "absent-key accepts ground lists without the key"
    (is (= '(:ok)
           (run* [q]
             (rmap/alist-absent-keyo :z '([:a 1] [:b 2]))
             (== q :ok)))))
  (testing "unknown entries retain a residual absence constraint"
    (let [answers (run 1 [q]
                    (fresh [k v]
                      (rmap/alist-absent-keyo :a (lcons [k v] '()))
                      (== q [k v])))]
      (is (= 1 (count answers)))
      (is (seq? (first answers)))
      (is (some #{':-} (flatten answers))))))

(deftest association-list-assoc-update-and-dissoc-are-map-like
  (testing "assoc replaces an existing key"
    (is (= '(([:a 1] [:b 20]))
           (run* [q]
             (rmap/alist-assoco :b 20 '([:a 1] [:b 2]) q)))))
  (testing "assoc appends an absent key"
    (is (= '(([:a 1] [:b 2]))
           (run* [q]
             (rmap/alist-assoco :b 2 '([:a 1]) q)))))
  (testing "update exposes the old value relationally"
    (is (= '(2)
           (run* [q]
             (rmap/alist-updateo :b q 20 '([:a 1] [:b 2]) '([:a 1] [:b 20]))))))
  (testing "update can synthesize the updated key and previous value"
    (is (= '([:b 2])
           (run* [q]
             (fresh [k old]
               (rmap/alist-updateo k old 20 '([:a 1] [:b 2]) '([:a 1] [:b 20]))
               (== q [k old]))))))
  (testing "dissoc removes every duplicate key from noncanonical input"
    (is (= '(([:b 2]))
           (run* [q]
             (rmap/alist-dissoco :a '([:a 1] [:b 2] [:a 3]) q)))))
  (testing "unique-key validation accepts canonical alists and rejects duplicates"
    (is (= '(:ok)
           (run* [q]
             (rmap/alist-unique-keyso '([:a 1] [:b 2]))
             (== q :ok))))
    (is (= '()
           (run* [q]
             (rmap/alist-unique-keyso '([:a 1] [:b 2] [:a 3]))
             (== q :ok))))))

(deftest clojure-persistent-maps-are-ground-values-not-open-relational-maps
  (testing "core.logic can unify exact ground map shapes"
    (is (= '(1)
           (run* [q]
             (== {:a q} {:a 1})))))
  (testing "map unification is exact; there is no open-map remainder"
    (is (= '()
           (run* [q]
             (== {:a q} {:a 1 :b 2})))))
  (testing "projected map lookup can synthesize a value from a ground key"
    (is (= '(1)
           (run* [q]
             (rmap/ground-map-lookupo :a {:a 1 :b 2} q)))))
  (testing "projected map lookup cannot enumerate unknown keys"
    (is (= '()
           (run* [q]
             (rmap/ground-map-lookupo q {:a 1 :b 2} 1)))))
  (testing "projected assoc/dissoc are useful only at a host boundary"
    (is (= '({:a 1, :b 20})
           (run* [q]
             (rmap/ground-map-assoco :b 20 {:a 1 :b 2} q))))
    (is (= '({:a 1})
           (run* [q]
             (rmap/ground-map-dissoco :b {:a 1 :b 2} q))))))
