(ns proflog.willard-sjas-code
  "Byte/base-64 coding utilities for the Willard SJAS profile.

   Willard's papers permit any natural semantic-tableau encoding satisfying the
   Conventional Tableaux Encoding Requirement. This namespace implements the
   concrete coding discipline used by Proflog's SJAS builder: syntax and proof
   objects are serialized as non-negative integers whose base-64 digits are
   six-bit bytes. Public object-language codes expose those digits directly as
   compact terms `(code-N b0 ... bN-1)`, where each byte `bi` is a small SJAS
   binary numeral over `0`, `1`, `dbl`, and `add`.

   The functions here are source-boundary encoders. They do not decide theorem
   validity; the proof profile consumes the resulting code terms during tableau
   search.

   See docs/log/2026-05-20-willard-tableau-proof-encoding.md for the
   Willard source trail and the boundary between the conventional encoding
   requirement and Proflog's selected proof-term byte layout."
  (:require [clojure.core.logic :refer [lvar]]
            [clojure.core.logic.nominal :as nominal]
            [proflog.ast :as ast]))

(def byte-base 64)

(def code-nom-entries
  "Canonical host noms used when formula-code variable indexes become AST noms.

   Core.logic nominal values are identity-bearing. Source-side theorem queries
   and kernel-side structural code decoding must therefore share these exact
   nom objects; recreating a nom with the same printed name is not enough for a
   decoded proof certificate to match the proof target it was generated from."
  (apply list
         (map (fn [idx]
                [idx (nominal/nom (lvar (symbol (str "sjas-v" (dec idx)))))])
              (range 1 byte-base))))

(def ^:private code-nom-by-index
  (into {} code-nom-entries))

(defn code-nom
  "Return the shared host nom for one-based formula-code variable index `idx`."
  [idx]
  (get code-nom-by-index idx))

(def u-grounding-sentinel-byte
  "Terminating byte used when a byte string is represented as one natural.

   Plain base-64 naturals cannot distinguish `[1]` from `[1 0]` after a
   round-trip through `natural->bytes`, because high zero digits are omitted.
   U-Grounding code terms therefore append this non-zero sentinel before the
   sequence is interpreted as a natural. The proof profile removes the final
   sentinel relationally when it decodes a formula, system, or proof code."
  63)

(def max-code-bytes
  "Largest byte-string code arity declared by the SJAS language.

   Willard's encodings are byte based; Proflog exposes those byte strings as
   first-order terms `(code-N b0 ... bN-1)`. Keeping the byte string flat avoids
   making a large Godel number into a deeply nested binary tower that ordinary
   language validation and miniKanren walking cannot traverse safely."
  4095)

(def ^:private zero-symbol (symbol "0"))
(def ^:private one-symbol (symbol "1"))

(def ^:private formula-tags
  '{true 1
    false 2
    pos 3
    neg 4
    eq 5
    neq 6
    and 7
    or 8
    not 9
    implies 10
    forall 11
    once-forall 12
    exists 13
    bounded-forall 14
    bounded-exists 15})

(def ^:private term-tags
  '{var 21
    par 22
    app 23
    code 24
    num 25})

(def ^:private system-tag 31)
(def ^:private profile-tableau0-tag 32)
(def ^:private profile-level1-tag 33)
(def ^:private reflected-clause-tag 34)
(def proof-symbol-tag 41)
(def proof-list-tag 42)
(def proof-empty-list-tag 43)
(def proof-wide-symbol-tag 44)
(def proof-byte-tag 45)

(def proof-symbols
  "Kernel proof atoms that may appear in encoded SJAS certificates."
  '[conj
    split
    univ
    once-univ
    witness
    eq-step
    eq-triggered-call
    eq-triggered-neg-call
    neq-close
    neq-rigid
    neq-store
    refl-close
    savefml
    false-close
    close
    pos-call
    neg-call
    neg-call-alt
    neg-call-guarded-alt
    skip-true
    lem-close
    skolemized
    propositional
    first-order
    guarded-alt
    guarded-neg-alt
    guarded-neg-alt-saturated
    guarded-seq-step
    guarded-seq-last
    guarded-call-seq-step
    guarded-call-seq-defer
    guarded-residual-seq-step
    guarded-residual-seq-last
    guarded-scope-exists
    query-pos-call
    query-neg-call
    query-neg-call-guarded-alt
    profiled
    willard-sjas-tableau0
    willard-sjas-level1
    willard-sjas-arithmetic
    willard-sjas-code
    willard-sjas-fact
    willard-sjas-axiom-member
    willard-sjas-theorem-code
    willard-sjas-proof-check
    willard-sjas-subst-code
    willard-sjas-subst-proof-check
    sjas-bind-done
    sjas-bind-num
    sjas-generated-axiom-member
    sjas-system-beta-axiom
    sjas-system-code-bytes
    sjas-system-reflected-axiom
    sjas-system-group-zero-axiom
    sjas-system-group-one-axiom
    sjas-system-code-header
    sjas-system-fixed-axiom
    sjas-system-group-three-axiom
    sjas-system-tableau0-group-three-axiom
    sjas-system-level1-group-three-axiom
    sjas-code-bytes
    sjas-ug-code-bytes
    sjas-ug-code-byte-cons
    sjas-ug-code-cons
    sjas-ug-code-end
    sjas-ug-code-canonical-byte
    sjas-ug-code-mul64-shift
    sjas-ug-code-mul64-zero
    sjas-equal
    sjas-eq-progress
    sjas-leq
    sjas-lt
    sjas-mult
    sjas-num-add-one
    sjas-num-dbl
    sjas-num-one
    sjas-num-zero
    sjas-read-add
    sjas-read-count
    sjas-read-dbl
    sjas-read-div
    sjas-read-log
    sjas-read-max
    sjas-read-one
    sjas-read-pred
    sjas-read-root
    sjas-read-sub
    sjas-read-var
    sjas-read-zero
    sjas-axiom
    wff
    delta-star-0-code
    pi-star-1-code
    sigma-star-1-code
    neg-pair
    sjas-code-arg
    sjas-code-args-end
    sjas-neg-pair-structural
    free-close])

(def proof-symbol->index
  (into {} (map-indexed (fn [idx sym] [sym (inc idx)]) proof-symbols)))

(def index->proof-symbol
  (into {} (map (fn [[sym idx]] [idx sym]) proof-symbol->index)))

(defn- checked-byte
  [label value]
  (when-not (and (integer? value)
                 (<= 0 value)
                 (< value byte-base))
    (throw (ex-info "SJAS byte out of range"
                    {:label label
                     :value value
                     :byte-base byte-base})))
  value)

(defn- positive-byte
  [label value]
  (checked-byte label value)
  (when-not (pos? value)
    (throw (ex-info "SJAS byte must be non-zero in this position"
                    {:label label
                     :value value})))
  value)

(defn- one-byte-count
  [label value]
  (positive-byte label (inc value)))

(defn bytes->natural
  "Interpret `bytes` as a little-endian base-64 natural number."
  [bytes]
  (reduce-kv (fn [acc idx byte]
               (+ acc (*' (checked-byte :byte byte)
                          (bigint (.pow (BigInteger/valueOf byte-base) idx)))))
             0N
             (vec bytes)))

(defn natural->bytes
  "Return the little-endian base-64 bytes for `value`."
  [value]
  {:pre [(and (integer? value) (not (neg? value)))]}
  (if (zero? value)
    '()
    (loop [n (bigint value)
           out []]
      (if (zero? n)
        (seq out)
        (recur (quot n byte-base)
               (conj out (int (mod n byte-base))))))))

(defn bytes->u-grounding-code-value
  "Encode a byte string as a sentinel-terminated base-64 natural.

   This is the natural-number representation used by ADR-0071's pure
   U-Grounding code format. The sentinel makes the mapping injective over byte
   strings, including strings whose final payload byte is zero."
  [bytes]
  (bytes->natural (concat (vec bytes) [u-grounding-sentinel-byte])))

(defn u-grounding-code-value->bytes
  "Decode a sentinel-terminated U-Grounding code natural.

   Returns nil when `value` is not a well-formed sentinel-terminated code."
  [value]
  (when (and (integer? value) (not (neg? value)))
    (loop [remaining (vec (natural->bytes value))
           out []]
      (cond
        (empty? remaining)
        nil

        (and (= 1 (count remaining))
             (= u-grounding-sentinel-byte (first remaining)))
        out

        :else
        (recur (subvec remaining 1)
               (conj out (checked-byte :u-grounding-code-byte
                                       (first remaining))))))))

(defn code-symbol
  "Return the function symbol for a flat code term with `byte-count` digits."
  [byte-count]
  {:pre [(and (integer? byte-count)
              (<= 0 byte-count max-code-bytes))]}
  (symbol (str "code-" byte-count)))

(def code-functions
  "Function declarations for compact base-64 SJAS code terms."
  (into {}
        (map (fn [byte-count]
               [(code-symbol byte-count) byte-count])
             (range (inc max-code-bytes)))))

(defn code-symbol-byte-count
  "Return the byte count encoded by a `code-N` symbol, or nil otherwise."
  [sym]
  (when (symbol? sym)
    (let [n (name sym)]
      (when (.startsWith n "code-")
        (let [suffix (subs n 5)]
          (when (re-matches #"\d+" suffix)
            (let [byte-count (Long/parseLong suffix)]
              (when (<= 0 byte-count max-code-bytes)
                byte-count))))))))

(defn binary-numeral-term
  "Write a small natural number as a binary SJAS numeral term.

   This is used for ordinary U-grounding numerals and for the individual bytes
   inside a flat code term. Full formula/proof codes use `code-term` below, so
   they are not represented as extremely deep binary constructor towers."
  [value]
  {:pre [(and (integer? value) (not (neg? value)))]}
  (let [n (biginteger value)]
    (if (zero? n)
      (ast/app-term zero-symbol)
      (let [highest-bit (dec (.bitLength n))]
        (loop [bit-index (dec highest-bit)
               term (ast/app-term one-symbol)]
          (if (neg? bit-index)
            term
            (let [doubled (ast/app-term 'dbl term)
                  next-term (if (.testBit n bit-index)
                              (ast/app-term 'add doubled (ast/app-term one-symbol))
                              doubled)]
              (recur (dec bit-index) next-term))))))))

(def byte-terms
  "The 64 canonical byte numerals used inside compact code terms."
  (vec (map binary-numeral-term (range byte-base))))

(defn bytes->code-term
  [bytes]
  (let [bytes (vec bytes)
        byte-count (count bytes)]
    (when (> byte-count max-code-bytes)
      (throw (ex-info "SJAS code byte string exceeds declared arity limit"
                      {:byte-count byte-count
                       :max-code-bytes max-code-bytes})))
    (apply ast/app-term
           (code-symbol byte-count)
           (map (fn [byte]
                  (nth byte-terms (checked-byte :code-byte byte)))
                bytes))))

(defn bytes->u-grounding-code-term
  "Write an exact byte string as one binary U-Grounding numeral term."
  [bytes]
  (binary-numeral-term (bytes->u-grounding-code-value bytes)))

(defn code-term
  "Write a natural number as a compact public SJAS Godel-code term.

   This is a natural-number view, so it uses the canonical base-64 expansion
   without trailing zero bytes. Syntax and proof encoders that already have a
   byte string should call `bytes->code-term` directly, preserving the byte
   count carried by the public `code-N` constructor."
  [value]
  (bytes->code-term (natural->bytes value)))

(defn- binary-numeral-value
  [term]
  (when (= 'app (ast/tag-of term))
    (let [head (second term)
          args (nnext term)]
      (cond
        (and (= zero-symbol head) (empty? args)) 0
        (and (= one-symbol head) (empty? args)) 1
        (and (= 'dbl head) (= 1 (count args)))
        (when-let [arg (binary-numeral-value (first args))]
          (*' 2 arg))
        (and (= 'add head) (= 2 (count args)))
        (when-let [left (binary-numeral-value (first args))]
          (when-let [right (binary-numeral-value (second args))]
            (+' left right)))
        :else nil))))

(defn code-term-bytes
  "Return the byte vector denoted by a compact code term, or nil if malformed."
  [term]
  (when (= 'app (ast/tag-of term))
    (let [head (second term)
          args (vec (nnext term))]
      (when-let [byte-count (code-symbol-byte-count head)]
        (when (= byte-count (count args))
          (let [bytes (mapv binary-numeral-value args)]
            (when (every? #(and (integer? %) (<= 0 %) (< % byte-base)) bytes)
              bytes)))))))

(defn u-grounding-code-term-bytes
  "Return the byte vector denoted by a U-Grounding numeral code, or nil."
  [term]
  (when-let [value (binary-numeral-value term)]
    (u-grounding-code-value->bytes value)))

(defn code-term?
  "Recognize the public compact SJAS code-term shape."
  [term]
  (boolean (code-term-bytes term)))

(defn u-grounding-code-term?
  "Recognize the sentinel-terminated U-Grounding code-term shape."
  [term]
  (boolean (u-grounding-code-term-bytes term)))

(defn bytes->formal-code-term
  "Encode `bytes` using one of the supported public SJAS code formats."
  [code-format bytes]
  (case code-format
    :compact (bytes->code-term bytes)
    :u-grounding (bytes->u-grounding-code-term bytes)
    (throw (ex-info "Unsupported SJAS code format"
                    {:code-format code-format
                     :supported #{:compact :u-grounding}}))))

(defn- encode-code-length
  [byte-count]
  (when (> byte-count max-code-bytes)
    (throw (ex-info "SJAS embedded code byte string exceeds encoding limit"
                    {:byte-count byte-count
                     :max-code-bytes max-code-bytes})))
  [(mod byte-count byte-base)
   (quot byte-count byte-base)])

(defn context
  "Build the symbol table used while encoding one SJAS system.

   The table is deterministic: symbols are ordered by printed name. Willard's
   presentations fix a coding for the language before coding formulas; this
   table is Proflog's finite-language counterpart."
  [symbols]
  (let [ordered (->> symbols
                     (filter symbol?)
                     distinct
                     (sort-by (juxt namespace name))
                     vec)
        symbol->index (into {} (map-indexed (fn [idx sym] [sym (inc idx)])
                                            ordered))]
    {:symbols ordered
     :symbol->index symbol->index
     :index->symbol (into {} (map (fn [[sym idx]] [idx sym]) symbol->index))}))

(defn- symbol-index
  [ctx sym]
  (or (get-in ctx [:symbol->index sym])
      (throw (ex-info "Symbol is absent from the SJAS coding context"
                      {:symbol sym
                       :known-symbols (:symbols ctx)}))))

(defn- tag-byte
  [table tag]
  (or (get table tag)
      (throw (ex-info "Unsupported SJAS coding tag"
                      {:tag tag
                       :known-tags (keys table)}))))

(declare encode-canonical-formula-bytes)

(defn- encode-canonical-term-bytes
  [ctx term]
  (if-let [code-bytes (code-term-bytes term)]
    (into [(tag-byte term-tags 'code)]
          (concat (encode-code-length (count code-bytes))
                  code-bytes))
    (if-some [numeric-value (binary-numeral-value term)]
      (let [payload (vec (natural->bytes numeric-value))]
        (into [(tag-byte term-tags 'num)]
              (concat (encode-code-length (count payload))
                      payload)))
      (case (first term)
        var [(tag-byte term-tags 'var)
             (positive-byte :var-index (inc (Long/parseLong (subs (name (second term)) 1))))]
        par [(tag-byte term-tags 'par)
             (positive-byte :par-index (inc (Long/parseLong (subs (name (second term)) 1))))]
        app (let [head (second term)
                  args (nnext term)]
              (into [(tag-byte term-tags 'app)
                     (positive-byte :symbol-index (symbol-index ctx head))
                     (one-byte-count :term-arity (count args))]
                    (mapcat #(encode-canonical-term-bytes ctx %) args)))
        (throw (ex-info "Unsupported canonical term for SJAS coding"
                        {:term term}))))))

(defn encode-canonical-formula-bytes
  "Encode a canonical formula produced by `proflog.willard-sjas`."
  [ctx formula]
  (case (first formula)
    true [(tag-byte formula-tags 'true)]
    false [(tag-byte formula-tags 'false)]
    pos (into [(tag-byte formula-tags 'pos)]
              (encode-canonical-term-bytes ctx (second formula)))
    neg (into [(tag-byte formula-tags 'neg)]
              (encode-canonical-term-bytes ctx (second formula)))
    eq (into [(tag-byte formula-tags 'eq)]
             (concat (encode-canonical-term-bytes ctx (second formula))
                     (encode-canonical-term-bytes ctx (nth formula 2))))
    neq (into [(tag-byte formula-tags 'neq)]
              (concat (encode-canonical-term-bytes ctx (second formula))
                      (encode-canonical-term-bytes ctx (nth formula 2))))
    and (into [(tag-byte formula-tags 'and)]
              (concat (encode-canonical-formula-bytes ctx (second formula))
                      (encode-canonical-formula-bytes ctx (nth formula 2))))
    or (into [(tag-byte formula-tags 'or)]
             (concat (encode-canonical-formula-bytes ctx (second formula))
                     (encode-canonical-formula-bytes ctx (nth formula 2))))
    not (into [(tag-byte formula-tags 'not)]
              (encode-canonical-formula-bytes ctx (second formula)))
    implies (into [(tag-byte formula-tags 'implies)]
                  (concat (encode-canonical-formula-bytes ctx (second formula))
                          (encode-canonical-formula-bytes ctx (nth formula 2))))
    forall [(tag-byte formula-tags 'forall)
            (positive-byte :binder-index (inc (Long/parseLong (subs (name (second formula)) 1))))
            (encode-canonical-formula-bytes ctx (nth formula 2))]
    once-forall [(tag-byte formula-tags 'once-forall)
                 (positive-byte :binder-index (inc (Long/parseLong (subs (name (second formula)) 1))))
                 (encode-canonical-formula-bytes ctx (nth formula 2))]
    exists [(tag-byte formula-tags 'exists)
            (positive-byte :binder-index (inc (Long/parseLong (subs (name (second formula)) 1))))
            (encode-canonical-formula-bytes ctx (nth formula 2))]
    bounded-forall (into [(tag-byte formula-tags 'bounded-forall)
                          (positive-byte :binder-index (inc (Long/parseLong (subs (name (second formula)) 1))))]
                         (concat (encode-canonical-term-bytes ctx (nth formula 2))
                                 (encode-canonical-formula-bytes ctx (nth formula 3))))
    bounded-exists (into [(tag-byte formula-tags 'bounded-exists)
                          (positive-byte :binder-index (inc (Long/parseLong (subs (name (second formula)) 1))))]
                         (concat (encode-canonical-term-bytes ctx (nth formula 2))
                                 (encode-canonical-formula-bytes ctx (nth formula 3))))
    (throw (ex-info "Unsupported canonical formula for SJAS coding"
                    {:formula formula}))))

(defn canonical-formula-code-bytes
  "Return the exact byte string for a canonical formula code.

   The byte string is the formal sequence object. It may legitimately end with
   zero, for example when the final term is an embedded code payload. Do not
   reconstruct public syntax codes by round-tripping this value through a
   natural number, because base-64 naturals have no trailing-zero memory."
  [ctx canonical-formula]
  (vec (flatten (encode-canonical-formula-bytes ctx canonical-formula))))

(defn canonical-formula-code-value
  [ctx canonical-formula]
  (bytes->natural (canonical-formula-code-bytes ctx canonical-formula)))

(defn canonical-formula-code-term
  [ctx canonical-formula]
  (bytes->code-term (canonical-formula-code-bytes ctx canonical-formula)))

(defn canonical-formula-formal-code-term
  [ctx canonical-formula code-format]
  (bytes->formal-code-term code-format
                           (canonical-formula-code-bytes ctx canonical-formula)))

(defn- profile-byte
  [profile]
  (case profile
    :willard-sjas-tableau0 profile-tableau0-tag
    :willard-sjas-level1 profile-level1-tag
    (throw (ex-info "Unsupported SJAS profile for coding"
                    {:profile profile}))))

(defn- encode-canonical-clause-bytes
  [ctx {:keys [relation arity body]}]
  (into [reflected-clause-tag
         (positive-byte :relation-index (symbol-index ctx relation))
         (one-byte-count :relation-arity arity)]
        (encode-canonical-formula-bytes ctx body)))

(defn system-code-bytes
  "Encode the finite reflected source as its exact byte string."
  [ctx {:keys [profile beta reflected-clauses]}]
  (vec
    (flatten
      (concat [system-tag
               (profile-byte profile)
               (one-byte-count :beta-count (count beta))]
              (map #(encode-canonical-formula-bytes ctx %) beta)
              [(one-byte-count :reflected-count (count reflected-clauses))]
              (map #(encode-canonical-clause-bytes ctx %) reflected-clauses)))))

(defn system-code-value
  "Encode the finite reflected source used to identify an `IS#_D(beta)` basis."
  [ctx canonical-source]
  (bytes->natural (system-code-bytes ctx canonical-source)))

(defn system-code-term
  [ctx canonical-source]
  (bytes->code-term (system-code-bytes ctx canonical-source)))

(defn system-formal-code-term
  [ctx canonical-source code-format]
  (bytes->formal-code-term code-format
                           (system-code-bytes ctx canonical-source)))

(declare proof-code-bytes)

(defn- proof-symbol-index
  [sym]
  (or (get proof-symbol->index sym)
      (throw (ex-info "Unsupported proof symbol in SJAS certificate"
                      {:symbol sym}))))

(defn- proof-symbol-index-bytes
  [idx]
  (if (< idx byte-base)
    [proof-symbol-tag (positive-byte :proof-symbol-index idx)]
    [proof-wide-symbol-tag
     (checked-byte :proof-symbol-index-high (quot idx byte-base))
     (checked-byte :proof-symbol-index-low (mod idx byte-base))]))

(defn proof-code-bytes
  "Encode a kernel proof datum as a base-64 byte string.

   This is Proflog's selected proof-code layout for the ordinary-tableau SJAS
   profile. Willard requires a natural semantic-tableaux proof encoding whose
   size satisfies the Conventional Tableaux Encoding Requirement; he does not
   require this exact byte layout. See
   docs/log/2026-05-20-willard-tableau-proof-encoding.md."
  [proof]
  (cond
    (symbol? proof)
    (proof-symbol-index-bytes (proof-symbol-index proof))

    (integer? proof)
    [proof-byte-tag (checked-byte :proof-byte proof)]

    (sequential? proof)
    (if (empty? proof)
      [proof-empty-list-tag]
      (into [proof-list-tag (one-byte-count :proof-list-count (count proof))]
            (mapcat proof-code-bytes proof)))

    :else
    (throw (ex-info "Unsupported proof payload in SJAS certificate"
                    {:value proof
                     :class (some-> proof class .getName)}))))

(defn proof-code-value
  [proof]
  (bytes->natural (proof-code-bytes proof)))

(defn proof-code-term
  [proof]
  (bytes->code-term (proof-code-bytes proof)))

(defn proof-formal-code-term
  [proof code-format]
  (bytes->formal-code-term code-format
                           (proof-code-bytes proof)))
