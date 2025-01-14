package mcjty.lib.setup;

import mcjty.lib.McJtyLib;
import mcjty.lib.api.container.CapabilityContainerProvider;
import mcjty.lib.api.information.CapabilityPowerInformation;
import mcjty.lib.api.infusable.CapabilityInfusable;
import mcjty.lib.api.module.CapabilityModuleSupport;
import mcjty.lib.network.PacketHandler;
import mcjty.lib.preferences.PreferencesDispatcher;
import mcjty.lib.preferences.PreferencesProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkRegistry;

import static mcjty.lib.McJtyLib.MODID;

public class ModSetup extends DefaultModSetup {

    public static final ResourceLocation PREFERENCES_CAPABILITY_KEY = new ResourceLocation(MODID, "preferences");

    public static boolean patchouli = false;

    public static Capability<PreferencesProperties> PREFERENCES_CAPABILITY = CapabilityManager.get(new CapabilityToken<>(){});

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        CapabilityContainerProvider.register(event);
        CapabilityInfusable.register(event);
        CapabilityPowerInformation.register(event);
        CapabilityModuleSupport.register(event);
        PreferencesProperties.register(event);
    }

    @Override
    public void init(FMLCommonSetupEvent e) {
        super.init(e);
        McJtyLib.networkHandler = NetworkRegistry.newSimpleChannel(new ResourceLocation(MODID, MODID), () -> "1.0", s -> true, s -> true);
        PacketHandler.registerMessages(McJtyLib.networkHandler);
        MinecraftForge.EVENT_BUS.register(new EventHandler());
    }

    @Override
    protected void setupModCompat() {
        patchouli = ModList.get().isLoaded("patchouli");
    }

    public static class EventHandler {

        @SubscribeEvent
        public void onWorldTick(TickEvent.LevelTickEvent event) {
            if (event.phase == TickEvent.Phase.START && event.level.dimension() == Level.OVERWORLD) {
                McJtyLib.SYNCER.sendOutData(event.level.getServer());
            }
        }

        @SubscribeEvent
        public void onChunkWatch(ChunkWatchEvent.Watch event) {
            McJtyLib.SYNCER.startWatching(event.getPlayer());
        }

        @SubscribeEvent
        public void onPlayerTickEvent(TickEvent.PlayerTickEvent event) {
            if (event.phase == TickEvent.Phase.START && !event.player.getCommandSenderWorld().isClientSide) {
                McJtyLib.getPreferencesProperties(event.player).ifPresent(handler -> handler.tick((ServerPlayer) event.player));
            }
        }

        @SubscribeEvent
        public void onEntityConstructing(AttachCapabilitiesEvent<Entity> event){
            if (event.getObject() instanceof Player) {
                if (!event.getCapabilities().containsKey(PREFERENCES_CAPABILITY_KEY) && !event.getObject().getCapability(PREFERENCES_CAPABILITY).isPresent()) {
                    event.addCapability(PREFERENCES_CAPABILITY_KEY, new PreferencesDispatcher());
                } else {
                    throw new IllegalStateException(event.getObject().toString());
                }
            }
        }

        // @todo multipart
//        @SubscribeEvent
//        public void onPlayerInteract(PlayerInteractEvent.LeftClickBlock event) {
//            Level world = event.getLevel();
//            BlockPos pos = event.getPos();
//            BlockState state = world.getBlockState(pos);
//            if (state.getBlock() instanceof MultipartBlock) {
//                BlockEntity tileEntity = world.getBlockEntity(pos);
//                if (tileEntity instanceof MultipartTE) {
//                    if (!world.isClientSide) {
//
//                        // @todo 1.14 until LeftClickBlock has 'hitVec' again we need to do this:
//                        Player player = event.getEntity();
//                        Vec3 start = player.getEyePosition(1.0f);
//                        Vec3 vec31 = player.getViewVector(1.0f);
//                        float dist = 20;
//                        Vec3 end = start.add(vec31.x * dist, vec31.y * dist, vec31.z * dist);
//                        ClipContext context = new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);
//                        HitResult result = player.getCommandSenderWorld().clip(context);
//                        Vec3 hitVec = result.getLocation();
//
//                        if (MultipartHelper.removePart((MultipartTE) tileEntity, state, player, hitVec/*@todo*/)) {
//                            world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
//                        }
//                    }
//                }
//                event.setCanceled(true);
//            }
//        }
//
    }

}
