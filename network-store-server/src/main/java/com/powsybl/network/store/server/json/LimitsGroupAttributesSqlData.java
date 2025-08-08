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
import com.powsybl.network.store.server.dto.OperationalLimitsGroupOwnerInfo;
import com.powsybl.network.store.server.dto.OwnerInfo;
import lombok.*;

import java.util.*;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class LimitsGroupAttributesSqlData {
    private Double currentLimitsPermanentLimit;
    private List<TemporaryLimitAttributes> currentLimitsTemporaryLimits;
    private Double apparentPowerLimitsPermanentLimit;
    private List<TemporaryLimitAttributes> apparentPowerLimitsTemporaryLimits;
    private Double activePowerLimitsPermanentLimit;
    private List<TemporaryLimitAttributes> activePowerLimitsTemporaryLimits;
    private Map<String, String> properties;

    public static LimitsGroupAttributesSqlData of(OperationalLimitsGroupAttributes operationalLimitsGroup) {
        return LimitsGroupAttributesSqlData.builder()
                .currentLimitsPermanentLimit(extractPermanentLimit(operationalLimitsGroup.getCurrentLimits()))
                .currentLimitsTemporaryLimits(convertToTemporaryLimitAttributes(operationalLimitsGroup.getCurrentLimits()))
                .apparentPowerLimitsPermanentLimit(extractPermanentLimit(operationalLimitsGroup.getApparentPowerLimits()))
                .apparentPowerLimitsTemporaryLimits(convertToTemporaryLimitAttributes(operationalLimitsGroup.getApparentPowerLimits()))
                .activePowerLimitsPermanentLimit(extractPermanentLimit(operationalLimitsGroup.getActivePowerLimits()))
                .activePowerLimitsTemporaryLimits(convertToTemporaryLimitAttributes(operationalLimitsGroup.getActivePowerLimits()))
                .properties(operationalLimitsGroup.getProperties())
                .build();
    }

    private static Double extractPermanentLimit(LimitsAttributes limitsAttributes) {
        return limitsAttributes != null ? limitsAttributes.getPermanentLimit() : null;
    }

    private static List<TemporaryLimitAttributes> convertToTemporaryLimitAttributes(LimitsAttributes limitsAttributes) {
        if (limitsAttributes == null || limitsAttributes.getTemporaryLimits() == null) {
            return null;
        }

        return limitsAttributes.getTemporaryLimits().values().stream()
                .map(temporaryLimit -> TemporaryLimitAttributes.builder()
                        .acceptableDuration(temporaryLimit.getAcceptableDuration())
                        .name(temporaryLimit.getName())
                        .value(temporaryLimit.getValue())
                        .fictitious(temporaryLimit.isFictitious())
                        .build())
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
}
