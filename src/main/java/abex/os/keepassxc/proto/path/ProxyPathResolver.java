package abex.os.keepassxc.proto.path;

import com.google.common.io.CharStreams;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.OSType;
import net.runelite.http.api.RuneLiteAPI;

@Slf4j
public abstract class ProxyPathResolver
{
	private static final Pattern ENTRY_REGEX = Pattern.compile(" {4}\\(Default\\) {4}REG_SZ {4}(.*?)\\r?\\n", Pattern.MULTILINE);

	private static String proxyPath;

	public static String getKeepassProxyPath()
	{
		if (proxyPath == null)
		{
			setProxyPath();
		}

		if (proxyPath == null)
		{
			log.warn("Could not locate keepass-proxy");

			// hope its on the path somewhere
			proxyPath = "keepassxc-proxy";
		}

		return proxyPath;
	}

	private static boolean setProxyPath()
	{
		// https://github.com/keepassxreboot/keepassxc/blob/develop/src/browser/NativeMessageInstaller.cpp#L36
		switch (OSType.getOSType())
		{
			case MacOS:
				return testOSXBrowser("Google/Chrome")
					|| testOSXBrowser("Mozilla")
					|| testOSXBrowser("Chromium")
					|| testOSXBrowser("Vivaldi")
					|| testOSXBrowser("BraveSoftware/Brave-Browser")
					|| testOSXBrowser("Microsoft Edge");
			case Linux:
			{
				File home = new File(System.getProperty("user.home"));
				File config = new File(home, ".config");
				File config2 = new File(home, "/etc/xdg");
				return testManifest(new File(config, "/google-chrome/NativeMessagingHosts"))
					|| testManifest(new File(config2, "/google-chrome/NativeMessagingHosts"))
					|| testManifest(new File(home, "/.mozilla/native-messaging-hosts"))
					|| testManifest(new File(config, "/chromium/NativeMessagingHosts"))
					|| testManifest(new File(config2, "/chromium/NativeMessagingHosts"))
					|| testManifest(new File(config, "/vivaldi/NativeMessagingHosts"))
					|| testManifest(new File(config2, "/vivaldi/NativeMessagingHosts"))
					|| testManifest(new File(config, "/BraveSoftware/Brave-Browser/NativeMessagingHosts"))
					|| testManifest(new File(config2, "/BraveSoftware/Brave-Browser/NativeMessagingHosts"))
					|| testManifest(new File(config, "/microsoftedge/NativeMessagingHosts"))
					|| testManifest(new File(config2, "/microsoftedge/NativeMessagingHosts"));
			}
			case Windows:
				return testWindowsBrowser("Google\\Chrome")
					|| testWindowsBrowser("Chromium")
					|| testWindowsBrowser("Mozilla")
					|| testWindowsBrowser("Microsoft\\Edge");
		}
		return false;
	}

	private static boolean testPath(File file)
	{
		if (file == null)
		{
			return false;
		}

		if (!file.exists())
		{
			log.info("proxy is not at \"{}\"", file);
			return false;
		}

		proxyPath = file.getAbsolutePath();
		return true;
	}

	private static boolean testOSXBrowser(String name)
	{
		return testManifest(new File(System.getProperty("user.home"), "/Library/Application Support/" + name + "/NativeMessagingHosts"));
	}

	private static boolean testManifest(File nativeHostsPath)
	{
		File manifestFile = new File(nativeHostsPath, "org.keepassxc.keepassxc_browser.json");
		if (!manifestFile.exists())
		{
			return false;
		}

		try
		{
			// parse json to NMManifest, which should contain proxy path
			String manifestJson = new String(Files.readAllBytes(manifestFile.toPath()), StandardCharsets.UTF_8);
			NativeMessagingManifest manifest = RuneLiteAPI.GSON.fromJson(manifestJson, NativeMessagingManifest.class);
			return testPath(new File(manifest.getPath()));
		}
		catch (Exception e)
		{
			log.debug("Failed to read manifest file", e);
			return false;
		}
	}


	private static boolean testWindowsBrowser(String browser)
	{
		String regKey = "HKEY_CURRENT_USER\\Software\\" + browser + "\\NativeMessagingHosts\\org.keepassxc.keepassxc_browser";

		try
		{
			Process regProc = new ProcessBuilder("reg", "query", regKey)
				.redirectOutput(ProcessBuilder.Redirect.PIPE)
				.start();

			String regOutput;
			try (InputStreamReader isr = new InputStreamReader(regProc.getInputStream()))
			{
				regOutput = CharStreams.toString(isr);
			}

			// extract manifest path output
			Matcher m = ENTRY_REGEX.matcher(regOutput);
			if (m.find())
			{
				return testPath(new File(m.group(1)));
			}
			return false;
		}
		catch (IOException e)
		{
			log.warn("Failed to read registry key {}: {}", regKey, e);
			return false;
		}
	}
}