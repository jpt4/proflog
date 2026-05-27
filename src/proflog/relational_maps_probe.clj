(ns proflog.relational-maps-probe
  "ADR-37 probe for map-like relations.

   Clojure persistent maps are excellent ground host values, but core.logic
   unifies them as exact maps. That gives no open-map tail and no relational
   key enumeration. This namespace therefore prototypes the practical first
   step: map-like operations over association lists, with small ground-map
   helpers only for comparison."
  (:refer-clojure :exclude [== contains?])
  (:require [clojure.core.logic :refer [!= == conde fail fresh lcons project]]))

(def empty-alist
  "The canonical empty association list."
  '())

(defn entryo
  "Relate an association-list entry to its key and value."
  [key value entry]
  (== [key value] entry))

(defn alist-lookupo
  "Relational lookup in an association list.

   The relation searches from the head and succeeds with the first entry whose
   key unifies with `key`. The disequality branch keeps later entries available
   when the head key is known to be different."
  [key alist value]
  (fresh [entry rest entry-key entry-value]
    (== (lcons entry rest) alist)
    (entryo entry-key entry-value entry)
    (conde
      [(== key entry-key)
       (== value entry-value)]
      [(!= key entry-key)
       (alist-lookupo key rest value)])))

(defn alist-contains-keyo
  "Succeed when `key` appears in `alist`."
  [key alist]
  (fresh [value]
    (alist-lookupo key alist value)))

(defn alist-absent-keyo
  "Succeed when `key` is absent from every discovered entry in `alist`.

   On open tails this leaves delayed disequality constraints rather than
   projecting the tail to a host collection."
  [key alist]
  (conde
    [(== empty-alist alist)]
    [(fresh [entry rest entry-key entry-value]
       (== (lcons entry rest) alist)
       (entryo entry-key entry-value entry)
       (!= key entry-key)
       (alist-absent-keyo key rest))]))

(defn alist-unique-keyso
  "Succeed when `alist` contains no duplicate keys.

   The map-like update relations preserve this property when their input has it.
   They do not enforce uniqueness automatically, because duplicate-key lists are
   useful for probing operational behavior and legacy substitution shapes."
  [alist]
  (conde
    [(== empty-alist alist)]
    [(fresh [entry rest entry-key entry-value]
       (== (lcons entry rest) alist)
       (entryo entry-key entry-value entry)
       (alist-absent-keyo entry-key rest)
       (alist-unique-keyso rest))]))

(defn alist-assoco
  "Relate `out` to `in` with `key` associated to `value`.

   If `key` is present, the first matching entry is replaced. If it is absent,
   a new entry is appended at the end. For canonical unique-key alists this has
   the same observable map behavior as `assoc`."
  [key value in out]
  (conde
    [(== empty-alist in)
     (== (lcons [key value] empty-alist) out)]
    [(fresh [old-value rest]
       (== (lcons [key old-value] rest) in)
       (== (lcons [key value] rest) out))]
    [(fresh [entry rest out-rest entry-key entry-value]
       (== (lcons entry rest) in)
       (entryo entry-key entry-value entry)
       (!= key entry-key)
       (alist-assoco key value rest out-rest)
       (== (lcons entry out-rest) out))]))

(defn alist-updateo
  "Relate `out` to `in` by replacing an existing key's old value with a new one.

   Unlike `alist-assoco`, this fails when the key is absent."
  [key old-value new-value in out]
  (conde
    [(fresh [rest]
       (== (lcons [key old-value] rest) in)
       (== (lcons [key new-value] rest) out))]
    [(fresh [entry rest out-rest entry-key entry-value]
       (== (lcons entry rest) in)
       (entryo entry-key entry-value entry)
       (!= key entry-key)
       (alist-updateo key old-value new-value rest out-rest)
       (== (lcons entry out-rest) out))]))

(defn alist-dissoco
  "Relate `out` to `in` with all entries for `key` removed.

   On canonical unique-key alists this is equivalent to Clojure `dissoc`. On
   noncanonical alists it removes every duplicate key to preserve map-like
   semantics."
  [key in out]
  (conde
    [(== empty-alist in)
     (== empty-alist out)]
    [(fresh [value rest]
       (== (lcons [key value] rest) in)
       (alist-dissoco key rest out))]
    [(fresh [entry rest out-rest entry-key entry-value]
       (== (lcons entry rest) in)
       (entryo entry-key entry-value entry)
       (!= key entry-key)
       (alist-dissoco key rest out-rest)
       (== (lcons entry out-rest) out))]))

(defn ground-map-lookupo
  "Projected lookup for ground Clojure maps.

   This is intentionally not a general relational map operation. It only works
   when the map and key are ground enough for host `contains?`/`get`."
  [key m value]
  (project [key m]
    (if (and (map? m) (clojure.core/contains? m key))
      (== value (get m key))
      fail)))

(defn ground-map-assoco
  "Projected assoc for ground Clojure maps.

   This is a comparison helper showing where host maps become a projection
   boundary."
  [key value m out]
  (project [key value m]
    (if (map? m)
      (== out (assoc m key value))
      fail)))

(defn ground-map-dissoco
  "Projected dissoc for ground Clojure maps.

   This is a comparison helper, not an open-map relation."
  [key m out]
  (project [key m]
    (if (map? m)
      (== out (dissoc m key))
      fail)))
