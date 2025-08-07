/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server.json;

import com.powsybl.network.store.model.OperationalLimitsGroupAttributes;
import com.powsybl.network.store.model.TemporaryLimitAttributes;
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
    private UUID networkUuid;
    private String equipmentType;
    private Integer variantNum;
    private String equipmentId;
    private String operationalLimitsGroupId;
    private Integer side;
    private Double currentLimitsPermanentLimit;
    private List<TemporaryLimitAttributes> currentLimitsTemporaryLimits;
    private Double apparentPowerLimitsPermanentLimit;
    private List<TemporaryLimitAttributes> apparentPowerLimitsTemporaryLimits;
    private Double activePowerLimitsPermanentLimit;
    private List<TemporaryLimitAttributes> activePowerLimitsTemporaryLimits;
    private Map<String, String> properties;

    //Maybe ouput a Map<OwnerInfo, List<LimitsGroupAttributesSqlData>>> ?
    public static List<LimitsGroupAttributesSqlData> of(Map<OwnerInfo, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> tapChangerStepAttributes) {
        List<LimitsGroupAttributesSqlData> result = new ArrayList<>();

        for (Map.Entry<OwnerInfo, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> ownerEntry : tapChangerStepAttributes.entrySet()) {
            OwnerInfo ownerInfo = ownerEntry.getKey();
            Map<Integer, Map<String, OperationalLimitsGroupAttributes>> sideMap = ownerEntry.getValue();

            for (Map.Entry<Integer, Map<String, OperationalLimitsGroupAttributes>> stepEntry : sideMap.entrySet()) {
                Integer side = stepEntry.getKey();
                Map<String, OperationalLimitsGroupAttributes> groupMap = stepEntry.getValue();

                for (Map.Entry<String, OperationalLimitsGroupAttributes> groupEntry : groupMap.entrySet()) {
                    String operationalLimitsGroupId = groupEntry.getKey();
                    OperationalLimitsGroupAttributes attributes = groupEntry.getValue();

                    LimitsGroupAttributesSqlData sqlData = LimitsGroupAttributesSqlData.builder()
                            .networkUuid(ownerInfo.getNetworkUuid())
                            .variantNum(ownerInfo.getVariantNum())
                            .equipmentType(ownerInfo.getEquipmentType().toString())
                            .equipmentId(ownerInfo.getEquipmentId())
                            .operationalLimitsGroupId(operationalLimitsGroupId)
                            .side(side)
                            .currentLimitsPermanentLimit(attributes.getCurrentLimits() != null ? attributes.getCurrentLimits().getPermanentLimit() : null)
                            .currentLimitsTemporaryLimits(attributes.getCurrentLimits() != null && attributes.getCurrentLimits().getTemporaryLimits() != null ?
                                    attributes.getCurrentLimits().getTemporaryLimits().values().stream()
                                            .map(temp -> TemporaryLimitAttributes.builder()
                                                    .acceptableDuration(temp.getAcceptableDuration())
                                                    .name(temp.getName())
                                                    .value(temp.getValue())
                                                    .fictitious(temp.isFictitious())
                                                    .build())
                                            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll) : null)
                            .apparentPowerLimitsPermanentLimit(attributes.getApparentPowerLimits() != null ? attributes.getApparentPowerLimits().getPermanentLimit() : null)
                            .apparentPowerLimitsTemporaryLimits(attributes.getApparentPowerLimits() != null && attributes.getApparentPowerLimits().getTemporaryLimits() != null ?
                                    attributes.getApparentPowerLimits().getTemporaryLimits().values().stream()
                                            .map(temp -> TemporaryLimitAttributes.builder()
                                                    .acceptableDuration(temp.getAcceptableDuration())
                                                    .name(temp.getName())
                                                    .value(temp.getValue())
                                                    .fictitious(temp.isFictitious())
                                                    .build())
                                            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll) : null)
                            .activePowerLimitsPermanentLimit(attributes.getActivePowerLimits() != null ? attributes.getActivePowerLimits().getPermanentLimit() : null)
                            .activePowerLimitsTemporaryLimits(attributes.getActivePowerLimits() != null && attributes.getActivePowerLimits().getTemporaryLimits() != null ?
                                    attributes.getActivePowerLimits().getTemporaryLimits().values().stream()
                                            .map(temp -> TemporaryLimitAttributes.builder()
                                                    .acceptableDuration(temp.getAcceptableDuration())
                                                    .name(temp.getName())
                                                    .value(temp.getValue())
                                                    .fictitious(temp.isFictitious())
                                                    .build())
                                            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll) : null)
                            .properties(attributes.getProperties())
                            .build();

                    result.add(sqlData);
                }
            }
        }

        return result;
    }
}
