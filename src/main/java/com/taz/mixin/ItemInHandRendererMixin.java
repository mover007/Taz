package com.taz.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.taz.general.HeldItem;
import com.taz.general.HeldItemConfig;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Held Item: the size, place, turn and swing of the item in your hand (NoammAddons' Animations). */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	@Shadow
	private float oMainHandHeight;
	@Shadow
	private float mainHandHeight;
	@Shadow
	private float oOffHandHeight;
	@Shadow
	private float offHandHeight;

	@Inject(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER), require = 0)
	private void taz$move(AbstractClientPlayer player, float frame, float xRot, InteractionHand hand, float attack, ItemStack stack, float height, PoseStack pose,
			SubmitNodeCollector collector, int light, CallbackInfo ci) {
		HeldItemConfig cfg = HeldItem.on();
		if (cfg == null || stack.isEmpty()) return;
		float sign = hand == InteractionHand.MAIN_HAND ? 1 : -1;
		pose.translate(cfg.x * sign, cfg.y, cfg.z);
	}

	@ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), ordinal = 2, argsOnly = true, require = 0)
	private float taz$swing(float attack) {
		return HeldItem.noSwing() ? 1f : attack;
	}

	@Inject(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"), require = 0)
	private void taz$scale(AbstractClientPlayer player, float frame, float xRot, InteractionHand hand, float attack, ItemStack stack, float height, PoseStack pose,
			SubmitNodeCollector collector, int light, CallbackInfo ci) {
		HeldItemConfig cfg = HeldItem.on();
		if (cfg == null) return;
		float s = 1 + cfg.scale;
		pose.mulPose(Axis.XP.rotationDegrees(cfg.rotX));
		pose.mulPose(Axis.YP.rotationDegrees(cfg.rotY));
		pose.mulPose(Axis.ZP.rotationDegrees(cfg.rotZ));
		pose.scale(s, s, s);
	}

	@WrapOperation(method = "swingArm", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"), require = 0)
	private void taz$swingDistance(PoseStack pose, float x, float y, float z, Operation<Void> original) {
		HeldItemConfig cfg = HeldItem.on();
		if (cfg == null) original.call(pose, x, y, z);
		else pose.translate(x * cfg.swingX, y * cfg.swingY, z * cfg.swingZ);
	}

	@Inject(method = "shouldInstantlyReplaceVisibleItem", at = @At("HEAD"), cancellable = true, require = 0)
	private void taz$noEquip(ItemStack shown, ItemStack expected, CallbackInfoReturnable<Boolean> cir) {
		HeldItemConfig cfg = HeldItem.on();
		if (cfg != null && cfg.noEquip) cir.setReturnValue(true);
	}

	@Inject(method = "tick", at = @At("TAIL"), require = 0)
	private void taz$noEquipHeight(CallbackInfo ci) {
		HeldItemConfig cfg = HeldItem.on();
		if (cfg == null || !cfg.noEquip) return;
		oMainHandHeight = mainHandHeight = oOffHandHeight = offHandHeight = 1f;
	}

	@WrapWithCondition(method = "renderHandsWithItems", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V"), require = 0)
	private boolean taz$noSway(PoseStack pose, Quaternionfc by) {
		HeldItemConfig cfg = HeldItem.on();
		return cfg == null || !cfg.noSway;
	}
}
