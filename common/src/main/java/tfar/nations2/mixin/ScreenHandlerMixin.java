package tfar.nations2.mixin;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tfar.nations2.sgui.virtual.inventory.VirtualInventory;

@Mixin(AbstractContainerMenu.class)
public class ScreenHandlerMixin {

    @Inject(method = "canItemQuickReplace", at = @At("HEAD"), cancellable = true)
    private static void blockIfVirtual(Slot slot, ItemStack stack, boolean allowOverflow, CallbackInfoReturnable<Boolean> cir) {
        if (slot.container instanceof VirtualInventory) {
            cir.setReturnValue(false);
        }
    }
}
