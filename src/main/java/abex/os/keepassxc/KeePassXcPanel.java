package abex.os.keepassxc;

import abex.os.keepassxc.proto.DatabaseClosedException;
import abex.os.keepassxc.proto.GetLogins;
import abex.os.keepassxc.proto.IOTimeoutException;
import abex.os.keepassxc.proto.KeePassXCSocket;
import abex.os.keepassxc.proto.NoLoginsFound;
import java.awt.BorderLayout;
import java.io.IOException;
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

@Slf4j
public class KeePassXcPanel extends PluginPanel
{
	private static final String URL = "https://secure.runescape.com/";

	private final Client client;
	private final ClientToolbar clientToolbar;

	private final NavigationButton button;

	@Inject
	public KeePassXcPanel(Client client, ClientToolbar clientToolbar)
	{
		this.client = client;
		this.clientToolbar = clientToolbar;

		this.button = NavigationButton.builder()
			.icon(ImageUtil.getResourceStreamFromClass(KeePassXcPlugin.class, "icon.png"))
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
				message = "No logins found for \"" + URL + "\"";
			}
			catch (DatabaseClosedException e)
			{
				message = "The database is locked";
			}
			catch (IOTimeoutException e)
			{
				message = "<html>Cannot connect to KeePassXC. Check that:<br>" +
					"1) KeePassXC is open, and<br>" +
					"2)<a href=\"https://github.com/abextm/keepassxc-runelite\">Browser Integration</a> is enabled<br>";
			}
			catch (IOException e)
			{
				message = e.toString();
			}
			catch (Throwable e)
			{
				log.warn("error connecting to KeePassXC", e);
				return;
			}
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
		for (GetLogins.Entry e : logins.getEntries())
		{
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
	}
}
