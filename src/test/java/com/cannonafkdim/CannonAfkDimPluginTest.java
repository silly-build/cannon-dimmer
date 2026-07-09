package com.cannonafkdim;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class CannonAfkDimPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(CannonAfkDimPlugin.class);
		RuneLite.main(args);
	}
}