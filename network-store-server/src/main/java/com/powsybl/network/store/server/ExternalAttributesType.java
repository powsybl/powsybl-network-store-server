/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server;

/**
 * @author Antoine Bouhours <antoine.bouhours at rte-france.com>
 */
public enum ExternalAttributesType {
    TEMPORARY_LIMIT,
    PERMANENT_LIMIT,
    REACTIVE_CAPABILITY_CURVE_POINT,
    REGULATING_POINT,
    TAP_CHANGER_STEP,
    AREA_BOUNDARIES
}
