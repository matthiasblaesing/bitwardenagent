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

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import eu.doppelhelix.app.bitwardenagent.impl.UriMatchTypeDeserialiser;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@JsonDeserialize(using = UriMatchTypeDeserialiser.class)
public enum UriMatchType {
    STANDARD(null),
    BASE_DOMAIN(0),
    HOST(1),
    STARTS_WITH(2),
    EXACT(3),
    REGEXP(4),
    NEVER(5);

    private static final Map<Integer, UriMatchType> ID_MAP;

    static {
        Map<Integer, UriMatchType> ID_MAP_BUILDER = new HashMap<>();
        for (UriMatchType umt : values()) {
            ID_MAP_BUILDER.put(umt.getWireValue(), umt);
        }
        ID_MAP = Collections.unmodifiableMap(ID_MAP_BUILDER);
    }

    public static UriMatchType fromWireValue(Integer id) {
        return ID_MAP.get(id);
    }

    private UriMatchType(Integer wireValue) {
        this.wireValue = wireValue;
    }

    private final Integer wireValue;

    @JsonValue
    public Integer getWireValue() {
        return wireValue;
    }

}
