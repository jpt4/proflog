(ns proflog.relational-arithmetic
  "Pure core.logic arithmetic over little-endian binary numerals.

   This is a Clojure translation of `numbers.scm` from
   michaelballantyne/faster-minikanren. Numerals use miniKanren's conventional
   little-endian bit-list representation:

   - `()` is zero
   - `(1)` is one
   - `(0 1)` is two
   - `(1 1)` is three

   The translation intentionally stays independent from Proflog's object
   language. It is a small relation library that core.logic goals can require
   directly.

   Adapted from faster-minikanren under the MIT license:
   Copyright (c) 2015 William E. Byrd."
  (:refer-clojure :exclude [== < <=])
  (:require [clojure.core.logic :refer [== conde fresh lcons]]))

(declare appendo *o odd-*o bound-*o gen-addero divo splito logo exp2 repeated-mul)

(defn build-num
  "Build a little-endian binary miniKanren numeral for non-negative integer `n`."
  [n]
  {:pre [(and (integer? n) (not (neg? n)))]}
  (cond
    (odd? n) (cons 1 (build-num (quot (- n 1) 2)))
    (pos? n) (cons 0 (build-num (quot n 2)))
    :else '()))

(defn zeroo
  [n]
  (== '() n))

(defn poso
  [n]
  (fresh [a d]
    (== (lcons a d) n)))

(defn >1o
  [n]
  (fresh [a ad dd]
    (== (lcons a (lcons ad dd)) n)))

(defn full-addero
  [b x y r c]
  (conde
    [(== 0 b) (== 0 x) (== 0 y) (== 0 r) (== 0 c)]
    [(== 1 b) (== 0 x) (== 0 y) (== 1 r) (== 0 c)]
    [(== 0 b) (== 1 x) (== 0 y) (== 1 r) (== 0 c)]
    [(== 1 b) (== 1 x) (== 0 y) (== 0 r) (== 1 c)]
    [(== 0 b) (== 0 x) (== 1 y) (== 1 r) (== 0 c)]
    [(== 1 b) (== 0 x) (== 1 y) (== 0 r) (== 1 c)]
    [(== 0 b) (== 1 x) (== 1 y) (== 0 r) (== 1 c)]
    [(== 1 b) (== 1 x) (== 1 y) (== 1 r) (== 1 c)]))

(defn addero
  [d n m r]
  (conde
    [(== 0 d) (== '() m) (== n r)]
    [(== 0 d) (== '() n) (== m r) (poso m)]
    [(== 1 d) (== '() m) (addero 0 n '(1) r)]
    [(== 1 d) (== '() n) (poso m) (addero 0 '(1) m r)]
    [(== '(1) n) (== '(1) m)
     (fresh [a c]
       (== (list a c) r)
       (full-addero d 1 1 a c))]
    [(== '(1) n) (gen-addero d n m r)]
    [(== '(1) m) (>1o n) (>1o r)
     (addero d '(1) n r)]
    [(>1o n) (gen-addero d n m r)]))

(defn gen-addero
  [d n m r]
  (fresh [a b c e x y z]
    (== (lcons a x) n)
    (== (lcons b y) m)
    (poso y)
    (== (lcons c z) r)
    (poso z)
    (full-addero d a b c e)
    (addero e x y z)))

(defn pluso
  [n m k]
  (addero 0 n m k))

(defn minuso
  [n m k]
  (pluso m k n))

(defn *o
  [n m p]
  (conde
    [(== '() n) (== '() p)]
    [(poso n) (== '() m) (== '() p)]
    [(== '(1) n) (poso m) (== m p)]
    [(>1o n) (== '(1) m) (== n p)]
    [(fresh [x z]
       (== (lcons 0 x) n)
       (poso x)
       (== (lcons 0 z) p)
       (poso z)
       (>1o m)
       (*o x m z))]
    [(fresh [x y]
       (== (lcons 1 x) n)
       (poso x)
       (== (lcons 0 y) m)
       (poso y)
       (*o m n p))]
    [(fresh [x y]
       (== (lcons 1 x) n)
       (poso x)
       (== (lcons 1 y) m)
       (poso y)
       (odd-*o x n m p))]))

(defn odd-*o
  [x n m p]
  (fresh [q]
    (bound-*o q p n m)
    (*o x m q)
    (pluso (lcons 0 q) m p)))

(defn bound-*o
  [q p n m]
  (conde
    [(== '() q) (poso p)]
    [(fresh [a0 a1 a2 a3 x y z]
       (== (lcons a0 x) q)
       (== (lcons a1 y) p)
       (conde
         [(== '() n)
          (== (lcons a2 z) m)
          (bound-*o x y z '())]
         [(== (lcons a3 z) n)
          (bound-*o x y z m)]))]))

(defn =lo
  [n m]
  (conde
    [(== '() n) (== '() m)]
    [(== '(1) n) (== '(1) m)]
    [(fresh [a x b y]
       (== (lcons a x) n)
       (poso x)
       (== (lcons b y) m)
       (poso y)
       (=lo x y))]))

(defn <lo
  [n m]
  (conde
    [(== '() n) (poso m)]
    [(== '(1) n) (>1o m)]
    [(fresh [a x b y]
       (== (lcons a x) n)
       (poso x)
       (== (lcons b y) m)
       (poso y)
       (<lo x y))]))

(defn <=lo
  [n m]
  (conde
    [(=lo n m)]
    [(<lo n m)]))

(defn <o
  [n m]
  (conde
    [(<lo n m)]
    [(=lo n m)
     (fresh [x]
       (poso x)
       (pluso n x m))]))

(defn <=o
  [n m]
  (conde
    [(== n m)]
    [(<o n m)]))

(defn divo
  "Relation for n = q * m + r with 0 <= r < m.

   Named `divo` because Clojure symbols beginning with `/` are awkward as
   public vars; this corresponds to `/o` in the Scheme source."
  [n m q r]
  (conde
    [(== r n) (== '() q) (<o n m)]
    [(== '(1) q) (=lo n m) (pluso r m n) (<o r m)]
    [(<lo m n)
     (<o r m)
     (poso q)
     (fresh [nh nl qh ql qlm qlmr rr rh]
       (splito n r nl nh)
       (splito q r ql qh)
       (conde
         [(== '() nh)
          (== '() qh)
          (minuso nl r qlm)
          (*o ql m qlm)]
         [(poso nh)
          (*o ql m qlm)
          (pluso qlm r qlmr)
          (minuso qlmr nl rr)
          (splito rr r '() rh)
          (divo nh m qh rh)]))]))

(defn splito
  [n r l h]
  (conde
    [(== '() n) (== '() h) (== '() l)]
    [(fresh [b n-tail]
       (== (lcons 0 (lcons b n-tail)) n)
       (== '() r)
       (== (lcons b n-tail) h)
       (== '() l))]
    [(fresh [n-tail]
       (== (lcons 1 n-tail) n)
       (== '() r)
       (== n-tail h)
       (== '(1) l))]
    [(fresh [b n-tail a r-tail]
       (== (lcons 0 (lcons b n-tail)) n)
       (== (lcons a r-tail) r)
       (== '() l)
       (splito (lcons b n-tail) r-tail '() h))]
    [(fresh [n-tail a r-tail]
       (== (lcons 1 n-tail) n)
       (== (lcons a r-tail) r)
       (== '(1) l)
       (splito n-tail r-tail '() h))]
    [(fresh [b n-tail a r-tail l-tail]
       (== (lcons b n-tail) n)
       (== (lcons a r-tail) r)
       (== (lcons b l-tail) l)
       (poso l-tail)
       (splito n-tail r-tail l-tail h))]))

(defn logo
  [n b q r]
  (conde
    [(== '(1) n) (poso b) (== '() q) (== '() r)]
    [(== '() q) (<o n b) (pluso r '(1) n)]
    [(== '(1) q) (>1o b) (=lo n b) (pluso r b n)]
    [(== '(1) b) (poso q) (pluso r '(1) n)]
    [(== '() b) (poso q) (== r n)]
    [(== '(0 1) b)
     (fresh [a ad dd]
       (poso dd)
       (== (lcons a (lcons ad dd)) n)
       (exp2 n '() q)
       (fresh [s]
         (splito n dd r s)))]
    [(fresh [a ad add ddd]
       (conde
         [(== '(1 1) b)]
         [(== (lcons a (lcons ad (lcons add ddd))) b)])
       (<lo b n)
       (fresh [bw1 bw nw nw1 ql1 ql s]
         (exp2 b '() bw1)
         (pluso bw1 '(1) bw)
         (<lo q n)
         (fresh [q1 bwq1]
           (pluso q '(1) q1)
           (*o bw q1 bwq1)
           (<o nw1 bwq1))
         (exp2 n '() nw1)
         (pluso nw1 '(1) nw)
         (divo nw bw ql1 s)
         (pluso ql '(1) ql1)
         (<=lo ql q)
         (fresh [bql qh qdh qd]
           (repeated-mul b ql bql)
           (divo nw bw1 qh s)
           (pluso ql qdh qh)
           (pluso ql qd q)
           (<=o qd qdh)
           (fresh [bqd bq1 bq]
             (repeated-mul b qd bqd)
             (*o bql bqd bq)
             (*o b bq bq1)
             (pluso bq r n)
             (<o n bq1)))))]))

(defn exp2
  [n b q]
  (conde
    [(== '(1) n) (== '() q)]
    [(>1o n) (== '(1) q)
     (fresh [s]
       (splito n b s '(1)))]
    [(fresh [q1 b2]
       (== (lcons 0 q1) q)
       (poso q1)
       (<lo b n)
       (appendo b (lcons 1 b) b2)
       (exp2 n b2 q1))]
    [(fresh [q1 nh b2 s]
       (== (lcons 1 q1) q)
       (poso q1)
       (poso nh)
       (splito n b s nh)
       (appendo b (lcons 1 b) b2)
       (exp2 nh b2 q1))]))

(defn appendo
  [l s out]
  (conde
    [(== '() l) (== s out)]
    [(fresh [a d res]
       (== (lcons a d) l)
       (== (lcons a res) out)
       (appendo d s res))]))

(defn repeated-mul
  [n q nq]
  (conde
    [(poso n) (== '() q) (== '(1) nq)]
    [(== '(1) q) (== n nq)]
    [(>1o q)
     (fresh [q1 nq1]
       (pluso q1 '(1) q)
       (repeated-mul n q1 nq1)
       (*o nq1 n nq))]))

(defn expo
  [b q n]
  (logo n b q '()))
