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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
        this.f = areAllFictitiousFalse ? null : fictitious.stream().map(m -> m ? 1 : 0).toArray(Integer[]::new);
    }

    private void setProperties(List<Map<String, String>> properties) {
        if (properties == null) {
            this.p = null;
            return;
        }
        boolean hasNoProperties = properties.stream().allMatch(m -> m != null && m.isEmpty());
        this.p = hasNoProperties ? null : properties.toArray(Map[]::new);
    }

    public TreeMap<Integer, TemporaryLimitAttributes> convertToTemporaryLimitAttributes() {
        TreeMap<Integer, TemporaryLimitAttributes> result = new TreeMap<>();
        for (int i = 0; i < n.length; i++) {
            Double value = Double.NaN;
            if (v[i] instanceof String) {
                if (v[i].equals("MAXD")) {
                    value = Double.MAX_VALUE;
                } else if (v[i].equals("MAXF")) {
                    value = (double) Float.MAX_VALUE;
                }
            } else {
                value = (Double) v[i];
            }
            Integer duration;
            if (d[i] instanceof String && d[i].equals("MAX")) {
                duration = Integer.MAX_VALUE;
            } else {
                assert d[i] instanceof Integer;
                duration = (Integer) d[i];
            }
            boolean fictitious = false;
            if (f != null && f.length != 0) {
                fictitious = f[i] == 1;
            }
            Map<String, String> properties = new HashMap<>();
            if (p != null && p.length != 0) {
                properties = p[i];
            }
            result.put(duration, new TemporaryLimitAttributes(n[i], value, duration, fictitious, properties));
        }
        return result;
    }
}
