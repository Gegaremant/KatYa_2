package com.katya.app.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Полосы прокрутки на десктопе — no-op.
 *
 * На Android полоса рисуется системно, и свой код был нужен, чтобы прятать её в
 * отдельных местах. На десктопе полоса мышиная, системная и всегда уместна, так
 * что рисовать свою было бы лишним. Общий код зовёт эти функции безусловно:
 * пустое тело здесь означает «системная полоса и так есть», а не «забыли
 * реализовать».
 */
@Composable
actual fun VerticalScrollbarForList(
    listState: LazyListState,
    modifier: Modifier,
) {
}

@Composable
actual fun VerticalScrollbarForScroll(
    scrollState: ScrollState,
    modifier: Modifier,
) {
}

@Composable
actual fun VerticalScrollbarForGrid(
    gridState: LazyGridState,
    modifier: Modifier,
) {
}
