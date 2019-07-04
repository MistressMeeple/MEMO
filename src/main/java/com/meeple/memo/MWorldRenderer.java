package com.meeple.memo;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.GLX;
import com.mojang.blaze3d.platform.GlStateManager;

import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.AbstractChunkRenderContainer;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.DestroyBlockProgress;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.chunk.ChunkRender;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.LivingRenderer;
import net.minecraft.client.renderer.entity.model.EntityModel;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.EnderDragonPartEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.resources.IReloadableResourceManager;
import net.minecraft.state.properties.ChestType;
import net.minecraft.tileentity.ChestTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

public class MWorldRenderer extends WorldRenderer {

	static final Field renderEntitiesStartupCounter = Utils.getPrivateFieldOrNull(WorldRenderer.class, "renderEntitiesStartupCounter"),
		world = Utils.getPrivateFieldOrNull(WorldRenderer.class, "world"),
		renderManager = Utils.getPrivateFieldOrNull(WorldRenderer.class, "renderManager"),
		mc = Utils.getPrivateFieldOrNull(WorldRenderer.class, "mc"),
		countEntitiesRendered = Utils.getPrivateFieldOrNull(WorldRenderer.class, "countEntitiesRendered"),
		countEntitiesHidden = Utils.getPrivateFieldOrNull(WorldRenderer.class, "countEntitiesHidden"),
		entityOutlinesRendered = Utils.getPrivateFieldOrNull(WorldRenderer.class, "entityOutlinesRendered"),
		entityOutlineFramebuffer = Utils.getPrivateFieldOrNull(WorldRenderer.class, "entityOutlineFramebuffer"),
		entityOutlineShader = Utils.getPrivateFieldOrNull(WorldRenderer.class, "entityOutlineShader"),
		renderInfos = Utils.getPrivateFieldOrNull(WorldRenderer.class, "renderInfos"),
		setTileEntities = Utils.getPrivateFieldOrNull(WorldRenderer.class, "setTileEntities"),
		damagedBlocks = Utils.getPrivateFieldOrNull(WorldRenderer.class, "damagedBlocks"),
		prevRenderSortX = Utils.getPrivateFieldOrNull(WorldRenderer.class, "prevRenderSortX"),
		prevRenderSortY = Utils.getPrivateFieldOrNull(WorldRenderer.class, "prevRenderSortY"),
		prevRenderSortZ = Utils.getPrivateFieldOrNull(WorldRenderer.class, "prevRenderSortZ"),
		renderContainer = Utils.getPrivateFieldOrNull(WorldRenderer.class, "renderContainer"),
		renderDispatcher = Utils.getPrivateFieldOrNull(WorldRenderer.class, "renderDispatcher"),
		destroyBlockIcons = Utils.getPrivateFieldOrNull(WorldRenderer.class, "destroyBlockIcons"),
		textureManager = Utils.getPrivateFieldOrNull(WorldRenderer.class, "textureManager");

	public MWorldRenderer(Minecraft mcIn) {
		super(mcIn);
		try {
			MWorldRenderer.renderContainer.set(this, new MVboRenderList());
			//TextureManager p_i50971_1_, ItemRenderer p_i50971_2_, IReloadableResourceManager p_i50971_3_
			Minecraft mc = Minecraft.getInstance();
			MEEntityRendererManager mer = new MEEntityRendererManager(mc.textureManager, mc.getItemRenderer(), (IReloadableResourceManager) mc.getResourceManager());
			MWorldRenderer.renderManager.set(this, mer);
			Field mcRenderManager = Utils.getPrivateField(Minecraft.class, "renderManager");
			mcRenderManager.set(mc, mer);
			if (!(mc.getRenderManager() instanceof MEEntityRendererManager)) {
				MemoTreetops.handleException(new Throwable("Failed to set minecraft render manager. this is required."));
			}
		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException err) {
			MemoTreetops.handleException(err);
		}

	}

	private static Field renderChunk;

	private ChunkRender getRenderChunk(Object o) {
		if (MWorldRenderer.renderChunk == null) {
			MWorldRenderer.renderChunk = Utils.getPrivateFieldOrNull(o.getClass(), "renderChunk");
		}
		try {
			return (ChunkRender) MWorldRenderer.renderChunk.get(o);
		} catch (IllegalArgumentException | IllegalAccessException err) {
			MemoTreetops.handleException(err);
		}
		return null;

	}

	public static class MEEntityRendererManager extends EntityRendererManager {
		static final Field renderPosX = Utils.getPrivateFieldOrNull(EntityRendererManager.class, "renderPosX"),
			renderPosY = Utils.getPrivateFieldOrNull(EntityRendererManager.class, "renderPosY"),
			renderPosZ = Utils.getPrivateFieldOrNull(EntityRendererManager.class, "renderPosZ"),
			renderOutlines = Utils.getPrivateFieldOrNull(EntityRendererManager.class, "renderOutlines");

		public MEEntityRendererManager(TextureManager p_i50971_1_, ItemRenderer p_i50971_2_, IReloadableResourceManager p_i50971_3_) {
			super(p_i50971_1_, p_i50971_2_, p_i50971_3_);
		}

		public void renderEntityStatic(Entity entityIn, float partialTicks, boolean p_188388_3_) {

			try {
				if (entityIn.ticksExisted == 0) {
					entityIn.lastTickPosX = entityIn.posX;
					entityIn.lastTickPosY = entityIn.posY;
					entityIn.lastTickPosZ = entityIn.posZ;
				}

				double d0 = MathHelper.lerp((double) partialTicks, entityIn.lastTickPosX, entityIn.posX);
				double d1 = MathHelper.lerp((double) partialTicks, entityIn.lastTickPosY, entityIn.posY);
				double d2 = MathHelper.lerp((double) partialTicks, entityIn.lastTickPosZ, entityIn.posZ);
				float f = MathHelper.lerp(partialTicks, entityIn.prevRotationYaw, entityIn.rotationYaw);
				int i = entityIn.getBrightnessForRender();
				if (entityIn.isBurning()) {
					i = 15728880;
				}

				int j = i % 65536;
				int k = i / 65536;
				GLX.glMultiTexCoord2f(GLX.GL_TEXTURE1, (float) j, (float) k);
				GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
				double renderPosX, renderPosY, renderPosZ;
				renderPosX = MEEntityRendererManager.renderPosX.getDouble(this);
				renderPosY = MEEntityRendererManager.renderPosY.getDouble(this);
				renderPosZ = MEEntityRendererManager.renderPosZ.getDouble(this);
				this.renderEntity(entityIn, d0 - renderPosX, d1 - renderPosY, d2 - renderPosZ, f, partialTicks, p_188388_3_);
			} catch (IllegalArgumentException | IllegalAccessException err) {
				MemoTreetops.handleException(err);
			}
		}

		protected static class MEntityRenderer<T extends LivingEntity, M extends EntityModel<T>> extends LivingRenderer<T, M> {
			static final Method _MGetEntityTexture = ObfuscationReflectionHelper.findMethod(EntityRenderer.class, "getEntityTexture", Entity.class),
				preRenderCallback = ObfuscationReflectionHelper.findMethod(LivingRenderer.class, "preRenderCallback", LivingEntity.class, float.class);
			static final Field _FShadowSize = Utils.getPrivateFieldOrNull(EntityRenderer.class, "shadowSize");
			protected LivingRenderer<T, M> parent;

			protected MEntityRenderer(LivingRenderer<T, M> parent, EntityRendererManager manager) throws IllegalArgumentException, IllegalAccessException {
				super(manager, parent.getEntityModel(), _FShadowSize.getFloat(parent));
				this.parent = parent;
			}

			@Override
			protected ResourceLocation getEntityTexture(T entity) {
				try {
					return (ResourceLocation) _MGetEntityTexture.invoke(parent, entity);
				} catch (Exception e) {
					MemoTreetops.handleException(e);
				}
				return null;

			}

			@Override
			protected void preRenderCallback(T entitylivingbaseIn, float partialTickTime) {
				try {
					preRenderCallback.invoke(parent, entitylivingbaseIn, partialTickTime);
				} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException err) {
					MemoTreetops.handleException(err);
				}

			}

			@Override
			public void doRender(T entity, double x, double y, double z, float entityYaw, float partialTicks) {
				super.doRender(entity, x, y, z, entityYaw, partialTicks);
			}

			@Override
			public void setRenderOutlines(boolean renderOutlinesIn) {
				parent.setRenderOutlines(renderOutlinesIn);
			}

			@Override
			public void doRenderShadowAndFire(Entity entityIn, double x, double y, double z, float yaw, float partialTicks) {
				// TODO Auto-generated method stub
				parent.doRenderShadowAndFire(entityIn, x, y, z, yaw, partialTicks);
			}

			public int hashCode() {
				return parent.hashCode();
			}

			public boolean shouldRender(T livingEntity, ICamera camera, double camX, double camY, double camZ) {
				return parent.shouldRender(livingEntity, camera, camX, camY, camZ);
			}

			public M getEntityModel() {
				return parent.getEntityModel();
			}

			public boolean equals(Object obj) {
				return parent.equals(obj);
			}

			public void bindTexture(ResourceLocation location) {
				parent.bindTexture(location);
			}

			public float prepareScale(T entitylivingbaseIn, float partialTicks) {
				float scale = MemoTreetops.scale;
				float ret = parent.prepareScale(entitylivingbaseIn, partialTicks) * scale;
				GlStateManager.translatef(0.0F, -1.501F * (scale - 1), 0.0F);
				return ret;
			}

			public String toString() {
				return parent.toString();
			}

			public FontRenderer getFontRendererFromRenderManager() {
				return parent.getFontRendererFromRenderManager();
			}

			public EntityRendererManager getRenderManager() {
				return parent.getRenderManager();
			}

			public boolean isMultipass() {
				return parent.isMultipass();
			}

			public void renderMultipass(T entityIn, double x, double y, double z, float entityYaw, float partialTicks) {
				parent.renderMultipass(entityIn, x, y, z, entityYaw, partialTicks);
			}

			public void func_217758_e(T p_217758_1_) {
				parent.func_217758_e(p_217758_1_);
			}

			public void renderName(T entity, double x, double y, double z) {
				parent.renderName(entity, x, y, z);
			}
		}

		public void renderEntity(Entity entityIn, double x, double y, double z, float yaw, float partialTicks, boolean p_188391_10_) {

			try {

				EntityRenderer<Entity> entityrenderer = null;
				boolean renderOutlines = MEEntityRendererManager.renderOutlines.getBoolean(this);
				boolean debugBoundingBox = Minecraft.getInstance().getRenderManager().isDebugBoundingBox();

				entityrenderer = this.getEntityRenderObject(entityIn);

				if (entityrenderer != null && this.textureManager != null) {
					if (entityrenderer instanceof LivingRenderer) {
						@SuppressWarnings({ "rawtypes", "unchecked" })
						LivingRenderer<LivingEntity, EntityModel<LivingEntity>> lr = (LivingRenderer) entityrenderer;
						MEntityRenderer<LivingEntity, EntityModel<LivingEntity>> renderer = new MEntityRenderer<LivingEntity, EntityModel<LivingEntity>>(lr, null);
						renderer.setRenderOutlines(renderOutlines);
						renderer.doRender((LivingEntity) entityIn, x, y, z, yaw, partialTicks);

						if (!renderOutlines) {
							renderer.doRenderShadowAndFire(entityIn, x, y, z, yaw, partialTicks);
						}
						//not doing normal rendering
					} else {

						entityrenderer.setRenderOutlines(renderOutlines);
						entityrenderer.doRender(entityIn, x, y, z, yaw, partialTicks);

						if (!renderOutlines) {
							entityrenderer.doRenderShadowAndFire(entityIn, x, y, z, yaw, partialTicks);
						}
					}
					if (debugBoundingBox && !entityIn.isInvisible() && !p_188391_10_ && !Minecraft.getInstance().isReducedDebug()) {

						this.renderDebugBoundingBox(entityIn, x, y, z, yaw, partialTicks);

					}
				}

			} catch (Exception e) {
				MemoTreetops.handleException(e);
			}

		}

		private void renderDebugBoundingBox(Entity entityIn, double x, double y, double z, float entityYaw, float partialTicks) throws IllegalArgumentException, IllegalAccessException {

			double renderPosX, renderPosY, renderPosZ;
			renderPosX = MEEntityRendererManager.renderPosX.getDouble(this);
			renderPosY = MEEntityRendererManager.renderPosY.getDouble(this);
			renderPosZ = MEEntityRendererManager.renderPosZ.getDouble(this);

			GlStateManager.depthMask(false);
			GlStateManager.disableTexture();
			GlStateManager.disableLighting();
			GlStateManager.disableCull();
			GlStateManager.disableBlend();
			float f = entityIn.getWidth() / (2.0F);
			AxisAlignedBB axisalignedbb = entityIn.getBoundingBox();
			WorldRenderer
				.drawBoundingBox(
					axisalignedbb.minX - entityIn.posX + x,
					axisalignedbb.minY - entityIn.posY + y,
					axisalignedbb.minZ - entityIn.posZ + z,
					axisalignedbb.maxX - entityIn.posX + x,
					axisalignedbb.maxY - entityIn.posY + y,
					axisalignedbb.maxZ - entityIn.posZ + z,
					1.0F,
					1.0F,
					1.0F,
					1.0F);
			if (entityIn instanceof EnderDragonEntity) {
				for (EnderDragonPartEntity enderdragonpartentity : ((EnderDragonEntity) entityIn).func_213404_dT()) {
					double d0 = (enderdragonpartentity.posX - enderdragonpartentity.prevPosX) * (double) partialTicks;
					double d1 = (enderdragonpartentity.posY - enderdragonpartentity.prevPosY) * (double) partialTicks;
					double d2 = (enderdragonpartentity.posZ - enderdragonpartentity.prevPosZ) * (double) partialTicks;
					AxisAlignedBB axisalignedbb1 = enderdragonpartentity.getBoundingBox();

					WorldRenderer
						.drawBoundingBox(
							axisalignedbb1.minX - renderPosX + d0,
							axisalignedbb1.minY - renderPosY + d1,
							axisalignedbb1.minZ - renderPosZ + d2,
							axisalignedbb1.maxX - renderPosX + d0,
							axisalignedbb1.maxY - renderPosY + d1,
							axisalignedbb1.maxZ - renderPosZ + d2,
							0.25F,
							1.0F,
							0.0F,
							1.0F);
				}
			}

			float eyeHeight = entityIn.getEyeHeight();
			if (entityIn instanceof LivingEntity) {
				float f1 = 0.01F;
				WorldRenderer
					.drawBoundingBox(
						x - (double) f,
						y + (double) eyeHeight - (double) f1,
						z - (double) f,
						x + (double) f,
						y + (double) eyeHeight + (double) f1,
						z + (double) f,
						1.0F,
						0.0F,
						0.0F,
						1.0F);
			}

			Tessellator tessellator = Tessellator.getInstance();
			BufferBuilder bufferbuilder = tessellator.getBuffer();
			Vec3d vec3d = entityIn.getLook(partialTicks);
			bufferbuilder.begin(3, DefaultVertexFormats.POSITION_COLOR);
			bufferbuilder.pos(x, y + (double) eyeHeight, z).color(0, 0, 255, 255).endVertex();
			bufferbuilder.pos(x + vec3d.x * 2.0D, y + (double) eyeHeight + vec3d.y * 2.0D, z + vec3d.z * 2.0D).color(0, 0, 255, 255).endVertex();
			tessellator.draw();
			GlStateManager.enableTexture();
			GlStateManager.enableLighting();
			GlStateManager.enableCull();
			GlStateManager.disableBlend();
			GlStateManager.depthMask(true);
		}
	}

	public void renderEntities(ActiveRenderInfo activeRenderInfo, ICamera camera, float partialTicks) {
		this.func_215326_a(activeRenderInfo, camera, partialTicks);
	}

	/**
	 * Entity rendering 
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void func_215326_a(ActiveRenderInfo activeRenderInfo, ICamera camera, float partialTicks) {

		try {
			if (renderEntitiesStartupCounter.getInt(this) > 0) {
				renderEntitiesStartupCounter.setInt(this, renderEntitiesStartupCounter.getInt(this) - 1);
			} else {
				double d0 = activeRenderInfo.getProjectedView().x;
				double d1 = activeRenderInfo.getProjectedView().y;
				double d2 = activeRenderInfo.getProjectedView().z;
				ClientWorld world = (ClientWorld) MWorldRenderer.world.get(this);
				Minecraft mc = (Minecraft) MWorldRenderer.mc.get(this);
				EntityRendererManager renderManager = (EntityRendererManager) MWorldRenderer.renderManager.get(this);
				world.getProfiler().startSection("prepare");
				TileEntityRendererDispatcher.instance.func_217665_a(world, mc.getTextureManager(), mc.fontRenderer, activeRenderInfo, mc.objectMouseOver);
				renderManager.func_217781_a(world, mc.fontRenderer, activeRenderInfo, mc.pointedEntity, mc.gameSettings);
				countEntitiesRendered.setInt(this, 0);
				countEntitiesHidden.setInt(this, 0);
				double d3 = activeRenderInfo.getProjectedView().x;
				double d4 = activeRenderInfo.getProjectedView().y;
				double d5 = activeRenderInfo.getProjectedView().z;
				TileEntityRendererDispatcher.staticPlayerX = d3;
				TileEntityRendererDispatcher.staticPlayerY = d4;
				TileEntityRendererDispatcher.staticPlayerZ = d5;
				renderManager.setRenderPosition(d3, d4, d5);
				mc.gameRenderer.enableLightmap();
				world.getProfiler().endStartSection("entities");
				List<Entity> renderOutlines = Lists.newArrayList();
				List<Entity> renderMultipass = Lists.newArrayList();

				for (Entity entity : world.func_217416_b()) {
					if ((renderManager.shouldRender(entity, camera, d0, d1, d2) || entity.isRidingOrBeingRiddenBy(mc.player)) && (entity != activeRenderInfo.func_216773_g() ||
						activeRenderInfo.func_216770_i() || activeRenderInfo.func_216773_g() instanceof LivingEntity && ((LivingEntity) activeRenderInfo.func_216773_g()).isSleeping())) {
						countEntitiesRendered.setInt(this, countEntitiesRendered.getInt(this) + 1);

						renderManager.renderEntityStatic(entity, partialTicks, false);

						if (entity.isGlowing() || entity instanceof PlayerEntity && mc.player.isSpectator() && mc.gameSettings.keyBindSpectatorOutlines.isKeyDown()) {
							renderOutlines.add(entity);
						}

						if (renderManager.isRenderMultipass(entity)) {
							renderMultipass.add(entity);
						}
					}
				}

				if (!renderMultipass.isEmpty()) {
					for (Entity entity1 : renderMultipass) {
						renderManager.renderMultipass(entity1, partialTicks);
					}
				}

				if (isRenderEntityOutlines() && (!renderOutlines.isEmpty() || entityOutlinesRendered.getBoolean(this))) {
					world.getProfiler().endStartSection("entityOutlines");
					Framebuffer entityOutlineFramebuffer = (Framebuffer) MWorldRenderer.entityOutlineFramebuffer.get(this);
					entityOutlineFramebuffer.func_216493_b(Minecraft.IS_RUNNING_ON_MAC);
					entityOutlinesRendered.setBoolean(this, !renderOutlines.isEmpty());
					if (!renderOutlines.isEmpty()) {
						GlStateManager.depthFunc(519);
						GlStateManager.disableFog();
						entityOutlineFramebuffer.bindFramebuffer(false);
						RenderHelper.disableStandardItemLighting();
						renderManager.setRenderOutlines(true);

						for (int i = 0; i < renderOutlines.size(); ++i) {
							renderManager.renderEntityStatic(renderOutlines.get(i), partialTicks, false);
						}

						renderManager.setRenderOutlines(false);
						RenderHelper.enableStandardItemLighting();
						GlStateManager.depthMask(false);
						ShaderGroup entityOutlineShader = (ShaderGroup) MWorldRenderer.entityOutlineShader.get(this);
						entityOutlineShader.render(partialTicks);
						GlStateManager.enableLighting();
						GlStateManager.depthMask(true);
						GlStateManager.enableFog();
						GlStateManager.enableBlend();
						GlStateManager.enableColorMaterial();
						GlStateManager.depthFunc(515);
						GlStateManager.enableDepthTest();
						GlStateManager.enableAlphaTest();
					}

					mc.getFramebuffer().bindFramebuffer(false);
				}

				world.getProfiler().endStartSection("blockentities");
				RenderHelper.enableStandardItemLighting();

				TileEntityRendererDispatcher.instance.preDrawBatch();

				List<?> renderInfos = (List<?>) MWorldRenderer.renderInfos.get(this);
				for (Object renderglobal$containerlocalrenderinformation : renderInfos) {

					ChunkRender renderChunk = getRenderChunk(renderglobal$containerlocalrenderinformation);
					List<TileEntity> list2 = renderChunk.getCompiledChunk().getTileEntities();
					if (!list2.isEmpty()) {
						for (TileEntity tileentity : list2) {
							if (!camera.isBoundingBoxInFrustum(tileentity.getRenderBoundingBox()))
								continue;
							TileEntityRendererDispatcher.instance.render(tileentity, partialTicks, -1);
						}
					}
				}
				Set<TileEntity> setTileEntities = (Set<TileEntity>) MWorldRenderer.setTileEntities.get(this);
				synchronized (setTileEntities) {
					for (TileEntity tileentity1 : setTileEntities) {
						if (!camera.isBoundingBoxInFrustum(tileentity1.getRenderBoundingBox()))
							continue;
						TileEntityRendererDispatcher.instance.render(tileentity1, partialTicks, -1);
					}
				}
				TileEntityRendererDispatcher.instance.drawBatch();

				preRenderDamagedBlocks();
				Map<Integer, DestroyBlockProgress> damagedBlocks = (Map<Integer, DestroyBlockProgress>) MWorldRenderer.damagedBlocks.get(this);
				for (DestroyBlockProgress destroyblockprogress : damagedBlocks.values()) {
					BlockPos blockpos = destroyblockprogress.getPosition();
					BlockState blockstate = world.getBlockState(blockpos);
					if (blockstate.hasTileEntity()) {
						TileEntity tileentity2 = world.getTileEntity(blockpos);
						if (tileentity2 instanceof ChestTileEntity && blockstate.get(ChestBlock.TYPE) == ChestType.LEFT) {
							blockpos = blockpos.offset(blockstate.get(ChestBlock.FACING).rotateY());
							tileentity2 = world.getTileEntity(blockpos);
						}

						if (tileentity2 != null && blockstate.hasCustomBreakingProgress()) {
							TileEntityRendererDispatcher.instance.render(tileentity2, partialTicks, destroyblockprogress.getPartialBlockDamage());
						}
					}
				}

				postRenderDamagedBlocks();
				mc.gameRenderer.disableLightmap();
				mc.getProfiler().endSection();
			}
		} catch (Exception e) {
			MemoTreetops.handleException(e);
		}
	}

	private void preRenderDamagedBlocks() {
		GlStateManager.blendFuncSeparate(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.SRC_COLOR, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
		GlStateManager.enableBlend();
		GlStateManager.color4f(1.0F, 1.0F, 1.0F, 0.5F);
		GlStateManager.polygonOffset(-1.0F, -10.0F);
		GlStateManager.enablePolygonOffset();
		GlStateManager.alphaFunc(516, 0.1F);
		GlStateManager.enableAlphaTest();
		GlStateManager.pushMatrix();
	}

	private void postRenderDamagedBlocks() {
		GlStateManager.disableAlphaTest();
		GlStateManager.polygonOffset(0.0F, 0.0F);
		GlStateManager.disablePolygonOffset();
		GlStateManager.enableAlphaTest();
		GlStateManager.depthMask(true);
		GlStateManager.popMatrix();
	}

	public int renderBlockLayer(BlockRenderLayer blockLayerIn, ActiveRenderInfo p_215323_2_) {

		try {

			Minecraft mc = (Minecraft) MWorldRenderer.mc.get(this);
			List<?> renderInfos = (List<?>) MWorldRenderer.renderInfos.get(this);
			AbstractChunkRenderContainer renderContainer = (AbstractChunkRenderContainer) MWorldRenderer.renderContainer.get(this);
			ChunkRenderDispatcher renderDispatcher = (ChunkRenderDispatcher) MWorldRenderer.renderDispatcher.get(this);
			RenderHelper.disableStandardItemLighting();
			if (blockLayerIn == BlockRenderLayer.TRANSLUCENT) {
				mc.getProfiler().startSection("translucent_sort");
				double d0 = p_215323_2_.getProjectedView().x - MWorldRenderer.prevRenderSortX.getDouble(this);
				double d1 = p_215323_2_.getProjectedView().y - MWorldRenderer.prevRenderSortY.getDouble(this);
				double d2 = p_215323_2_.getProjectedView().z - MWorldRenderer.prevRenderSortZ.getDouble(this);
				if (d0 * d0 + d1 * d1 + d2 * d2 > 1.0D) {
					prevRenderSortX.setDouble(this, p_215323_2_.getProjectedView().x);
					prevRenderSortY.setDouble(this, p_215323_2_.getProjectedView().y);
					prevRenderSortX.setDouble(this, p_215323_2_.getProjectedView().z);
					int k = 0;

					for (Object renderglobal$containerlocalrenderinformation : renderInfos) {

						ChunkRender renderChunk = getRenderChunk(renderglobal$containerlocalrenderinformation);
						if (renderChunk.compiledChunk.isLayerStarted(blockLayerIn) && k++ < 15) {

							renderDispatcher.updateTransparencyLater(renderChunk);
						}
					}
				}

				mc.getProfiler().endSection();
			}

			mc.getProfiler().startSection("filterempty");
			int l = 0;
			boolean flag = blockLayerIn == BlockRenderLayer.TRANSLUCENT;
			int i1 = flag ? renderInfos.size() - 1 : 0;
			int i = flag ? -1 : renderInfos.size();
			int j1 = flag ? -1 : 1;

			for (int j = i1; j != i; j += j1) {
				Object o = renderInfos.get(j);
				ChunkRender chunkrender = getRenderChunk(o);
				if (!chunkrender.getCompiledChunk().isLayerEmpty(blockLayerIn)) {
					++l;
					renderContainer.addRenderChunk(chunkrender, blockLayerIn);
				}
			}

			mc.getProfiler().endStartSection("render_" + blockLayerIn);
			this.renderBlockLayer(blockLayerIn);
			mc.getProfiler().endSection();
			return l;

		} catch (Exception e) {
			return MemoTreetops.handleException(e);
		}
	}

	private void renderBlockLayer(BlockRenderLayer blockLayerIn) throws IllegalArgumentException, IllegalAccessException {
		Minecraft mc = (Minecraft) MWorldRenderer.mc.get(this);
		mc.gameRenderer.enableLightmap();
		if (GLX.useVbo()) {
			GlStateManager.enableClientState(32884);
			GLX.glClientActiveTexture(GLX.GL_TEXTURE0);
			GlStateManager.enableClientState(32888);
			GLX.glClientActiveTexture(GLX.GL_TEXTURE1);
			GlStateManager.enableClientState(32888);
			GLX.glClientActiveTexture(GLX.GL_TEXTURE0);
			GlStateManager.enableClientState(32886);
		}
		AbstractChunkRenderContainer renderContainer = (AbstractChunkRenderContainer) MWorldRenderer.renderContainer.get(this);
		((MVboRenderList) renderContainer).renderChunkLayer(blockLayerIn);
		if (GLX.useVbo()) {
			for (VertexFormatElement vertexformatelement : DefaultVertexFormats.BLOCK.getElements()) {
				VertexFormatElement.Usage vertexformatelement$usage = vertexformatelement.getUsage();
				int i = vertexformatelement.getIndex();
				switch (vertexformatelement$usage) {
					case POSITION:
						GlStateManager.disableClientState(32884);
						break;
					case UV:
						GLX.glClientActiveTexture(GLX.GL_TEXTURE0 + i);
						GlStateManager.disableClientState(32888);
						GLX.glClientActiveTexture(GLX.GL_TEXTURE0);
						break;
					case COLOR:
						GlStateManager.disableClientState(32886);
						GlStateManager.clearCurrentColor();
					default:
						break;
				}
			}
		}

		mc.gameRenderer.disableLightmap();
	}

	@Override
	public void func_215318_a(Tessellator p_215318_1_, BufferBuilder p_215318_2_, ActiveRenderInfo p_215318_3_) {
		/*	try {
				Minecraft mc = (Minecraft) MWorldRenderer.mc.get(this);
				World world = (World) MWorldRenderer.world.get(this);
		
				@SuppressWarnings("unchecked")
				Map<Integer, DestroyBlockProgress> damagedBlocks = (Map<Integer, DestroyBlockProgress>) MWorldRenderer.damagedBlocks.get(this);
				TextureAtlasSprite[] destroyBlockIcons = (TextureAtlasSprite[]) MWorldRenderer.destroyBlockIcons.get(this);
				TextureManager textureManager = (TextureManager) MWorldRenderer.textureManager.get(this);
				double d0 = p_215318_3_.getProjectedView().x;
				double d1 = p_215318_3_.getProjectedView().y;
				double d2 = p_215318_3_.getProjectedView().z;
				if (!damagedBlocks.isEmpty()) {
					textureManager.bindTexture(AtlasTexture.LOCATION_BLOCKS_TEXTURE);
					this.preRenderDamagedBlocks();
					p_215318_2_.begin(7, DefaultVertexFormats.BLOCK);
					p_215318_2_.setTranslation(-d0, -d1, -d2);
					p_215318_2_.noColor();
					Iterator<DestroyBlockProgress> iterator = damagedBlocks.values().iterator();
		
					while (iterator.hasNext()) {
						DestroyBlockProgress destroyblockprogress = iterator.next();
						BlockPos blockpos = destroyblockprogress.getPosition();
						Block block = world.getBlockState(blockpos).getBlock();
						TileEntity te = world.getTileEntity(blockpos);
						boolean hasBreak = block instanceof ChestBlock || block instanceof EnderChestBlock || block instanceof AbstractSignBlock || block instanceof AbstractSkullBlock;
						if (!hasBreak)
							hasBreak = te != null && te.canRenderBreaking();
		
						if (!hasBreak) {
							double d3 = (double) blockpos.getX() - d0;
							double d4 = (double) blockpos.getY() - d1;
							double d5 = (double) blockpos.getZ() - d2;
							if (d3 * d3 + d4 * d4 + d5 * d5 > 1024.0D) {
								iterator.remove();
							} else {
								BlockState blockstate = world.getBlockState(blockpos);
								if (!blockstate.isAir(world, blockpos)) {
									int i = destroyblockprogress.getPartialBlockDamage();
									TextureAtlasSprite textureatlassprite = destroyBlockIcons[i];
									BlockRendererDispatcher blockrendererdispatcher = mc.getBlockRendererDispatcher();
									blockrendererdispatcher.renderBlockDamage(blockstate, blockpos, textureatlassprite, world);
								}
							}
						}
					}
		
					p_215318_1_.draw();
					p_215318_2_.setTranslation(0.0D, 0.0D, 0.0D);
					this.postRenderDamagedBlocks();
				}
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(0);
			}*/
	}

	@OnlyIn(Dist.CLIENT)
	public static class MVboRenderList extends AbstractChunkRenderContainer {
		static final Field viewEntityX = Utils.getPrivateFieldOrNull(AbstractChunkRenderContainer.class, "viewEntityX"),
			viewEntityY = Utils.getPrivateFieldOrNull(AbstractChunkRenderContainer.class, "viewEntityY"),
			viewEntityZ = Utils.getPrivateFieldOrNull(AbstractChunkRenderContainer.class, "viewEntityZ");

		@Override
		public void renderChunkLayer(BlockRenderLayer layer) {
			try {

				if (this.initialized) {
					for (ChunkRender chunkrender : this.renderChunks) {
						VertexBuffer vertexbuffer = chunkrender.getVertexBufferByLayer(layer.ordinal());
						GlStateManager.pushMatrix();

						BlockPos blockpos = chunkrender.getPosition();

						float x = (float) ((double) (blockpos.getX()) - MVboRenderList.viewEntityX.getDouble(this));
						float y = (float) ((double) (blockpos.getY()) - MVboRenderList.viewEntityY.getDouble(this));
						float z = (float) ((double) (blockpos.getZ()) - MVboRenderList.viewEntityZ.getDouble(this));
						GlStateManager.translatef(x, y , z);
						//has to be post translate
						GlStateManager.scalef(0.5f, 0.5f ,0.5f);
						//						GlStateManager.translatef(-x * (scale), -y, -z * (scale));

						vertexbuffer.bindBuffer();
						this.setupArrayPointers();
						vertexbuffer.drawArrays(7);
						GlStateManager.popMatrix();
					}

					VertexBuffer.unbindBuffer();
					GlStateManager.clearCurrentColor();
					this.renderChunks.clear();
				}

			} catch (Exception e) {
				MemoTreetops.handleException(e);
			}
		}

		private void setupArrayPointers() {
			GlStateManager.vertexPointer(3, 5126, 28, 0);
			GlStateManager.colorPointer(4, 5121, 28, 12);
			GlStateManager.texCoordPointer(2, 5126, 28, 16);
			GLX.glClientActiveTexture(GLX.GL_TEXTURE1);
			GlStateManager.texCoordPointer(2, 5122, 28, 24);
			GLX.glClientActiveTexture(GLX.GL_TEXTURE0);
		}
	}
}
