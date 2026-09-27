package dev.cptgummiball.railnet.mixin;

import dev.cptgummiball.railnet.TrainData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Logical trains have no Vanilla cart/entity pushing. Unmanaged carts keep Vanilla behavior. */
@Mixin(AbstractMinecartEntity.class)
public abstract class ManagedCartCollisionMixin {
    private boolean railnet$managed() {
        AbstractMinecartEntity self=(AbstractMinecartEntity)(Object)this;
        return self.getWorld() instanceof ServerWorld world && TrainData.get(world).byCart(self.getUuid())!=null;
    }
    @Inject(method="collidesWith",at=@At("HEAD"),cancellable=true)
    private void railnet$noCollision(Entity other,CallbackInfoReturnable<Boolean> cir) {
        if(railnet$managed())cir.setReturnValue(false);
    }
    @Inject(method="pushAwayFrom",at=@At("HEAD"),cancellable=true)
    private void railnet$noPushing(Entity other,CallbackInfo ci) {
        if(railnet$managed())ci.cancel();
    }
    @Inject(method="moveOnRail",at=@At("HEAD"),cancellable=true)
    private void railnet$managedMotion(BlockPos pos,BlockState state,CallbackInfo ci) {
        AbstractMinecartEntity self=(AbstractMinecartEntity)(Object)this;
        if(self.getWorld() instanceof ServerWorld world) {
            TrainData.Train train=TrainData.get(world).byCart(self.getUuid());
            if(train!=null && (train.status==TrainData.Status.RUNNING
                || train.status==TrainData.Status.WAITING_FOR_TRACK))ci.cancel();
        }
    }
}
