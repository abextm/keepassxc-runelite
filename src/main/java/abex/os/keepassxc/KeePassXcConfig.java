package abex.os.keepassxc;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("keepassxc")
public interface KeePassXcConfig extends Config
{
	@ConfigItem(
			keyName = "autoPopulate",
			name = "Auto Populate Login",
			description = "When enabled the KeePassXC Entry matching Title will be auto populated",
			position = 1
	)
	default boolean autoPopulate()
	{
		return false;
	}
	@ConfigItem(
			keyName = "defaultTitle",
			name = "Title to Auto Populate",
			description = "The Title of the login Entry to auto populate",
			position = 2
	)
	default String defaultTitle()
	{
		return "";
	}
}
