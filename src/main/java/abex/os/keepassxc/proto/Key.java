package abex.os.keepassxc.proto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Key
{
	byte[] id;
	byte[] key;
}
