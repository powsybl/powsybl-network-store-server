/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server.json;

import com.powsybl.network.store.model.LimitsAttributes;
import com.powsybl.network.store.model.OperationalLimitsGroupAttributes;
import com.powsybl.network.store.model.TemporaryLimitAttributes;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class OperationalLimitsGroupAttributesSqlData {
    private Double currentLimitsPermanentLimit;
    private JsonTemporaryLimitsAttributes currentLimitsTemporaryLimits;
    private Map<String, String> currentLimitsProperties;
    private Double apparentPowerLimitsPermanentLimit;
    private JsonTemporaryLimitsAttributes apparentPowerLimitsTemporaryLimits;
    private Map<String, String> apparentPowerLimitsProperties;
    private Double activePowerLimitsPermanentLimit;
    private JsonTemporaryLimitsAttributes activePowerLimitsTemporaryLimits;
    private Map<String, String> activePowerLimitsProperties;
    private Map<String, String> properties;

    public static OperationalLimitsGroupAttributesSqlData of(OperationalLimitsGroupAttributes operationalLimitsGroup) {
        return OperationalLimitsGroupAttributesSqlData.builder()
                .currentLimitsPermanentLimit(extractPermanentLimit(operationalLimitsGroup.getCurrentLimits()))
                .currentLimitsTemporaryLimits(convertToJsonTemporaryLimitsAttributes(operationalLimitsGroup.getCurrentLimits()))
                .currentLimitsProperties(extractLimitProperties(operationalLimitsGroup.getCurrentLimits()))
                .apparentPowerLimitsPermanentLimit(extractPermanentLimit(operationalLimitsGroup.getApparentPowerLimits()))
                .apparentPowerLimitsTemporaryLimits(convertToJsonTemporaryLimitsAttributes(operationalLimitsGroup.getApparentPowerLimits()))
                .apparentPowerLimitsProperties(extractLimitProperties(operationalLimitsGroup.getActivePowerLimits()))
                .activePowerLimitsPermanentLimit(extractPermanentLimit(operationalLimitsGroup.getActivePowerLimits()))
                .activePowerLimitsTemporaryLimits(convertToJsonTemporaryLimitsAttributes(operationalLimitsGroup.getActivePowerLimits()))
                .activePowerLimitsProperties(extractLimitProperties(operationalLimitsGroup.getActivePowerLimits()))
                .properties(operationalLimitsGroup.getProperties())
                .build();
    }

    private static Double extractPermanentLimit(LimitsAttributes limitsAttributes) {
        return limitsAttributes != null ? limitsAttributes.getPermanentLimit() : null;
    }

    private static Map<String, String> extractLimitProperties(LimitsAttributes limitsAttributes) {
        return limitsAttributes != null ? limitsAttributes.getProperties() : null;
    }

    private static JsonTemporaryLimitsAttributes convertToJsonTemporaryLimitsAttributes(LimitsAttributes limitsAttributes) {
        if (limitsAttributes == null || limitsAttributes.getTemporaryLimits() == null) {
            return null;
        }

        List<String> names = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        List<Integer> acceptableDurations = new ArrayList<>();
        List<Boolean> fictitious = new ArrayList<>();
        List<Map<String, String>> properties = new ArrayList<>();

        for (TemporaryLimitAttributes temporaryLimit : limitsAttributes.getTemporaryLimits().values()) {
            names.add(temporaryLimit.getName());
            values.add(temporaryLimit.getValue());
            acceptableDurations.add(temporaryLimit.getAcceptableDuration());
            fictitious.add(temporaryLimit.isFictitious());
            properties.add(temporaryLimit.getProperties());
        }

        return new JsonTemporaryLimitsAttributes(names.toArray(String[]::new), acceptableDurations.toArray(Integer[]::new), values.toArray(Double[]::new), fictitious.toArray(Boolean[]::new), properties.toArray(Map[]::new));
    }
}
