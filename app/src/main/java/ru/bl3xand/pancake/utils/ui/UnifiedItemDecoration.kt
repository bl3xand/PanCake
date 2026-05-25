package ru.bl3xand.pancake.utils.ui

import android.graphics.Rect
import android.view.View
import androidx.annotation.DimenRes
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import ru.bl3xand.pancake.R

/**
 * Единый механизм отступов для всех экранов приложения.
 *
 * Паддинги внутри разделителя (paddingTop/Bottom) заданы в стиле Widget.Pancake.SectionHeader.
 * Все внешние отступы (между карточками, от края экрана) — только здесь.
 *
 * КАРТОЧКИ:
 *  - left/right = card_side_margin
 *  - top = card_vertical_spacing (только после header или в начале списка, иначе 0)
 *  - bottom = card_vertical_spacing
 *
 * HEADER (разделитель):
 *  - left/right = 0 (header занимает всю ширину)
 *  - top = header_external_top_gap
 *  - bottom = 0 (отступ после header берёт на себя карточка через свой top)
 *
 * GRID (spanCount > 1):
 *  - left  = sidePx - col * sidePx / spanCount
 *  - right = (col + 1) * sidePx / spanCount
 *  → отступ от края = расстояние между колонками = sidePx
 */
class UnifiedItemDecoration(
    @DimenRes private val sideMarginRes: Int = R.dimen.card_side_margin,
    @DimenRes private val verticalSpacingRes: Int = R.dimen.card_vertical_spacing,
    @DimenRes private val headerTopGapRes: Int = R.dimen.header_external_top_gap,
    private val spanCount: Int = 1,
    private val isHeader: (Int) -> Boolean = { false }
) : RecyclerView.ItemDecoration() {

    private var sidePx: Int = -1
    private var vertPx: Int = -1
    private var headerTopGapPx: Int = -1

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        if (sidePx < 0) {
            sidePx = view.resources.getDimensionPixelSize(sideMarginRes)
            vertPx = view.resources.getDimensionPixelSize(verticalSpacingRes)
            headerTopGapPx = view.resources.getDimensionPixelSize(headerTopGapRes)
        }

        val pos = parent.getChildAdapterPosition(view)
        if (pos == RecyclerView.NO_POSITION) return

        val isFirstPos = pos == 0
        val prevIsHeader = pos > 0 && isHeader(pos - 1)
        val nextIsHeader = (pos + 1) < state.itemCount && isHeader(pos + 1)

        if (isHeader(pos)) {
            outRect.left = 0
            outRect.right = 0
            // Единый внешний отступ над разделителями.
            outRect.top = headerTopGapPx
            outRect.bottom = 0
        } else {
            // Если сразу после карточки идёт разделитель, нижний отступ у карточки не нужен.
            outRect.bottom = if (nextIsHeader) 0 else vertPx

            if (spanCount <= 1) {
                outRect.left = sidePx
                outRect.right = sidePx
                // Карточки сразу после header или первая в списке получают top
                outRect.top = if (isFirstPos || prevIsHeader) vertPx else 0
            } else {
                val col = when (val lp = view.layoutParams) {
                    is GridLayoutManager.LayoutParams -> lp.spanIndex
                    is StaggeredGridLayoutManager.LayoutParams -> lp.spanIndex
                    else -> pos % spanCount
                }
                // includeEdge-подобная формула: одинаковый интервал между колонками и по краям
                outRect.left = sidePx - col * sidePx / spanCount
                outRect.right = (col + 1) * sidePx / spanCount

                // В Grid обе карточки первой строки секции получают одинаковый top.
                val lastHeaderPos = findPreviousHeaderPosition(pos)
                val isFirstRowInSection = if (lastHeaderPos >= 0) {
                    (pos - lastHeaderPos - 1) < spanCount
                } else {
                    pos < spanCount
                }
                outRect.top = if (isFirstRowInSection || prevIsHeader) vertPx else 0
            }
        }
    }

    private fun findPreviousHeaderPosition(fromPosition: Int): Int {
        var i = fromPosition - 1
        while (i >= 0) {
            if (isHeader(i)) return i
            i--
        }
        return -1
    }
}
