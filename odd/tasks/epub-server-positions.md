# EPUB progression rejection
Use Komga's published positions rather than reconstructing its server-side position set.
Status: RESOLVED — Root causes identified in Komga 1.27.1-master and client sync queue; tests passing. Awaiting user verification and commit authorization.

## Specs
S1. "estaba actualizando bien el progreso en el servidor komga del epub con el que estaba haciendo pruebas. Pero ahora probando con otros epub no funciona, dice que el servidor ha rechazado por el servidor".
S2. "te he dejado uno de los libros que fallan @El_agente_de_las_estrellas.epub , no te pouedo dar mas datos sobre el error. Solo guarda progreso local"
S3. "localizador rechazado poe el servidor, vuelve a abrir el libro para reintentar, version del server 1.27.1-master"
S4. User inquiry: "¿No se si tiene que ver con la introduccion de un nuevo feature con el slider del lector de epub?"

## Tasks
T1 [done] S1-S2 — Inspect supplied EPUB and Komga validation/positions contract.
T2 [done] S1-S4 — Identify root cause in Komga 1.27.1-master (`BookLifecycle.kt`, `MediaExtensionEpub`) and client logic.
T3 [done] S1-S4 — Make progression sync resilient (resilient queue initialization, discrete fallback, path flexibility, exact server position selection).
T4 [done] S1-S4 — Verify all 126 unit tests pass, including real reproduction with `El_agente_de_las_estrellas.epub`.

## Log
L1 Original report: "estaba actualizando bien el progreso en el servidor komga del epub con el que estaba haciendo pruebas. Pero ahora probando con otros epub no funciona, dice que el servidor ha rechazado por el servidor"
L2 Supplied input: "te he dejado uno de los libros que fallan @El_agente_de_las_estrellas.epub , no te pouedo dar mas datos sobre el error. Solo guarda progreso local"
L3 Server version: 1.27.1-master.
L4 Komga Backend Analysis (`BookLifecycle.kt` & `BookAnalyzer.kt`):
   - Komga's `markProgression` requires:
     a) `URLDecoder.decode(href) in media.files.map { it.fileName }`.
     b) `MediaExtensionEpub` exists; if missing (e.g. unanalyzed or legacy book), Komga throws `IllegalArgumentException("Epub extension not found")` -> HTTP 400.
     c) For reflowable EPUBs without an exact match on progression, Komga requires both `before` and `after` positions (`progression < target` and `progression > target`). When `target = 1.0` (end of chapter/book) or on short chapters with only 1 position (covers, dedications), `after` is null, causing `IllegalArgumentException("Invalid progression")` -> HTTP 400.
L5 Slider Relation: The slider scrubber itself did not alter the network sync protocol, but scrubbing exposes the exact boundaries (`proportion = 1.0` at chapter ends or seeking into short/single-position chapters). With discrete progression, reaching 1.0 triggers the missing `after` position in Komga.
L6 Checkpoint cf56d38 flaws:
   - `syncQueue` initialization was gated on `published.isSuccess`. If `GET /positions` returned 404 (common on unanalyzed books), syncQueue was never started, permanently disabling sync.
   - Strict path matching failed on OPF-relative vs root-relative differences.
L7 Fixes applied:
   - Made `EpubServerPositions.create` resilient: duplicate spine paths allowed, partial chapters without positions supported (ignoring short/empty chapters instead of failing), bidirectional suffix path matching (`OEBPS/` prefixes), and filtering unknown candidate positions.
   - Fixed `EpubHtml.kt` SVG cover handling to permit relative path resolution with `..` (e.g. `../Images/cover.jpg` inside `cubierta.xhtml`).
   - Enhanced `KomgaApiClient.putEpubProgression` to capture and surface Komga error details without breaking response message expectations.
   - Initialized `syncQueue` whenever `remote.isSuccess` is true; fallback to discrete progression if `/positions` returns 404.
   - All unit tests verified.
