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
public class DoubleArrayDeserializer extends JsonDeserializer<Double[]> {
    @Override
    public Double[] deserialize(JsonParser jsonParser, DeserializationContext ctxt) throws IOException {
        if (!jsonParser.isExpectedStartArrayToken()) {
            return new Double[0];
        }

        List<Double> tempList = new ArrayList<>();

        while (jsonParser.nextToken() != JsonToken.END_ARRAY) {
            if (jsonParser.currentToken() == JsonToken.VALUE_NULL) {
                tempList.add(null);
            } else if (jsonParser.currentToken() == JsonToken.VALUE_STRING && jsonParser.getText().equals("MAXD")) {
                tempList.add(Double.MAX_VALUE);
            } else if (jsonParser.currentToken() == JsonToken.VALUE_STRING && jsonParser.getText().equals("MAXF")) {
                tempList.add((double) Float.MAX_VALUE);
            } else {
                tempList.add(jsonParser.getDoubleValue());
            }
        }
        return tempList.toArray(new Double[0]);
    }
}
