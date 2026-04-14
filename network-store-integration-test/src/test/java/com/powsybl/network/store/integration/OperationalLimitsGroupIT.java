/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.integration;

import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.OperationalLimitsGroup;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerFactory;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.RestClientImpl;
import com.powsybl.network.store.server.NetworkStoreApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;

import java.util.List;

import static com.powsybl.network.store.integration.TestUtils.createNetworkStoreService;

/**
 * @author Etienne Lesot <etienne.lesot at rte-france.com>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextHierarchy({@ContextConfiguration(classes = {NetworkStoreApplication.class, NetworkStoreService.class, RestClientImpl.class})})
class OperationalLimitsGroupIT {

    @AfterEach
    void tearDown() {
        try (NetworkStoreService service = createNetworkStoreService(randomServerPort)) {
            service.deleteAllNetworks();
        }
    }

    @LocalServerPort
    private int randomServerPort;

    @Test
    void testDeleteOperationalLimitsGroup() {
        try (NetworkStoreService service = createNetworkStoreService(randomServerPort)) {
            Network network = FourSubstationsNodeBreakerFactory.create(service.getNetworkFactory());
            Line lineS3S4 = network.getLine("LINE_S3S4");
            // create 2 operational limits group
            lineS3S4.newOperationalLimitsGroup2("TEST")
                    .newCurrentLimits()
                    .setPermanentLimit(300)
                    .beginTemporaryLimit()
                    .setName("IST")
                    .setValue(1000)
                    .setFictitious(false)
                    .setAcceptableDuration(Integer.MAX_VALUE)
                    .endTemporaryLimit()
                    .beginTemporaryLimit()
                    .setName("LD51")
                    .setValue(Double.MAX_VALUE)
                    .setAcceptableDuration(600)
                    .endTemporaryLimit()
                    .add();
            lineS3S4.newOperationalLimitsGroup1("TEST2")
                    .newCurrentLimits()
                    .setPermanentLimit(450)
                    .beginTemporaryLimit()
                    .setName("IST")
                    .setValue(1100)
                    .setFictitious(false)
                    .setAcceptableDuration(Integer.MAX_VALUE)
                    .endTemporaryLimit()
                    .beginTemporaryLimit()
                    .setName("LD61")
                    .setValue(Double.MAX_VALUE)
                    .setAcceptableDuration(1200)
                    .endTemporaryLimit()
                    .add();
            service.flush(network);
        }

        try (NetworkStoreService service = createNetworkStoreService(randomServerPort)) {
            Network network = service.getNetwork(service.getNetworkIds().keySet().iterator().next());
            Line lineS3S4 = network.getLine("LINE_S3S4");

            // delete directly olg TEST2 without loading
            lineS3S4.removeOperationalLimitsGroup1("TEST2");

            // check olg are created
            Assertions.assertTrue(lineS3S4.getSelectedOperationalLimitsGroup2().isPresent());
            Assertions.assertEquals("DEFAULT", lineS3S4.getSelectedOperationalLimitsGroup2().get().getId());
            List<OperationalLimitsGroup> operationalLimitsGroupList = lineS3S4.getOperationalLimitsGroups2().stream().toList();
            Assertions.assertEquals(2, operationalLimitsGroupList.size());
            List<OperationalLimitsGroup> operationalLimitsGroupList1 = lineS3S4.getOperationalLimitsGroups1().stream().toList();
            Assertions.assertEquals(1, operationalLimitsGroupList1.size());

            // remove them
            lineS3S4.removeOperationalLimitsGroup2("TEST");
            lineS3S4.removeOperationalLimitsGroup2("DEFAULT");
            service.flush(network);
        }

        try (NetworkStoreService service = createNetworkStoreService(randomServerPort)) {
            Network network = service.getNetwork(service.getNetworkIds().keySet().iterator().next());
            Line lineS3S4 = network.getLine("LINE_S3S4");
            Assertions.assertTrue(lineS3S4.getOperationalLimitsGroup1("TEST2").isEmpty());
            Assertions.assertTrue(lineS3S4.getOperationalLimitsGroup2("TEST").isEmpty());
            Assertions.assertTrue(lineS3S4.getOperationalLimitsGroup2("DEFAULT").isEmpty());
            Assertions.assertTrue(lineS3S4.getSelectedOperationalLimitsGroup2().isEmpty());
        }
    }
}
