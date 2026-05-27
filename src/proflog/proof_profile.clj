(ns proflog.proof-profile
  "Generic language-selected proof-profile dispatch.

   The ordinary query API validates a formula against its program language, then
   asks this namespace how to prove it. Most languages use `:default`, which is
   just the existing program kernel. Theory extensions can register additional
   profile keys with `prove-program*` and keep their conversion machinery out of
   `proflog.query`."
  (:require [proflog.kernel :as kernel]
            [proflog.kernel.robinson-q-profile :as robinson-q-profile]
            [proflog.kernel.willard-sjas-profile :as willard-sjas-profile]))

(def default-profile
  "The proof profile used when a language does not opt in to a theory layer."
  :default)

(defn profile-key
  "Return the proof profile selected by a compiled program's language."
  [program]
  (get-in program [:language :proof-profile] default-profile))

(defmulti prove-program*
  "Dispatch program proof search by proof-profile key.

   Methods receive `[profile program formula proof-limit fuel]`. External
   profiles can extend this multimethod without changing `proflog.query`."
  (fn [profile _program _formula _proof-limit _fuel] profile)
  :default ::unknown-profile)

(defmethod prove-program* ::unknown-profile
  [profile _program _formula _proof-limit _fuel]
  (throw (ex-info "Unknown proof profile"
                  {:proof-profile profile})))

(defmethod prove-program* :default
  [_profile program formula proof-limit fuel]
  (if (nil? fuel)
    (kernel/prove-program program formula proof-limit)
    (kernel/prove-program program formula proof-limit fuel)))

(defmethod prove-program* :robinson-q
  [_profile program formula proof-limit fuel]
  (robinson-q-profile/prove-program program formula proof-limit fuel))

(defmethod prove-program* :willard-sjas-tableau0
  [profile program formula proof-limit fuel]
  (willard-sjas-profile/prove-program profile program formula proof-limit fuel))

(defmethod prove-program* :willard-sjas-level1
  [profile program formula proof-limit fuel]
  (willard-sjas-profile/prove-program profile program formula proof-limit fuel))

(defn prove-program
  "Prove `formula` relative to `program` using the program language profile."
  ([program formula proof-limit]
   (prove-program program formula proof-limit nil))
  ([program formula proof-limit fuel]
   (prove-program*
     (profile-key program)
     program
     formula
     proof-limit
     fuel)))
