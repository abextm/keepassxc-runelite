package abex.os.keepassxc.proto;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class InterruptableInputStream extends FilterInputStream
{
	private long deadline = 0;
	private long timeout = 0;

	public InterruptableInputStream(InputStream in)
	{
		super(in);
	}

	public void setDeadline(long ms)
	{
		timeout = ms * 1_000_000;
		refreshDeadline();
	}

	public void clearDeadline()
	{
		timeout = 0;
		deadline = 0;
	}

	public void refreshDeadline()
	{
		if (timeout > 0)
		{
			deadline = System.nanoTime() + timeout;
		}
	}

	private void check() throws IOException
	{
		if (deadline != 0 && System.nanoTime() > deadline)
		{
			throw new IOTimeoutException();
		}
		try
		{
			Thread.sleep(1);
		}
		catch (InterruptedException e)
		{
			throw new IOTimeoutException(e);
		}
	}

	public int read() throws IOException
	{
		while (in.available() <= 0)
		{
			check();
		}

		return in.read();
	}

	public int read(byte b[], int off, int len) throws IOException
	{
		int read = 0;
		for (; ; )
		{
			int avail = available();
			if (avail <= 0)
			{
				check();
				continue;
			}
			if (avail > len)
			{
				avail = len;
			}

			int thisread = in.read(b, off, avail);
			if (thisread < 0)
			{
				return read;
			}

			read += thisread;
			off += thisread;
			len -= thisread;

			if (len <= 0)
			{
				break;
			}
		}
		return read;
	}
}
