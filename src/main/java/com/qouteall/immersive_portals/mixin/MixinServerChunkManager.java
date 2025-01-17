package com.qouteall.immersive_portals.mixin;

import com.qouteall.immersive_portals.exposer.IEServerChunkManager;
import net.minecraft.server.world.ChunkTicketManager;
import net.minecraft.server.world.ServerChunkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerChunkManager.class)
public abstract class MixinServerChunkManager implements IEServerChunkManager {
    
    @Override
    @Accessor("ticketManager")
    public abstract ChunkTicketManager getTicketManager();

//    @Shadow
//    @Final
//    private ChunkTicketManager ticketManager;
//
//    @Override
//    public ChunkTicketManager getTicketManager() {
//        return ticketManager;
//    }
}
