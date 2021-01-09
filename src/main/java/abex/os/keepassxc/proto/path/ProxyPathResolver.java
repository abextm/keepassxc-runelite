package abex.os.keepassxc.proto.path;

import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.OSType;

@Slf4j
public abstract class ProxyPathResolver
{

	private static final List<String> manifestPaths = new ArrayList<>(5);

	static
	{
		// linux
		manifestPaths.add("~/.config/google-chrome/NativeMessagingHosts/org.keepassxc.keepassxc_browser.json");
		manifestPaths.add("/etc/opt/chrome/native-messaging-hosts/org.keepassxc.keepassxc_browser.json");

		// macos
		manifestPaths.add("~/Library/Application Support/Google/Chrome/NativeMessagingHosts/org.keepassxc.keepassxc_browser.json");
		manifestPaths.add("/Library/Google/Chrome/NativeMessagingHosts/org.keepassxc.keepassxc_browser.json");

		// windows
		if (OSType.getOSType().equals(OSType.Windows))
		{
			String winManifestPath = WindowsRegistryReader.getManifestPath();
			if (winManifestPath != null)
			{
				manifestPaths.add(winManifestPath);
			}
		}
	}

	public static String getKeepassProxyPath()
	{
		// check if keepass-proxy is on PATH
		if (isKeepassProxyOnPATH())
		{
			return "keepass-proxy";
		}

		// check for native messaging manifests to pull path from
		for (String manifestPath : manifestPaths)
		{
			String result = getProxyPathFromManifest(manifestPath);
			if (result != null)
			{
				return result;
			}
		}

		log.error("Could not locate keepass-proxy");
		return null;
	}

	private static boolean isKeepassProxyOnPATH()
	{
		try
		{
			Process pathedProcess = Runtime.getRuntime().exec("keepass-proxy");
			pathedProcess.destroy();
			return true;
		}
		catch (IOException e)
		{
			// wasn't on path
			log.debug("keepass-proxy not on PATH");
			return false;
		}
	}

	private static final String getProxyPathFromManifest(String manifestPath)
	{
		final Gson GSON = new Gson();
		try
		{
			// make sure manifest actually exists before reading
			File manifestFile = new File(manifestPath);
			if (!manifestFile.exists())
			{
				return null;
			}

			// parse json to NMManifest, which should contain proxy path
			String manifestJson = new String(Files.readAllBytes(manifestFile.toPath()));
			NativeMessagingManifest manifest = GSON.fromJson(manifestJson, NativeMessagingManifest.class);
			return manifest.getPath();
		}
		catch (IOException e)
		{
			log.debug("Failed to read manifest file", e);
			return null;
		}
	}


}
