package com.meeple.memo;

public class ClientProxy implements Proxy {

	@Override
	public void preloadClasses() {
		Proxy.super.preloadClasses();
//		preloadClass("net.minecraft.client.renderer.chunk.ChunkRenderer", "ChunkRender");
		
//		preloadClass("net.minecraft.client.renderer.FluidBlockRenderer", "FluidBlockRenderer");
	}

	@Override
	public void replaceFluidRendererCauseImBored() {
		// TODO Auto-generated method stub
	}

}
