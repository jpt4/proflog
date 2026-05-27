(ns proflog.list-kernel-matrix-test
  (:require [clojure.test :refer [deftest is testing]]
            [proflog.kernel.constructor-recursive :as constructor-recursive]
            [proflog.list-kernel-matrix-probe :as matrix]))

(deftest list-kernel-matrix-covers-forward-reverse-and-partial-modes
  (testing "the raw-kernel matrix spans append/reverse, flat/nested, and synthesis modes"
    (let [catalog (matrix/case-catalog)
          by-op (group-by :operation catalog)
          modes (set (map :mode catalog))
          shapes (set (map :shape catalog))]
      (is (seq (:append by-op)))
      (is (seq (:reverse by-op)))
      (is (contains? modes :forward))
      (is (contains? modes :output-synthesis))
      (is (contains? modes :input-synthesis))
      (is (contains? modes :partial-suffix))
      (is (contains? modes :partial-prefix))
      (is (contains? modes :inverse-splits))
      (is (contains? modes :partial-output))
      (is (contains? shapes :flat))
      (is (contains? shapes :nested))
      (is (some #(= :longer (:size %)) catalog)))))

(deftest list-kernel-matrix-promotes-guarded-raw-kernel-rows
  (testing "guarded IR closes representative rows without sidecar settlement"
    (with-redefs [constructor-recursive/settle-record
                  (fn [& _]
                    (throw (ex-info "ADR-35 matrix rows must not use sidecar settlement"
                                    {})))]
      (doseq [case-id [:append-forward-flat-3
                       :append-forward-nested-3
                       :reverse-forward-flat-3
                       :reverse-forward-nested-3
                       :append-output-flat
                       :append-suffix-flat
                       :append-prefix-flat
                       :append-inverse-flat
                       :reverse-input-flat-longer
                       :reverse-output-deep-nested-longer
                       :reverse-partial-output-longer-tail]]
        (is (:target-found? (matrix/run-case case-id))
            (str case-id " should find its target"))))))
