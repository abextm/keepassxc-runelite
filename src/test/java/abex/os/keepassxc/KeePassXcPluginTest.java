package abex.os.keepassxc;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class KeePassXcPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(KeePassXcPlugin.class);
		RuneLite.main(args);
	}
}