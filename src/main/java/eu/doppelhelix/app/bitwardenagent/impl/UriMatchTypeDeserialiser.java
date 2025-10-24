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
package eu.doppelhelix.app.bitwardenagent.impl;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.BaseJsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import eu.doppelhelix.app.bitwardenagent.http.UriMatchType;
import java.io.IOException;

public class UriMatchTypeDeserialiser extends StdDeserializer<UriMatchType> {

    public UriMatchTypeDeserialiser() {
        this(null);
    }

    public UriMatchTypeDeserialiser(Class<?> vc) {
        super(vc);
    }

    @Override
    public UriMatchType deserialize(JsonParser jp, DeserializationContext ctxt)
      throws IOException, JsonProcessingException {
        BaseJsonNode valueNode = jp.readValueAsTree();
        if(valueNode.isNull()) {
            return UriMatchType.fromWireValue(null);
        } else {
            return UriMatchType.fromWireValue(valueNode.asInt());
        }
    }

    @Override
    public UriMatchType getNullValue(DeserializationContext ctxt) throws JsonMappingException {
        // Someone decided, that it would be wise to specialize null, instead
        // of letting JsonCreator do what is promises
        return UriMatchType.fromWireValue(null);
    }

}