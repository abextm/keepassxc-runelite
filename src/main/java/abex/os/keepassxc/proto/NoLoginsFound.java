package abex.os.keepassxc.proto;

public class NoLoginsFound extends KeePassException
{
	protected NoLoginsFound(int code, String message)
	{
		super(code, message);
	}
}
