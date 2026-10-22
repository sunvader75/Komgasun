package com.sunvader.komgasun.ui.reader.epub

import org.junit.Assert.*
import org.junit.Test

class EpubCurlGeometryTest {
    @Test fun endpointsAndReverseArrival() {
        val mesh = EpubCurlGeometry()
        mesh.update(1000f, 1600f, 0f, 1, false)
        assertEquals(0f, mesh.vertices[0], 0f)
        assertEquals(1000f, mesh.vertices[EpubCurlGeometry.COLUMNS * 2], 0f)
        mesh.update(1000f, 1600f, 1f, 1, false)
        assertTrue(mesh.vertices.filterIndexed { i, _ -> i % 2 == 0 }.all { it <= 0f })
        val departed = mesh.vertices.copyOf()
        mesh.update(1000f, 1600f, 0f, -1, false)
        assertArrayEquals(departed, mesh.vertices, 0f)
        mesh.update(1000f, 1600f, 1f, -1, false)
        assertEquals(1000f, mesh.vertices[EpubCurlGeometry.COLUMNS * 2], 0f)
    }

    @Test fun continuousCurvaturePerspectiveAndReusableStorage() {
        val mesh = EpubCurlGeometry()
        val vertices = mesh.vertices
        val colors = mesh.colors
        for (dark in listOf(false, true)) for (direction in listOf(-1, 1)) {
            for (step in 0..100) {
                mesh.update(1000f, 1600f, step / 100f, direction, dark)
                assertSame(vertices, mesh.vertices)
                assertSame(colors, mesh.colors)
                assertTrue(vertices.all { it.isFinite() })
                for (row in 0..EpubCurlGeometry.ROWS) {
                    val offset = row * (EpubCurlGeometry.COLUMNS + 1) * 2
                    for (col in 1..EpubCurlGeometry.COLUMNS) {
                        assertTrue(vertices[offset + col * 2] >= vertices[offset + (col - 1) * 2])
                    }
                }
                assertTrue(colors.all { (it ushr 24) == 255 })
            }
        }
        mesh.update(1000f, 1600f, .5f, 1, false)
        val middle = EpubCurlGeometry.COLUMNS / 2 * 2
        assertTrue(kotlin.math.abs(vertices[middle] - (vertices[0] + vertices[EpubCurlGeometry.COLUMNS * 2]) / 2) > 1f)
        assertTrue(vertices[EpubCurlGeometry.COLUMNS * 2 + 1] < 0f)
    }
}
