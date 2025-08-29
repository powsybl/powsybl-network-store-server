/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@Builder
@Schema(description = "Operational limits group properties attributes")
public class OperationalLimitsGroupPropertiesAttributes {

    @JsonIgnore
    @Schema(description = "Operational limits group Id")
    private String operationalLimitsGroupId;

    @JsonIgnore
    @Schema(description = "side")
    private Integer side;

    @Schema(description = "properties")
    private Map<String, String> properties;
}
