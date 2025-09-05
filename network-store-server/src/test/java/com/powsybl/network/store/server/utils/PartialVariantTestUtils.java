/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server.utils;

import com.powsybl.network.store.model.*;
import com.powsybl.network.store.server.NetworkStoreRepository;

import java.util.*;

/**
 * @author Antoine Bouhours <antoine.bouhours at rte-france.com>
 */
public final class PartialVariantTestUtils {
    private PartialVariantTestUtils() throws IllegalAccessException {
        throw new IllegalAccessException("Utility class can not be initialize.");
    }

    public static Resource<LineAttributes> createLine(NetworkStoreRepository repository, UUID networkUuid, int variantNum, String lineId, String voltageLevel1, String voltageLevel2) {
        Resource<LineAttributes> line = Resource.lineBuilder()
                .id(lineId)
                .variantNum(variantNum)
                .attributes(LineAttributes.builder()
                        .voltageLevelId1(voltageLevel1)
                        .voltageLevelId2(voltageLevel2)
                        .build())
                .build();
        repository.createLines(networkUuid, List.of(line));
        return line;
    }

    public static Resource<LineAttributes> createLineWithLimits(NetworkStoreRepository repository, UUID networkUuid, int variantNum,
                                                                String lineId, String voltageLevel1, String voltageLevel2, List<String> olgSide1, List<String> olgSide2) {
        Map<String, OperationalLimitsGroupAttributes> operationalLimitsGroups1 = new HashMap<>();
        olgSide1.forEach(olgId -> operationalLimitsGroups1.put(olgId, createOperationalLimitsGroup(olgId)));
        Map<String, OperationalLimitsGroupAttributes> operationalLimitsGroups2 = new HashMap<>();
        olgSide2.forEach(olgId -> operationalLimitsGroups2.put(olgId, createOperationalLimitsGroup(olgId)));
        Resource<LineAttributes> line = Resource.lineBuilder()
                .id(lineId)
                .variantNum(variantNum)
                .attributes(LineAttributes.builder()
                        .voltageLevelId1(voltageLevel1)
                        .voltageLevelId2(voltageLevel2)
                        .operationalLimitsGroups1(operationalLimitsGroups1)
                        .operationalLimitsGroups2(operationalLimitsGroups2)
                        .build())
                .build();
        repository.createLines(networkUuid, List.of(line));
        return line;
    }

    private static OperationalLimitsGroupAttributes createOperationalLimitsGroup(String id) {
        TreeMap<Integer, TemporaryLimitAttributes> temporaryLimits = new TreeMap<>();
        temporaryLimits.put(600, TemporaryLimitAttributes.builder().value(20).acceptableDuration(600).name("10 minutes").build());
        temporaryLimits.put(60, TemporaryLimitAttributes.builder().value(30).acceptableDuration(60).name("1 minute").build());
        return OperationalLimitsGroupAttributes.builder()
                .id(id)
                .currentLimits(LimitsAttributes.builder()
                        .permanentLimit(10)
                        .temporaryLimits(temporaryLimits)
                        .build())
                .properties(Map.of("property1", "valueProperty1"))
                .build();
    }

    public static void createLineAndLoad(NetworkStoreRepository repository, UUID networkUuid, int variantNum, String loadId, String lineId, String voltageLevel1, String voltageLevel2) {
        createLine(repository, networkUuid, variantNum, lineId, voltageLevel1, voltageLevel2);
        Resource<LoadAttributes> load1 = buildLoad(loadId, variantNum, voltageLevel1);
        repository.createLoads(networkUuid, List.of(load1));
    }

    public static Resource<LoadAttributes> buildLoad(String loadId, int variantNum, String voltageLevel) {
        return Resource.loadBuilder()
                .id(loadId)
                .variantNum(variantNum)
                .attributes(LoadAttributes.builder()
                        .voltageLevelId(voltageLevel)
                        .build())
                .build();
    }

    public static void createNetwork(NetworkStoreRepository repository, UUID networkUuid, String networkId, int variantNum, String variantId, int fullVariantNum) {
        Resource<NetworkAttributes> network1 = Resource.networkBuilder()
                .id(networkId)
                .variantNum(variantNum)
                .attributes(NetworkAttributes.builder()
                        .uuid(networkUuid)
                        .variantId(variantId)
                        .fullVariantNum(fullVariantNum)
                        .build())
                .build();
        repository.createNetworks(List.of(network1));
    }

    public static void createFullVariantNetwork(NetworkStoreRepository repository, UUID networkUuid, String networkId, int variantNum, String variantId) {
        createNetwork(repository, networkUuid, networkId, variantNum, variantId, -1);
    }
}
