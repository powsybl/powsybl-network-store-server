/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * @author Etienne Lesot <etienne.lesot at rte-france.com>
 */
public class IntegerArraySerializer extends JsonSerializer<Integer[]> {
    @Override
    public void serialize(Integer[] value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartArray();
        if (value != null) {
            for (Integer integer : value) {
                if (integer == Integer.MAX_VALUE) {
                    gen.writeString("MAX");
                } else {
                    gen.writeNumber(integer);
                }
            }
        }
        gen.writeEndArray();
    }
}
