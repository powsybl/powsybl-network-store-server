/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server.json;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@ToString
@AllArgsConstructor
@Getter
public class TemporaryLimitInfosSqlData {
    private String name;
    private double value;
    private Integer acceptableDuration;
    private boolean fictitious;
}
