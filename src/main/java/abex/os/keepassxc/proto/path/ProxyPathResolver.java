package abex.os.keepassxc.proto.path;

import com.google.common.base.Strings;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.Win32Exception;
import com.sun.jna.platform.win32.WinReg;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.OSType;
import net.runelite.http.api.RuneLiteAPI;

@Slf4j
public class ProxyPathResolver
{
	private static String proxyPath;

	private ProxyPathResolver()
	{
	}

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
				return testManifestDir(new File(config, "/google-chrome/NativeMessagingHosts"))
					|| testManifestDir(new File(config2, "/google-chrome/NativeMessagingHosts"))
					|| testManifestDir(new File(home, "/.mozilla/native-messaging-hosts"))
					|| testManifestDir(new File(config, "/chromium/NativeMessagingHosts"))
					|| testManifestDir(new File(config2, "/chromium/NativeMessagingHosts"))
					|| testManifestDir(new File(config, "/vivaldi/NativeMessagingHosts"))
					|| testManifestDir(new File(config2, "/vivaldi/NativeMessagingHosts"))
					|| testManifestDir(new File(config, "/BraveSoftware/Brave-Browser/NativeMessagingHosts"))
					|| testManifestDir(new File(config2, "/BraveSoftware/Brave-Browser/NativeMessagingHosts"))
					|| testManifestDir(new File(config, "/microsoftedge/NativeMessagingHosts"))
					|| testManifestDir(new File(config2, "/microsoftedge/NativeMessagingHosts"));
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
		return testManifestDir(new File(System.getProperty("user.home"), "/Library/Application Support/" + name + "/NativeMessagingHosts"));
	}

	private static boolean testManifestDir(File nativeHostsPath)
	{
		return testManifest(new File(nativeHostsPath, "org.keepassxc.keepassxc_browser.json"));
	}

	private static boolean testManifest(File manifestFile)
	{
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
		String regKey = "Software\\" + browser + "\\NativeMessagingHosts\\org.keepassxc.keepassxc_browser";

		try
		{
			String manifestFile = Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, regKey, "");
			return !Strings.isNullOrEmpty(manifestFile) && testManifest(new File(manifestFile));
		}
		catch (Win32Exception e)
		{
			log.debug("Failed to read registry key {}", regKey);
			return false;
		}
	}
}
