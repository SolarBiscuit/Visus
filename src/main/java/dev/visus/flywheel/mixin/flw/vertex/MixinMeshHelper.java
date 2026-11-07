// Adapted from Iris Flywheel Compat (irisflw) 2.0.5, https://github.com/leon-o/iris-flw-compat (branch 1.20.1-2.0,
// commit 6894e01a3). Copyright (c) 2022 Red Face. MIT License: META-INF/visus/irisflw-LICENSE.
// Visus changes are listed in docs/CHANGES-FROM-OCULUS.md.
package dev.visus.flywheel.mixin.flw.vertex;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.engine_room.flywheel.lib.memory.MemoryBlock;
import dev.engine_room.flywheel.lib.model.SimpleQuadMesh;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.visus.flywheel.IrisFlw;
import dev.visus.flywheel.backend.model.IrisExtVertexView;
import dev.visus.flywheel.backend.model.IrisVertexReader;

import java.nio.ByteBuffer;

@Mixin(targets = "dev.engine_room.flywheel.lib.model.baked.MeshHelper", remap = false)
public class MixinMeshHelper {
    @Inject(method = "blockVerticesToMesh", remap = false, at = @At("HEAD"), cancellable = true)
    private static void irisflw$blockVerticesToMesh(BufferBuilder.RenderedBuffer buffer, @Nullable String meshDescriptor, CallbackInfoReturnable<SimpleQuadMesh> cir)
    {
        if(IrisFlw.isUsingExtendedVertexFormat())
        {
            BufferBuilder.DrawState drawState = buffer.drawState();
            int vertexCount = drawState.vertexCount();
            long srcStride = drawState.format().getVertexSize();
            IrisExtVertexView vertexView = new IrisExtVertexView();
            long dstStride = vertexView.stride();
            ByteBuffer src = buffer.vertexBuffer();
            MemoryBlock dst = MemoryBlock.mallocTracked((long)vertexCount * dstStride);
            long srcPtr = MemoryUtil.memAddress(src);
            long dstPtr = dst.ptr();

            vertexView.ptr(dstPtr);
            vertexView.vertexCount(vertexCount);
            vertexView.nativeMemoryOwner(dst);

            var vertexReader = new IrisVertexReader(srcPtr, vertexCount);
            vertexReader.writeAll(vertexView);
            vertexView.readAllExtended(vertexReader);

            cir.setReturnValue(new SimpleQuadMesh(vertexView, meshDescriptor));
        }
    }
}
