package net.irisshaders.batchedentityrendering.mixin;

import net.irisshaders.batchedentityrendering.impl.DrawCallTrackingRenderBuffers;
import net.irisshaders.batchedentityrendering.impl.FullyBufferedMultiBufferSource;
import net.irisshaders.batchedentityrendering.impl.MemoryTrackingBuffer;
import net.irisshaders.batchedentityrendering.impl.MemoryTrackingRenderBuffers;
import net.irisshaders.batchedentityrendering.impl.RenderBuffersExt;
import net.minecraft.client.renderer.ChunkBufferBuilderPack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderBuffers.class)
public class MixinRenderBuffers implements RenderBuffersExt, MemoryTrackingRenderBuffers, DrawCallTrackingRenderBuffers {
	@Unique
	private final FullyBufferedMultiBufferSource buffered = new FullyBufferedMultiBufferSource();
	@Unique
	private final OutlineBufferSource outlineBufferSource = new OutlineBufferSource(buffered);
	@Unique
	private int begins = 0;
	@Unique
	private int maxBegins = 0;
	// Visus (CoO batching.skipWithoutShaders): begin/end pairs skipped because no shader pack was in use. Counted so a
	// skipped begin always pairs with its own end, even if a pack is toggled in between.
	@Unique
	private int visus$skippedBegins = 0;
	@Shadow
	@Final
	private MultiBufferSource.BufferSource bufferSource;

	@Shadow
	@Final
	private MultiBufferSource.BufferSource crumblingBufferSource;

	@Shadow
	@Final
	private ChunkBufferBuilderPack fixedBufferPack;

	@Inject(method = "bufferSource", at = @At("HEAD"), cancellable = true)
	private void batchedentityrendering$replaceBufferSource(CallbackInfoReturnable<MultiBufferSource.BufferSource> cir) {
		if (begins == 0) {
			return;
		}

		cir.setReturnValue(buffered);
	}

	@Inject(method = "crumblingBufferSource", at = @At("HEAD"), cancellable = true)
	private void batchedentityrendering$replaceCrumblingBufferSource(CallbackInfoReturnable<MultiBufferSource.BufferSource> cir) {
		if (begins == 0) {
			return;
		}

		// NB: We can return the same MultiBufferSource here as long as the block entity and its breaking animation
		// use different render layers. This seems like a sound assumption to make. This only works with our fully
		// buffered vertex consumer provider - vanilla's bufferSource cannot be used here since it would try to return the
		// same buffer for the block entity and its breaking animation in many cases.
		//
		// If anything goes wrong here, Vanilla *will* catch the "duplicate delegates" error, so
		// this shouldn't cause silent bugs.

		// Prevent vanilla from explicitly flushing the wrapper at the wrong time.
		cir.setReturnValue(buffered.getUnflushableWrapper());
	}

	@Inject(method = "outlineBufferSource", at = @At("HEAD"), cancellable = true)
	private void batchedentityrendering$replaceOutlineBufferSource(CallbackInfoReturnable<OutlineBufferSource> provider) {
		if (begins == 0) {
			return;
		}

		provider.setReturnValue(outlineBufferSource);
	}

	@Override
	public void beginLevelRendering() {
		if (visus$skippedBegins > 0 || (begins == 0 && dev.visus.config.VisusConfig.get().skipBatchingWithoutShaders
			&& !net.irisshaders.iris.Iris.isPackInUseQuick())) {
			// Without a shader pack the batched buffers change nothing on screen; vanilla's buffer source is cheaper.
			visus$skippedBegins++;
			return;
		}

		if (begins == 0) {
			buffered.assertWrapStackEmpty();
		}

		begins += 1;

		maxBegins = Math.max(begins, maxBegins);
	}

	@Override
	public void endLevelRendering() {
		if (visus$skippedBegins > 0) {
			visus$skippedBegins--;
			return;
		}

		begins -= 1;

		if (begins == 0) {
			buffered.assertWrapStackEmpty();
		}
	}

	@Override
	public int getEntityBufferAllocatedSize() {
		return ((MemoryTrackingBuffer) buffered).getAllocatedSize();
	}

	@Override
	public int getMiscBufferAllocatedSize() {
		return ((MemoryTrackingBuffer) bufferSource).getAllocatedSize();
	}

	@Override
	public int getMaxBegins() {
		return maxBegins;
	}

	@Override
	public void freeAndDeleteBuffers() {
		buffered.freeAndDeleteBuffer();
		((ChunkBufferBuilderPackAccessor) this.fixedBufferPack).getBuilders().values().forEach(bufferBuilder -> ((MemoryTrackingBuffer) bufferBuilder).freeAndDeleteBuffer());
		((BufferSourceAccessor) bufferSource).getFixedBuffers().forEach((renderType, bufferBuilder) -> ((MemoryTrackingBuffer) bufferBuilder).freeAndDeleteBuffer());
		((BufferSourceAccessor) bufferSource).getFixedBuffers().clear();
		((MemoryTrackingBuffer) ((OutlineBufferSourceAccessor) outlineBufferSource).getOutlineBufferSource()).freeAndDeleteBuffer();
	}

	@Override
	public int getDrawCalls() {
		return buffered.getDrawCalls();
	}

	@Override
	public int getRenderTypes() {
		return buffered.getRenderTypes();
	}

	@Override
	public void resetDrawCounts() {
		buffered.resetDrawCalls();
	}
}
