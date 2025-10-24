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
package eu.doppelhelix.app.bitwardenagent.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;



public class UriDataTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testDecode() throws JsonProcessingException {
        UriData uriData = objectMapper.readValue("{\"uri\": \"dummy\", \"uriChecksum\": \"Test\", \"match\": 2}", UriData.class);
        assertNotNull(uriData);
        assertEquals(UriMatchType.STARTS_WITH, uriData.match());
        assertEquals("dummy", uriData.uri());
        assertEquals("Test", uriData.uriChecksum());
    }

    @Test
    public void testNullMatchDecode() throws JsonProcessingException {
        // API maps "STANDARD" Matching to NULL, we map it to an enum value here

        UriData uriData = objectMapper.readValue("{\"uri\": \"dummy\", \"uriChecksum\": \"Test\", \"match\": null}", UriData.class);
        assertNotNull(uriData);
        assertEquals(UriMatchType.STANDARD, uriData.match());

        // Should also work in isolation
        assertEquals(UriMatchType.STANDARD, objectMapper.readValue("null", UriMatchType.class));
    }

}
