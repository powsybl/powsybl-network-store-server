/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.tck;

import com.powsybl.iidm.network.tck.AbstractAreaTest;
import com.powsybl.network.store.server.NetworkStoreApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;

/**
 * @author Etienne Lesot <etienne.lesot at rte-france.com>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {"spring.config.location=classpath:application.yaml"})
@ContextHierarchy({@ContextConfiguration(classes = {NetworkStoreApplication.class})})
public class AreaIt extends AbstractAreaTest {
    @Override
    public void mergeAndFlatten() {
        // merge is not implemented
    }

    @Override
    public void throwAddVoltageLevelOtherNetwork() {
        // creation of subnetwork needed
    }

    @Override
    public void throwBoundaryOtherNetwork() {
        // creation of subnetwork needed
    }

    @Override
    public void removeEquipmentRemovesAreaBoundaryMergeAndDetach() {
        // merge is not implemented
    }
}
