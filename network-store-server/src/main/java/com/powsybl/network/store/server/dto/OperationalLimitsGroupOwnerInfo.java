/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server.dto;

import com.powsybl.network.store.model.ResourceType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class OperationalLimitsGroupOwnerInfo extends OwnerInfo {
    private String operationalLimitsGroupId;
    private Integer side;

    public OperationalLimitsGroupOwnerInfo(String equipmentId,
                                           ResourceType equipmentType,
                                           UUID networkUuid,
                                           int variantNum,
                                           String operationalLimitsGroupId,
                                           Integer side) {
        super(equipmentId, equipmentType, networkUuid, variantNum);
        this.operationalLimitsGroupId = operationalLimitsGroupId;
        this.side = side;
    }
}
