package abex.os.keepassxc.proto;

import abex.os.keepassxc.proto.path.ProxyPathResolver;
import com.google.common.io.LittleEndianDataInputStream;
import com.google.common.io.LittleEndianDataOutputStream;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.neilalexander.jnacl.NaCl;
import com.neilalexander.jnacl.crypto.curve25519xsalsa20poly1305;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// this whole thing is a trainwreck
// especially jnacl, which takes arguments in the wrong order, gives you the wrong output
// and in general is wrong. Assume there is no crypto happening here because there probably isn't
// Fortunately this is only over a local unix socket / named pipe, so it should be fine
@Slf4j
public class KeePassXCSocket implements Closeable
{
	private static final int KEY_SIZE = 32;
	private static final int ID_SIZE = 24;

	private static final Gson gson = new GsonBuilder()
		.disableHtmlEscaping()
		.registerTypeHierarchyAdapter(byte[].class, new Base64Adapter())
		.create();

	private Process proc;
	private InterruptableInputStream stdoutInterrupt;
	private LittleEndianDataOutputStream stdin;
	private LittleEndianDataInputStream stdout;

	private byte[] clientID = new byte[ID_SIZE];
	private byte[] privateKey = new byte[KEY_SIZE];
	private byte[] publicKey = new byte[KEY_SIZE];
	private byte[] serverPublicKey;
	private NaCl nacl;

	private Keyring keyring;

	private final SecureRandom secureRandom = new SecureRandom();

	public KeePassXCSocket() throws IOException
	{
		keyring = new Keyring();
		String keepassProxyPath = ProxyPathResolver.getKeepassProxyPath();
		if (keepassProxyPath == null)
		{
			throw KeePassException.create(0, "Could not locate keepass-proxy.");
		}

		ProcessBuilder pb = new ProcessBuilder();
		pb.command(keepassProxyPath);
		pb.redirectInput(ProcessBuilder.Redirect.PIPE);
		pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
		proc = pb.start();
		stdin = new LittleEndianDataOutputStream(proc.getOutputStream());
		stdoutInterrupt = new InterruptableInputStream(proc.getInputStream());
		stdout = new LittleEndianDataInputStream(stdoutInterrupt);
	}

	public void setDeadline(long ms)
	{
		stdoutInterrupt.setDeadline(ms);
	}

	public void clearDeadline()
	{
		stdoutInterrupt.clearDeadline();
	}

	public void init() throws IOException
	{
		secureRandom.nextBytes(clientID);
		curve25519xsalsa20poly1305.crypto_box_keypair(publicKey, privateKey);

		{
			byte[] nonce = new byte[ID_SIZE];
			secureRandom.nextBytes(nonce);

			byte[] msg = gson.toJson(ChangePublicKeys.Request.builder()
				.nonce(nonce)
				.clientID(clientID)
				.publicKey(publicKey)
				.build()).getBytes(StandardCharsets.UTF_8);
			stdin.writeInt(msg.length);
			stdin.write(msg);
			stdin.flush();

			increment(nonce);

			byte[] rs = new byte[stdout.readInt()];
			stdout.readFully(rs);
			ChangePublicKeys.Response r = gson.fromJson(new String(rs, StandardCharsets.UTF_8), ChangePublicKeys.Response.class);
			if (!Arrays.equals(r.nonce, nonce))
			{
				throw new IOException("Incorrect nonce: " + Arrays.toString(r.nonce) + " != " + Arrays.toString(nonce));
			}
			if (!r.success)
			{
				throw new IOException("success == false");
			}

			serverPublicKey = r.publicKey;
			try
			{
				nacl = new NaCl(privateKey, serverPublicKey);
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		}

		ensureAssociate();
	}

	private void increment(byte[] nonce)
	{
		int carry = 1;
		for (int i = 0; i < nonce.length; i++)
		{
			int v = (nonce[i] & 0xFF) + carry;
			nonce[i] = (byte) v;
			carry = v >>> 8;
		}
	}

	@AllArgsConstructor
	private static class RequestWrapper
	{
		String action;
		byte[] message;
		byte[] nonce;
		byte[] clientID;
	}

	private static class ResponseWrapper
	{
		byte[] message;
		byte[] nonce;
		String error;
		int errorCode;
	}

	private static class ResponseShared
	{
		byte[] nonce;
		boolean success;
		String error;
		int errorCode;
	}

	public synchronized <T> T call(String action, Object send, Class<T> type) throws IOException
	{
		byte[] nonce = new byte[ID_SIZE];
		secureRandom.nextBytes(nonce);

		byte[] rawMsg = gson.toJson(send).getBytes(StandardCharsets.UTF_8);
		byte[] cryptMsgAndGarbage = nacl.encrypt(rawMsg, nonce);
		byte[] cryptMsg = new byte[cryptMsgAndGarbage.length - curve25519xsalsa20poly1305.crypto_secretbox_BOXZEROBYTES];
		System.arraycopy(cryptMsgAndGarbage, curve25519xsalsa20poly1305.crypto_secretbox_BOXZEROBYTES, cryptMsg, 0, cryptMsg.length);
		byte[] wrappedMsg = gson.toJson(new RequestWrapper(action, cryptMsg, nonce, clientID))
			.getBytes(StandardCharsets.UTF_8);

		stdin.writeInt(wrappedMsg.length);
		stdin.write(wrappedMsg);
		stdin.flush();

		increment(nonce);

		byte[] rs = new byte[stdout.readInt()];
		stdout.readFully(rs);
		ResponseWrapper res = gson.fromJson(new String(rs, StandardCharsets.UTF_8), ResponseWrapper.class);
		if (res.error != null)
		{
			throw KeePassException.create(res.errorCode, res.error);
		}
		byte[] cryptResAndGarbage = new byte[res.message.length + curve25519xsalsa20poly1305.crypto_secretbox_BOXZEROBYTES];
		System.arraycopy(res.message, 0, cryptResAndGarbage, curve25519xsalsa20poly1305.crypto_secretbox_BOXZEROBYTES, res.message.length);
		byte[] rawRes = nacl.decrypt(cryptResAndGarbage, res.nonce);
		String resStr = new String(rawRes, StandardCharsets.UTF_8);

		ResponseShared meta = gson.fromJson(resStr, ResponseShared.class);
		if (!meta.success)
		{
			throw KeePassException.create(meta.errorCode, meta.error);
		}
		if (!Arrays.equals(meta.nonce, nonce))
		{
			throw new IOException("Nonce mismatch");
		}

		return gson.fromJson(resStr, type);
	}

	public synchronized String getDatabaseHash()
	{
		try
		{
			GetDatabaseHash.Response hashResponse = call(GetDatabaseHash.ACTION, new GetDatabaseHash.Request(), GetDatabaseHash.Response.class);
			return hashResponse.getHash();
		}
		catch (IOException e)
		{
			log.debug("", e);
			return null;
		}
	}

	public synchronized void associate(String dbHash) throws IOException
	{
		setDeadline(30000); // user will need to accept a prompt
		byte[] id = new byte[KEY_SIZE];
		secureRandom.nextBytes(id);

		Key k = new Key();
		k.key = id;
		k.id = call(Associate.ACTION, Associate.Request.builder()
			.idKey(id)
			.key(publicKey)
			.build(), Associate.Response.class)
			.id;

		keyring.storeKey(dbHash, k);
	}

	synchronized boolean testAssociate(Key k) throws IOException
	{
		try
		{
			call(TestAssociate.ACTION, TestAssociate.Request.builder()
				.id(k.id)
				.key(k.key)
				.build(), TestAssociate.Response.class);
			return true;
		}
		catch (IOException e)
		{
			log.debug("", e);
			return false;
		}
	}

	public synchronized void ensureAssociate() throws IOException
	{
		String currentDbHash = getDatabaseHash();
		Key currentDbKey = keyring.getKey(currentDbHash);
		if (currentDbKey != null && testAssociate(currentDbKey))
		{
			return;
		}

		associate(currentDbHash);
	}

	public Collection<Key> getKeys()
	{
		return Collections.unmodifiableCollection(keyring.allKeys());
	}

	public void close() throws IOException
	{
		stdin.close();
		proc.destroy();
	}
}
