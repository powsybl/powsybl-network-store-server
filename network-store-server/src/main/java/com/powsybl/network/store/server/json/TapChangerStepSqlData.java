/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server.json;

import com.powsybl.network.store.model.TapChangerStepAttributes;
import com.powsybl.network.store.model.TapChangerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * @author Etienne Lesot <etienne.lesot at rte-france.com>
 */
@ToString
@Builder
@AllArgsConstructor
@Getter
public class TapChangerStepSqlData {

    private double rho;
    private double r;
    private double x;
    private double g;
    private double b;
    private Double alpha;
    private Integer index;
    private Integer side;
    private TapChangerType type;

    public TapChangerStepSqlData() {
        // empty constructor for Jackson
    }

    public static TapChangerStepSqlData of(TapChangerStepAttributes tapChangerStepAttributes) {
        return TapChangerStepSqlData.builder()
            .rho(tapChangerStepAttributes.getRho())
            .r(tapChangerStepAttributes.getR())
            .x(tapChangerStepAttributes.getX())
            .g(tapChangerStepAttributes.getG())
            .b(tapChangerStepAttributes.getB())
            .alpha(tapChangerStepAttributes.getAlpha())
            .index(tapChangerStepAttributes.getIndex())
            .side(tapChangerStepAttributes.getSide())
            .type(tapChangerStepAttributes.getType())
            .build();
    }

    public TapChangerStepAttributes toTapChangerStepAttributes() {
        return TapChangerStepAttributes.builder()
            .rho(rho)
            .r(r)
            .x(x)
            .g(g)
            .b(b)
            .alpha(alpha)
            .index(index)
            .side(side)
            .type(type)
            .build();
    }
}
