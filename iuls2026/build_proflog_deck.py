#!/usr/bin/env python3
"""Generate the IULS 2026 Proflog slide deck from the sample ODP.

The sample deck supplies the visual frame: a sparse white slide, a blue
footer bar, DejaVu Math TeX Gyre text, centered slide numbers, and a compact
title band.  This script replaces the sample slide pages with talk-specific
content while preserving the surrounding ODP styles and metadata.
"""

from __future__ import annotations

import html
import os
import re
import zipfile
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parent
SAMPLE = ROOT / "20260527_iu_logic_seminar.odp"
OUT_ODP = ROOT / "proflog_implementation_and_rationale_iuls2026.odp"
OUT_NOTES = ROOT / "proflog_implementation_and_rationale_iuls2026_lecture_notes.md"
OUT_SLIDES = ROOT / "proflog_implementation_and_rationale_iuls2026_slides.md"


@dataclass(frozen=True)
class Slide:
    title: str
    lines: tuple[str, ...]
    notes: tuple[str, ...]


P1_EVEN_TRACE = (
    "neg-call-guarded-alt",
    "guarded-alt",
    "guarded-neg-alt-saturated",
    "guarded-scope-done",
    "guard-eq",
    "decompose",
    "guard-saturation-done",
    "guarded-call-seq-done",
    "guarded-seq-done",
)

P1_ODD_TRACE = (
    "neg-call",
    "witness",
    "conj",
    "eq-step",
    "par-bind",
    "pos-call",
    "split",
    "free-close",
    "witness",
    "conj",
    "eq-step",
    "decompose",
    "args",
    "par-bind",
    "pos-call",
    "univ",
    "split",
    "neg-call-guarded-alt",
    "guarded-alt",
    "guarded-neg-alt-saturated",
    "guarded-scope-done",
    "guard-eq",
    "eq-bind",
    "guard-saturation-done",
    "guarded-call-seq-done",
    "guarded-seq-done",
    "refl-close",
)

P2_WIN4_TRACE = (
    "neg-call",
    "once-univ",
    "split",
    "conj",
    "neq-close",
    "decompose",
    "args",
    "eq-bind",
    "pos-call",
    "witness",
    "conj",
    "savefml",
    "split",
    "eq-step",
    "decompose",
    "args",
    "par-bind",
    "eq-triggered-neg-call",
    "once-univ",
    "split",
    "conj",
    "neq-close",
    "decompose",
    "args",
    "decompose",
    "args",
    "eq-bind",
    "pos-call",
    "witness",
    "conj",
    "savefml",
    "split",
    "free-close",
    "free-close",
    "decompose",
    "decompose",
    "decompose",
    "free-close",
)

P2_WIN3_TRACE = (
    "pos-call",
    "witness",
    "conj",
    "savefml",
    "split",
    "eq-step",
    "decompose",
    "args",
    "par-bind",
    "eq-triggered-neg-call",
    "once-univ",
    "split",
    "conj",
    "neq-close",
    "decompose",
    "args",
    "decompose",
    "args",
    "eq-bind",
    "pos-call",
    "witness",
    "conj",
    "savefml",
    "split",
    "free-close",
    "free-close",
    "decompose",
    "decompose",
    "decompose",
    "free-close",
)


def trace_line(trace: tuple[str, ...]) -> str:
    return " > ".join(trace)


SLIDES = [
    Slide(
        "Proflog, its implementation and rationale",
        (
            "https://github.com/jpt4/proflog        IU Logic Seminar - 2026MAY27",
            "James P. Torre, IV        jpt4@proton.me",
        ),
        (
            "Open with the claim that Proflog is a useful meeting point between "
            "logic programming and proof theory: the evaluator is meant to be a "
            "deductive apparatus, not merely a host-language search routine.",
            "The concrete implementation is a Clojure/core.logic greenfield track "
            "for Melvin Fitting's tableau-based Proflog.",
        ),
    ),
    Slide(
        "Contents",
        (
            "I: Proflog as proof-search programming",
            "II: Fitting tableaus plus Procedure Call",
            "III: Current implementation map",
            "IV: Demonstrations: P1 and P2",
            "V: SJAS motivation and future work",
        ),
        (
            "Use this as the audience contract: first motivate the language, then "
            "show the deductive mechanism, then identify where the current code "
            "faithfully follows Fitting and where implementation choices enter.",
        ),
    ),
    Slide(
        "Logic Programming",
        (
            "Permissive reading: programs are logical statements manipulated by code.",
            "Strict reading: evaluation is the proof procedure of a named logic.",
            "Proflog is strict in intent: a query result is witnessed by a tableau.",
            "The proof object is not an afterthought; it is the computation trace.",
        ),
        (
            "The intent notes distinguish logic programming from arbitrary symbolic "
            "templating over logical syntax. This matters because the project later "
            "asks what the computational equivalent of a logical system is.",
            "A Proflog implementation is interesting only if its operational behavior "
            "can be related back to the deductive apparatus it claims to execute.",
        ),
    ),
    Slide(
        "Semantic Tableaux",
        (
            "To prove T, build a closed tableau for not T.",
            "Alpha rules extend one branch; beta rules split branches.",
            "Gamma introduces reusable free proof variables.",
            "Delta introduces rigid parameters.",
            "A closed branch contains a contradiction or valid theory closure.",
        ),
        (
            "This is the basic tableau discipline underlying both Fitting's paper "
            "and Amin's leanTAP line. The important visual idea is a tree: some "
            "rules add work to the current branch, while beta rules branch.",
            "A query succeeds only when the relevant tableau closes.",
        ),
    ),
    Slide(
        "From Tableaux to Proflog",
        (
            "Fitting adds a Procedure Call rule to first-order tableau proof search.",
            "A defined atom R(t) opens a subsidiary tableau for R's clause body.",
            "A negative atom not R(t) opens one for the negated body.",
            "Clauses are biconditional in behavior, not Horn implications only.",
        ),
        (
            "This is the conceptual bridge from theorem proving to programming. "
            "Procedure calls are not Prolog resolution steps; they are tableau "
            "steps that ask a new proof obligation about the clause body.",
            "Negative calls are therefore classical, not negation-as-failure.",
        ),
    ),
    Slide(
        "A Small Program",
        (
            "p(x) :- x = a.",
            "",
            "query p(a): close the tableau for not p(a)       => succeeds",
            "query p(b): close the tableau for p(b)           => fails",
            "no closure on either side within bounds          => unresolved",
        ),
        (
            "Use the one-clause example from the README. The query API probes both "
            "semidecision directions: success is a closed tableau for the negated "
            "query; failure is a closed tableau for the query itself.",
            "The implementation also reports inconsistent if both closures are found.",
        ),
    ),
    Slide(
        "Source to Kernel",
        (
            "Frontend: pf/language, pf/proflog, pf/q, pf/run.",
            "Compiler: source clauses -> relation entries with body and negated-body.",
            "Kernel: prove-stateo closes branches with explicit branch state.",
            "Query: query-status interleaves success and failure probes.",
        ),
        (
            "Point to the exact code crossover: proflog.frontend builds the public "
            "surface; proflog.language validates and compiles; proflog.program "
            "provides relational clause lookup; proflog.kernel performs tableau "
            "closure; proflog.query exposes the user-facing status probes.",
        ),
    ),
    Slide(
        "Kernel State",
        (
            "fml and unexpanded: current formula and pending branch work.",
            "lits: saved positive and negative atoms.",
            "env: lexical substitution for bound variables.",
            "sigma: explicit equality substitution.",
            "neqs: delayed disequalities.",
            "proof: constructor tree witnessing closure.",
        ),
        (
            "The current kernel makes state explicit that Fitting's presentation can "
            "leave implicit. This is a central engineering decision: equality and "
            "procedure calls interact through saved literals, so branch state must "
            "be inspected after unification and disequality updates.",
        ),
    ),
    Slide(
        "Procedure Calls in Code",
        (
            "program/call-clauseo is relational lookup over compiled clauses.",
            "Positive call: prove the compiled body in a subsidiary tableau.",
            "Negative call: prove the precomputed NNF negated body.",
            "Equality can later make a saved atom callable.",
        ),
        (
            "The Procedure Call rule appears concretely in proflog.kernel as "
            "pos-call, neg-call, and equality-triggered variants. The saved-call "
            "path is important: whether a call becomes ground before or after an "
            "equality step should not change completeness.",
        ),
    ),
    Slide(
        "Proof Objects",
        (
            "Proof search returns structured terms: split, conj, close, savefml, ...",
            "Procedure evidence records pos-call, neg-call, and triggered calls.",
            "Proof objects are used for tests, diagnostics, and SJAS reflection.",
            "The implementation is therefore executable proof theory.",
        ),
        (
            "Stress that the proof object is not decorative. It is the artifact that "
            "lets the implementation be audited against Fitting's rules and later "
            "be encoded for self-justifying axiom-system work.",
        ),
    ),
    Slide(
        "P1 Program",
        (
            "Paper-equivalent Proflog:",
            "even(x) <-",
            "  x = 0 or exists y.(x = s(y) and odd(y))",
            "",
            "odd(x) <-",
            "  forall y.(even(y) => x != y)",
        ),
        (
            "This slide turns the P1 demo into a worked example. It is the paper's "
            "original forall-based odd clause, rendered in the talk's Proflog "
            "notation.",
            "Implementation anchor: proflog.fitting-programs/p1-program builds the "
            "same structure through the public AST and language compiler.",
        ),
    ),
    Slide(
        "P1 Output",
        (
            "Run:",
            "  evaluate-case :p1-even-0-succeeds",
            "  evaluate-case :p1-odd-1-succeeds",
            "",
            "Results:",
            "  even(0)  => :succeeds, root neg-call-guarded-alt",
            "  odd(s(0)) => :succeeds, root neg-call",
            "  both carry proof-count 1",
        ),
        (
            "The output is from `lein run -m proflog.fitting-programs "
            "p1-even-0-succeeds p1-odd-1-succeeds ...`.",
            "The important fact for the talk is not merely the boolean status. The "
            "result includes a proof count, a proof root, and ordered proof-step "
            "evidence from `proflog.proof/collect-steps`.",
        ),
    ),
    Slide(
        "P1 Proof Traces",
        (
            "even(0) trace:",
            "  neg-call-guarded-alt > guarded-alt > guard-eq",
            "  > decompose > guarded-seq-done",
            "",
            "odd(s(0)) trace:",
            "  neg-call > witness > conj > eq-step > par-bind",
            "  > pos-call > split > free-close > univ > refl-close",
        ),
        (
            "Full even(0) proof steps: " + trace_line(P1_EVEN_TRACE),
            "Full odd(s(0)) proof steps: " + trace_line(P1_ODD_TRACE),
            "Read this as the executable tableau trace: negative procedure call, "
            "witness/equality work, subsidiary positive calls, branching, and "
            "closure evidence.",
        ),
    ),
    Slide(
        "P2 Program",
        (
            "Paper-equivalent Proflog:",
            "win(x) <- exists y.",
            "  ((x = s(y) or x = s(s(y)))",
            "   and not win(y))",
            "",
            "One-clause Nim: remove one or two tokens.",
            "Move logic stays inline, per Fitting's warning.",
        ),
        (
            "This is Fitting's P2 shape as an executable Proflog clause. The move "
            "predicate is not factored into a helper relation because that factoring "
            "is a known semantic trap in the paper and in the implementation tests.",
            "Implementation anchor: proflog.fitting-programs/p2-program.",
        ),
    ),
    Slide(
        "P2 Output",
        (
            "Run:",
            "  evaluate-case :p2-win-4-succeeds",
            "  evaluate-case :p2-win-3-fails",
            "",
            "Results:",
            "  win(4) => :succeeds, root neg-call",
            "  win(3) => :fails, root pos-call",
            "  both carry proof-count 1",
        ),
        (
            "In the query API, success for win(4) means a closed tableau for the "
            "negated query. Failure for win(3) means a closed tableau for the "
            "positive query.",
            "The root tags make that visible: win(4) starts from neg-call, while "
            "win(3) starts from pos-call.",
        ),
    ),
    Slide(
        "P2 Proof Traces",
        (
            "win(4) trace:",
            "  neg-call > once-univ > split > conj > neq-close",
            "  > pos-call > eq-triggered-neg-call > free-close",
            "",
            "win(3) trace:",
            "  pos-call > witness > conj > savefml > split",
            "  > eq-triggered-neg-call > pos-call > free-close",
        ),
        (
            "Full win(4) proof steps: " + trace_line(P2_WIN4_TRACE),
            "Full win(3) proof steps: " + trace_line(P2_WIN3_TRACE),
            "P2 is the compact demonstration that recursive classical negation is "
            "being handled as proof search over subsidiary tableaux, not as a "
            "Prolog-style negation-as-failure convention.",
        ),
    ),
    Slide(
        "Beyond the Paper",
        (
            "Proof-producing query API with bounded iterative deepening.",
            "Answer export and residuals for open variables.",
            "Explicit equality, disequality, and delayed-call machinery.",
            "Opt-in profiles: Robinson Q, constructor recursion, Willard SJAS.",
        ),
        (
            "Make clear that these are implementation layers around the deductive "
            "core. They are useful, but they must remain accountable to the logic. "
            "This theme recurs in the SJAS section, where shortcuts become suspect.",
        ),
    ),
    Slide(
        "Performance Discipline",
        (
            "core.logic makes the tableau kernel relational but search-sensitive.",
            "Fast gate: lein test-proflog-fast.",
            "Extended gate: lein test-proflog-extended.",
            "Resource-heavy SJAS work uses focused var-by-var timing.",
            "Performance work is subordinate to proof-rule correctness.",
        ),
        (
            "The current development practice separates semantic regressions from "
            "deep synthesis and recursive probes. For this talk, the point is that "
            "tractability is a real engineering problem, but it should not obscure "
            "whether the intended proof relation has been implemented.",
        ),
    ),
    Slide(
        "Why SJAS Enters",
        (
            "Willard-style SJAS asks a system to reason about its own proofs.",
            "Then implementation details can become mathematically relevant.",
            "A shortcut through the host kernel may preserve theorem extension.",
            "But self-reference can depend on proof shape, code size, and closure.",
        ),
        (
            "This is the transition from Proflog as a language implementation to "
            "Proflog as an object of proof-theoretic scrutiny. If a proof predicate "
            "talks about the deductive apparatus, replacing that apparatus with a "
            "host callback must be justified, not assumed harmless.",
        ),
    ),
    Slide(
        "SJAS Internalization",
        (
            "System, theorem, proof, and substitution codes are inspected as bytes.",
            "Recent work removed host public-code byte projectors.",
            "tableau-proof and subst-prf now use local proof-check relations.",
            "Remaining work: more proof constructors, signature coding, tractability.",
        ),
        (
            "Summarize the current branch without overclaiming. The project has "
            "moved proof predicates away from direct kernel validation and toward "
            "arithmetized object-language checks, but full arithmetic internalization "
            "is not finished.",
        ),
    ),
    Slide(
        "Autarkic Formal Systems",
        (
            "Question: what parts of a formal system are determined internally?",
            "Consistency, definability, decidability, interpretation, replication.",
            "Proflog supplies an executable setting for intensional proof questions.",
            "SJAS supplies the pressure test: proof machinery must account for itself.",
        ),
        (
            "Tie the talk back to the broader research program. The computational "
            "lesson is that equivalence at the theorem level may be too weak when "
            "the system can encode statements about the proof procedure itself.",
        ),
    ),
    Slide(
        "References",
        (
            "Fitting, Tableaus for Logic Programming, 1993.",
            "Amin, leanTAP / alphaleanTAP line.",
            "Willard, self-justifying axiom systems.",
            "Project: github.com/jpt4/proflog",
            "Support: FUTO Fellowship; Bloominglabs; Seth Frey, UC Davis.",
            "AI disclosure: Codex GPT-5.4/5.5; Claude Opus 4.6/4.7.",
        ),
        (
            "Close with references and disclosure. Mention that the phrase 'animate "
            "literature' is apt here: the implementation is a way of making the "
            "proof-theoretic literature executable and then interrogating the gaps.",
        ),
    ),
]


def xml_text(value: str) -> str:
    return html.escape(value, quote=True)


def paragraphs(lines: tuple[str, ...], style: str = "P10", span: str = "T6") -> str:
    out = []
    for line in lines:
        if line == "":
            out.append(f'<text:p text:style-name="{style}"/>')
        else:
            out.append(
                f'<text:p text:style-name="{style}">'
                f'<text:span text:style-name="{span}">{xml_text(line)}</text:span>'
                "</text:p>"
            )
    return "".join(out)


def notes_block(slide_no: int, notes: tuple[str, ...]) -> str:
    note_paragraphs = paragraphs(notes, style="P9", span="T6")
    return (
        '<presentation:notes draw:style-name="dp2">'
        '<draw:page-thumbnail draw:style-name="gr5" draw:layer="layout" '
        'svg:width="18.624cm" svg:height="10.476cm" svg:x="1.482cm" '
        f'svg:y="2.123cm" draw:page-number="{slide_no}" presentation:class="page"/>'
        '<draw:frame presentation:style-name="pr3" draw:text-style-name="P9" '
        'draw:layer="layout" svg:width="17.271cm" svg:height="12.572cm" '
        'svg:x="2.159cm" svg:y="13.271cm" presentation:class="notes">'
        f"<draw:text-box>{note_paragraphs}</draw:text-box>"
        "</draw:frame>"
        "</presentation:notes>"
    )


def title_page(slide: Slide, slide_no: int) -> str:
    return (
        f'<draw:page draw:name="page{slide_no}" draw:style-name="dp1" '
        'draw:master-page-name="Default" presentation:presentation-page-layout-name="AL1T0">'
        '<office:forms form:automatic-focus="false" form:apply-design-mode="false"/>'
        '<draw:frame presentation:style-name="pr1" draw:text-style-name="P1" draw:layer="layout" '
        'svg:width="27.991cm" svg:height="1.878cm" svg:x="0cm" svg:y="0.027cm" '
        'presentation:class="title" presentation:user-transformed="true"><draw:text-box>'
        '<text:p><text:s/></text:p></draw:text-box></draw:frame>'
        '<draw:frame presentation:style-name="pr2" draw:text-style-name="P3" draw:layer="layout" '
        'svg:width="25.191cm" svg:height="9.318cm" svg:x="1.399cm" svg:y="3.592cm" '
        'presentation:class="subtitle" presentation:user-transformed="true"><draw:text-box>'
        '<text:p/></draw:text-box></draw:frame>'
        '<draw:frame draw:style-name="gr1" draw:text-style-name="P4" draw:layer="layout" '
        'svg:width="27.991cm" svg:height="1.237cm" svg:x="0cm" svg:y="14.705cm">'
        '<draw:text-box><text:p/></draw:text-box></draw:frame>'
        '<draw:frame draw:style-name="gr2" draw:text-style-name="P6" draw:layer="layout" '
        'svg:width="22.52cm" svg:height="4.913cm" svg:x="2.838cm" svg:y="5.418cm">'
        '<draw:text-box><text:p text:style-name="P5">'
        f'<text:span text:style-name="T2">{xml_text(slide.title)}</text:span>'
        '</text:p></draw:text-box></draw:frame>'
        '<draw:frame draw:style-name="gr3" draw:text-style-name="P7" draw:layer="layout" '
        'svg:width="23.26cm" svg:height="0.887cm" svg:x="2.468cm" svg:y="0.495cm">'
        '<draw:text-box><text:p>'
        f'<text:span text:style-name="T3">{xml_text(slide.lines[0])}</text:span>'
        '</text:p></draw:text-box></draw:frame>'
        '<draw:frame draw:style-name="gr3" draw:text-style-name="P7" draw:layer="layout" '
        'svg:width="23.26cm" svg:height="0.887cm" svg:x="2.468cm" svg:y="14.896cm">'
        '<draw:text-box><text:p>'
        f'<text:span text:style-name="T3">{xml_text(slide.lines[1])}</text:span>'
        '</text:p></draw:text-box></draw:frame>'
        '<draw:frame draw:style-name="gr4" draw:text-style-name="P8" draw:layer="layout" '
        'svg:width="1.905cm" svg:height="0.886cm" svg:x="13.043cm" svg:y="14.862cm">'
        '<draw:text-box><text:p text:style-name="P5">'
        f'<text:span text:style-name="T4">{slide_no}</text:span>'
        '</text:p></draw:text-box></draw:frame>'
        f"{notes_block(slide_no, slide.notes)}</draw:page>"
    )


def body_page(slide: Slide, slide_no: int) -> str:
    return (
        f'<draw:page draw:name="page{slide_no}" draw:style-name="dp1" '
        'draw:master-page-name="Default" presentation:presentation-page-layout-name="AL1T0">'
        '<office:forms form:automatic-focus="false" form:apply-design-mode="false"/>'
        '<draw:frame presentation:style-name="pr1" draw:text-style-name="P1" draw:layer="layout" '
        'svg:width="27.991cm" svg:height="1.878cm" svg:x="0cm" svg:y="0.027cm" '
        'presentation:class="title" presentation:user-transformed="true"><draw:text-box>'
        '<text:p><text:s/></text:p></draw:text-box></draw:frame>'
        '<draw:frame presentation:style-name="pr2" draw:text-style-name="P11" draw:layer="layout" '
        'svg:width="25.191cm" svg:height="9.318cm" svg:x="1.399cm" svg:y="3.592cm" '
        'presentation:class="subtitle" presentation:user-transformed="true"><draw:text-box>'
        f"{paragraphs(slide.lines)}</draw:text-box></draw:frame>"
        '<draw:frame draw:style-name="gr1" draw:text-style-name="P4" draw:layer="layout" '
        'svg:width="27.991cm" svg:height="1.237cm" svg:x="0cm" svg:y="14.705cm">'
        '<draw:text-box><text:p/></draw:text-box></draw:frame>'
        '<draw:frame draw:style-name="gr6" draw:text-style-name="P6" draw:layer="layout" '
        'svg:width="22.52cm" svg:height="1.805cm" svg:x="2.838cm" svg:y="0cm">'
        '<draw:text-box><text:p text:style-name="P5">'
        f'<text:span text:style-name="T2">{xml_text(slide.title)}</text:span>'
        '</text:p></draw:text-box></draw:frame>'
        '<draw:frame draw:style-name="gr4" draw:text-style-name="P8" draw:layer="layout" '
        'svg:width="1.905cm" svg:height="0.886cm" svg:x="13.043cm" svg:y="14.862cm">'
        '<draw:text-box><text:p text:style-name="P5">'
        f'<text:span text:style-name="T4">{slide_no}</text:span>'
        '</text:p></draw:text-box></draw:frame>'
        f"{notes_block(slide_no, slide.notes)}</draw:page>"
    )


def pages_xml() -> str:
    pages = [title_page(SLIDES[0], 1)]
    pages.extend(body_page(slide, idx) for idx, slide in enumerate(SLIDES[1:], start=2))
    return "".join(pages)


def replace_pages(content: str) -> str:
    first = content.find("<draw:page ")
    last = content.rfind("</draw:page>")
    if first == -1 or last == -1:
        raise RuntimeError("sample content.xml does not contain draw:page entries")
    last += len("</draw:page>")
    return content[:first] + pages_xml() + content[last:]


def write_odp() -> None:
    with zipfile.ZipFile(SAMPLE, "r") as source:
        content = source.read("content.xml").decode("utf-8")
        new_content = replace_pages(content).encode("utf-8")
        temp_path = OUT_ODP.with_suffix(".odp.tmp")
        with zipfile.ZipFile(temp_path, "w") as out:
            # ODF requires mimetype to be the first entry and uncompressed.
            out.writestr(
                zipfile.ZipInfo("mimetype"),
                source.read("mimetype"),
                compress_type=zipfile.ZIP_STORED,
            )
            for item in source.infolist():
                if item.filename in {"mimetype", "content.xml"}:
                    continue
                out.writestr(item, source.read(item.filename))
            out.writestr("content.xml", new_content, compress_type=zipfile.ZIP_DEFLATED)
        os.replace(temp_path, OUT_ODP)


def write_notes() -> None:
    lines = [
        "# Proflog, its implementation and rationale",
        "",
        "Lecture notes for the IULS 2026 slide deck.",
        "",
    ]
    for idx, slide in enumerate(SLIDES, start=1):
        lines.extend([f"## {idx}. {slide.title}", "", "Slide text:", ""])
        lines.extend(f"- {line}" if line else "" for line in slide.lines)
        lines.extend(["", "Speaker notes:", ""])
        lines.extend(f"- {note}" for note in slide.notes)
        lines.append("")
    OUT_NOTES.write_text("\n".join(lines), encoding="utf-8")


def write_slide_source() -> None:
    lines = ["# Proflog IULS 2026 Slide Source", ""]
    for idx, slide in enumerate(SLIDES, start=1):
        lines.extend([f"## Slide {idx}: {slide.title}", ""])
        lines.extend(line if line else "" for line in slide.lines)
        lines.append("")
    OUT_SLIDES.write_text("\n".join(lines), encoding="utf-8")


def main() -> None:
    write_odp()
    write_notes()
    write_slide_source()
    print(f"wrote {OUT_ODP}")
    print(f"wrote {OUT_NOTES}")
    print(f"wrote {OUT_SLIDES}")


if __name__ == "__main__":
    main()
