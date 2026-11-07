// Adapted from Iris Flywheel Compat (irisflw) 2.0.5, https://github.com/leon-o/iris-flw-compat (branch 1.20.1-2.0,
// commit 6894e01a3). Copyright (c) 2022 Red Face. MIT License: META-INF/visus/irisflw-LICENSE.
// Visus changes are listed in docs/CHANGES-FROM-OCULUS.md.
package dev.visus.flywheel.mixin.flw.vertex;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.irisshaders.iris.vertices.BlockSensitiveBufferBuilder;
import org.jetbrains.annotations.UnknownNullability;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "dev.engine_room.flywheel.lib.model.baked.MeshEmitter", remap = false)
public abstract class MixinMeshEmitter implements VertexConsumer, BlockSensitiveBufferBuilder {
    @Shadow private @UnknownNullability BufferBuilder bufferBuilder;
    @Unique
    private int currentBlock = -1;
    @Unique
    private short currentRenderType = -1;
    @Unique
    private int currentLocalPosX;
    @Unique
    private int currentLocalPosY;
    @Unique
    private int currentLocalPosZ;

    @Inject(method = "prepareForGeometry(Z)V", at = @At("TAIL"))
    public void irisflw$prepareForGeometry(boolean shade, CallbackInfo ci)
    {
        if(this.bufferBuilder instanceof BlockSensitiveBufferBuilder blockSensitiveBufferBuilder)
        {
            blockSensitiveBufferBuilder.beginBlock((short)this.currentBlock, this.currentRenderType, this.currentLocalPosX, this.currentLocalPosY, this.currentLocalPosZ);
        }
    }



    @Override
    public void beginBlock(short block, short renderType, int localPosX, int localPosY, int localPosZ) {
        this.currentBlock = block;
        this.currentRenderType = renderType;
        this.currentLocalPosX = localPosX;
        this.currentLocalPosY = localPosY;
        this.currentLocalPosZ = localPosZ;
    }

    @Override
    public void endBlock() {
        this.currentBlock = -1;
        this.currentRenderType = -1;
        this.currentLocalPosX = 0;
        this.currentLocalPosY = 0;
        this.currentLocalPosZ = 0;

        if(this.bufferBuilder instanceof BlockSensitiveBufferBuilder blockSensitiveBufferBuilder)
        {
            blockSensitiveBufferBuilder.endBlock();
        }
    }
}
