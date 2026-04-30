/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Etienne Lesot <etienne.lesot at rte-france.com>
 */
public class BooleanArrayDeserializer extends JsonDeserializer<Boolean[]> {
    @Override
    public Boolean[] deserialize(JsonParser jsonParser, DeserializationContext ctxt) throws IOException {
        if (!jsonParser.isExpectedStartArrayToken()) {
            return new Boolean[0];
        }

        List<Boolean> tempList = new ArrayList<>();

        while (jsonParser.nextToken() != JsonToken.END_ARRAY) {
            if (jsonParser.currentToken() == JsonToken.VALUE_NULL) {
                tempList.add(null);
            } else {
                tempList.add(jsonParser.getIntValue() != 0);
            }
        }
        return tempList.toArray(new Boolean[0]);
    }
}
