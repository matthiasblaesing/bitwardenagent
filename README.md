Bitwarden Agent
===============

This is a java implementation of the Bitwarden CLI protocol. The bitwarden CLI
allows remote access, but requires a session-id, that must somehow be
communitcated to the calling programm.

The approach taken here is more along the ssh-agent line. A simple protocol is
run over a unix domain socket.

The socket is placed in:

- Windows: `%LOCALAPPDATA%/BitwardenAgent/sockets/socket`
- Linux/mac OS: `$HOME/.cache/BitwardenAgent/sockets`

Using `socat` a query could look like this:

The query is formatted as `<ID>/<levelOneSelector>/<levelTwoSelector>/....`

The selectors are:

- `login`
  - `username`: Username field
  - `password`: Password field
  - `totp`: TOTP url
  - `totpToken`: token calculated from `totp` filed
- `sshKey`
  - `keyFingerprint`
  - `privateKey`
  - `publicKey`
- `notes`: string field
- `fields`: the group entry is followed by the index or name of the target
            field. Follwing that the detail field is specified:
  - `linkedId`
  - `name`
  - `value`
  - `type`

Sample Queries (Bash)
---------------------

```bash
# This fetches the `username` field from the `login` fieldset of entry `47770b12-faef-4095-9144-b32d01137f14`.
USERNAME=`echo "47770b12-faef-4095-9144-b32d01137f14/login/username" | socat - UNIX-CONNECT:$HOME/.cache/BitwardenAgent/sockets/socket`
# This fetches the value of the second field for entry `58592c99-9a60-4273-aab3-b32e0138171c`.
FIELD_1=`echo "58592c99-9a60-4273-aab3-b32e0138171c/fields/1/value" | socat -t 1 - UNIX-CONNECT:$HOME/.cache/BitwardenAgent/sockets/socket`
# This fetches the value of field `text` for entry `58592c99-9a60-4273-aab3-b32e0138171c`.
FIELD_TEXT=`echo "58592c99-9a60-4273-aab3-b32e0138171c/fields/text/value" | socat -t 1 - UNIX-CONNECT:$HOME/.cache/BitwardenAgent/sockets/socket`
echo "Username: $USERNAME"
echo "Field 1: $FIELD_1"
echo "Field 'text': $FIELD_TEXT"
```

Sample Queries (Java)
---------------------

```java
public class QueryBitwardenAgent {
    public static void main(String[] args) throws IOException, InterruptedException {
        Map<String, String> labelQuery = new LinkedHashMap<>();
        labelQuery.put("47770b12-faef-4095-9144-b32d01137f14/login/username", "Username");
        labelQuery.put("58592c99-9a60-4273-aab3-b32e0138171c/fields/1/value", "Field 1");
        labelQuery.put("58592c99-9a60-4273-aab3-b32e0138171c/fields/text/value", "Field 'text'");

        for(Entry<String,String> e: labelQuery.entrySet()) {
            System.out.printf("%s: %s%n", e.getValue(), runQuery(e.getKey()));
        }
    }

    private static String runQuery(String query) throws IOException {
        UnixDomainSocketAddress socketAddress = UnixDomainSocketAddress.of(System.getenv("HOME") + "/.cache/BitwardenAgent/sockets/socket");
        try (SocketChannel sc = SocketChannel.open(socketAddress)) {
            ByteBuffer readBuffer = ByteBuffer.allocate(4096);
            sc.write(ByteBuffer.wrap(query.getBytes(UTF_8)));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            while (sc.read(readBuffer) > 0) {
                readBuffer.flip();
                baos.write(readBuffer.array(), readBuffer.position(), readBuffer.limit() - readBuffer.position());
            }
            return baos.toString(UTF_8);
        }
    }
}
```
