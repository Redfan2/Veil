package foundry.veil.render.deferred.light.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import foundry.veil.render.deferred.LightRenderer;
import foundry.veil.render.deferred.light.Light;
import foundry.veil.render.wrapper.CullFrustum;
import org.jetbrains.annotations.ApiStatus;
import org.lwjgl.system.NativeResource;

import java.util.List;

/**
 * Renders all lights of a specific type.
 *
 * @param <T> The type of lights to render
 */
public interface LightTypeRenderer<T extends Light> extends NativeResource {

    /**
     * <p>Renders all specified lights with this renderer.</p>
     * <p>Shaders, custom uniforms, and the way lights are rendered is up to the individual renderer.</p>
     *
     * @param lightRenderer The light renderer instance
     * @param lights        The lights to render
     * @param frustum       The culling view frustum
     */
    @ApiStatus.OverrideOnly
    void renderLights(LightRenderer lightRenderer, List<T> lights, CullFrustum frustum);

    /**
     * @return A full screen unit quad for drawing a light
     */
    static BufferBuilder.RenderedBuffer createQuad() {
        Tesselator tesselator = RenderSystem.renderThreadTesselator();
        BufferBuilder bufferBuilder = tesselator.getBuilder();

        bufferBuilder.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION);
        bufferBuilder.vertex(-1, -1, 0).endVertex();
        bufferBuilder.vertex(1, -1, 0).endVertex();
        bufferBuilder.vertex(-1, 1, 0).endVertex();
        bufferBuilder.vertex(1, 1, 0).endVertex();

        return bufferBuilder.end();
    }
}
