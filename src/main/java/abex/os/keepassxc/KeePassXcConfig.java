package abex.os.keepassxc;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("keepassxc")
public interface KeePassXcConfig extends Config
{
	@ConfigItem(
		keyName = "defaultTitle",
		name = "Auto fill entry title",
		description = "The title of the Entry to automatically populate into the login screen"
	)
	default String defaultTitle()
	{
		return "";
	}

	@ConfigItem(
			keyName = "restoreState",
			name = "Collapse/close sidebar after fill",
			description = "Collapse/close the sidebar if it was collapsed/closed before the autofill menu was opened."
	)
	default boolean restoreSidebarState() { return true; }
}
