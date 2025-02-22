package foundry.veil.api.event;

import foundry.veil.api.client.render.post.PostPipeline;
import foundry.veil.api.client.render.post.PostProcessingManager;
import net.minecraft.resources.ResourceLocation;

/**
 * <p>Events fired when Veil runs post-processing.</p>
 *
 * <p><b><i>Note: These events are only fired if there are post-processing steps to run.</i></b></p>
 *
 * @author Ocelot
 * @see PostProcessingManager
 */
public final class VeilPostProcessingEvent {

    private VeilPostProcessingEvent() {
    }

    /**
     * Fired before Veil runs the default post-processing steps.
     *
     * @author Ocelot
     */
    @FunctionalInterface
    public interface Pre {

        /**
         * Called before Veil runs the default post-processing pipeline.
         *
         * @param name     The name of the pipeline being run
         * @param pipeline The pipeline running
         * @param context  The context for running pipelines
         */
        void preVeilPostProcessing(ResourceLocation name, PostPipeline pipeline, PostPipeline.Context context);
    }

    /**
     * Fired after Veil runs the default post-processing steps.
     *
     * @author Ocelot
     */
    @FunctionalInterface
    public interface Post {

        /**
         * Called after Veil runs the default post-processing pipeline.
         *
         * @param name     The name of the pipeline being run
         * @param pipeline The pipeline running
         * @param context  The context for running pipelines
         */
        void postVeilPostProcessing(ResourceLocation name, PostPipeline pipeline, PostPipeline.Context context);
    }
}
