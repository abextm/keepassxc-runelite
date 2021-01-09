package abex.os.keepassxc;

import abex.os.keepassxc.proto.NoLoginsFound;
import abex.os.keepassxc.proto.DatabaseClosedException;
import abex.os.keepassxc.proto.GetLogins;
import abex.os.keepassxc.proto.IOTimeoutException;
import abex.os.keepassxc.proto.KeePassXCSocket;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import java.applet.Applet;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.DynamicGridLayout;

@Slf4j
@PluginDescriptor(
	name = "KeePassXC"
)
public class KeePassXcPlugin extends Plugin
{
	private static final String URL = "https://secure.runescape.com/";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private Applet applet;

	@Inject
	private ScheduledExecutorService executor;

	private JScrollPane overlay;

	private final ComponentListener anyChanges = new ComponentListener()
	{
		@Override
		public void componentResized(ComponentEvent e)
		{
			relayoutOverlay();
		}

		@Override
		public void componentMoved(ComponentEvent e)
		{
			relayoutOverlay();
		}

		@Override
		public void componentShown(ComponentEvent e)
		{
			relayoutOverlay();
		}

		@Override
		public void componentHidden(ComponentEvent e)
		{
			relayoutOverlay();
		}
	};

	@Override
	public void startUp()
	{
		overlay = new JScrollPane();

		JLayeredPane lay = SwingUtilities.getRootPane(applet).getLayeredPane();
		lay.add(overlay, JLayeredPane.MODAL_LAYER);

		applet.addComponentListener(anyChanges);

		relayoutOverlay();
		lastLoginState = -1;
		updatePanel(client.getGameState());
	}

	private void relayoutOverlay()
	{
		int y = /* soul level */ 387;
		Dimension size = overlay.getPreferredSize();
		int w = size.width;

		Dimension asize = applet.getSize();
		int x = (asize.width - w) / 2;
		int h = asize.height - y;
		if (size.height < h)
		{
			h = size.height;
		}

		overlay.validate();
		overlay.getParent().validate();
		overlay.setBounds(new Rectangle(
			SwingUtilities.convertPoint(applet, x, y, overlay.getParent()),
			new Dimension(w, h)));
	}

	@Override
	public void shutDown()
	{
		overlay.getParent().remove(overlay);
		isTicking = false;
	}

	private void loadUsername(JPanel container)
	{
		container.removeAll();
		Thread t = new Thread(() ->
		{
			String message;
			try (KeePassXCSocket sock = new KeePassXCSocket())
			{
				sock.setDeadline(500);
				sock.init();
				sock.clearDeadline();
				GetLogins.Response r = sock.call(GetLogins.ACTION, GetLogins.Request.builder()
					.action(GetLogins.ACTION)
					.url("https://secure.runescape.com/")
					.keys(sock.getKeys())
					.build(), GetLogins.Response.class);
				SwingUtilities.invokeLater(() ->
				{
					container.setLayout(new DynamicGridLayout(0, 1, 0, 0));
					boolean hideUsernames = getHideUsernames();
					for (GetLogins.Entry e : r.getEntries())
					{
						String name = hideUsernames ? e.getName() : e.getLogin();
						JButton b = new JButton(name);
						b.addActionListener(_e ->
						{
							client.setPassword(e.getPassword());
							client.setUsername(e.getLogin());
						});
						container.add(b);
					}
					container.validate();
					relayoutOverlay();
				});
				return;
			}
			catch (NoLoginsFound e)
			{
				message = "No logins found for \"" + URL + "\"";
			}
			catch (DatabaseClosedException e)
			{
				message = "The database is locked";
			}
			catch (IOTimeoutException e)
			{
				message = "Cannot connect to KeePassXC";
			}
			catch (IOException e)
			{
				message = e.toString();
			}
			SwingUtilities.invokeLater(() ->
			{
				container.setLayout(new BorderLayout());
				container.setBorder(new EmptyBorder(5, 5, 5, 5));
				container.add(new JLabel(message), BorderLayout.CENTER);
				JButton button = new JButton("Retry");
				container.add(button, BorderLayout.SOUTH);
				button.addActionListener(e -> loadUsername(container));

				container.validate();
				relayoutOverlay();
			});
		});

		t.setDaemon(true);
		t.start();
	}

	private boolean getHideUsernames()
	{
		//TODO: add an api for this
		return false;
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
					overlay.setViewportView(null);
					overlay.setVisible(false);
					relayoutOverlay();
					break;
			}
		});
	}

	private void onLoginStateChanged(int loginState)
	{
		overlay.setViewportView(null);
		switch (loginState)
		{
			case 2: // username + password
				JPanel container = new JPanel();
				overlay.setViewportView(container);
				loadUsername(container);
				overlay.setVisible(true);
				relayoutOverlay();
				break;
			default:
				overlay.setVisible(false);
				break;
		}
	}
}
