package abex.os.keepassxc.proto;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

@Slf4j
public class Keyring
{

	private static final Type KEYRING_TYPE = new TypeToken<Map<String, Key>>(){}.getType();
	private static final Gson gson = new GsonBuilder()
		.disableHtmlEscaping()
		.registerTypeHierarchyAdapter(byte[].class, new Base64Adapter())
		.create();

	private static final File KEYRING_FILE = new File(RuneLite.RUNELITE_DIR, "keepassxc.keyring");

	private Map<String, Key> keys;

	public Keyring() throws IOException
	{
		if (!KEYRING_FILE.exists())
		{
			keys = new HashMap<>();
			writeKeys();
		}
		else
		{
			readKeys();
		}
	}

	synchronized void readKeys() throws IOException
	{
		keys = gson.fromJson(new String(Files.readAllBytes(KEYRING_FILE.toPath()), StandardCharsets.UTF_8), KEYRING_TYPE);
	}

	synchronized void writeKeys() throws IOException
	{
		Files.write(KEYRING_FILE.toPath(), gson.toJson(keys).getBytes(StandardCharsets.UTF_8),
			StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
	}

	synchronized void storeKey(String dbHash, Key k) throws IOException
	{
		keys.put(dbHash, k);
		writeKeys();
	}

	synchronized Key getKey(String dbHash)
	{
		return keys.get(dbHash);
	}

	synchronized Collection<Key> allKeys()
	{
		return keys.values();
	}

}
