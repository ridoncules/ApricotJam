package com.apricotjam.spacepanic.desktop;

import com.apricotjam.spacepanic.SpacePanic;
import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.badlogic.gdx.tools.texturepacker.TexturePacker;

public class DesktopLauncher {
	public static void main(String[] arg) {
		//TexturePacker.process("../../images", "atlas", "art");

		LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
		config.title = "Untethered";
		config.width = SpacePanic.WIDTH;
		config.height = SpacePanic.HEIGHT;
		new LwjglApplication(new SpacePanic(), config);
	}
}
