package abex.os.keepassxc.proto.msg;

import lombok.Builder;
import lombok.Data;

public class Associate
{
	private Associate(){}

	public static final String ACTION = "associate";

	@Data
	@Builder
	public static class Request {
		@Builder.Default
		String action = ACTION;
		byte[] key;
		byte[] idKey;
	}

	@Data
	public static class Response {
		byte[] id;
	}
}
