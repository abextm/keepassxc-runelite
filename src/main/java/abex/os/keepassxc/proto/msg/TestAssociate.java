package abex.os.keepassxc.proto.msg;

import lombok.Builder;
import lombok.Data;

public class TestAssociate
{
	private TestAssociate(){};

	public static final String ACTION = "test-associate";

	@Data
	@Builder
	public static class Request {
		@Builder.Default
		String action = ACTION;
		String id;
		byte[] key;
	}

	@Data
	public static class Response {
	}
}
