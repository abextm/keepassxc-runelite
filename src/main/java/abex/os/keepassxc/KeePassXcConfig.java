package abex.os.keepassxc;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("keepassxc")
public interface KeePassXcConfig extends Config
{
	@ConfigItem(
			keyName = "defaultTitle",
			name = "Default to Entry Title",
			description = "The Title of the login Entry to auto-populate",
			position = 1
	)
	default String defaultTitle()
	{
		return "";
	}
}
