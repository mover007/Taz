package com.taz.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The sign a sign screen edits, and its lines (Auction Price Input reads them). */
@Mixin(AbstractSignEditScreen.class)
public interface AbstractSignEditScreenAccessor {
	@Accessor("sign")
	SignBlockEntity taz$sign();

	@Accessor("messages")
	String[] taz$lines();
}
