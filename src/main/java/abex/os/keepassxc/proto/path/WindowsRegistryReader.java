package abex.os.keepassxc.proto.path;

import com.google.common.io.CharStreams;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WindowsRegistryReader
{

	private static final String HKCU_REG_PATH = "HKCU\\SOFTWARE\\Google\\Chrome\\NativeMessagingHosts\\org.keepassxc.keepassxc_browser";
	private static final String HKLM_REG_PATH = "HKLM\\SOFTWARE\\Google\\Chrome\\NativeMessagingHosts\\org.keepassxc.keepassxc_browser";
	private static final Pattern ENTRY_REGEX = Pattern.compile(" {4}\\(Default\\) {4}REG_SZ {4}(.*?)\\r?\\n", Pattern.MULTILINE);

	private static String readRegistryPath(String regPath)
	{
		try
		{
			// query registry via reg cmdlet
			Process regProc = Runtime.getRuntime().exec("reg query " + regPath);
			InputStreamReader isr = new InputStreamReader(regProc.getInputStream());
			String regOutput = CharStreams.toString(isr);
			isr.close(); // CharStreams.toString doesn't close underlying stream

			// extract manifest path output
			Matcher m = ENTRY_REGEX.matcher(regOutput);
			return m.find() ? m.group(1) : null;
		}
		catch (IOException e)
		{
			log.warn("Failed to read registry key {}: {}", regPath, e);
			return null;
		}
	}

	public static String getManifestPath()
	{
		// check current user registry first, since it takes priority over local machine
		String hkcuResult = readRegistryPath(HKCU_REG_PATH);
		return hkcuResult != null ? hkcuResult : readRegistryPath(HKLM_REG_PATH);
	}

}
