package dev.visus.api.impl;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.BufferUploader;
import dev.visus.api.gl.GlScope;
import dev.visus.gl.BindTracker;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.buffer.ShaderStorageBufferHolder;
import net.irisshaders.iris.mixin.GlStateManagerAccessor;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL40C;

/**
 * A raw-GL scope (fixes NeoVoxy #29, GL state drift). On close the real GL state is made equal to what Minecraft's
 * {@code GlStateManager} cache, Iris's sampler cache and Visus's {@link BindTracker} believe it is, whatever raw GL
 * ran inside: blend (enable and function), depth (test, function, mask), colour mask, active texture and the textures
 * of the cached units, sampler objects, the program, the viewport, cull, the framebuffers bound when the scope
 * opened, and the pack's shader storage buffers (Iris binds those once per pipeline). Per-buffer blend state changed
 * through the scope's own methods is put back exactly as it was. The units and
 * bindings an owner got from {@code SamplerUnits} are its own and are left as they are.
 *
 * <p>One instance, reused (scopes do not nest), so opening and closing does not allocate. Render thread only.
 */
final class GlScopeImpl implements GlScope {
	private static final int MAX_BUFFERS = 8;

	private boolean open;
	private String owner;
	private int drawFramebuffer;
	private int readFramebuffer;
	private boolean cull;
	private int touchedBuffers;
	private final int[] srcRgb = new int[MAX_BUFFERS];
	private final int[] dstRgb = new int[MAX_BUFFERS];
	private final int[] srcAlpha = new int[MAX_BUFFERS];
	private final int[] dstAlpha = new int[MAX_BUFFERS];
	private final boolean[] blendEnabled = new boolean[MAX_BUFFERS];

	GlScope open(String owner) {
		if (open) {
			throw new IllegalStateException("A Visus GL scope is already open (by " + this.owner + "); scopes do not nest");
		}
		open = true;
		this.owner = owner;
		touchedBuffers = 0;
		drawFramebuffer = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
		readFramebuffer = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
		cull = GL11C.glIsEnabled(GL11C.GL_CULL_FACE);
		return this;
	}

	private void touch(int buffer) {
		if (!open) throw new IllegalStateException("The Visus GL scope is closed");
		if (buffer < 0 || buffer >= MAX_BUFFERS) throw new IllegalArgumentException("draw buffer " + buffer + " is outside 0.." + (MAX_BUFFERS - 1));
		int bit = 1 << buffer;
		if ((touchedBuffers & bit) != 0) return;
		touchedBuffers |= bit;
		srcRgb[buffer] = GL30C.glGetIntegeri(GL14C.GL_BLEND_SRC_RGB, buffer);
		dstRgb[buffer] = GL30C.glGetIntegeri(GL14C.GL_BLEND_DST_RGB, buffer);
		srcAlpha[buffer] = GL30C.glGetIntegeri(GL14C.GL_BLEND_SRC_ALPHA, buffer);
		dstAlpha[buffer] = GL30C.glGetIntegeri(GL14C.GL_BLEND_DST_ALPHA, buffer);
		blendEnabled[buffer] = GL30C.glIsEnabledi(GL11C.GL_BLEND, buffer);
	}

	@Override
	public void blendFuncSeparatei(int buffer, int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
		touch(buffer);
		GL40C.glBlendFuncSeparatei(buffer, srcRgb, dstRgb, srcAlpha, dstAlpha);
	}

	@Override
	public void enableBlendi(int buffer) {
		touch(buffer);
		GL30C.glEnablei(GL11C.GL_BLEND, buffer);
	}

	@Override
	public void disableBlendi(int buffer) {
		touch(buffer);
		GL30C.glDisablei(GL11C.GL_BLEND, buffer);
	}

	@Override
	public void close() {
		if (!open) return;
		open = false;
		owner = null;

		// Global blend first (glBlendFuncSeparate sets every buffer), then the buffers changed through this scope.
		GlStateManager.BlendState blend = GlStateManagerAccessor.getBLEND();
		setEnabled(GL11C.GL_BLEND, blend.mode.enabled);
		GL14C.glBlendFuncSeparate(blend.srcRgb, blend.dstRgb, blend.srcAlpha, blend.dstAlpha);
		for (int i = 0; i < MAX_BUFFERS; i++) {
			if ((touchedBuffers & (1 << i)) == 0) continue;
			GL40C.glBlendFuncSeparatei(i, srcRgb[i], dstRgb[i], srcAlpha[i], dstAlpha[i]);
			if (blendEnabled[i]) GL30C.glEnablei(GL11C.GL_BLEND, i);
			else GL30C.glDisablei(GL11C.GL_BLEND, i);
		}
		touchedBuffers = 0;

		GlStateManager.DepthState depth = GlStateManagerAccessor.getDEPTH();
		setEnabled(GL11C.GL_DEPTH_TEST, depth.mode.enabled);
		GL11C.glDepthFunc(depth.func);
		GL11C.glDepthMask(depth.mask);

		GlStateManager.ColorMask mask = GlStateManagerAccessor.getCOLOR_MASK();
		GL11C.glColorMask(mask.red, mask.green, mask.blue, mask.alpha);

		setEnabled(GL11C.GL_CULL_FACE, cull);

		GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
		GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, readFramebuffer);
		GL11C.glViewport(GlStateManager.Viewport.x(), GlStateManager.Viewport.y(), GlStateManager.Viewport.width(),
			GlStateManager.Viewport.height());

		GL20C.glUseProgram(BindTracker.lastProgram());

		GlStateManager.TextureState[] textures = GlStateManagerAccessor.getTEXTURES();
		for (int i = 0; i < textures.length; i++) {
			GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + i);
			GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, textures[i].binding);
		}
		GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + GlStateManagerAccessor.getActiveTexture());
		IrisRenderSystem.visus$reapplySamplers();

		// The pack's shader storage buffers are bound once, when its pipeline is built, and must stay bound.
		if (Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline pipeline) {
			ShaderStorageBufferHolder buffers = pipeline.visus$shaderStorageBuffers();
			if (buffers != null && !pipeline.visus$isDestroyed()) buffers.setupBuffers();
		}

		// Vanilla re-binds its vertex arrays only when it thinks they changed.
		BufferUploader.invalidate();
	}

	private static void setEnabled(int cap, boolean enabled) {
		if (enabled) GL11C.glEnable(cap);
		else GL11C.glDisable(cap);
	}
}
