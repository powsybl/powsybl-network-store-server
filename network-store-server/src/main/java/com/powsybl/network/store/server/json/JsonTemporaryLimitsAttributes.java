/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.powsybl.network.store.model.TemporaryLimitAttributes;
import lombok.Getter;

import java.util.*;

/**
 * @author Etienne Lesot <etienne.lesot at rte-france.com>
 */
@Getter
public class JsonTemporaryLimitsAttributes {
    private final String[] n;
    private Object[] d;
    private Object[] v;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer[] f;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, String>[] p;

    public JsonTemporaryLimitsAttributes(String[] names, List<Integer> acceptableDurations, List<Double> values, List<Boolean> fictitious, List<Map<String, String>> properties) {
        this.n = names;
        setAcceptableDuration(acceptableDurations);
        setValues(values);
        setFictitious(fictitious);
        setProperties(properties);
    }

    private void setAcceptableDuration(List<Integer> acceptableDurations) {
        if (acceptableDurations == null) {
            this.d = null;
            return;
        }
        this.d = acceptableDurations.stream().map(acceptableDuration -> acceptableDuration == Integer.MAX_VALUE ? "MAX" : acceptableDuration).toArray();
    }

    private void setValues(List<Double> values) {
        if (values == null) {
            this.v = null;
            return;
        }
        this.v = values.stream().map(val -> {
            if (val == Double.MAX_VALUE) {
                return "MAXD";
            } else if (val == Float.MAX_VALUE) {
                return "MAXF";
            } else {
                return val;
            }
        }).toArray();
    }

    private void setFictitious(List<Boolean> fictitious) {
        if (fictitious == null) {
            this.f = null;
            return;
        }
        boolean areAllFictitiousFalse = fictitious.stream().allMatch(m -> m != null && !m);
        if (areAllFictitiousFalse) {
            this.f = null;
        } else {
            this.f = fictitious.stream().map(m -> Boolean.TRUE.equals(m) ? 1 : 0).toArray(Integer[]::new);
        }
    }

    private void setProperties(List<Map<String, String>> properties) {
        if (properties == null) {
            this.p = null;
            return;
        }
        boolean hasNoProperties = properties.stream().allMatch(m -> m != null && m.isEmpty());
        this.p = hasNoProperties ? null : properties.toArray(Map[]::new);
    }

    public SortedMap<Integer, TemporaryLimitAttributes> convertToTemporaryLimitAttributes() {
        TreeMap<Integer, TemporaryLimitAttributes> result = new TreeMap<>();
        for (int i = 0; i < n.length; i++) {
            Integer duration = parseDuration(d[i]);
            result.put(duration, new TemporaryLimitAttributes(n[i], parseValue(v[i]), duration, parseFictitious(i), parseProperties(i)));
        }
        return result;
    }

    private Double parseValue(Object rawValue) {
        if (rawValue instanceof String s) {
            if ("MAXD".equals(s)) {
                return Double.MAX_VALUE;
            } else if ("MAXF".equals(s)) {
                return (double) Float.MAX_VALUE;
            }
            return Double.NaN;
        }
        return (Double) rawValue;
    }

    private Integer parseDuration(Object rawDuration) {
        if (rawDuration instanceof String s && "MAX".equals(s)) {
            return Integer.MAX_VALUE;
        }
        return (Integer) rawDuration;
    }

    private boolean parseFictitious(int i) {
        return f != null && f.length != 0 && f[i] == 1;
    }

    private Map<String, String> parseProperties(int i) {
        return p != null && p.length != 0 ? p[i] : new HashMap<>();
    }
}
