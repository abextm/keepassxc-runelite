package abex.os.keepassxc.proto;

public class DatabaseClosedException extends KeePassException
{
	protected DatabaseClosedException(int code, String message)
	{
		super(code, message);
	}
}
