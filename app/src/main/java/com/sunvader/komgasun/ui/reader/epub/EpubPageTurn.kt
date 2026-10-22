package com.sunvader.komgasun.ui.reader.epub

/** Gesture decisions and speculative destinations never mutate reading progress. */
internal object EpubPageTurn {
    fun complete(progress: Float, forwardVelocity: Float) = progress >= 1f / 3f || forwardVelocity > 1.2f
    fun tapDirection(ratio: Float) = when {
        ratio < 0.22f -> -1
        ratio > 0.78f -> 1
        else -> 0
    }
    fun destination(state: EpubReaderUiState, chapters: Int, direction: Int): EpubReaderUiState? {
        val source = EpubPosition(state.currentChapterIndex, state.page)
        val target = EpubPagination.move(source, state.pageCount, chapters, direction)
        if (target == source) return null
        val crossing = target.chapter != source.chapter
        return state.copy(currentChapterIndex = target.chapter, page = target.page,
            proportion = if (crossing) { if (direction > 0) 0.0 else 1.0 }
                else EpubPagination.proportion(target.page, state.pageCount),
            chapterGeneration = if (crossing) state.chapterGeneration + 1 else state.chapterGeneration)
    }
}
