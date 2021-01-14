package abex.os.keepassxc;

import abex.os.keepassxc.proto.msg.GetLogins;
import abex.os.keepassxc.proto.KeePassXCSocket;

public class ManualTest
{
	public static void main(String... args) throws Exception
	{
		try (KeePassXCSocket s = new KeePassXCSocket())
		{
			s.setDeadline(500);
			long start = System.currentTimeMillis();
			s.init();
			System.out.println((System.currentTimeMillis() - start) + " ms");
			s.clearDeadline();
			GetLogins.Response r = s.call(GetLogins.ACTION, GetLogins.Request.builder()
				.action(GetLogins.ACTION)
				.url("https://secure.runescape.com/")
				.keys(s.getKeys())
				.build(), GetLogins.Response.class);
			System.out.println(r.toString());
		}
	}
}
