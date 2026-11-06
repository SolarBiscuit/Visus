package dev.visus;

import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for the "visus" mod id.
 *
 * <p>Visus ships two mod entries in one jar (see {@code META-INF/mods.toml}):
 * <ul>
 *   <li>{@code visus}: this class; carries the real Visus version.</li>
 *   <li>{@code oculus}: {@link net.irisshaders.iris.Iris}, a compatibility alias on the 1.8.x line with
 *       {@code provides=["iris"]}, so mods that check for Oculus or depend on {@code oculus [1.8.0,)} keep working.</li>
 * </ul>
 * All rendering is still driven from the Iris/Oculus code base; this class only identifies Visus.
 */
@Mod(Visus.MODID)
public final class Visus {
	public static final String MODID = "visus";
	public static final Logger LOGGER = LoggerFactory.getLogger("Visus");

	public Visus() {
		// Client-only mod: never ask the server for it.
		ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
			() -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (remote, network) -> true));
		LOGGER.info("Visus {} loaded (Oculus-compatible alias {})", version(MODID), version("oculus"));
	}

	private static String version(String modId) {
		return net.minecraftforge.fml.ModList.get().getModContainerById(modId)
			.map(c -> c.getModInfo().getVersion().toString()).orElse("?");
	}
}
