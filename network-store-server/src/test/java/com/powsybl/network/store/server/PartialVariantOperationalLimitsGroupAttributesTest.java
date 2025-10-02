/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server;

import com.powsybl.network.store.model.OperationalLimitsGroupAttributes;
import com.powsybl.network.store.model.ResourceType;
import com.powsybl.network.store.server.dto.OperationalLimitsGroupOwnerInfo;
import com.powsybl.network.store.server.exceptions.UncheckedSqlException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.powsybl.network.store.server.utils.PartialVariantTestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Etienne Lesot <etienne.lesot at rte-france.com>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PartialVariantOperationalLimitsGroupAttributesTest {

    @Autowired
    private NetworkStoreRepository networkStoreRepository;

    @Autowired
    private DataSource dataSource;

    @DynamicPropertySource
    static void makeTestDbSuffix(DynamicPropertyRegistry registry) {
        UUID uuid = UUID.randomUUID();
        registry.add("testDbSuffix", () -> uuid);
    }

    private static final UUID NETWORK_UUID = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");

    @Test
    void deleteOperationalLimitsGroupOnFullVariant() {
        String networkId = "network1";
        String lineId1 = "line1";
        String operationalLimitsGroupId1Side1 = "olg11";
        String operationalLimitsGroupId2Side1 = "olg12";
        String operationalLimitsGroupId1Side2 = "olg21";
        createFullVariantNetwork(networkStoreRepository, NETWORK_UUID, networkId, 0, "variant0");
        createLineWithLimits(networkStoreRepository, NETWORK_UUID, 0, lineId1, "vl1", "vl2",
                List.of(operationalLimitsGroupId1Side1, operationalLimitsGroupId2Side1),
                List.of(operationalLimitsGroupId1Side2));
        networkStoreRepository.removeOperationalLimitsGroupAttributes(NETWORK_UUID, 0, ResourceType.LINE, Map.of(lineId1, Map.of(1, Set.of(operationalLimitsGroupId1Side1))));

        assertEquals(1, getOperationalLimitsGroupForLineForVariant(NETWORK_UUID, 0).get(lineId1).get(1).size());
        assertNull(getOperationalLimitsGroupForLineForVariant(NETWORK_UUID, 0).get(lineId1).get(1).get(operationalLimitsGroupId1Side1));
        assertNotNull(getOperationalLimitsGroupForLineForVariant(NETWORK_UUID, 0).get(lineId1).get(1).get(operationalLimitsGroupId2Side1));
        assertTrue(getTombstonedOperationalLimitsGroups(NETWORK_UUID, 0).isEmpty());
    }

    @Test
    void deleteOperationalLimitsGroupOnPartialVariant() {
        String networkId = "network1";
        String lineId1 = "line1";
        String operationalLimitsGroupId1Side1 = "olg11";
        String operationalLimitsGroupId2Side1 = "olg12";
        String operationalLimitsGroupId1Side2 = "olg21";
        createNetwork(networkStoreRepository, NETWORK_UUID, networkId, 1, "variant1", 0);
        createLineWithLimits(networkStoreRepository, NETWORK_UUID, 1, lineId1, "vl1", "vl2",
                List.of(operationalLimitsGroupId1Side1, operationalLimitsGroupId2Side1),
                List.of(operationalLimitsGroupId1Side2));
        networkStoreRepository.removeOperationalLimitsGroupAttributes(NETWORK_UUID, 1, ResourceType.LINE, Map.of(lineId1, Map.of(1, Set.of(operationalLimitsGroupId1Side1))));

        assertEquals(1, getOperationalLimitsGroupForLineForVariant(NETWORK_UUID, 1).get(lineId1).get(1).size());
        assertNull(getOperationalLimitsGroupForLineForVariant(NETWORK_UUID, 1).get(lineId1).get(1).get(operationalLimitsGroupId1Side1));
        assertNotNull(getOperationalLimitsGroupForLineForVariant(NETWORK_UUID, 1).get(lineId1).get(1).get(operationalLimitsGroupId2Side1));
        assertEquals(1, getTombstonedOperationalLimitsGroups(NETWORK_UUID, 1).size());
        assertTrue(getTombstonedOperationalLimitsGroups(NETWORK_UUID, 1).contains(new
                OperationalLimitsGroupOwnerInfo(lineId1, ResourceType.LINE, NETWORK_UUID, 1, operationalLimitsGroupId1Side1, 1)));
    }

    private Map<String, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> getOperationalLimitsGroupForLineForVariant(UUID networkUuid, int variantNum) {
        return networkStoreRepository.getAllOperationalLimitsGroupAttributesByResourceType(networkUuid, variantNum, ResourceType.LINE);
    }

    private Set<OperationalLimitsGroupOwnerInfo> getTombstonedOperationalLimitsGroups(UUID networkUuid, int variantNum) {
        try (var connection = dataSource.getConnection()) {
            return networkStoreRepository.getLimitsHandler().getTombstonedOperationalLimitsGroups(connection, networkUuid, variantNum);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }
}
