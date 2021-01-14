package abex.os.keepassxc.proto.msg;

import lombok.Data;

public class GetDatabaseHash
{
	private GetDatabaseHash() {};

	public static final String ACTION = "get-databasehash";

	@Data
	public static class Request
	{
		String action = ACTION;
	}

	@Data
	public static class Response
	{
		String hash;
	}
}
