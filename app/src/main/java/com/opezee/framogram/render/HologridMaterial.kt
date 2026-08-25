package com.opezee.framogram.render

import com.google.android.filament.Engine
import com.google.android.filament.Material
import com.google.android.filament.filamat.MaterialBuilder
import com.google.android.filament.filamat.MaterialPackage

/**
 * Builds the holographic grid material at runtime with filamat. Building at runtime
 * (instead of shipping a matc-compiled .filamat) guarantees the material package always
 * matches the Filament runtime version — the classic matc/engine version-skew pitfall
 * cannot happen.
 *
 * Unlit + transparent, procedural antialiased grid computed from in-plane meters
 * carried in uv0, with a depth fade carried in uv1.x and a back-wall flag in uv1.y.
 */
object HologridMaterial {

    fun build(engine: Engine): Material {
        MaterialBuilder.init()
        try {
            val pkg: MaterialPackage = MaterialBuilder()
                .name("hologrid")
                .platform(MaterialBuilder.Platform.MOBILE)
                .targetApi(MaterialBuilder.TargetApi.ALL)
                .optimization(MaterialBuilder.Optimization.NONE)
                .shading(MaterialBuilder.Shading.UNLIT)
                .blending(MaterialBuilder.BlendingMode.TRANSPARENT)
                .culling(MaterialBuilder.CullingMode.NONE)
                .require(MaterialBuilder.VertexAttribute.UV0)
                .require(MaterialBuilder.VertexAttribute.UV1)
                .uniformParameter(MaterialBuilder.UniformType.FLOAT3, "gridColor")
                .uniformParameter(MaterialBuilder.UniformType.FLOAT, "cellSize")
                .uniformParameter(MaterialBuilder.UniformType.FLOAT, "lineWidthPx")
                .uniformParameter(MaterialBuilder.UniformType.FLOAT, "fadePower")
                .uniformParameter(MaterialBuilder.UniformType.FLOAT, "backFloor")
                .uniformParameter(MaterialBuilder.UniformType.FLOAT, "pattern")
                .uniformParameter(MaterialBuilder.UniformType.FLOAT, "dotRadius")
                .uniformParameter(MaterialBuilder.UniformType.FLOAT, "crossLen")
                .uniformParameter(MaterialBuilder.UniformType.FLOAT, "nodeRadiusPx")
                .material(FRAGMENT)
                .build()
            check(pkg.isValid) { "hologrid material failed to compile" }
            return Material.Builder()
                .payload(pkg.buffer, pkg.buffer.remaining())
                .build(engine)
        } finally {
            MaterialBuilder.shutdown()
        }
    }

    // uv0 = in-plane coordinates in meters; uv1 = (depth01: 0 at screen plane → 1 at
    // back wall, isBack: 1 on the back wall else 0).
    private val FRAGMENT = """
        void material(inout MaterialInputs material) {
            prepareMaterial(material);

            vec2 uv = getUV0();
            vec2 extra = getUV1();
            float depth01 = extra.x;
            float isBack = extra.y;

            vec2 coord = uv / materialParams.cellSize;
            vec2 fw = fwidth(coord);
            vec2 cell = fract(coord) - 0.5;

            // Lines: antialiased distance to the nearest grid line, in pixel units.
            vec2 gridDist = abs(fract(coord - 0.5) - 0.5) / fw;
            float lineDist = min(gridDist.x, gridDist.y);
            float lines = 1.0 - min(lineDist / materialParams.lineWidthPx, 1.0);

            // Dots at cell centers.
            float aa = max(fw.x, fw.y);
            float dots = 1.0 - smoothstep(
                materialParams.dotRadius - aa,
                materialParams.dotRadius + aa,
                length(cell));

            // Crosses at grid intersections: line pattern limited near intersections.
            vec2 fc = abs(fract(coord + 0.5) - 0.5);
            vec2 pxd = fc / fw;
            float armX = 1.0 - min(pxd.y / materialParams.lineWidthPx, 1.0);
            float armY = 1.0 - min(pxd.x / materialParams.lineWidthPx, 1.0);
            float within = step(max(fc.x, fc.y), materialParams.crossLen);
            float crosses = max(armX, armY) * within;

            // Bright nodes at the intersections; the connecting lines stay dimmer so
            // the wireframe reads as points joined by faint lines.
            float nodePx = length(pxd);
            float node = 1.0 - smoothstep(
                materialParams.nodeRadiusPx - 1.0,
                materialParams.nodeRadiusPx + 1.0,
                nodePx);
            float linesNodes = max(lines * 0.5, node);

            float p = materialParams.pattern;
            float v = p < 0.5 ? linesNodes : (p < 1.5 ? dots : crosses);

            // Moire kill: when cells shrink toward a pixel (grazing angles, far depth),
            // the pattern is unresolvable and shimmers — fade it out instead. fw is in
            // cells-per-pixel; by ~1 cell/pixel the pattern must be gone.
            float density = max(fw.x, fw.y);
            v *= 1.0 - smoothstep(0.35, 1.0, density);

            // Brightest at the screen plane, dissolving toward the back; the back wall
            // keeps a small constant floor so the box reads as closed.
            float fade = pow(clamp(1.0 - depth01, 0.0, 1.0), materialParams.fadePower);
            fade = max(fade, materialParams.backFloor * isBack);

            float alpha = v * fade;
            material.baseColor = vec4(materialParams.gridColor * alpha, alpha);
        }
    """.trimIndent()
}
