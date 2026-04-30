/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.powsybl.network.store.model.TemporaryLimitAttributes;

import java.util.*;

/**
 * @author Etienne Lesot <etienne.lesot at rte-france.com>
 */
public class JsonTemporaryLimitsAttributes {
    public String[] n;
    @JsonSerialize(using = IntegerArraySerializer.class)
    @JsonDeserialize(using = IntegerArrayDeserializer.class)
    public Integer[] d;
    @JsonSerialize(using = DoubleArraySerializer.class)
    @JsonDeserialize(using = DoubleArrayDeserializer.class)
    public Double[] v;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonSerialize(using = BooleanArraySerializer.class)
    @JsonDeserialize(using = BooleanArrayDeserializer.class)
    public Boolean[] f;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Map<String, String>[] p;

    public JsonTemporaryLimitsAttributes(String[] names, Integer[] acceptableDurations, Double[] values, Boolean[] fictitious, Map<String, String>[] properties) {
        this.n = names;
        this.d = acceptableDurations;
        this.v = values;
        setFictitious(fictitious);
        setProperties(properties);
    }

    private void setFictitious(Boolean[] fictitious) {
        if (fictitious == null) {
            this.f = null;
            return;
        }
        boolean areAllFictitiousFalse = Arrays.stream(fictitious).allMatch(m -> m != null && !m);
        if (areAllFictitiousFalse) {
            this.f = null;
        } else {
            this.f = fictitious;
        }
    }

    private void setProperties(Map<String, String>[] properties) {
        if(properties == null) {
            this.p = null;
            return;
        }
        boolean hasNoProperties = Arrays.stream(properties).allMatch(m -> m != null && m.isEmpty());
        if (hasNoProperties) {
            this.p = null;
        } else {
            this.p = properties;
        }
    }

    public TreeMap<Integer, TemporaryLimitAttributes> convertToTemporaryLimitAttributes() {
        TreeMap<Integer, TemporaryLimitAttributes> result = new TreeMap<>();

        for (int i = 0; i < n.length; i++) {
            boolean fictitious = false;
            if (f != null && f.length != 0) {
                fictitious = f[i];
            }
            Map<String, String> properties = new HashMap<>();
            if (p != null && p.length != 0) {
                properties = p[i];
            }
            result.put(d[i], new TemporaryLimitAttributes(n[i], v[i], d[i], fictitious, properties));
        }
        return result;
    }
}
