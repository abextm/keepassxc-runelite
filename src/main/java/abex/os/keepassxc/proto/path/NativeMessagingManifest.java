package abex.os.keepassxc.proto.path;

import java.util.List;
import lombok.Value;

@Value
public class NativeMessagingManifest
{

	List<String> allowedOrigins;
	String description;
	String name;
	String path;
	String type;

}
