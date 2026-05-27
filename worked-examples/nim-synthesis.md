# Nim Synthesis

This file covers `test/proflog/nim_synthesis_test.clj`.

The current winning-move witness formula is:

```clojure
(x = s(y) or x = s(s(y))) and not win(y)
```

So each example asks for a concrete witness `y` showing that position `x` has a
move to a losing position.

## Winning Witnesses

Current committed witnesses:

```clojure
win(1) via y = 0
win(2) via y = 0
win(4) via y = 3
win(5) via y = 3
```

These witness formulas all succeed directly.

## Wrong Witnesses

The suite also checks that bad witnesses are rejected:

```clojure
win(1) via y = 1 => fails
win(4) via y = 2 => fails
```

So the current kernel is not merely finding some proof. It is distinguishing
the intended losing successor from an arbitrary candidate.

## Deeper Ground Positions

The extended namespace also checks:

```clojure
win(4) => succeeds
win(5) => succeeds
```

These are the next concrete winning positions beyond the fast baseline.

## Source To Kernel Descent

This file isolates one clause body from Fitting P2. In source form, the inlined
winning relation is:

```prolog
win(x) :- exists y.
            ((x = s(y) or x = s(s(y))) and not win(y)).
```

The witness tests make the existential candidate explicit. For `win(4) via
y = 3`, the checked formula is the body instance:

```clojure
(and (or (= four (s three))
         (= four (s (s three))))
     (not (win three)))
```

At the backend layer this is an `ast/and-form` whose first conjunct is ordinary
constructor equality and whose second conjunct is a negative program call. The
kernel entry is still `query/query-succeeds` or `query/query-fails` over the
compiled P2 program:

```clojure
(kernel/prove-program p2-program
                      (neg witness-formula)
                      1
                      fuel)
```

for success, or the positive `witness-formula` for refutation. The fixed
witness parameter is deliberately not exported as an answer variable here; the
test is checking correctness of a candidate move, not asking the answer overlay
to synthesize all moves.
