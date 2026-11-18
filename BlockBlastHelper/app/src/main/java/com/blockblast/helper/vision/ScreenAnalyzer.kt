package com.blockblast.helper.vision

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.blockblast.helper.model.BlockShape
import com.blockblast.helper.model.BoardCalibration
import com.blockblast.helper.model.CellOffset
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * 캡처된 게임 화면 Bitmap에서 8x8 보드 상태, 하단 3개 블록 형태, 상단 점수를 추출.
 */
class ScreenAnalyzer {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun extractBoardGrid(bitmap: Bitmap, calibration: BoardCalibration): Array<BooleanArray> {
        val grid = Array(8) { BooleanArray(8) }
        val rect = calibration.boardRect
        val cellW = rect.width() / 8f
        val cellH = rect.height() / 8f

        for (r in 0 until 8) {
            for (c in 0 until 8) {
                val cx = (rect.left + (c + 0.5f) * cellW).toInt().coerceIn(0, bitmap.width - 1)
                val cy = (rect.top + (r + 0.5f) * cellH).toInt().coerceIn(0, bitmap.height - 1)
                grid[r][c] = isCellFilled(bitmap.getPixel(cx, cy))
            }
        }
        return grid
    }

    fun extractAvailableBlocks(
        bitmap: Bitmap,
        calibration: BoardCalibration
    ): Map<Int, BlockShape> {
        val result = mutableMapOf<Int, BlockShape>()
        calibration.slotRects.forEachIndexed { slotIndex, slotRect ->
            val shape = detectBlockInSlot(bitmap, slotRect)
            if (shape != null && shape.cells.isNotEmpty()) {
                result[slotIndex] = shape
            }
        }
        return result
    }

    /**
     * 하단 슬롯 내부를 5x5 세부 그리드로 샘플링하여 블록의 상대 좌표 형태를 복원.
     * 실제 기기 해상도에 맞춰 셀 크기/임계값 조정이 필요할 수 있음.
     */
    private fun detectBlockInSlot(bitmap: Bitmap, slotRect: android.graphics.RectF): BlockShape? {
        val gridSamples = 5
        val stepX = slotRect.width() / gridSamples
        val stepY = slotRect.height() / gridSamples
        val rawCells = mutableListOf<CellOffset>()

        for (r in 0 until gridSamples) {
            for (c in 0 until gridSamples) {
                val px = (slotRect.left + (c + 0.5f) * stepX).toInt().coerceIn(0, bitmap.width - 1)
                val py = (slotRect.top + (r + 0.5f) * stepY).toInt().coerceIn(0, bitmap.height - 1)
                if (isBlockPixel(bitmap.getPixel(px, py))) {
                    rawCells.add(CellOffset(r, c))
                }
            }
        }

        if (rawCells.isEmpty()) return null
        return BlockShape(rawCells).normalized()
    }

    suspend fun extractScore(bitmap: Bitmap, calibration: BoardCalibration): Int? {
        val sRect = calibration.scoreRect
        val cropRect = Rect(
            sRect.left.toInt().coerceAtLeast(0),
            sRect.top.toInt().coerceAtLeast(0),
            sRect.right.toInt().coerceAtMost(bitmap.width),
            sRect.bottom.toInt().coerceAtMost(bitmap.height)
        )
        if (cropRect.width() <= 0 || cropRect.height() <= 0) return null

        val cropped = Bitmap.createBitmap(
            bitmap,
            cropRect.left,
            cropRect.top,
            cropRect.width(),
            cropRect.height()
        )
        val inputImage = InputImage.fromBitmap(cropped, 0)

        return suspendCoroutine { cont ->
            textRecognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val digits = visionText.text
                        .replace(",", "")
                        .replace(" ", "")
                        .filter { it.isDigit() }
                    cont.resume(digits.toIntOrNull())
                }
                .addOnFailureListener {
                    cont.resume(null)
                }
        }
    }

    private fun isCellFilled(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        // Block Blast의 빈 격자 셀은 어두운 남색/회색 계열(명도·채도 낮음)
        val maxChannel = maxOf(r, g, b)
        val minChannel = minOf(r, g, b)
        val brightness = (r + g + b) / 3
        val saturation = maxChannel - minChannel
        return brightness > 85 || saturation > 45
    }

    private fun isBlockPixel(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        val brightness = (r + g + b) / 3
        val saturation = maxOf(r, g, b) - minOf(r, g, b)
        return brightness > 95 && saturation > 35
    }
}
