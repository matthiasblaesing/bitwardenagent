/*
 * Copyright 2025 matthias.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.doppelhelix.app.bitwardenagent.server;

import eu.doppelhelix.app.bitwardenagent.Configuration;
import eu.doppelhelix.app.bitwardenagent.impl.BitwardenClient;
import eu.doppelhelix.app.bitwardenagent.impl.TOTPUtil;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static eu.doppelhelix.app.bitwardenagent.Configuration.PROP_ALLOW_ACCESS;
import static java.nio.charset.StandardCharsets.UTF_8;


public class UnixDomainSocketServer extends Thread {

    private static final System.Logger LOG = System.getLogger(UnixDomainSocketServer.class.getName());

    private final Executor executor = Executors.newWorkStealingPool(10);
    private final Path socketDirectory;
    private final BitwardenClient bitwardenClient;
    private Set<String> allowAccess = Collections.synchronizedSet(new HashSet<>());
    private ServerSocketChannel listenChannel;

    public UnixDomainSocketServer(BitwardenClient bitwardenClient) {
        setDaemon(true);
        this.bitwardenClient = bitwardenClient;
        this.socketDirectory = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows")
                ? Path.of(System.getenv("LOCALAPPDATA"), "BitwardenAgent", "sockets")
                : Path.of(System.getenv("HOME"), ".cache/BitwardenAgent", "sockets");
        allowAccess.addAll(Configuration.getConfiguration().getAllowAccess());
        Configuration.getConfiguration().addObserver((name, value) -> {
            if (PROP_ALLOW_ACCESS.equals(name)) {
                allowAccess.addAll((Collection<String>) value);
                allowAccess.retainAll((Collection<String>) value);
            }
        });
    }

    public void shutdown() throws IOException {
        if(listenChannel != null) {
            listenChannel.close();
        }
    }

    @Override
    public void run() {
        try {
            if (!Files.exists(socketDirectory)) {
                Files.createDirectories(socketDirectory);
            }

            Path socketPath = socketDirectory.resolve("socket");
            Files.deleteIfExists(socketPath);

            try {
                UnixDomainSocketAddress udsa = UnixDomainSocketAddress.of(socketPath);
                listenChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
                listenChannel.bind(udsa);
                LOG.log(Level.INFO, "Started UnixDomainSocket server");
                while (true) {
                    SocketChannel ch = listenChannel.accept();
                    executor.execute(() -> {
                        try {
                            ByteBuffer bb = ByteBuffer.allocate(4096);
                            ch.read(bb);
                            bb.flip();
                            String input = new String(bb.array(), 0, bb.limit());
                            String[] target = input.trim().split("/", 2);
                            String id = target[0];
                            String result = "";
                            if (allowAccess.contains(id) || Configuration.getConfiguration().isAllowAllAccess()) {
                                String attribute = target[1];
                                result = bitwardenClient.getSyncData()
                                        .getCiphers()
                                        .stream()
                                        .filter(dcd -> Objects.equals(id, dcd.getId()))
                                        .findFirst()
                                        .map(dcd -> switch (attribute) {
                                                    case "username" ->
                                                        dcd.getLogin().getUsername();
                                                    case "password" ->
                                                        dcd.getLogin().getPassword();
                                                    case "totpToken" ->
                                                        TOTPUtil.calculateTOTP(dcd.getLogin().getTotp());
                                                    default ->
                                                        "-";
                                                }
                                        )
                                        .orElse("-");
                            }
                            bb.clear();
                            bb.put(result.getBytes(UTF_8));
                            bb.put("\n".getBytes(UTF_8));
                            bb.flip();
                            ch.write(bb);
                            ch.close();
                        } catch (IOException | RuntimeException ex1) {
                            System.getLogger(UnixDomainSocketServer.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex1);
                        }
                    });
                }
            } catch (AsynchronousCloseException ex) {
            } finally {
                listenChannel = null;
                Files.delete(socketPath);
                LOG.log(Level.INFO, "Shutted down UnixDomainSocket server");
            }
        } catch (IOException ex) {
            LOG.log(Level.ERROR, "Failed to create socket directory: " + socketDirectory, ex);
        }
    }
}
