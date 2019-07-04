package com.meeple.memo;

import javax.annotation.Nonnull;

import net.minecraft.crash.CrashReport;
import net.minecraft.crash.ReportedException;

public interface Proxy {
	

	default void preloadClasses() {
		preloadClass("net.minecraft.block.BlockState", "BlockState");
		preloadClass("net.minecraft.world.IWorldReader", "IWorldReader");
		preloadClass("net.minecraft.world.World", "World");
	}

	default void preloadClass(@Nonnull final String qualifiedName, @Nonnull final String simpleName) {
		try {
			MemoTreetops.LOGGER.info("Loading class \"" + simpleName + "\"...");
			final ClassLoader classLoader = this.getClass().getClassLoader();
			final long startTime = System.nanoTime();
			Class.forName(qualifiedName, false, classLoader);
			MemoTreetops.LOGGER.info("Loaded class \"" + simpleName + "\" in " + (System.nanoTime() - startTime) + " nano seconds");
			MemoTreetops.LOGGER.info("Initialising class \"" + simpleName + "\"...");
			Class.forName(qualifiedName, true, classLoader);
			MemoTreetops.LOGGER.info("Initialised \"" + simpleName + "\"");
		} catch (final ClassNotFoundException e) {
			final CrashReport crashReport = new CrashReport("Failed to load class \"" + simpleName + "\". This should not be possible!", e);
			crashReport.makeCategory("Loading class");
			throw new ReportedException(crashReport);
		}
	}


	void replaceFluidRendererCauseImBored();

}
