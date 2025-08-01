/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server.json;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@ToString
@NoArgsConstructor
@Getter
@Setter
public class LimitsGroupAttributesSqlData {
    private double currentLimitsPermanentLimit = Double.NaN;
    private List<TemporaryLimitInfosSqlData> currentLimitsTemporaryLimits = new ArrayList<>();
    private double apparentPowerLimitsPermanentLimit = Double.NaN;
    private List<TemporaryLimitInfosSqlData> apparentPowerLimitsTemporaryLimits = new ArrayList<>();
    private double activePowerLimitsPermanentLimit = Double.NaN;
    private List<TemporaryLimitInfosSqlData> activePowerLimitsTemporaryLimits = new ArrayList<>();
    private Map<String, String> properties = new HashMap<>();
}
