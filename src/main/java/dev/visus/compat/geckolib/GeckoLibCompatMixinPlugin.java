package dev.visus.compat.geckolib;

import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Applies the GeckoLib mixins only to GeckoLib 4.x, whose {@code GeoModel.handleAnimations} has the
 * {@code lastRenderedInstance} early return they adjust (architecture §4.7: version guarded).
 */
public class GeckoLibCompatMixinPlugin implements IMixinConfigPlugin {
	private static final Logger LOGGER = LogManager.getLogger("Visus");
	private Boolean apply;

	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (apply == null) {
			apply = decide();
		}
		return apply;
	}

	private static boolean decide() {
		ModFileInfo file = LoadingModList.get().getModFileById("geckolib");
		if (file == null) {
			return false;
		}
		for (IModInfo mod : file.getMods()) {
			if ("geckolib".equals(mod.getModId())) {
				int major = mod.getVersion().getMajorVersion();
				if (major == 4) {
					LOGGER.info("GeckoLib {}: animations are posed in the shadow and the main pass (V-09)", mod.getVersion());
					return true;
				}
				LOGGER.info("GeckoLib {} is not 4.x; the shadow-pass animation fix is not applied", mod.getVersion());
				return false;
			}
		}
		return false;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}
}
