package com.meeple.memo;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.platform.GlStateManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.Matrix4f;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.culling.ClippingHelper;
import net.minecraft.client.renderer.culling.ClippingHelperImpl;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.texture.AtlasTexture;
import net.minecraft.client.settings.CloudOption;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.fluid.IFluidState;
import net.minecraft.potion.Effects;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

@OnlyIn(Dist.CLIENT)
public class MGameRenderer extends GameRenderer {

	static final Field lightmapTexture = Utils.getPrivateFieldOrNull(GameRenderer.class, "lightmapTexture"),
		mc = Utils.getPrivateFieldOrNull(GameRenderer.class, "mc"),
		farPlaneDistance = Utils.getPrivateFieldOrNull(GameRenderer.class, "farPlaneDistance"),
		//TODO this is activeRenderInfo
		activeRenderInfo = Utils.getPrivateFieldOrNull(GameRenderer.class, "field_215317_L"),
		fogRenderer = Utils.getPrivateFieldOrNull(GameRenderer.class, "fogRenderer"),
		fovModifierHandPrev = Utils.getPrivateFieldOrNull(GameRenderer.class, "fovModifierHandPrev"),
		fovModifierHand = Utils.getPrivateFieldOrNull(GameRenderer.class, "fovModifierHand"),
		debugView = Utils.getPrivateFieldOrNull(GameRenderer.class, "debugView"),

		rendererUpdateCount = Utils.getPrivateFieldOrNull(GameRenderer.class, "rendererUpdateCount"),
		cameraZoom = Utils.getPrivateFieldOrNull(GameRenderer.class, "cameraZoom"),
		cameraYaw = Utils.getPrivateFieldOrNull(GameRenderer.class, "cameraYaw"),
		cameraPitch = Utils.getPrivateFieldOrNull(GameRenderer.class, "cameraPitch"),
		renderHand = Utils.getPrivateFieldOrNull(GameRenderer.class, "renderHand"),
		frameCount = Utils.getPrivateFieldOrNull(GameRenderer.class, "frameCount")

	;

	static final Method isDrawBlockOutline = ObfuscationReflectionHelper.findMethod(GameRenderer.class, "isDrawBlockOutline"),
		applyBobbing = ObfuscationReflectionHelper.findMethod(GameRenderer.class, "applyBobbing", float.class),
		hurtCameraEffect = ObfuscationReflectionHelper.findMethod(GameRenderer.class, "hurtCameraEffect", float.class);

	public MGameRenderer(Minecraft minecraft) {
		super(minecraft, minecraft.getResourceManager());
	}

	static boolean hasFailed = false;

	@Override
	public void renderWorld(float partialTicks, long finishTimeNano) {
		try {

			GlStateManager.matrixMode(GL11.GL_MODELVIEW);
			GlStateManager.loadIdentity();
			GlStateManager.pushMatrix();
			GlStateManager.loadIdentity();
			float scale = 1f / MemoTreetops.scale;
			GlStateManager.scalef(scale, scale, scale);
			GlStateManager.pushMatrix();
			LightTexture lightmapTexture = (LightTexture) MGameRenderer.lightmapTexture.get(this);
			Minecraft mc = (Minecraft) MGameRenderer.mc.get(this);

			lightmapTexture.updateLightmap(partialTicks);
			if (mc.getRenderViewEntity() == null) {
				mc.setRenderViewEntity(mc.player);
			}

			getMouseOver(partialTicks);
			GlStateManager.enableDepthTest();
			GlStateManager.enableAlphaTest();
			GlStateManager.alphaFunc(GL11.GL_GREATER, 0.5F);
			mc.getProfiler().startSection("center");
			updateCameraAndRender(mc, partialTicks, finishTimeNano);
			mc.getProfiler().endSection();
			GlStateManager.popMatrix();

			GlStateManager.popMatrix();
		} catch (Exception e) {
			MemoTreetops.handleException(e);
		}
	}

	protected void changePerspective(Minecraft mc, ActiveRenderInfo activerenderinfo, float partialTicks, boolean t, float mul) throws IllegalArgumentException, IllegalAccessException {

		GlStateManager
			.multMatrix(
				Matrix4f
					.perspective(
						getFOVModifier(mc, activerenderinfo, partialTicks, t),
						(float) mc.mainWindow.getFramebufferWidth() / (float) mc.mainWindow.getFramebufferHeight(),
						0.05F,
						farPlaneDistance.getFloat(this) * mul));
	}

	private void updateCameraAndRender(Minecraft mc, float partialTicks, long nanoTime) throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {
		FogRenderer fogRenderer = (FogRenderer) MGameRenderer.fogRenderer.get(this);
		WorldRenderer worldrenderer = mc.worldRenderer;
		ParticleManager particlemanager = mc.particles;
		boolean flag = (boolean) MGameRenderer.isDrawBlockOutline.invoke(this);

		GlStateManager.matrixMode(GL11.GL_MODELVIEW);
		GlStateManager.loadIdentity();

		GlStateManager.enableCull();
		mc.getProfiler().endStartSection("camera");
		setupCameraTransform(mc, partialTicks);
		ActiveRenderInfo activerenderinfo = (ActiveRenderInfo) MGameRenderer.activeRenderInfo.get(this);
		activerenderinfo
			.func_216772_a(
				mc.world,
				(Entity) (mc.getRenderViewEntity() == null ? mc.player : mc.getRenderViewEntity()),
				mc.gameSettings.thirdPersonView > 0,
				mc.gameSettings.thirdPersonView == 2,
				partialTicks);
		ClippingHelper clippinghelper = ClippingHelperImpl.getInstance();
		mc.getProfiler().endStartSection("clear");
		GlStateManager.viewport(0, 0, mc.mainWindow.getFramebufferWidth(), mc.mainWindow.getFramebufferHeight());
		fogRenderer.func_217619_a(activerenderinfo, partialTicks);
		GlStateManager.clear(16640, Minecraft.IS_RUNNING_ON_MAC);
		mc.getProfiler().endStartSection("culling");
		ICamera icamera = new Frustum(clippinghelper);
		double d0 = activerenderinfo.getProjectedView().x;
		double d1 = activerenderinfo.getProjectedView().y;
		double d2 = activerenderinfo.getProjectedView().z;
		icamera.setPosition(d0, d1, d2);
		{
			if (mc.gameSettings.renderDistanceChunks >= 4) {
				fogRenderer.setupFog(activerenderinfo, -1, partialTicks);
				mc.getProfiler().endStartSection("sky");
				GlStateManager.matrixMode(GL11.GL_PROJECTION);
				GlStateManager.loadIdentity();
				changePerspective(mc, activerenderinfo, partialTicks, true, 2f);
				GlStateManager.matrixMode(GL11.GL_MODELVIEW);
				worldrenderer.renderSky(partialTicks);
				GlStateManager.matrixMode(GL11.GL_PROJECTION);
				GlStateManager.loadIdentity();
				changePerspective(mc, activerenderinfo, partialTicks, true, MathHelper.SQRT_2);

				GlStateManager.matrixMode(GL11.GL_MODELVIEW);
			}
		}
		fogRenderer.setupFog(activerenderinfo, 0, partialTicks);
		{
			//----------------Cloud bottom----------------//
			GlStateManager.shadeModel(GL11.GL_SMOOTH);
			if (activerenderinfo.getProjectedView().y < 128.0D) {
				renderCloudCheck(mc, fogRenderer, activerenderinfo, worldrenderer, partialTicks, d0, d1, d2);
			}
		}

		{

			//----------------terrain----------------//
			{
				//----------------terrain setup----------------//
				mc.getProfiler().endStartSection("prepareterrain");
				fogRenderer.setupFog(activerenderinfo, 0, partialTicks);
				mc.getTextureManager().bindTexture(AtlasTexture.LOCATION_BLOCKS_TEXTURE);
				RenderHelper.disableStandardItemLighting();
				mc.getProfiler().endStartSection("terrain_setup");
				mc.world.getChunkProvider().getLightManager().func_215575_a(Integer.MAX_VALUE, true, true);

				{
					int frameCount = MGameRenderer.frameCount.getInt(this);
					worldrenderer.func_215320_a(activerenderinfo, icamera, frameCount++, mc.player.isSpectator());
					MGameRenderer.frameCount.setInt(this, frameCount);
				}
				mc.getProfiler().endStartSection("updatechunks");
				mc.worldRenderer.updateChunks(nanoTime);
			}
			{
				//----------------terrain render----------------//
				mc.getProfiler().endStartSection("terrain");
				GlStateManager.matrixMode(GL11.GL_MODELVIEW);
				GlStateManager.pushMatrix();
				GlStateManager.disableAlphaTest();
				worldrenderer.renderBlockLayer(BlockRenderLayer.SOLID, activerenderinfo);
				GlStateManager.enableAlphaTest();
				mc.getTextureManager().getTexture(AtlasTexture.LOCATION_BLOCKS_TEXTURE).setBlurMipmap(false, mc.gameSettings.mipmapLevels > 0); // FORGE: fix flickering leaves when mods mess up the blurMipmap settings
				worldrenderer.renderBlockLayer(BlockRenderLayer.CUTOUT_MIPPED, activerenderinfo);
				mc.getTextureManager().getTexture(AtlasTexture.LOCATION_BLOCKS_TEXTURE).restoreLastBlurMipmap();
				mc.getTextureManager().getTexture(AtlasTexture.LOCATION_BLOCKS_TEXTURE).setBlurMipmap(false, false);
				worldrenderer.renderBlockLayer(BlockRenderLayer.CUTOUT, activerenderinfo);
			}
		}
		{

			//----------------reset----------------//
			mc.getTextureManager().getTexture(AtlasTexture.LOCATION_BLOCKS_TEXTURE).restoreLastBlurMipmap();
			GlStateManager.shadeModel(GL11.GL_FLAT);
			GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
			GlStateManager.matrixMode(GL11.GL_MODELVIEW);
			GlStateManager.popMatrix();
			GlStateManager.pushMatrix();
			RenderHelper.enableStandardItemLighting();
		}
		{

			//			float scale = MemoTreetops.scale;
			//			GlStateManager.scalef(scale, scale, scale);
			//			GlStateManager.pushMatrix();
			//----------------entities----------------//
			mc.getProfiler().endStartSection("entities");
			try {

				((MWorldRenderer) worldrenderer).renderEntities(activerenderinfo, icamera, partialTicks);
			} catch (Exception e) {
				worldrenderer.func_215326_a(activerenderinfo, icamera, partialTicks);
			}
			//			GlStateManager.popMatrix();
		}

		RenderHelper.disableStandardItemLighting();
		disableLightmap();
		GlStateManager.matrixMode(GL11.GL_MODELVIEW);
		GlStateManager.popMatrix();
		{
			if (flag && mc.objectMouseOver != null) {
				GlStateManager.disableAlphaTest();
				mc.getProfiler().endStartSection("outline");
				if (!net.minecraftforge.client.ForgeHooksClient.onDrawBlockHighlight(worldrenderer, activerenderinfo, mc.objectMouseOver, 0, partialTicks))
					worldrenderer.drawSelectionBox(activerenderinfo, mc.objectMouseOver, 0);
				GlStateManager.enableAlphaTest();
			}
		}
		if (mc.debugRenderer.shouldRender()) {
			mc.debugRenderer.renderDebug(nanoTime);
		}

		{

			//----------------destroy progress----------------//
			mc.getProfiler().endStartSection("destroyProgress");
			GlStateManager.enableBlend();
			GlStateManager.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
			mc.getTextureManager().getTexture(AtlasTexture.LOCATION_BLOCKS_TEXTURE).setBlurMipmap(false, false);
			worldrenderer.func_215318_a(Tessellator.getInstance(), Tessellator.getInstance().getBuffer(), activerenderinfo);
			mc.getTextureManager().getTexture(AtlasTexture.LOCATION_BLOCKS_TEXTURE).restoreLastBlurMipmap();
			GlStateManager.disableBlend();
		}
		{

			//----------------particles----------------//
			enableLightmap();
			fogRenderer.setupFog(activerenderinfo, 0, partialTicks);
			mc.getProfiler().endStartSection("particles");
			particlemanager.func_215233_a(activerenderinfo, partialTicks);
			disableLightmap();
			GlStateManager.depthMask(false);
			GlStateManager.enableCull();
		}
		{
			//----------------weather----------------//
			mc.getProfiler().endStartSection("weather");
			renderRainSnow(partialTicks);
			GlStateManager.depthMask(true);
		}
		{

			//----------------world border??----------------//
			worldrenderer.func_215322_a(activerenderinfo, partialTicks);
		}
		{
			GlStateManager.disableBlend();
			GlStateManager.enableCull();
			GlStateManager.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
			GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
			fogRenderer.setupFog(activerenderinfo, 0, partialTicks);
		}
		{
			GlStateManager.enableBlend();
			GlStateManager.depthMask(false);
			mc.getTextureManager().bindTexture(AtlasTexture.LOCATION_BLOCKS_TEXTURE);
			GlStateManager.shadeModel(GL11.GL_SMOOTH);
			mc.getProfiler().endStartSection("translucent");
			worldrenderer.renderBlockLayer(BlockRenderLayer.TRANSLUCENT, activerenderinfo);
			GlStateManager.shadeModel(GL11.GL_FLAT);
			GlStateManager.depthMask(true);
			GlStateManager.disableBlend();
		}
		{
			GlStateManager.enableCull();
			GlStateManager.disableFog();
			if (activerenderinfo.getProjectedView().y >= 128.0D) {
				mc.getProfiler().endStartSection("aboveClouds");
				renderCloudCheck(mc, fogRenderer, activerenderinfo, worldrenderer, partialTicks, d0, d1, d2);
			}
		}
		{

			//----------------post render event dispatch----------------//
			mc.getProfiler().endStartSection("forge_render_last");
			net.minecraftforge.client.ForgeHooksClient.dispatchRenderLast(worldrenderer, partialTicks);
		}
		{

			//----------------render hand----------------//
			mc.getProfiler().endStartSection("hand");
			if (renderHand.getBoolean(this)) {
				GlStateManager.clear(256, Minecraft.IS_RUNNING_ON_MAC);
				renderHand(mc, activerenderinfo, partialTicks);
			}
		}

	}

	private void setupCameraTransform(Minecraft mc, float partialTicks) throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {
		int rendererUpdateCount = MGameRenderer.rendererUpdateCount.getInt(this);
		double cameraZoom = MGameRenderer.cameraZoom.getDouble(this), cameraYaw = MGameRenderer.cameraYaw.getDouble(this), cameraPitch = MGameRenderer.cameraPitch.getDouble(this);
		farPlaneDistance.setFloat(this, (float) (mc.gameSettings.renderDistanceChunks * 16));
		GlStateManager.matrixMode(GL11.GL_PROJECTION);
		GlStateManager.loadIdentity();
		if (cameraZoom != 1.0D) {
			GlStateManager.translatef((float) cameraYaw, (float) (-cameraPitch), 0.0F);
			GlStateManager.scaled(cameraZoom, cameraZoom, 1.0D);
		}

		changePerspective(mc, (ActiveRenderInfo) activeRenderInfo.get(this), partialTicks, true, MathHelper.SQRT_2);

		float scale = 1 / MemoTreetops.scale;
		GlStateManager.scalef(scale, scale, scale);
		GlStateManager.matrixMode(GL11.GL_MODELVIEW);
		GlStateManager.loadIdentity();
		hurtCameraEffect.invoke(this, partialTicks);
		if (mc.gameSettings.viewBobbing) {
			applyBobbing.invoke(this, partialTicks);
		}

		float f = MathHelper.lerp(partialTicks, mc.player.prevTimeInPortal, mc.player.timeInPortal);
		if (f > 0.0F) {
			int i = 20;
			if (mc.player.isPotionActive(Effects.NAUSEA)) {
				i = 7;
			}

			float f1 = 5.0F / (f * f + 5.0F) - f * 0.04F;
			f1 = f1 * f1;
			GlStateManager.rotatef(((float) rendererUpdateCount + partialTicks) * (float) i, 0.0F, 1.0F, 1.0F);
			GlStateManager.scalef(1.0F / f1, 1.0F, 1.0F);
			GlStateManager.rotatef(-((float) rendererUpdateCount + partialTicks) * (float) i, 0.0F, 1.0F, 1.0F);
		}

	}

	private double getFOVModifier(Minecraft mc, ActiveRenderInfo p_215311_1_, float p_215311_2_, boolean p_215311_3_) throws IllegalArgumentException, IllegalAccessException {
		if (debugView.getBoolean(this)) {
			return 90.0D;
		} else {
			double d0 = 70.0D;
			if (p_215311_3_) {
				d0 = mc.gameSettings.fov;
				d0 = d0 * (double) MathHelper.lerp(p_215311_2_, fovModifierHandPrev.getDouble(this), fovModifierHand.getDouble(this));
			}

			if (p_215311_1_.func_216773_g() instanceof LivingEntity && ((LivingEntity) p_215311_1_.func_216773_g()).getHealth() <= 0.0F) {
				float f = (float) ((LivingEntity) p_215311_1_.func_216773_g()).deathTime + p_215311_2_;
				d0 /= (double) ((1.0F - 500.0F / (f + 500.0F)) * 2.0F + 1.0F);
			}

			IFluidState ifluidstate = p_215311_1_.func_216771_k();
			if (!ifluidstate.isEmpty()) {
				d0 = d0 * 60.0D / 70.0D;
			}

			return net.minecraftforge.client.ForgeHooksClient.getFOVModifier(this, p_215311_1_, p_215311_2_, d0);
		}
	}

	private void renderCloudCheck(Minecraft mc, FogRenderer fogRenderer, ActiveRenderInfo p_215313_1_, WorldRenderer p_215313_2_, float p_215313_3_, double p_215313_4_, double p_215313_6_,
		double p_215313_8_) throws IllegalArgumentException, IllegalAccessException {
		if (mc.gameSettings.getCloudOption() != CloudOption.OFF) {
			mc.getProfiler().endStartSection("clouds");
			GlStateManager.matrixMode(GL11.GL_PROJECTION);
			GlStateManager.loadIdentity();

			changePerspective(mc, p_215313_1_, p_215313_3_, true, 4f);

			GlStateManager.matrixMode(GL11.GL_MODELVIEW);
			GlStateManager.pushMatrix();
			fogRenderer.setupFog(p_215313_1_, 0, p_215313_3_);
			p_215313_2_.renderClouds(p_215313_3_, p_215313_4_, p_215313_6_, p_215313_8_);
			GlStateManager.disableFog();
			GlStateManager.popMatrix();
			GlStateManager.matrixMode(GL11.GL_PROJECTION);
			GlStateManager.loadIdentity();

			changePerspective(mc, p_215313_1_, p_215313_3_, true, MathHelper.SQRT_2);
			GlStateManager.matrixMode(GL11.GL_MODELVIEW);
		}

	}

	private void renderHand(Minecraft mc, ActiveRenderInfo p_215308_1_, float p_215308_2_) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		if (!MGameRenderer.debugView.getBoolean(this)) {
			GlStateManager.matrixMode(GL11.GL_PROJECTION);
			GlStateManager.loadIdentity();

			changePerspective(mc, p_215308_1_, p_215308_2_, true, 2f);
			GlStateManager.matrixMode(GL11.GL_MODELVIEW);
			GlStateManager.loadIdentity();
			GlStateManager.pushMatrix();
			hurtCameraEffect.invoke(this, p_215308_2_);
			if (mc.gameSettings.viewBobbing) {
				applyBobbing.invoke(this, p_215308_2_);
			}

			boolean flag = mc.getRenderViewEntity() instanceof LivingEntity && ((LivingEntity) mc.getRenderViewEntity()).isSleeping();
			if (!net.minecraftforge.client.ForgeHooksClient.renderFirstPersonHand(mc.worldRenderer, p_215308_2_))
				if (mc.gameSettings.thirdPersonView == 0 && !flag && !mc.gameSettings.hideGUI && mc.playerController.getCurrentGameType() != GameType.SPECTATOR) {
					enableLightmap();
					itemRenderer.renderItemInFirstPerson(p_215308_2_);
					disableLightmap();
				}

			GlStateManager.popMatrix();
			if (mc.gameSettings.thirdPersonView == 0 && !flag) {
				itemRenderer.renderOverlays(p_215308_2_);
				hurtCameraEffect.invoke(this, p_215308_2_);
			}

			if (mc.gameSettings.viewBobbing) {
				applyBobbing.invoke(this, p_215308_2_);
			}

		}
	}
}
