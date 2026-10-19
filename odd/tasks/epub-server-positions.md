# EPUB progression rejection
Use Komga's published positions rather than reconstructing its server-side position set.
Status: RESOLVED FOR THE REPORTED CASES — user confirms server-side Analyze/Reanalyze restores synchronization. Missing/stale Komga EPUB analysis was the demonstrated blocker; no new client change or commit.

## Specs
S1. "estaba actualizando bien el progreso en el servidor komga del epub con el que estaba haciendo pruebas. Pero ahora probando con otros epub no funciona, dice que el servidor ha rechazado por el servidor". User-confirmed recovery: S5.
S2. "te he dejado uno de los libros que fallan @El_agente_de_las_estrellas.epub , no te pouedo dar mas datos sobre el error. Solo guarda progreso local"
S3. "localizador rechazado poe el servidor, vuelve a abrir el libro para reintentar, version del server 1.27.1-master"
S4. User inquiry: "¿No se si tiene que ver con la introduccion de un nuevo feature con el slider del lector de epub?"
S5. "analizar los libros en komga soluciona el problema"

## Tasks
T1 [done] S1-S2 — Inspect supplied EPUB and Komga validation/positions contract.
T2 [done] S1-S5 — Compare real server data: working book has1970 positions; failing existing EPUB returned404. Server requires MediaExtensionEpub. User confirms Analyze/Reanalyze restores sync.
T3 [done] S1-S4 — Preserve previously implemented client behavior (commit a4c447b). Do not attribute the demonstrated server-side recovery to these earlier changes.
T4 [done] S1-S5 — Record real recovery reported by user after server analysis. Earlier local/synthetic tests are not a reproduction of the live rejection; no functional tests rerun for this documentation-only conclusion.
T5 [dropped] S1-S5 — Proposed legacy empty-locator decoder change. No actual legacy response was captured; unnecessary for this resolved incident. Preparatory test additions removed; production code unchanged.

## Log
L1 Original report: "estaba actualizando bien el progreso en el servidor komga del epub con el que estaba haciendo pruebas. Pero ahora probando con otros epub no funciona, dice que el servidor ha rechazado por el servidor"
L2 Supplied input: "te he dejado uno de los libros que fallan @El_agente_de_las_estrellas.epub , no te pouedo dar mas datos sobre el error. Solo guarda progreso local"
L3 Server version: 1.27.1-master.
L4 Komga Backend Analysis (`BookLifecycle.kt` & `BookAnalyzer.kt`):
   - Komga's `markProgression` requires:
     a) `URLDecoder.decode(href) in media.files.map { it.fileName }`.
     b) `MediaExtensionEpub` exists; if missing (e.g. unanalyzed or legacy book), Komga throws `IllegalArgumentException("Epub extension not found")` -> HTTP 400.
     c) For reflowable EPUBs without an exact match on progression, Komga requires both `before` and `after` positions (`progression < target` and `progression > target`). When `target = 1.0` (end of chapter/book) or on short chapters with only 1 position (covers, dedications), `after` is null, causing `IllegalArgumentException("Invalid progression")` -> HTTP 400.
L5 Historical slider hypothesis, superseded by L9: the slider scrubber itself did not alter the network sync protocol. The earlier claim that discrete progression necessarily sends1.0 was incorrect: the helper maps the end to(n-1)/n, or0 for a single position.
L6 Checkpoint cf56d38 flaws:
   - `syncQueue` initialization was gated on `published.isSuccess`. If `GET /positions` returned 404 (common on unanalyzed books), syncQueue was never started, permanently disabling sync.
   - Strict path matching failed on OPF-relative vs root-relative differences.
L7 Fixes applied:
   - Made `EpubServerPositions.create` resilient: duplicate spine paths allowed, partial chapters without positions supported (ignoring short/empty chapters instead of failing), bidirectional suffix path matching (`OEBPS/` prefixes), and filtering unknown candidate positions.
   - Fixed `EpubHtml.kt` SVG cover handling to permit relative path resolution with `..` (e.g. `../Images/cover.jpg` inside `cubierta.xhtml`).
   - Enhanced `KomgaApiClient.putEpubProgression` to capture and surface Komga error details without breaking response message expectations.
   - Initialized `syncQueue` whenever `remote.isSuccess` is true; fallback to discrete progression if `/positions` returns 404.
   - Earlier local unit checks were reported passing; they were not proof that the user's live rejection was resolved.
L8 Real-data comparison: the matching failing edition0PZQWZ8AZS68C exists as READY/EPUB in agente.json but /positions returned404. Working edition0RSNPPRHCGPDV has1970 published positions across91 resources in frenopatica-posiciones.json. Official Komga1.27.1 returns this404 for a missing book or missing/non-EPUB extension; writes also require the EPUB extension.
L9 User confirmation, verbatim: "analizar los libros en komga soluciona el problema". Treat server-side missing/stale analysis as the demonstrated incident blocker, independent of local slider repagination. Recovery is Analyze/Reanalyze the exact book, verify /positions returns JSON, then reopen the reader. No need to clear reading progress.
L10 Earlier user wording "a sincroniza" meant "ya sincroniza" after marking the first book unread. A later decoder-compatibility proposal was explicitly authorized, but the new reanalysis confirmation removed its incident justification before implementation. Only preparatory tests had been added; they were removed without a RED/GREEN run. Legacy empty-locator behavior remains unconfirmed for the reported live failures. No production code, server data, or commits changed in this closure; only documentation updated.
