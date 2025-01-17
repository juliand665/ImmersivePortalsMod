package com.qouteall.immersive_portals.optifine_compatibility;

import com.mojang.blaze3d.platform.GlStateManager;
import com.qouteall.immersive_portals.CGlobal;
import com.qouteall.immersive_portals.my_util.Helper;
import com.qouteall.immersive_portals.portal.Portal;
import com.qouteall.immersive_portals.render.PortalRenderer;
import com.qouteall.immersive_portals.render.ShaderManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlFramebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.optifine.shaders.Shaders;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public class RendererDeferred extends PortalRenderer {
    private GlFramebuffer deferredBuffer;
    private ShaderManager shaderManager;
    
    public static boolean isDebugMode = false;
    
    @Override
    public boolean shouldSkipClearing() {
        return false;
    }
    
    @Override
    protected void prepareStates() {
        GlFramebuffer mainFrameBuffer = mc.getFramebuffer();
        int width = mainFrameBuffer.viewWidth;
        int height = mainFrameBuffer.viewHeight;
        if (deferredBuffer == null) {
            deferredBuffer = new GlFramebuffer(
                width, height,
                true,//has depth attachment
                MinecraftClient.IS_SYSTEM_MAC
            );
        }
        if (width != deferredBuffer.viewWidth ||
            height != deferredBuffer.viewHeight
        ) {
            deferredBuffer.resize(
                width,
                height,
                MinecraftClient.IS_SYSTEM_MAC
            );
            Helper.log("Deferred buffer resized");
        }
        if (shaderManager == null) {
            shaderManager = new ShaderManager();
        }
    
        deferredBuffer.setClearColor(1, 0, 0, 0);
        deferredBuffer.clear(MinecraftClient.IS_SYSTEM_MAC);
    
        OFHelper.bindToShaderFrameBuffer();
    
        GlStateManager.viewport(0, 0, Shaders.renderWidth, Shaders.renderHeight);
    }
    
    @Override
    protected void doRenderPortal(Portal portal) {
        if (isRendering()) {
            //currently only support one-layer portal
            return;
        }
        if (isDebugMode) {
            if (renderedPortalNum >= 1) {
                return;
            }
        }

        if (!isDebugMode) {
            copyDepthFromMainToDeferred();
        }

        if (!isDebugMode) {
            if (!testShouldRenderPortal(portal)) {
                return;
            }
        }

        portalLayers.push(portal);

        manageCameraAndRenderPortalContent(portal);
        //it will bind the gbuffer of rendered dimension

        portalLayers.pop();

        deferredBuffer.beginWrite(true);

        if (!isDebugMode) {
            drawFrameBufferUp(portal, mc.getFramebuffer(), shaderManager);
        }
        else {
            GlStateManager.activeTexture(GL13.GL_TEXTURE0);
            mc.getFramebuffer().draw(
                deferredBuffer.viewWidth,
                deferredBuffer.viewHeight
            );
        }
    
        OFHelper.bindToShaderFrameBuffer();
    
    }
    
    @Override
    protected void renderPortalContentWithContextSwitched(
        Portal portal, Vec3d oldCameraPos
    ) {
        OFGlobal.shaderContextManager.switchContextAndRun(
            () -> {
                OFHelper.bindToShaderFrameBuffer();
                super.renderPortalContentWithContextSwitched(portal, oldCameraPos);
            }
        );
    }
    
    @Override
    public void renderPortalInEntityRenderer(Portal portal) {
        if (shouldRenderPortalInEntityRenderer(portal)) {
            drawPortalViewTriangle(portal);
        }
    }
    
    private boolean shouldRenderPortalInEntityRenderer(Portal portal) {
        Entity cameraEntity = MinecraftClient.getInstance().cameraEntity;
        if (cameraEntity == null) {
            return false;
        }
        Vec3d cameraPos = cameraEntity.getPos();
        if (Shaders.isShadowPass) {
            return true;
        }
        if (isRendering()) {
            return portal.isInFrontOfPortal(cameraPos);
        }
        return false;
    }
    
    //NOTE it will write to shader depth buffer
    private boolean testShouldRenderPortal(Portal portal) {
        return renderAndGetDoesAnySamplePassed(() -> {
            GlStateManager.enableDepthTest();
            GlStateManager.disableTexture();
            GlStateManager.colorMask(false, false, false, false);
            setupCameraTransformation();
            GL20.glUseProgram(0);
            
            drawPortalViewTriangle(portal);
            
            GlStateManager.enableTexture();
            GlStateManager.colorMask(true, true, true, true);
        });
    }
    
    private void copyDepthFromMainToDeferred() {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, OFGlobal.getDfb.get());
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, deferredBuffer.fbo);
        
        GL30.glBlitFramebuffer(
            0, 0, Shaders.renderWidth, Shaders.renderHeight,
            0, 0, deferredBuffer.viewWidth, deferredBuffer.viewHeight,
            GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST
        );
    
        OFHelper.bindToShaderFrameBuffer();
    }
    
    @Override
    public void onShaderRenderEnded() {
        if (isRendering()) {
            return;
        }
    
        if (renderedPortalNum == 0) {
            return;
        }
    
        GlStateManager.enableAlphaTest();
        GlFramebuffer mainFrameBuffer = mc.getFramebuffer();
        mainFrameBuffer.beginWrite(true);
        
        if (!isDebugMode) {
            CGlobal.doDisableAlphaTestWhenRenderingFrameBuffer = false;
        }
        deferredBuffer.draw(mainFrameBuffer.viewWidth, mainFrameBuffer.viewHeight);
        CGlobal.doDisableAlphaTestWhenRenderingFrameBuffer = true;
    }
}
