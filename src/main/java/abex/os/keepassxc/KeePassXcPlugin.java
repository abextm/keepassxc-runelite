package abex.os.keepassxc;

import javax.inject.Inject;
import javax.swing.SwingUtilities;
import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@PluginDescriptor(
	name = "KeePassXC"
)
public class KeePassXcPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private KeePassXcConfig config;

	private KeePassXcPanel panel;

	@Override
	public void startUp()
	{
		panel = injector.getInstance(KeePassXcPanel.class);

		lastLoginState = -1;
		updatePanel(client.getGameState());
	}

	@Override
	public void shutDown()
	{
		panel.close();
		isTicking = false;
	}

	@Provides
	KeePassXcConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(KeePassXcConfig.class);
	}

	private int lastLoginState = -1;
	private boolean isTicking = false;

	@Subscribe
	public void onGameStateChanged(GameStateChanged ev)
	{
		updatePanel(ev.getGameState());
	}

	private void updatePanel(GameState gs)
	{
		SwingUtilities.invokeLater(() ->
		{
			switch (gs)
			{
				case LOGIN_SCREEN:
				case LOGIN_SCREEN_AUTHENTICATOR:
					if (!isTicking)
					{
						isTicking = true;
						clientThread.invokeLater(() ->
						{
							if (!isTicking)
							{
								SwingUtilities.invokeLater(() -> onLoginStateChanged(-1));
								return true;
							}

							int loginState = client.getLoginIndex();
							if (loginState != lastLoginState)
							{
								lastLoginState = loginState;
								SwingUtilities.invokeLater(() -> onLoginStateChanged(loginState));
							}
							return false;
						});
					}
					break;
				default:
					isTicking = false;
					panel.close();
					break;
			}
		});
	}

	private void onLoginStateChanged(int loginState)
	{
		if (loginState == 2)
		{
			panel.load();
		}
		else
		{
			panel.close();
		}
	}
}
