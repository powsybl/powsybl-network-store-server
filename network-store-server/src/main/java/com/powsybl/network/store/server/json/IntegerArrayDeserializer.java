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
public class IntegerArrayDeserializer extends JsonDeserializer<Integer[]> {
    @Override
    public Integer[] deserialize(JsonParser jsonParser, DeserializationContext ctxt) throws IOException {
        if (!jsonParser.isExpectedStartArrayToken()) {
            return null; // ou lance une exception
        }

        List<Integer> tempList = new ArrayList<>();

        // Parcours du tableau JSON
        while (jsonParser.nextToken() != JsonToken.END_ARRAY) {
            if (jsonParser.currentToken() == JsonToken.VALUE_NULL) {
                tempList.add(null); // gérer les nulls
            } else if (jsonParser.currentToken() == JsonToken.VALUE_STRING && jsonParser.getText().equals("MAX")) {
                tempList.add(Integer.MAX_VALUE);
            } else {
                tempList.add(jsonParser.getIntValue());
            }
        }

        // Convertit la liste en tableau
        return tempList.toArray(new Integer[0]);
    }
}
