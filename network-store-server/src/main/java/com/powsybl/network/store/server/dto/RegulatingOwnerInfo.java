/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server.dto;

import com.powsybl.network.store.model.RegulatingTapChangerType;
import com.powsybl.network.store.model.ResourceType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * @author Etienne Lesot <etienne.lesot at rte-france.com>
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RegulatingOwnerInfo extends OwnerInfo {

    private RegulatingTapChangerType regulatingTapChangerType;

    public RegulatingOwnerInfo(String equipmentId, ResourceType equipmentType, UUID networkUuid, int variantNum) {
        super(equipmentId, equipmentType, networkUuid, variantNum);
        this.regulatingTapChangerType = RegulatingTapChangerType.NONE;
    }

    public RegulatingOwnerInfo(String equipmentId, ResourceType equipmentType, RegulatingTapChangerType regulatingTapChangerType, UUID networkUuid, int variantNum) {
        super(equipmentId, equipmentType, networkUuid, variantNum);
        this.regulatingTapChangerType = regulatingTapChangerType;
    }
}
