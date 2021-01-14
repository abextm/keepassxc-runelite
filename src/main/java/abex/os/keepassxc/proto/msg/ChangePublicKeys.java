package abex.os.keepassxc.proto.msg;

import lombok.Builder;
import lombok.Data;

public class ChangePublicKeys
{
	private ChangePublicKeys(){}

	public static final String ACTION = "change-public-keys";

	@Data
	@Builder
	public static class Request {
		@Builder.Default
		String action = ACTION;
		byte[] publicKey;
		byte[] nonce;
		byte[] clientID;
	}

	@Data
	public static class Response {
		String action;
		String version;
		byte[] publicKey;
		byte[] nonce;
		boolean success;
	}
}
