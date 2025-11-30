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

import eu.doppelhelix.app.bitwardenagent.impl.BitwardenClient;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class UnixDomainSocketServerTest {

    public UnixDomainSocketServerTest() {
    }

    @Test
    public void assertShutdownBlocksUntilDone() throws IOException {
        Path socketDirectory = Path.of("target/test/sockets");
        recursiveDelete(socketDirectory);
        Files.createDirectories(socketDirectory);

        BitwardenClient bitwardenClient = Mockito.mock(BitwardenClient.class);

        UnixDomainSocketServer server = new UnixDomainSocketServer(bitwardenClient, socketDirectory);

        server.start();
        server.shutdown();

        assertFalse(server.isAlive(), "Thread still alive: " + server);
    }

    private static void recursiveDelete(Path basePath) throws IOException {
        if(basePath == null || ! Files.exists(basePath)) {
            return;
        }
        Files.walkFileTree(basePath, new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path t, BasicFileAttributes bfa) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path t, BasicFileAttributes bfa) throws IOException {
                Files.delete(t);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path t, IOException ioe) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path t, IOException ioe) throws IOException {
                Files.delete(t);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
