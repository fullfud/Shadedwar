package dev.lazurite.lattice.impl.mixin.core.player;

import dev.lazurite.lattice.api.player.LatticePlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Player.class)
public abstract class PlayerMixin implements LatticePlayer {

    @Override
    @SuppressWarnings("ObjectEquality")
    public boolean isViewPointSelf() {
        return this.getViewPoint() == this;
    }

}
