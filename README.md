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

```
# This fetches the `username` field from the `login` fieldset of entry `47770b12-faef-4095-9144-b32d01137f14`.
echo "47770b12-faef-4095-9144-b32d01137f14/login/username" | socat - UNIX-CONNECT:$HOME/.cache/BitwardenAgent/sockets/socket
# This fetches the value of the second field for entry `58592c99-9a60-4273-aab3-b32e0138171c`.
echo "58592c99-9a60-4273-aab3-b32e0138171c/fields/1/value" | socat -t 1 - UNIX-CONNECT:$HOME/.cache/BitwardenAgent/sockets/socket
# This fetches the value of field `text` for entry `58592c99-9a60-4273-aab3-b32e0138171c`.
echo "58592c99-9a60-4273-aab3-b32e0138171c/fields/text/value" | socat -t 1 - UNIX-CONNECT:$HOME/.cache/BitwardenAgent/sockets/socket
```
