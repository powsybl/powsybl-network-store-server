/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.tck;

import com.powsybl.iidm.network.tck.AbstractMultiVariantNetworkTest;
import com.powsybl.network.store.server.NetworkStoreApplication;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ContextHierarchy({
    @ContextConfiguration(classes = {NetworkStoreApplication.class})
    })
@TestPropertySource(properties = { "spring.config.location=classpath:application.yaml" })
class MultiVariantNetworkIT extends AbstractMultiVariantNetworkTest {

    @Test
    @Override
    public void multiThreadTest() {
        // FIXME delete this test when multi-thread access is supported
    }

    @Test
    @Override
    public void multiVariantTopologyTest() {
        // FIXME delete this test when multi-thread access is supported
    }

    @Test
    @Override
    public void variantNotSetTest() {
        // FIXME delete this test when multi-thread access is supported
    }

    @Test
    @Override
    public void variantSetTest() {
        // FIXME delete this test when multi-thread access is supported
    }

}
