package dev.visus.flywheel;

import dev.visus.flywheel.backend.IrisFlwBackends;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.ModList;

/**
 * Starts Visus's Flywheel backend (absorbed from irisflw's Forge entry point). Called from {@code dev.visus.Visus}
 * only when Flywheel is installed; nothing else outside this package references Flywheel.
 */
public final class FlywheelSetup {
	private FlywheelSetup() {
	}

	public static void init() {
		IrisFlw.init();
		MinecraftForge.EVENT_BUS.addListener(FlywheelSetup::warnScalableLux);
	}

	/** irisflw's check: ScalableLux replaces the light engine in a way this backend does not support. */
	private static void warnScalableLux(PlayerEvent.PlayerLoggedInEvent event) {
		if (ModList.get().isLoaded("scalablelux") && event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
			player.sendSystemMessage(Component.literal(ChatFormatting.DARK_RED + "Visus's Flywheel backend is not compatible with ScalableLux!"));
		}
	}
}
