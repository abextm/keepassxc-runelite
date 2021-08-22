package abex.os.keepassxc;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("keepassxc")
public interface KeePassXcConfig extends Config
{
	@ConfigItem(
			keyName = "defaultFirstEntry",
			name = "Default to First Entry",
			description = "Use the first login entry found in the database",
			position = 1
	)
	default boolean defaultFirstEntry()
	{
		return false;
	}
}
