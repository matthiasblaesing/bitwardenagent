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

public enum LinkedId {
    PASSWORD(101),
    USERNAME(100);

    private static final Map<Integer,LinkedId> ID_MAP = Map.of(
            PASSWORD.getWireValue(), PASSWORD,
            USERNAME.getWireValue(), USERNAME
    );

    @JsonCreator
    public static LinkedId fromWireValue(Integer id) {
        if (id == null) {
            return null;
        } else {
            return ID_MAP.get(id);
        }
    }

    private LinkedId(int wireValue) {
        this.wireValue = wireValue;
    }

    private final int wireValue;

    @JsonValue
    public int getWireValue() {
        return wireValue;
    }

}
