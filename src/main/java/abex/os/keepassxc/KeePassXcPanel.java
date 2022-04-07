package abex.os.keepassxc;

import abex.os.keepassxc.proto.DatabaseClosedException;
import abex.os.keepassxc.proto.msg.GetLogins;
import abex.os.keepassxc.proto.IOTimeoutException;
import abex.os.keepassxc.proto.KeePassXCSocket;
import abex.os.keepassxc.proto.NoLoginsFound;
import java.awt.BorderLayout;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.inject.Inject;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;
import net.runelite.client.ui.ClientUI;

@Slf4j
public class KeePassXcPanel extends PluginPanel
{
	private static final String URL = "https://secure.runescape.com/";

	private final Client client;
	private final ClientToolbar clientToolbar;
	private final ClientUI clientUI;

	private final NavigationButton button;

	@Inject
	private KeePassXcConfig config;

	private int sidebarWidth = -1;

	@Inject
	public KeePassXcPanel(Client client, ClientToolbar clientToolbar, ClientUI clientUI)
	{
		this.client = client;
		this.clientToolbar = clientToolbar;
		this.clientUI = clientUI;

		this.button = NavigationButton.builder()
			.icon(ImageUtil.loadImageResource(KeePassXcPlugin.class, "icon.png"))
			.panel(this)
			.priority(999)
			.tooltip("KeePassXC")
			.build();

		setBorder(new EmptyBorder(5, 5, 5, 5));
	}

	public void load()
	{
		Thread t = new Thread(() ->
		{
			String message;
			Throwable ex;
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
				SwingUtilities.invokeLater(() -> this.open(r));
				return;
			}
			catch (NoLoginsFound e)
			{
				ex = e;
				message = "No logins found for \"" + URL + "\"";
			}
			catch (DatabaseClosedException e)
			{
				ex = e;
				message = "The database is locked";
			}
			catch (IOTimeoutException e)
			{
				ex = e;
				message = "<html>Cannot connect to KeePassXC. Check that:<br>" +
					"1) KeePassXC is open, and<br>" +
					"2)<a href=\"https://github.com/abextm/keepassxc-runelite\">Browser Integration</a> is enabled<br>";
			}
			catch (IOException e)
			{
				ex = e;
				message = e.toString();
			}
			catch (Throwable e)
			{
				log.warn("error connecting to KeePassXC", e);
				return;
			}
			log.warn("error connecting to KeePassXC", ex);
			SwingUtilities.invokeLater(() -> this.open(message));
		}, "KeePassXCSocketConnection");

		t.setDaemon(true);
		t.start();
	}

	public void open(GetLogins.Response logins)
	{
		SwingUtil.fastRemoveAll(this);
		setLayout(new DynamicGridLayout(0, 1, 0, 0));
		boolean hideUsernames = client.getPreferences().getHideUsername();

		String defaultTitle = config.defaultTitle().trim();

		for (GetLogins.Entry e : logins.getEntries())
		{
			if (!defaultTitle.isEmpty() && e.getName().trim().equalsIgnoreCase(defaultTitle))
			{
				client.setPassword(e.getPassword());
				client.setUsername(e.getLogin());
			}

			String name = hideUsernames ? e.getName() : e.getLogin();
			JButton b = new JButton(name);
			b.addActionListener(_e ->
			{
				client.setPassword(e.getPassword());
				client.setUsername(e.getLogin());
			});
			add(b);
		}
		open();
	}

	public void open(String error)
	{
		SwingUtil.fastRemoveAll(this);
		setLayout(new BorderLayout());

		JLabel titleLabel = new JLabel("KeePassXC");
		titleLabel.setBorder(new EmptyBorder(5, 5, 5, 5));
		titleLabel.setFont(FontManager.getRunescapeBoldFont());
		add(titleLabel, BorderLayout.NORTH);

		JLabel errorLabel = new JLabel(error);
		errorLabel.setBorder(new EmptyBorder(5, 5, 5, 5));
		add(errorLabel, BorderLayout.CENTER);

		JButton button = new JButton("Retry");
		add(button, BorderLayout.SOUTH);
		button.addActionListener(e -> load());

		open();
	}

	private void open()
	{
		revalidate();
		sidebarWidth = clientUI.getWidth() - client.getCanvasWidth();
		clientToolbar.addNavigation(button);
		if (!button.isSelected())
		{
			SwingUtilities.invokeLater(() -> button.getOnSelect().run());
		}
	}

	public void close()
	{
		// clientui doesn't unset selected if we close the panel by removing the navbutton
		button.setSelected(false);
		clientToolbar.removeNavigation(button);

		if (config.restoreSidebarState())
		{
			try
			{
				Method m;
				switch (sidebarWidth)
				{
					case 0: // sidebar was closed
					m = clientUI.getClass().getDeclaredMethod("toggleSidebar");
					m.setAccessible(true);
					m.invoke(clientUI);
					break;

					case 36: // sidebar was contracted
					m = clientUI.getClass().getDeclaredMethod("contract");
					m.setAccessible(true);
					m.invoke(clientUI);
					break;
				}
			}
			catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e)
			{
				log.warn("Couldn't modify the sidebar state");
			}
		}
	}
}
