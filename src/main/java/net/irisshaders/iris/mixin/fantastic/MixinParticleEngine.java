package net.irisshaders.iris.mixin.fantastic;

import com.google.common.collect.ImmutableSet;
import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.visus.particle.ParticlePhases;
import net.irisshaders.iris.Iris;
import net.minecraft.client.renderer.texture.TextureManager;
import org.lwjgl.opengl.GL11C;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.particle.Particle;
import net.irisshaders.iris.fantastic.ParticleRenderingPhase;
import net.irisshaders.iris.fantastic.PhasedParticleEngine;
import net.irisshaders.iris.pipeline.programs.ShaderAccess;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;
import java.util.function.Supplier;

/**
 * Extends the ParticleEngine class to allow multiple phases of particle rendering.
 * <p>
 * This is used to enable the rendering of known-opaque particles much earlier than other particles, most notably before
 * translucent content. Normally, particles behind translucent blocks are not visible on Fancy graphics, and a user must
 * enable the much more intensive Fabulous graphics option. This is not ideal because Fabulous graphics is fundamentally
 * incompatible with most shader packs.
 * <p>
 * So what causes this? Essentially, on Fancy graphics, all particles are rendered after translucent terrain. Aside from
 * causing problems with particles being invisible, this also causes particles to write to the translucent depth buffer,
 * even when they are not translucent. This notably causes problems with particles on Sildur's Enhanced Default when
 * underwater.
 * <p>
 * So, what these mixins do is try to render known-opaque particles right before entities are rendered and right after
 * opaque terrain has been rendered. This seems to be an acceptable injection point, and has worked in my testing. It
 * fixes issues with particles when underwater, fixes a vanilla bug, and doesn't have any significant performance hit.
 * A win-win!
 * <p>
 * Unfortunately, there are limitations. Some particles rendering in texture sheets where translucency is supported. So,
 * even if an individual particle from that sheet is not translucent, it will still be treated as translucent, and thus
 * will not be affected by this patch. Without making more invasive and sweeping changes, there isn't a great way to get
 * around this.
 * <p>
 * As the saying goes, "Work smarter, not harder."
 */
@Mixin(ParticleEngine.class)
public class MixinParticleEngine implements PhasedParticleEngine {
	private static final Set<ParticleRenderType> OPAQUE_PARTICLE_RENDER_TYPES;
	@Shadow
	@Final
	private Map<ParticleRenderType, Queue<Particle>> particles;

	static {
		OPAQUE_PARTICLE_RENDER_TYPES = ImmutableSet.of(
				ParticleRenderType.PARTICLE_SHEET_OPAQUE,
				ParticleRenderType.PARTICLE_SHEET_LIT,
				ParticleRenderType.CUSTOM,
				ParticleRenderType.NO_RENDER
		);
	}

	// Visus V-10: one phase per type; modded types are classified by the state their begin() sets up.
	@Unique
	private static final ParticlePhases<ParticleRenderType> VISUS_PHASES = new ParticlePhases<>(OPAQUE_PARTICLE_RENDER_TYPES,
		List.of(ParticleRenderType.TERRAIN_SHEET, ParticleRenderType.PARTICLE_SHEET_OPAQUE, ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT,
			ParticleRenderType.PARTICLE_SHEET_LIT, ParticleRenderType.CUSTOM, ParticleRenderType.NO_RENDER));

	// Live filtered views of particles.keySet(), made once: Forge's TreeMap keeps its comparator order through them.
	@Unique
	private Set<ParticleRenderType> visus$opaqueTypes;
	@Unique
	private Set<ParticleRenderType> visus$translucentTypes;

	@Unique
	private ParticleRenderingPhase phase = ParticleRenderingPhase.EVERYTHING;

	@Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShader(Ljava/util/function/Supplier;)V"), remap = false)
	private void iris$changeParticleShader(Supplier<ShaderInstance> pSupplier0) {
		RenderSystem.setShader(phase == ParticleRenderingPhase.TRANSLUCENT ? ShaderAccess::getParticleTranslucentShader : pSupplier0);
	}

	@Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;)V", at = @At(value = "INVOKE", target = "Ljava/util/Map;keySet()Ljava/util/Set;"), remap = false)
	private Set<ParticleRenderType> iris$selectParticlesToRender(Map<ParticleRenderType, Queue<Particle>> instance) {
		Set<ParticleRenderType> keySet = instance.keySet();

		// Visus V-10: Oculus's opaque filter was "everything except PARTICLE_SHEET_TRANSLUCENT", so modded types (and
		// TERRAIN_SHEET) drew in both phases. Each type now draws in exactly one.
		if (phase == ParticleRenderingPhase.TRANSLUCENT) {
			if (visus$translucentTypes == null) {
				visus$translucentTypes = Sets.filter(keySet, VISUS_PHASES::drawsInTranslucentPhase);
			}
			return visus$translucentTypes;
		} else if (phase == ParticleRenderingPhase.OPAQUE) {
			if (visus$opaqueTypes == null) {
				visus$opaqueTypes = Sets.filter(keySet, VISUS_PHASES::drawsInOpaquePhase);
			}
			return visus$opaqueTypes;
		} else {
			// Don't override particle rendering
			return keySet;
		}
	}

	/** Visus V-10: the first time a modded type draws, read the blend and depth-write state its begin() set up. */
	@Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleRenderType;begin(Lcom/mojang/blaze3d/vertex/BufferBuilder;Lnet/minecraft/client/renderer/texture/TextureManager;)V", remap = true), remap = false)
	private void visus$beginAndClassify(ParticleRenderType type, BufferBuilder builder, TextureManager textureManager) {
		type.begin(builder, textureManager);

		if (VISUS_PHASES.needsProbe(type)) {
			boolean blend = GL11C.glIsEnabled(GL11C.GL_BLEND);
			boolean depthWrite = GL11C.glGetBoolean(GL11C.GL_DEPTH_WRITEMASK);
			ParticlePhases.Kind kind = VISUS_PHASES.learn(type, blend, depthWrite);
			Iris.logger.info("Particle render type " + type + " (" + type.getClass().getName() + ") draws in the " + kind + " phase");
		}
	}

	@Override
	public void setParticleRenderingPhase(ParticleRenderingPhase phase) {
		this.phase = phase;
	}
}
