package com.opezee.framogram.render

import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Scene
import com.google.android.filament.VertexBuffer
import com.opezee.framogram.config.ScreenGeometry
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The hologram box: five interior grid walls (floor, ceiling, left, right, back — the
 * front face is open, coincident with the screen plane at z = 0). Geometry is sized
 * exactly to the physical screen so the box's front edges land on the screen corners,
 * which is the visual invariant that validates the off-axis projection.
 *
 * Vertex layout (interleaved, 7 floats): position(3), uv0 = in-plane meters(2),
 * uv1 = (depth01, isBackWall)(2).
 */
class GridBox(private val engine: Engine, private val scene: Scene) {

    private val material = HologridMaterial.build(engine)
    private val materialInstance: MaterialInstance = material.createInstance().apply {
        setParameter("gridColor", 0.20f, 0.80f, 1.00f)
        setParameter("cellSize", 0.018f)
        setParameter("lineWidthPx", 1.3f)
        setParameter("fadePower", 1.6f)
        setParameter("backFloor", 0.18f)
        setParameter("pattern", 0f)
        setParameter("dotRadius", 0.09f)
        setParameter("crossLen", 0.16f)
    }

    private var entity = 0
    private var vertexBuffer: VertexBuffer? = null
    private var indexBuffer: IndexBuffer? = null

    fun setPattern(pattern: Int) {
        materialInstance.setParameter("pattern", pattern.toFloat())
    }

    fun setColor(r: Float, g: Float, b: Float) {
        materialInstance.setParameter("gridColor", r, g, b)
    }

    /** (Re)builds the box geometry for the current screen geometry. */
    fun rebuild(geom: ScreenGeometry) {
        destroyGeometry()

        val hw = geom.widthM / 2f
        val hh = geom.heightM / 2f
        val d = geom.depthM

        // 5 quads * 4 vertices * 7 floats.
        val verts = FloatArray(5 * 4 * 7)
        var i = 0
        fun v(x: Float, y: Float, z: Float, u0: Float, v0: Float, depth01: Float, isBack: Float) {
            verts[i++] = x; verts[i++] = y; verts[i++] = z
            verts[i++] = u0; verts[i++] = v0
            verts[i++] = depth01; verts[i++] = isBack
        }

        // Floor (y = -hh): in-plane meters (x, -z); depth01 = -z / d.
        v(-hw, -hh, 0f, -hw, 0f, 0f, 0f)
        v(hw, -hh, 0f, hw, 0f, 0f, 0f)
        v(hw, -hh, -d, hw, d, 1f, 0f)
        v(-hw, -hh, -d, -hw, d, 1f, 0f)
        // Ceiling (y = +hh).
        v(-hw, hh, 0f, -hw, 0f, 0f, 0f)
        v(hw, hh, 0f, hw, 0f, 0f, 0f)
        v(hw, hh, -d, hw, d, 1f, 0f)
        v(-hw, hh, -d, -hw, d, 1f, 0f)
        // Left wall (x = -hw): in-plane meters (-z, y).
        v(-hw, -hh, 0f, 0f, -hh, 0f, 0f)
        v(-hw, hh, 0f, 0f, hh, 0f, 0f)
        v(-hw, hh, -d, d, hh, 1f, 0f)
        v(-hw, -hh, -d, d, -hh, 1f, 0f)
        // Right wall (x = +hw).
        v(hw, -hh, 0f, 0f, -hh, 0f, 0f)
        v(hw, hh, 0f, 0f, hh, 0f, 0f)
        v(hw, hh, -d, d, hh, 1f, 0f)
        v(hw, -hh, -d, d, -hh, 1f, 0f)
        // Back wall (z = -d): in-plane meters (x, y).
        v(-hw, -hh, -d, -hw, -hh, 1f, 1f)
        v(hw, -hh, -d, hw, -hh, 1f, 1f)
        v(hw, hh, -d, hw, hh, 1f, 1f)
        v(-hw, hh, -d, -hw, hh, 1f, 1f)

        val indices = ShortArray(5 * 6)
        for (q in 0 until 5) {
            val b = q * 4
            val o = q * 6
            indices[o] = b.toShort()
            indices[o + 1] = (b + 1).toShort()
            indices[o + 2] = (b + 2).toShort()
            indices[o + 3] = b.toShort()
            indices[o + 4] = (b + 2).toShort()
            indices[o + 5] = (b + 3).toShort()
        }

        val vbuf = ByteBuffer.allocateDirect(verts.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().put(verts).apply { rewind() }
        val ibuf = ByteBuffer.allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder()).asShortBuffer().put(indices).apply { rewind() }

        val stride = 7 * 4
        val vb = VertexBuffer.Builder()
            .bufferCount(1)
            .vertexCount(20)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0,
                VertexBuffer.AttributeType.FLOAT3, 0, stride)
            .attribute(VertexBuffer.VertexAttribute.UV0, 0,
                VertexBuffer.AttributeType.FLOAT2, 12, stride)
            .attribute(VertexBuffer.VertexAttribute.UV1, 0,
                VertexBuffer.AttributeType.FLOAT2, 20, stride)
            .build(engine)
        vb.setBufferAt(engine, 0, vbuf)

        val ib = IndexBuffer.Builder()
            .indexCount(indices.size)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)
        ib.setBuffer(engine, ibuf)

        val e = EntityManager.get().create()
        RenderableManager.Builder(1)
            .boundingBox(Box(0f, 0f, -d / 2f, hw, hh, d / 2f))
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vb, ib)
            .material(0, materialInstance)
            .culling(false)
            .castShadows(false)
            .receiveShadows(false)
            .build(engine, e)
        scene.addEntity(e)

        entity = e
        vertexBuffer = vb
        indexBuffer = ib
    }

    private fun destroyGeometry() {
        if (entity != 0) {
            scene.removeEntity(entity)
            engine.renderableManager.destroy(entity)
            EntityManager.get().destroy(entity)
            entity = 0
        }
        vertexBuffer?.let { engine.destroyVertexBuffer(it) }
        indexBuffer?.let { engine.destroyIndexBuffer(it) }
        vertexBuffer = null
        indexBuffer = null
    }

    fun destroy() {
        destroyGeometry()
        engine.destroyMaterialInstance(materialInstance)
        engine.destroyMaterial(material)
    }
}
