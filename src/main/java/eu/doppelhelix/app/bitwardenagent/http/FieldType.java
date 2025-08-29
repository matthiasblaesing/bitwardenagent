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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Map;

import static eu.doppelhelix.app.bitwardenagent.http.FieldType.CHECKBOX;
import static eu.doppelhelix.app.bitwardenagent.http.FieldType.HIDDEN;
import static eu.doppelhelix.app.bitwardenagent.http.FieldType.LINKED;
import static eu.doppelhelix.app.bitwardenagent.http.FieldType.TEXT;

public enum FieldType {
    TEXT(0),
    HIDDEN(1),
    CHECKBOX(2),
    LINKED(3);

    private static final Map<Integer,FieldType> ID_MAP = Map.of(
            TEXT.getWireValue(), TEXT,
            HIDDEN.getWireValue(), HIDDEN,
            CHECKBOX.getWireValue(), CHECKBOX,
            LINKED.getWireValue(), LINKED
    );

    @JsonCreator
    public static FieldType fromWireValue(Integer id) {
        if (id == null) {
            return null;
        } else {
            return ID_MAP.get(id);
        }
    }

    private FieldType(int wireValue) {
        this.wireValue = wireValue;
    }

    private final int wireValue;

    @JsonValue
    public int getWireValue() {
        return wireValue;
    }

}
