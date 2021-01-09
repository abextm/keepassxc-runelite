package abex.os.keepassxc.proto.path;

import java.util.List;
import lombok.Data;

@Data
public class NativeMessagingManifest
{
	private List<String> allowedOrigins;
	private String description;
	private String name;
	private String path;
	private String type;
}
