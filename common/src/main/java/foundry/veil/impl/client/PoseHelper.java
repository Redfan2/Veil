package foundry.veil.impl.client;


import com.mojang.blaze3d.vertex.PoseStack;
import foundry.veil.api.client.pose.ExtendedPose;
import foundry.veil.api.client.pose.PoseData;
import foundry.veil.api.client.registry.PoseRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

@ApiStatus.Internal
public class PoseHelper {
    public static boolean poseItemUsing(PoseData data, ItemInHandRenderer pRenderer) {
        AtomicBoolean flag = new AtomicBoolean(false);
        PoseRegistry.poses.forEach((item, pose) -> {
            if (item == null || pose == null) return;
            pose.data = data;
            if (item.test(data.stack.getItem())) {
                pose.poseItemUsing(pRenderer);
                flag.set(pose.overrideItemTransform());
            }
        });
        return flag.get();
    }

    public static boolean poseItem(PoseData data, ItemInHandRenderer pRenderer) {
        AtomicBoolean flag = new AtomicBoolean(false);
        PoseRegistry.poses.forEach((item, pose) -> {
            if (item == null || pose == null) return;
            pose.data = data;
            if (item.test(data.stack.getItem())) {
                pose.poseItem(pRenderer);
                flag.set(pose.overrideItemTransform());
            }
        });
        return flag.get();
    }

    public static void offhandCapture(PoseData data, ItemStack pStack, PoseStack pMatrixStack, MultiBufferSource pBuffer, int pCombinedLight, float pEquippedProgress, float pSwingProgress, HumanoidArm pSide, ItemInHandRenderer pRenderer) {
        for (Map.Entry<Predicate<Item>, ExtendedPose> pose : PoseRegistry.poses.entrySet()) {
            if (pose.getKey().test(pStack.getItem())) {
                pose.getValue().data = data;
                if (pose.getValue().forceRenderMainHand()) {
                    pMatrixStack.pushPose();
                    pose.getValue().poseMainHandFirstPerson(pMatrixStack);
                    pRenderer.renderPlayerArm(pMatrixStack, pBuffer, pCombinedLight, pEquippedProgress, pSwingProgress, Minecraft.getInstance().player.getMainArm());
                    pMatrixStack.popPose();
                }
                if (pose.getValue().forceRenderOffhand()) {
                    pMatrixStack.pushPose();
                    pose.getValue().poseOffHandFirstPerson(pMatrixStack);
                    pRenderer.renderPlayerArm(pMatrixStack, pBuffer, pCombinedLight, pEquippedProgress, pSwingProgress, Minecraft.getInstance().player.getMainArm().getOpposite());
                    pMatrixStack.popPose();
                }
            }
        }
    }
}
