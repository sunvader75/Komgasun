# Bounded software-paint experiments

Synthetic fixture: 67 identical paragraphs, 320x480dp software-layer EpubWebView; EpubHtml.apply at 20px font, 33px line height. Raster obtained through WebView.draw(Canvas(bitmap)) after a visual-state callback.

First/final first-line dark-pixel run heights:

- Baseline: 53/38 px.
- Experiment 1, html -webkit-text-size-adjust:none!important: 53/38 px. Computed adjustment became 100%; no repair.
- Experiment 2, multicol pages contain:paint!important via stylesheet: 53/0 px. Clips overflowing columns; rejected. Initial diagnostic stopped at the blank-page assertion; subsequent diagnostic recorded blank as zero to complete the bounded experiments.
- Experiment 3, append empty block with height innerHeight-lastParagraph.getBoundingClientRect().bottom (117 CSS px): 53/38 px. No repair.

All cases retained last paragraph DOM font-size 20px, line-height 33px, rect top/bottom 330/363, viewport height 480 CSS px. Thus DOM typography alone does not capture the raster defect; disabling autosizing did not repair it. This does not establish a Chromium root cause.

No experimental production changes retained. Diagnostic loop reverted to the synchronized failing regression. No book content copied. Device emulator-5554. Build succeeded; focused instrumentation deliberately failed while reporting experimental measurements. No broad suite repeated.
