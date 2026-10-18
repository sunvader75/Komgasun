# EPUB library cover progress
Show EPUB cover progress and distinguish server positions from reader-layout pages.
Status: implementation and functional checks complete; independent review blocked. No commits without explicit user authorization.

## Specs
S1. "Falta un medidor de progreso en los epub dentro de las bibliotecas. Un pdf en lectura te muestra la portada y el porcentaje de progreso. En los epub no, deberia mostrarse visualmente tanto la barra como el porcentaje de progreso."
S2. "Tambien parece que no corresponde el numero de paginas que muestra en la portada que las calculadas con el slider"

## Tasks
T1 [done] S1-S2 — Persist an account/server/book-scoped reader summary; enrich library books without using Komga position numbers as physical pages. Route: inline (subagent provider unavailable). Commit: none, explicit authorization required.
T2 [done] S1-S2 — Display progress bar/percentage and the last reader-calculated total on shared covers; preserve PDF/comic/unread/completed behavior. Route: inline. Commit: none.
T3 [done] S1-S2 — Run focused RED/GREEN, unit and scoped device checks; document data availability and verification limits. Route: inline. Commit: none.
T4 [blocked] S1-S2 — Independent verification required by concurrency item 4. Waiting for configured subagent credentials or explicit human disposition of missing review. No automatic retry. Commit: none.

## Log
L1 User request: "lo he probado yo y va bien, pasemos a otra cosa. Falta un medidor de progreso en los epub dentro de las bibliotecas. Un pdf en lectura te muestra la portada y el porcentaje de progreso. En los epub no, deberia mostrarse visualmente tanto la barra como el porcentaje de progreso. Tambien parece que no corresponde el numero de paginas que muestra en la portada que las calculadas con el slider"
L2 Evidence: BookReadingStatus explicitly suppresses EPUB fractions. BookGridItem displays media.pagesCount for every format. Both library/continue-reading and series grids reuse BookGridItem and reload on resume.
L3 Implementation decision: persist the last reader-known whole-book fraction and optional exact total for that layout. Prefer that summary on covers. Without a local summary, use only a valid remote locator.locations.totalProgression, not locator.position/readProgress.page divided by an unrelated total. Hide EPUB physical-page badge until the reader has a measured total. Keep remote locator API and its chapter-relative progression unchanged.
L4 Prior reader task: user reports it works on their device and explicitly stops that diagnostic. Prior emulator gesture failures are retained as known, not retroactively marked passed.
L5 T1 evidence: job-17 connected EpubCoverProgressStoreTest exit 0; persisted total/fraction survive reconstruction, server/account/book are isolated, rejected invalid input leaves previous record unchanged, targeted clear does not remove another account's data. Reader captures the current measured map without changing locator synchronization. No commit (explicit authorization required).
L6 T2 evidence: job-18 passed focused BookReadingStatus unit tests and 9 scoped cover/store device tests (8 cover scenarios plus persistence/isolation). Self-review found explicit unread must also clear the UI copy; RED observed in job-19 and GREEN in job-20. PDF/comic page counts and completed/unavailable states retain their tests. No commit (explicit authorization required).
L7 T3 evidence: job-21 `bash gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.sunvader.komgasun.ui.books --console=plain` exited 0. XML reports: 119 unit tests and 9 device tests, zero failures/errors/skips. README documents opening pre-existing EPUB readings once and suppressing unknown physical totals. Existing earlier reader emulator failures were not rerun, per L4.
L8 T4: native assessment escalated concurrency item 4; native scope assessment could not resolve untracked paths. Independent subagent provider remains unavailable (No API key found for openai), established earlier in this session; no repeat launch. Implementation is not represented as independently reviewed. Await credentials or explicit human decision. No commits or publishing.
