package com.blockblast.helper.model

import android.graphics.RectF

data class CellOffset(val dRow: Int, val dCol: Int)

data class BlockShape(
    val cells: List<CellOffset>
) {
    val height: Int = (cells.maxOfOrNull { it.dRow } ?: 0) + 1
    val width: Int = (cells.maxOfOrNull { it.dCol } ?: 0) + 1
    val cellCount: Int = cells.size

    fun normalized(): BlockShape {
        if (cells.isEmpty()) return this
        val minR = cells.minOf { it.dRow }
        val minC = cells.minOf { it.dCol }
        return BlockShape(cells.map { CellOffset(it.dRow - minR, it.dCol - minC) })
    }
}

data class PlacementStep(
    val slotIndex: Int,      // 0, 1, 2 (하단 왼쪽/가운데/오른쪽)
    val order: Int,          // 1, 2, 3 (배치 순서)
    val block: BlockShape,
    val boardRow: Int,       // 0..7
    val boardCol: Int,       // 0..7
    val clearedLines: Int    // 이 배치로 지워지는 줄 수
)

data class SolveResult(
    val steps: List<PlacementStep>,
    val totalLinesCleared: Int,
    val isCrisisMode: Boolean,
    val evaluationScore: Double
)

enum class HelperMode {
    IDLE,
    RECOMMEND,
    AUTO_PLAY
}

data class BoardCalibration(
    val boardRect: RectF,
    val slotRects: List<RectF>,
    val scoreRect: RectF
) {
    fun cellCenter(row: Int, col: Int): Pair<Float, Float> {
        val cellW = boardRect.width() / 8f
        val cellH = boardRect.height() / 8f
        val x = boardRect.left + (col + 0.5f) * cellW
        val y = boardRect.top + (row + 0.5f) * cellH
        return x to y
    }

    fun slotCenter(slotIndex: Int): Pair<Float, Float> {
        val rect = slotRects[slotIndex]
        return rect.centerX() to rect.centerY()
    }

    companion object {
        /**
         * 일반적인 9:20 세로형 스마트폰 해상도 기준 기본 비율 캘리브레이션.
         * 기기별 UI 여백이 다를 경우 설정 또는 보정 기능으로 미세 조정 가능.
         */
        fun defaultForScreen(screenWidth: Int, screenHeight: Int): BoardCalibration {
            val w = screenWidth.toFloat()
            val h = screenHeight.toFloat()
            val boardSize = w * 0.88f
            val boardLeft = (w - boardSize) / 2f
            val boardTop = h * 0.255f
            val boardRect = RectF(boardLeft, boardTop, boardLeft + boardSize, boardTop + boardSize)

            val slotTop = boardTop + boardSize + h * 0.055f
            val slotBottom = slotTop + h * 0.145f
            val slotMargin = w * 0.06f
            val slotAreaW = w - slotMargin * 2f
            val eachSlotW = slotAreaW / 3f
            val slots = (0 until 3).map { i ->
                RectF(
                    slotMargin + i * eachSlotW,
                    slotTop,
                    slotMargin + (i + 1) * eachSlotW,
                    slotBottom
                )
            }

            val scoreRect = RectF(w * 0.25f, h * 0.07f, w * 0.75f, h * 0.16f)
            return BoardCalibration(boardRect, slots, scoreRect)
        }
    }
}
