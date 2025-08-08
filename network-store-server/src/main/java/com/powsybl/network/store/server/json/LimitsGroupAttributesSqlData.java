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

    public static Map<OperationalLimitsGroupOwnerInfo, LimitsGroupAttributesSqlData> of(Map<OwnerInfo, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> operationalLimitsGroups) {
        Map<OperationalLimitsGroupOwnerInfo, LimitsGroupAttributesSqlData> result = new HashMap<>();

        for (Map.Entry<OwnerInfo, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> ownerEntry : operationalLimitsGroups.entrySet()) {
            OwnerInfo ownerInfo = ownerEntry.getKey();
            Map<Integer, Map<String, OperationalLimitsGroupAttributes>> sideMap = ownerEntry.getValue();

            for (Map.Entry<Integer, Map<String, OperationalLimitsGroupAttributes>> sideEntry : sideMap.entrySet()) {
                Integer side = sideEntry.getKey();
                Map<String, OperationalLimitsGroupAttributes> groupMap = sideEntry.getValue();

                for (Map.Entry<String, OperationalLimitsGroupAttributes> groupEntry : groupMap.entrySet()) {
                    String operationalLimitsGroupId = groupEntry.getKey();
                    OperationalLimitsGroupAttributes attributes = groupEntry.getValue();

                    LimitsGroupAttributesSqlData sqlData = createLimitsGroupSqlData(attributes);
                    OperationalLimitsGroupOwnerInfo ownerInfoKey = createOperationalLimitsGroupOwnerInfo(ownerInfo, operationalLimitsGroupId, side);

                    result.put(ownerInfoKey, sqlData);
                }
            }
        }
        return result;
    }

    private static LimitsGroupAttributesSqlData createLimitsGroupSqlData(OperationalLimitsGroupAttributes attributes) {
        return LimitsGroupAttributesSqlData.builder()
                .currentLimitsPermanentLimit(extractPermanentLimit(attributes.getCurrentLimits()))
                .currentLimitsTemporaryLimits(convertToTemporaryLimitAttributes(attributes.getCurrentLimits()))
                .apparentPowerLimitsPermanentLimit(extractPermanentLimit(attributes.getApparentPowerLimits()))
                .apparentPowerLimitsTemporaryLimits(convertToTemporaryLimitAttributes(attributes.getApparentPowerLimits()))
                .activePowerLimitsPermanentLimit(extractPermanentLimit(attributes.getActivePowerLimits()))
                .activePowerLimitsTemporaryLimits(convertToTemporaryLimitAttributes(attributes.getActivePowerLimits()))
                .properties(attributes.getProperties())
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

    private static OperationalLimitsGroupOwnerInfo createOperationalLimitsGroupOwnerInfo(OwnerInfo ownerInfo, String operationalLimitsGroupId, Integer side) {
        return new OperationalLimitsGroupOwnerInfo(
                ownerInfo.getEquipmentId(),
                ownerInfo.getEquipmentType(),
                ownerInfo.getNetworkUuid(),
                ownerInfo.getVariantNum(),
                operationalLimitsGroupId,
                side
        );
    }
}
