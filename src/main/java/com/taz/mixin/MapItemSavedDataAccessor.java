package com.taz.mixin;

import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** A map's markers by key ("icon-0"...), for the dungeon map's player heads. */
@Mixin(MapItemSavedData.class)
public interface MapItemSavedDataAccessor {
	@Accessor("decorations")
	Map<String, MapDecoration> taz$decorations();
}
