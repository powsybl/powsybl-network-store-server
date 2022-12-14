/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "MergedXnode extension attributes")
public class MergedXnodeAttributes {

    @Schema(description = "r divider position 1 -> 2")
    private Double rdp;

    @Schema(description = "x divider position 1 -> 2")
    private Double xdp;

    @Schema(description = "Xnode active power consumption in MW of side 1")
    private Double xnodeP1;

    @Schema(description = "Xnode reactive power consumption in MW of side 1")
    private Double xnodeQ1;

    @Schema(description = "Xnode active power consumption in MW of side 2")
    private Double xnodeP2;

    @Schema(description = "Xnode reactive power consumption in MW of side 2")
    private Double xnodeQ2;

    @Schema(description = "line name of side 1")
    private String line1Name;

    @Schema(description = "line name of side 2")
    private String line2Name;

    @Schema(description = "UCTE Xnode code corresponding to this line")
    private String code;
}
