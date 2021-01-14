package abex.os.keepassxc;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
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
