package com.meeple.memo;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.model.EntityModel;
import net.minecraft.client.renderer.model.BakedQuad;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntitySize;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.Pose;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.InputEvent.KeyInputEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.event.lifecycle.GatherDataEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(MemoTreetops.modid)
public class MemoTreetops {
	public static final String modid = "memo";

	// Directly reference a log4j logger.
	public static final Logger LOGGER = LogManager.getLogger();
	public static float scale = 2f;
	public static final Proxy PROXY = DistExecutor.runForDist(() -> () -> new ClientProxy(), () -> () -> new ServerProxy());

	public static <T> T handleException(Throwable t) {
		LOGGER.catching(Level.FATAL, t);
		System.exit(0);
		return (T) null;
	}

	public MemoTreetops() {

		try {

			//			new ASMTest(Runnable.class);
			//			new ASMHelloWorld(LivingRenderer.class);

		} catch (Exception err) {

			err.printStackTrace();
		}
//		BakedQuad
		//		net.minecraft.client.gui.screen.inventory.InventoryScreen.drawGuiContainerBackgroundLayer
		PROXY.preloadClasses();
		IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
		modEventBus.addListener(this::setup);
		modEventBus.addListener(this::enqueueIMC);
		modEventBus.addListener(this::processIMC);
		modEventBus.addListener(this::doClientStuff);
		modEventBus.addListener(this::gatherData);
		net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(this::onGetEyeHeight);
		//		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::loadComplete);

		// Register ourselves for server and other game events we are interested in
		MinecraftForge.EVENT_BUS.register(this);

		modEventBus.addListener((FMLLoadCompleteEvent event) -> PROXY.replaceFluidRendererCauseImBored());
		/*	modEventBus.addListener((ModConfig.ModConfigEvent event) -> {
				final ModConfig config = event.getConfig();
				if (config.getSpec() == ConfigHolder.CLIENT_SPEC) {
					ConfigHelper.bakeClient(config);
				} else if (config.getSpec() == ConfigHolder.SERVER_SPEC) {
					ConfigHelper.bakeServer(config);
				}
			});*/

		final ModLoadingContext modLoadingContext = ModLoadingContext.get();
		//		modLoadingContext.registerConfig(ModConfig.Type.CLIENT, ConfigHolder.CLIENT_SPEC);
		//		modLoadingContext.registerConfig(ModConfig.Type.SERVER, ConfigHolder.SERVER_SPEC);

	}

	private void setup(final FMLCommonSetupEvent event) {
		float ratio = (scale / 2);
		{
			//---------------//remap player pose sizes//---------------//
			Map<Pose, EntitySize> poses = Utils.getPrivateValue(PlayerEntity.class, null, "SIZE_BY_POSE");
			System.out.println(">>" + poses);
			if (poses != null) {
				if (poses instanceof Map) {
					Map<Pose, EntitySize> writeTo = new HashMap<>(poses.size());

					Set<Entry<Pose, EntitySize>> set = poses.entrySet();

					for (Iterator<Entry<Pose, EntitySize>> iterator = set.iterator(); iterator.hasNext();) {
						Entry<Pose, EntitySize> entry = iterator.next();
						Pose pose = entry.getKey();
						EntitySize entitySize = entry.getValue();
						System.out.println(">>" + entitySize);
						writeTo.put(pose, EntitySize.flexible(entitySize.width + ratio, entitySize.height + ratio));

					}

					Utils.setPrivateValue(PlayerEntity.class, null, (Map<Pose, EntitySize>) writeTo, "SIZE_BY_POSE");

				}
			}
		}
		{
			//---------------//change the default size//---------------//
			PlayerEntity.STANDING_SIZE.scale(scale);
		}
		{
			//---------------//set default reach distance to scale * normal//---------------//
			IAttribute REACH_DISTANCE =
				new net.minecraft.entity.ai.attributes.RangedAttribute(null, "generic.reachDistance", PlayerEntity.REACH_DISTANCE.getDefaultValue() * MemoTreetops.scale, 0.0D, 1024.0D)
					.setShouldWatch(true);

			Utils.setPrivateValue(PlayerEntity.class, null, REACH_DISTANCE, "REACH_DISTANCE");
		}
		{
			IAttribute STEP_HEIGHT;
		}

		{
			LOGGER.debug("Change all registered entity sizes to '" + scale + "' times bigger");
			for (EntityType<?> entity : net.minecraftforge.registries.ForgeRegistries.ENTITIES) {
				EntitySize size = entity.getSize();
				EntitySize newSize = new EntitySize(size.width + ratio, size.height + ratio, false);
				Utils.setPrivateValue(EntityType.class, entity, newSize, "size");

			}
		}

		{

			for (TileEntityType<?> tileEntity : net.minecraftforge.registries.ForgeRegistries.TILE_ENTITIES) {

			}
		}
		{

			for (Block b : net.minecraftforge.registries.ForgeRegistries.BLOCKS) {

			}
		}

		{
			//---------------//replace game renderer//---------------//
			Minecraft mc = Minecraft.getInstance();

			mc.deferTask(new Runnable() {

				@Override
				public void run() {
					System.out.println("Replacing minecraft renderer");
					mc.gameRenderer = new MGameRenderer(mc);
					mc.worldRenderer = new MWorldRenderer(mc);

				}
			});

			//			net.minecraftforge.fml.ModList

		}

		keybinds.put(new KeyBinding("key." + modid + "scale.decr", 58, "key." + modid + ".scale"), new Consumer<InputEvent.KeyInputEvent>() {

			@Override
			public void accept(KeyInputEvent t) {
				scale -= 1;
			}
		});
		keybinds.put(new KeyBinding("key." + modid + "scale.incr", 59, "key." + modid + ".scale"), new Consumer<InputEvent.KeyInputEvent>() {

			@Override
			public void accept(KeyInputEvent t) {
				scale += 1;
			}
		});
		Set<KeyBinding> keyBindings = keybinds.keySet();
		synchronized (keyBindings) {
			for (Iterator<KeyBinding> iterator = keyBindings.iterator(); iterator.hasNext();) {
				KeyBinding keyBinding = iterator.next();
				ClientRegistry.registerKeyBinding(keyBinding);
			}
		}

	}

	static Map<KeyBinding, Consumer<KeyInputEvent>> keybinds = new HashMap<>();

	public void onGetEyeHeight(EntityEvent.EyeHeight event) {
		EntitySize size = event.getSize();

		//0.85f because thats what entity.getEyeHeight(Pose,EntitySize) uses
		event.setNewHeight(size.height * 0.85f);
		//		System.out.println("\r\n" + event.getOldHeight() + "/" + (size.height / MemoTreetops.scale) + "\r\n" + event.getNewHeight() + "/" + size.height);
	}

	private void doClientStuff(final FMLClientSetupEvent event) {
		// do something that can only be done on the client
		LOGGER.info("Got game settings {}", event.getMinecraftSupplier().get().gameSettings);

	}

	private void enqueueIMC(final InterModEnqueueEvent event) {
		// some example code to dispatch IMC to another mod
		InterModComms.sendTo(modid, "helloworld", () -> {
			LOGGER.info("Hello world from the MDK");
			return "Hello world";
		});
	}

	private void processIMC(final InterModProcessEvent event) {

		// some example code to receive and process InterModComms from other mods
		LOGGER.info("Got IMC {}", event.getIMCStream().map(m -> m.getMessageSupplier().get()).collect(Collectors.toList()));

	}

	private void gatherData(final GatherDataEvent event) {

	}

	// You can use SubscribeEvent and let the Event Bus discover methods to call
	@SubscribeEvent
	public void onServerStarting(FMLServerStartingEvent event) {
		// do something when the server starts
		LOGGER.info("HELLO from server starting");
	}

	// You can use EventBusSubscriber to automatically subscribe events on the contained class (this is subscribing to the MOD
	// Event bus for receiving Registry Events)
	@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
	public static class RegistryEvents {
		@SubscribeEvent
		public static void onBlocksRegistry(final RegistryEvent.Register<Block> blockRegistryEvent) {
			// register a new block here
			LOGGER.info("HELLO from Register Block");
		}
	}

	@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
	public static class KeyInputEvents {

		@SubscribeEvent
		public static void onKeyEvent(KeyInputEvent event) {
			Map<KeyBinding, Consumer<KeyInputEvent>> keyBindings = keybinds;
			Set<Entry<KeyBinding, Consumer<KeyInputEvent>>> set = keyBindings.entrySet();
			System.out.println(event);
			synchronized (keyBindings) {
				for (Iterator<Entry<KeyBinding, Consumer<KeyInputEvent>>> iterator = set.iterator(); iterator.hasNext();) {
					Entry<KeyBinding, Consumer<KeyInputEvent>> entry = iterator.next();
					KeyBinding keyBinding = entry.getKey();
					Consumer<KeyInputEvent> consumer = entry.getValue();
					if (keyBinding.isPressed()) {
						consumer.accept(event);
					}
				}
			}

		}

	}
}
