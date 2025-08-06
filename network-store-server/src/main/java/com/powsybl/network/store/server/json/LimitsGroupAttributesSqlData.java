/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server.json;

import com.powsybl.network.store.model.OperationalLimitsGroupAttributes;
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
    private OperationalLimitsGroupAttributes operationalLimitsGroupAttributes;

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
                            .operationalLimitsGroupAttributes(attributes)
                            .build();

                    result.add(sqlData);
                }
            }
        }

        return result;
    }
}
