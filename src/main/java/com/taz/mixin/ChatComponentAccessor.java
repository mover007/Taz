package com.taz.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/** The chat's messages, wrapped lines and scroll, for Ctrl + click to copy and Stack Duplicates. */
@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
	@Accessor("trimmedMessages")
	List<GuiMessage.Line> taz$lines();

	@Accessor("chatScrollbarPos")
	int taz$scroll();

	/** Every message, newest first. */
	@Accessor("allMessages")
	List<GuiMessage> taz$messages();

	/** Wraps every message into lines again, after one is taken out. */
	@Invoker("refreshTrimmedMessages")
	void taz$refresh();
}
