 (ns proflog.parity-test
   (:require [clojure.test :refer [deftest is testing]]
             [proflog.answers :as answers]
             [proflog.ast :as ast]
             [proflog.list-programs-test :as lp]))

 (defn- binding-terms
   [records binding-nom]
   (mapv #(answers/binding-term % binding-nom) records))

 (deftest closed-answer-parity-mode-and-generic-query-answers-agree-on-reverse
   (testing "generic query-answers now returns the same closed reverse answer as parity mode for the known list family"
     (ast/nom r
       (let [program (lp/list-program)
             input (lp/list-term (ast/app-term 'a)
                                 (ast/app-term 'b))
             query (ast/pos-lit (ast/app-term 'reverse input (ast/var-term r)))
             symbolic-records (answers/query-answers
                                program
                                query
                                [r]
                                {:proof-limit 1
                                 :max-raw-proof-limit 16
                                 :fuel 32
                                 :call-depth 1})
             parity-records (answers/query-parity-answers
                              program
                              query
                              [r]
                              {:proof-limit 1
                               :fuel 256
                               :failure-timeout-ms 2000
                               :max-term-size 2})]
         (is (= [(lp/list-term (ast/app-term 'b)
                               (ast/app-term 'a))]
                (binding-terms symbolic-records r)))
         (is (= [[]]
                (mapv :residuals symbolic-records)))
         (is (= [(lp/list-term (ast/app-term 'b)
                               (ast/app-term 'a))]
                (binding-terms parity-records r)))
         (is (= [[]]
                (mapv :residuals parity-records)))))))

 (deftest closed-answer-parity-mode-recovers-all-four-inverse-append-splits
   (testing "parity mode materializes the four closed legacy splits for append(x, y, [a, b, c])"
     (let [program (lp/list-program)
           abc (lp/list-term (ast/app-term 'a)
                             (ast/app-term 'b)
                             (ast/app-term 'c))]
       (ast/nom left right
         (let [records (answers/query-parity-answers
                         program
                         (ast/pos-lit (ast/app-term 'append
                                                    (ast/var-term left)
                                                    (ast/var-term right)
                                                    abc))
                         [left right]
                         {:proof-limit 4
                          :fuel 256
                          :failure-timeout-ms 2000
                          :max-term-size 3})]
           (is (= [[[left (lp/list-term)]
                    [right abc]]
                   [[left (lp/list-term (ast/app-term 'a))]
                    [right (lp/list-term (ast/app-term 'b)
                                         (ast/app-term 'c))]]
                   [[left (lp/list-term (ast/app-term 'a)
                                        (ast/app-term 'b))]
                    [right (lp/list-term (ast/app-term 'c))]]
                   [[left abc]
                    [right (lp/list-term)]]]
                  (mapv :bindings records)))
           (is (every? empty?
                       (map :residuals records))))))))

 (deftest closed-answer-parity-mode-recovers-nested-inverse-append-splits
   (testing "parity mode materializes the closed nested append splits that remain expensive in the generic path"
     (let [program (lp/list-program)
           sub-a (lp/list-term (ast/app-term 'a))
           sub-b (lp/list-term (ast/app-term 'b))
           whole (lp/list-term sub-a sub-b)]
       (ast/nom left right
         (let [records (answers/query-parity-answers
                         program
                         (ast/pos-lit (ast/app-term 'append
                                                    (ast/var-term left)
                                                    (ast/var-term right)
                                                    whole))
                         [left right]
                         {:proof-limit 3
                          :fuel 256
                          :failure-timeout-ms 2000
                          :max-term-size 4})]
           (is (= [[[left (lp/list-term)]
                    [right whole]]
                   [[left (lp/list-term sub-a)]
                    [right (lp/list-term sub-b)]]
                   [[left whole]
                    [right (lp/list-term)]]]
                  (mapv :bindings records)))
           (is (every? empty?
                       (map :residuals records))))))))

 (deftest closed-answer-parity-mode-and-generic-query-answers-agree-on-the-nested-suffix
   (testing "generic query-answers now returns the same closed nested suffix answer as parity mode for the known list family"
     (let [program (lp/list-program)
           sub-ab (lp/list-term (ast/app-term 'a)
                                (ast/app-term 'b))
           sub-c (lp/list-term (ast/app-term 'c))
           left (lp/list-term sub-ab)
           whole (lp/list-term sub-ab sub-c)]
       (ast/nom z
         (let [query (ast/pos-lit
                       (ast/app-term 'append
                                     left
                                     (ast/var-term z)
                                     whole))
               symbolic-records (answers/query-answers
                                  program
                                  query
                                  [z]
                                  {:proof-limit 1
                                   :max-raw-proof-limit 1
                                   :fuel 16
                                   :call-depth 2})
               parity-records (answers/query-parity-answers
                                program
                                query
                                [z]
                                {:proof-limit 1
                                 :fuel 256
                                 :failure-timeout-ms 2000
                                 :max-term-size 5})]
           (is (= [(lp/list-term sub-c)]
                  (binding-terms symbolic-records z)))
           (is (= [[]]
                  (mapv :residuals symbolic-records)))
           (is (= [(lp/list-term sub-c)]
                  (binding-terms parity-records z)))
           (is (= [[]]
                  (mapv :residuals parity-records))))))))
