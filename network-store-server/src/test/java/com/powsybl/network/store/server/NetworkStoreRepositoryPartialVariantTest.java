/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.LimitType;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.network.extensions.ActivePowerControl;
import com.powsybl.iidm.network.extensions.OperatingStatus;
import com.powsybl.network.store.model.*;
import com.powsybl.network.store.server.dto.LimitsInfos;
import com.powsybl.network.store.server.dto.OwnerInfo;
import com.powsybl.network.store.server.dto.PermanentLimitAttributes;
import com.powsybl.network.store.server.exceptions.UncheckedSqlException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.function.BiConsumer;

import static com.powsybl.network.store.server.Mappings.*;
import static com.powsybl.network.store.server.QueryCatalog.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Antoine Bouhours <antoine.bouhours at rte-france.com>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class NetworkStoreRepositoryPartialVariantTest {

    @Autowired
    private Mappings mappings;

    @Autowired
    private ExtensionHandler extensionHandler;

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
    void getVariantsInfosContainsVariantModeSrcVariantNum() {
        String networkId = "network1";
        int variantNum = 0;
        String variantId = VariantManagerConstants.INITIAL_VARIANT_ID;
        VariantMode variantMode = VariantMode.PARTIAL;
        int srcVariantNum = -1;
        Resource<NetworkAttributes> network1 = Resource.networkBuilder()
                .id(networkId)
                .variantNum(variantNum)
                .attributes(NetworkAttributes.builder()
                        .uuid(NETWORK_UUID)
                        .variantId(variantId)
                        .variantMode(variantMode)
                        .srcVariantNum(srcVariantNum)
                        .build())
                .build();
        networkStoreRepository.createNetworks(List.of(network1));

        List<VariantInfos> variantsInfos = networkStoreRepository.getVariantsInfos(NETWORK_UUID);

        assertEquals(1, variantsInfos.size());
        VariantInfos variantInfo = variantsInfos.get(0);
        assertEquals(variantId, variantInfo.getId());
        assertEquals(variantNum, variantInfo.getNum());
        assertEquals(variantMode, variantInfo.getVariantMode());
        assertEquals(srcVariantNum, variantInfo.getSrcVariantNum());
    }

    @Test
    void getNetworkContainsVariantModeSrcVariantNum() {
        String networkId = "network1";
        int variantNum = 0;
        String variantId = VariantManagerConstants.INITIAL_VARIANT_ID;
        VariantMode variantMode = VariantMode.PARTIAL;
        int srcVariantNum = -1;
        Resource<NetworkAttributes> network1 = Resource.networkBuilder()
                .id(networkId)
                .variantNum(variantNum)
                .attributes(NetworkAttributes.builder()
                        .uuid(NETWORK_UUID)
                        .variantId(variantId)
                        .variantMode(variantMode)
                        .srcVariantNum(srcVariantNum)
                        .build())
                .build();
        networkStoreRepository.createNetworks(List.of(network1));

        Optional<Resource<NetworkAttributes>> networkAttributesOpt = networkStoreRepository.getNetwork(NETWORK_UUID, variantNum);

        assertTrue(networkAttributesOpt.isPresent());
        Resource<NetworkAttributes> networkAttributes = networkAttributesOpt.get();
        assertEquals(networkId, networkAttributes.getId());
        assertEquals(variantNum, networkAttributes.getVariantNum());
        assertEquals(NETWORK_UUID, networkAttributes.getAttributes().getUuid());
        assertEquals(variantId, networkAttributes.getAttributes().getVariantId());
        assertEquals(variantMode, networkAttributes.getAttributes().getVariantMode());
        assertEquals(srcVariantNum, networkAttributes.getAttributes().getSrcVariantNum());
    }

    @Test
    void updateNetworkUpdatesVariantModeSrcVariantNum() {
        String networkId = "network1";
        int variantNum = 3;
        String variantId = VariantManagerConstants.INITIAL_VARIANT_ID;
        Resource<NetworkAttributes> network1 = Resource.networkBuilder()
                .id(networkId)
                .variantNum(variantNum)
                .attributes(NetworkAttributes.builder()
                        .uuid(NETWORK_UUID)
                        .variantId(variantId)
                        .variantMode(VariantMode.PARTIAL)
                        .srcVariantNum(-1)
                        .build())
                .build();
        networkStoreRepository.createNetworks(List.of(network1));

        VariantMode variantMode = VariantMode.FULL;
        int srcVariantNum = 2;
        network1 = Resource.networkBuilder()
                .id(networkId)
                .variantNum(variantNum)
                .attributes(NetworkAttributes.builder()
                        .uuid(NETWORK_UUID)
                        .variantId(variantId)
                        .variantMode(variantMode)
                        .srcVariantNum(srcVariantNum)
                        .build())
                .build();
        networkStoreRepository.updateNetworks(List.of(network1));

        Optional<Resource<NetworkAttributes>> networkAttributesOpt = networkStoreRepository.getNetwork(NETWORK_UUID, variantNum);
        assertTrue(networkAttributesOpt.isPresent());
        Resource<NetworkAttributes> networkAttributes = networkAttributesOpt.get();
        assertEquals(networkId, networkAttributes.getId());
        assertEquals(variantNum, networkAttributes.getVariantNum());
        assertEquals(NETWORK_UUID, networkAttributes.getAttributes().getUuid());
        assertEquals(variantId, networkAttributes.getAttributes().getVariantId());
        assertEquals(variantMode, networkAttributes.getAttributes().getVariantMode());
        assertEquals(srcVariantNum, networkAttributes.getAttributes().getSrcVariantNum());
    }

    @Test
    void cloneAllVariantsOfNetwork() {
        String networkId = "network1";
        String loadId1 = "load1";
        String lineId1 = "line1";
        String loadId2 = "load2";
        String lineId2 = "line2";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL);
        createLineAndLoad(0, loadId1, lineId1, "vl1", "vl2");
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);
        createLineAndLoad(1, loadId2, lineId2, "vl1", "vl2");
        UUID targetNetworkUuid = UUID.fromString("0dd45074-009d-49b8-877f-8ae648a8e8b4");

        networkStoreRepository.cloneNetwork(targetNetworkUuid, NETWORK_UUID, List.of("variant0", "variant1"));

        // Check variant 0
        Optional<Resource<NetworkAttributes>> networkAttributesOptVariant0 = networkStoreRepository.getNetwork(targetNetworkUuid, 0);
        assertTrue(networkAttributesOptVariant0.isPresent());
        Resource<NetworkAttributes> networkAttributesVariant0 = networkAttributesOptVariant0.get();
        assertEquals(networkId, networkAttributesVariant0.getId());
        assertEquals(0, networkAttributesVariant0.getVariantNum());
        assertEquals(targetNetworkUuid, networkAttributesVariant0.getAttributes().getUuid());
        assertEquals("variant0", networkAttributesVariant0.getAttributes().getVariantId());
        assertEquals(VariantMode.PARTIAL, networkAttributesVariant0.getAttributes().getVariantMode());
        assertEquals(-1, networkAttributesVariant0.getAttributes().getSrcVariantNum());
        assertEquals(List.of(loadId1, lineId1), getStoredIdentifiableIdsInVariant(targetNetworkUuid, 0));

        // Check variant 1
        Optional<Resource<NetworkAttributes>> networkAttributesOptVariant1 = networkStoreRepository.getNetwork(targetNetworkUuid, 1);
        assertTrue(networkAttributesOptVariant1.isPresent());
        Resource<NetworkAttributes> networkAttributesVariant1 = networkAttributesOptVariant1.get();
        assertEquals(networkId, networkAttributesVariant1.getId());
        assertEquals(1, networkAttributesVariant1.getVariantNum());
        assertEquals(targetNetworkUuid, networkAttributesVariant1.getAttributes().getUuid());
        assertEquals("variant1", networkAttributesVariant1.getAttributes().getVariantId());
        assertEquals(VariantMode.PARTIAL, networkAttributesVariant1.getAttributes().getVariantMode());
        assertEquals(0, networkAttributesVariant1.getAttributes().getSrcVariantNum());
        assertEquals(List.of(loadId2, lineId2), getStoredIdentifiableIdsInVariant(targetNetworkUuid, 1));
    }

    @Test
    void cloneAllVariantsOfNetworkWithExternalAttributes() {
        String networkId = "network1";
        String lineId1 = "line1";
        String lineId2 = "line2";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL);
        createLineWithExternalAttributes(0, lineId1);
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);
        createLineWithExternalAttributes(1, lineId2);
        UUID targetNetworkUuid = UUID.fromString("0dd45074-009d-49b8-877f-8ae648a8e8b4");

        networkStoreRepository.cloneNetwork(targetNetworkUuid, NETWORK_UUID, List.of("variant0", "variant1"));

        // Check variant 0
        assertEquals(List.of(lineId1), getStoredIdentifiableIdsInVariant(targetNetworkUuid, 0));
        verifyExternalAttributes(lineId1, 0, targetNetworkUuid);

        // Check variant 1
        assertEquals(List.of(lineId2), getStoredIdentifiableIdsInVariant(targetNetworkUuid, 1));
        verifyExternalAttributes(lineId2, 1, targetNetworkUuid);
    }

    private void verifyExternalAttributes(String lineId, int variantNum, UUID networkUuid) {
        OwnerInfo ownerInfo = new OwnerInfo(lineId, ResourceType.LINE, networkUuid, variantNum);
        TableMapping tableMapping = mappings.getLineMappings();

        // Tap Changer Steps
        List<TapChangerStepAttributes> tapChangerSteps = networkStoreRepository.getTapChangerSteps(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, lineId, tableMapping).get(ownerInfo);
        assertEquals(2, tapChangerSteps.size());
        assertEquals(1.0, tapChangerSteps.get(0).getRho());
        assertEquals(2.0, tapChangerSteps.get(1).getRho());

        // Temporary Limits
        List<TemporaryLimitAttributes> temporaryLimits = networkStoreRepository.getTemporaryLimits(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, lineId, tableMapping).get(ownerInfo);
        assertEquals(2, temporaryLimits.size());
        assertEquals(100, temporaryLimits.get(0).getAcceptableDuration());
        assertEquals(200, temporaryLimits.get(1).getAcceptableDuration());

        // Permanent Limits
        List<PermanentLimitAttributes> permanentLimits = networkStoreRepository.getPermanentLimits(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, lineId, tableMapping).get(ownerInfo);
        assertEquals(2, permanentLimits.size());
        assertEquals(2.5, permanentLimits.get(0).getValue());
        assertEquals(2.6, permanentLimits.get(1).getValue());

        // Reactive Capability Curve Points
        List<ReactiveCapabilityCurvePointAttributes> curvePoints = networkStoreRepository.getReactiveCapabilityCurvePoints(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, lineId, tableMapping).get(ownerInfo);
        assertEquals(2, curvePoints.size());
        assertEquals(-100.0, curvePoints.get(0).getMinQ());
        assertEquals(30.0, curvePoints.get(1).getMaxQ());

        // Regulating Points
        RegulatingPointAttributes regulatingPoint = networkStoreRepository.getRegulatingPoints(networkUuid, variantNum, ResourceType.LINE, tableMapping).get(ownerInfo);
        assertNotNull(regulatingPoint);
        assertEquals("regulationMode", regulatingPoint.getRegulationMode());

        // Extensions
        Map<String, ExtensionAttributes> extensions = networkStoreRepository.getAllExtensionsAttributesByIdentifiableId(networkUuid, variantNum, lineId);
        assertEquals(2, extensions.size());
        assertTrue(extensions.containsKey("activePowerControl"));
        assertTrue(extensions.containsKey("operatingStatus"));
        ActivePowerControlAttributes activePowerControl = (ActivePowerControlAttributes) extensions.get("activePowerControl");
        assertEquals(6.0, activePowerControl.getDroop());
        OperatingStatusAttributes operatingStatus = (OperatingStatusAttributes) extensions.get("operatingStatus");
        assertEquals("test12", operatingStatus.getOperatingStatus());
    }

    private void verifyEmptyExternalAttributes(String lineId, int variantNum, UUID networkUuid) {
        try (Connection connection = dataSource.getConnection()) {
            // Tap Changer Steps
            assertTrue(networkStoreRepository.getTapChangerStepsForVariant(connection, networkUuid, variantNum, EQUIPMENT_ID_COLUMN, lineId, variantNum).isEmpty());

            // Temporary Limits
            assertTrue(networkStoreRepository.getTemporaryLimitsForVariant(connection, networkUuid, variantNum, EQUIPMENT_ID_COLUMN, lineId, variantNum).isEmpty());

            // Permanent Limits
            assertTrue(networkStoreRepository.getPermanentLimitsForVariant(connection, networkUuid, variantNum, EQUIPMENT_ID_COLUMN, lineId, variantNum).isEmpty());

            // Reactive Capability Curve Points
            assertTrue(networkStoreRepository.getReactiveCapabilityCurvePointsForVariant(connection, networkUuid, variantNum, EQUIPMENT_ID_COLUMN, lineId, variantNum).isEmpty());

            // Regulating Points
            OwnerInfo ownerInfo = new OwnerInfo(lineId, ResourceType.LINE, networkUuid, variantNum);
            assertNull(networkStoreRepository.getRegulatingPointsForVariant(connection, networkUuid, variantNum, ResourceType.LINE, variantNum).get(ownerInfo));

            // Extensions
            assertTrue(extensionHandler.getAllExtensionsAttributesByIdentifiableId(connection, networkUuid, variantNum, lineId).isEmpty());
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private void createLineWithExternalAttributes(int variantNum, String lineId) {
        Resource<LineAttributes> line1 = Resource.lineBuilder()
                .id(lineId)
                .variantNum(variantNum)
                .attributes(LineAttributes.builder()
                        .voltageLevelId1("vl1")
                        .voltageLevelId2("vl2")
                        .build())
                .build();
        networkStoreRepository.createLines(NETWORK_UUID, List.of(line1));
        // Tap changer steps
        OwnerInfo ownerInfo = new OwnerInfo(lineId, ResourceType.LINE, NETWORK_UUID, variantNum);
        TapChangerStepAttributes ratioStepA1 = buildTapChangerStepAttributes(1., 0);
        TapChangerStepAttributes ratioStepA2 = buildTapChangerStepAttributes(2., 1);
        networkStoreRepository.insertTapChangerSteps(Map.of(ownerInfo, List.of(ratioStepA1, ratioStepA2)));
        // Temporary limits
        TemporaryLimitAttributes templimitA = TemporaryLimitAttributes.builder()
                .side(2)
                .acceptableDuration(100)
                .limitType(LimitType.CURRENT)
                .operationalLimitsGroupId("group1")
                .build();
        TemporaryLimitAttributes templimitB = TemporaryLimitAttributes.builder()
                .side(2)
                .acceptableDuration(200)
                .limitType(LimitType.CURRENT)
                .operationalLimitsGroupId("group1")
                .build();
        List<TemporaryLimitAttributes> temporaryLimitAttributes = List.of(templimitA, templimitB);
        // Permanent limits
        PermanentLimitAttributes permlimitA1 = PermanentLimitAttributes.builder()
                .side(1)
                .limitType(LimitType.CURRENT)
                .operationalLimitsGroupId("group1")
                .value(2.5)
                .build();
        PermanentLimitAttributes permlimitA2 = PermanentLimitAttributes.builder()
                .side(2)
                .limitType(LimitType.CURRENT)
                .operationalLimitsGroupId("group1")
                .value(2.6)
                .build();
        List<PermanentLimitAttributes> permanentLimitAttributes = List.of(permlimitA1, permlimitA2);
        LimitsInfos limitsInfos = new LimitsInfos();
        limitsInfos.setTemporaryLimits(temporaryLimitAttributes);
        limitsInfos.setPermanentLimits(permanentLimitAttributes);
        networkStoreRepository.insertTemporaryLimits(Map.of(ownerInfo, limitsInfos));
        networkStoreRepository.insertPermanentLimits(Map.of(ownerInfo, limitsInfos));
        // Reactive capability curve points
        ReactiveCapabilityCurvePointAttributes curvePointA = ReactiveCapabilityCurvePointAttributes.builder()
                .minQ(-100.)
                .maxQ(100.)
                .p(0.)
                .build();
        ReactiveCapabilityCurvePointAttributes curvePointB = ReactiveCapabilityCurvePointAttributes.builder()
                .minQ(10.)
                .maxQ(30.)
                .p(20.)
                .build();
        networkStoreRepository.insertReactiveCapabilityCurvePoints(Map.of(ownerInfo, List.of(curvePointA, curvePointB)));
        // Regulating points
        RegulatingPointAttributes regulatingPointAttributes = RegulatingPointAttributes.builder()
                .regulationMode("regulationMode")
                .regulatingTerminal(TerminalRefAttributes.builder().connectableId("idEq").side("ONE").build())
                .localTerminal(TerminalRefAttributes.builder().connectableId("id").build())
                .build();
        networkStoreRepository.insertRegulatingPoints(Map.of(ownerInfo, regulatingPointAttributes));
        // Extensions
        Map<String, ExtensionAttributes> extensionAttributes = Map.of("activePowerControl", ActivePowerControlAttributes.builder().droop(6.0).participate(true).participationFactor(1.5).build(),
                "operatingStatus", OperatingStatusAttributes.builder().operatingStatus("test12").build());
        createExtensions(Map.of(ownerInfo, extensionAttributes));
    }

    @Test
    void cloneAllVariantsOfNetworkWithTombstonedIdentifiable() {
        String networkId = "network1";
        String loadId1 = "load1";
        String lineId1 = "line1";
        String loadId2 = "load2";
        String lineId2 = "line2";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL);
        createLineAndLoad(0, loadId1, lineId1, "vl1", "vl2");
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);
        createLineAndLoad(1, loadId2, lineId2, "vl1", "vl2");
        networkStoreRepository.deleteIdentifiable(NETWORK_UUID, 0, lineId1, LINE_TABLE);
        networkStoreRepository.deleteIdentifiable(NETWORK_UUID, 1, loadId1, LOAD_TABLE);
        networkStoreRepository.deleteIdentifiable(NETWORK_UUID, 1, loadId2, LOAD_TABLE);
        UUID targetNetworkUuid = UUID.fromString("0dd45074-009d-49b8-877f-8ae648a8e8b4");

        networkStoreRepository.cloneNetwork(targetNetworkUuid, NETWORK_UUID, List.of("variant0", "variant1"));

        assertEquals(List.of(loadId1), getStoredIdentifiableIdsInVariant(targetNetworkUuid, 0));
        assertEquals(List.of(lineId2), getStoredIdentifiableIdsInVariant(targetNetworkUuid, 1));
        assertEquals(Set.of(loadId1, loadId2), getTombstonedIdentifiableIdsInVariant(targetNetworkUuid, 1));
    }

    @Test
    void cloneAllVariantsOfNetworkWithTombstonedExtension() {
        String networkId = "network1";
        String lineId1 = "line1";
        String lineId2 = "line2";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL);
        OwnerInfo ownerInfo1 = new OwnerInfo(lineId1, ResourceType.LINE, NETWORK_UUID, 0);
        Map<String, ExtensionAttributes> extensionAttributesMap1 = buildExtensionAttributesMap(5.6, "status1");
        createExtensions(Map.of(ownerInfo1, extensionAttributesMap1));
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);
        OwnerInfo ownerInfo2 = new OwnerInfo(lineId2, ResourceType.LINE, NETWORK_UUID, 1);
        Map<String, ExtensionAttributes> extensionAttributesMap2 = buildExtensionAttributesMap(8.9, "status2");
        createExtensions(Map.of(ownerInfo2, extensionAttributesMap2));
        networkStoreRepository.removeExtensionAttributes(NETWORK_UUID, 0, lineId1, ActivePowerControl.NAME);
        networkStoreRepository.removeExtensionAttributes(NETWORK_UUID, 1, lineId1, OperatingStatus.NAME);
        networkStoreRepository.removeExtensionAttributes(NETWORK_UUID, 1, lineId2, ActivePowerControl.NAME);
        UUID targetNetworkUuid = UUID.fromString("0dd45074-009d-49b8-877f-8ae648a8e8b4");

        networkStoreRepository.cloneNetwork(targetNetworkUuid, NETWORK_UUID, List.of("variant0", "variant1"));

        assertEquals(Map.of(lineId1, Set.of(OperatingStatus.NAME), lineId2, Set.of(ActivePowerControl.NAME)), getTombstonedExtensions(targetNetworkUuid, 1));
    }

    @Test
    void clonePartialVariantInPartialMode() {
        String networkId = "network1";
        String loadId2 = "load2";
        String lineId2 = "line2";
        createPartialNetwork(networkId, 1, "variant1", VariantMode.PARTIAL, 0);
        createLineAndLoad(1, loadId2, lineId2, "vl1", "vl2");
        networkStoreRepository.deleteIdentifiable(NETWORK_UUID, 1, loadId2, LOAD_TABLE);

        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 1, 2, "variant2", VariantMode.PARTIAL);

        assertEquals(List.of(lineId2), getStoredIdentifiableIdsInVariant(NETWORK_UUID, 2));
        assertEquals(Set.of(loadId2), getTombstonedIdentifiableIdsInVariant(NETWORK_UUID, 2));
    }

    @Test
    void clonePartialVariantInPartialModeWithExternalAttributes() {
        String networkId = "network1";
        String lineId1 = "line1";
        createPartialNetwork(networkId, 1, "variant1", VariantMode.PARTIAL, 0);
        createLineWithExternalAttributes(1, lineId1);

        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 1, 2, "variant2", VariantMode.PARTIAL);

        verifyExternalAttributes(lineId1, 2, NETWORK_UUID);
    }

    @Test
    void cloneFullVariantInPartialMode() {
        String networkId = "network1";
        String loadId1 = "load1";
        String lineId1 = "line1";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL);
        createLineAndLoad(0, loadId1, lineId1, "vl1", "vl2");
        networkStoreRepository.deleteIdentifiable(NETWORK_UUID, 0, loadId1, LOAD_TABLE);

        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);

        assertTrue(getStoredIdentifiableIdsInVariant(NETWORK_UUID, 1).isEmpty());
        assertTrue(getTombstonedIdentifiableIdsInVariant(NETWORK_UUID, 1).isEmpty());
    }

    @Test
    void cloneFullVariantInPartialModeWithExternalAttributes() {
        String networkId = "network1";
        String lineId1 = "line1";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL);
        createLineWithExternalAttributes(1, lineId1);

        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);

        verifyEmptyExternalAttributes(lineId1, 2, NETWORK_UUID);
    }

    @Test
    void cloneFullVariantInFullMode() {
        String networkId = "network1";
        String loadId1 = "load1";
        String lineId1 = "line1";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.FULL);
        createLineAndLoad(0, loadId1, lineId1, "vl1", "vl2");

        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.FULL);

        assertEquals(List.of(loadId1, lineId1), getStoredIdentifiableIdsInVariant(NETWORK_UUID, 1));
    }

    @Test
    void cloneFullVariantInFullModeWithExternalAttributes() {
        String networkId = "network1";
        String lineId1 = "line1";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.FULL);
        createLineWithExternalAttributes(1, lineId1);

        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.FULL);

        verifyExternalAttributes(lineId1, 1, NETWORK_UUID);
    }

    @Test
    @Disabled("To implement")
    void clonePartialVariantInFullMode() {
        String networkId = "network1";
        String loadId1 = "load1";
        String lineId1 = "line1";
        String loadId2 = "load2";
        String lineId2 = "line2";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL);
        createLineAndLoad(0, loadId1, lineId1, "vl1", "vl2");
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);
        createLineAndLoad(1, loadId2, lineId2, "vl1", "vl2");
        networkStoreRepository.deleteIdentifiable(NETWORK_UUID, 0, loadId1, LOAD_TABLE);
        networkStoreRepository.deleteIdentifiable(NETWORK_UUID, 1, lineId1, LINE_TABLE);

        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 1, 2, "variant2", VariantMode.FULL);

        assertEquals(List.of(loadId2, lineId2), getStoredIdentifiableIdsInVariant(NETWORK_UUID, 2));
        assertTrue(getTombstonedIdentifiableIdsInVariant(NETWORK_UUID, 2).isEmpty());
    }

    @Test
    @Disabled("To implement")
    void clonePartialVariantInFullModeWithExternalAttributes() {
        String networkId = "network1";
        String lineId1 = "line1";
        String lineId2 = "line2";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL);
        createLineWithExternalAttributes(0, lineId1);
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);
        createLineWithExternalAttributes(1, lineId2);

        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 1, 2, "variant2", VariantMode.FULL);

        verifyExternalAttributes(lineId1, 2, NETWORK_UUID);
        verifyExternalAttributes(lineId2, 2, NETWORK_UUID);
    }

    @Test
    void getIdentifiableWithoutNetwork() {
        PowsyblException exception = assertThrows(PowsyblException.class, () -> networkStoreRepository.getIdentifiablesIds(NETWORK_UUID, 0));
        assertTrue(exception.getMessage().contains("Cannot retrieve source network attributes"));
        exception = assertThrows(PowsyblException.class, () -> networkStoreRepository.getIdentifiable(NETWORK_UUID, 0, "unknownId"));
        assertTrue(exception.getMessage().contains("Cannot retrieve source network attributes"));
        exception = assertThrows(PowsyblException.class, () -> networkStoreRepository.getLoad(NETWORK_UUID, 0, "unknownId"));
        assertTrue(exception.getMessage().contains("Cannot retrieve source network attributes"));
        exception = assertThrows(PowsyblException.class, () -> networkStoreRepository.getVoltageLevelLoads(NETWORK_UUID, 0, "unknownId"));
        assertTrue(exception.getMessage().contains("Cannot retrieve source network attributes"));
        exception = assertThrows(PowsyblException.class, () -> networkStoreRepository.getLoads(NETWORK_UUID, 0));
        assertTrue(exception.getMessage().contains("Cannot retrieve source network attributes"));
    }

    @Test
    void getIdentifiableFromPartialCloneWithNoIdentifiableInPartialVariant() {
        String networkId = "network1";
        String loadId1 = "load1";
        String lineId1 = "line1";
        String loadId2 = "load2";
        String lineId2 = "line2";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL);
        createLineAndLoad(0, loadId1, lineId1, "vl1", "vl2");
        createLineAndLoad(0, loadId2, lineId2, "vl3", "vl4");
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);

        List<String> identifiablesIds = networkStoreRepository.getIdentifiablesIds(NETWORK_UUID, 1);
        assertEquals(List.of(loadId1, loadId2, lineId1, lineId2), identifiablesIds);

        // Variant num of load1 must be 1
        Resource<LoadAttributes> expLoad1 = buildLoad(loadId1, 1, "vl1");
        assertTrue(networkStoreRepository.getIdentifiable(NETWORK_UUID, 1, "unknown").isEmpty());
        assertEquals(Optional.of(expLoad1), networkStoreRepository.getIdentifiable(NETWORK_UUID, 1, loadId1));
        assertTrue(networkStoreRepository.getLoad(NETWORK_UUID, 1, "unknown").isEmpty());
        assertEquals(Optional.of(expLoad1), networkStoreRepository.getLoad(NETWORK_UUID, 1, loadId1));

        assertTrue(networkStoreRepository.getVoltageLevelLoads(NETWORK_UUID, 1, "unknown").isEmpty());
        assertEquals(List.of(expLoad1), networkStoreRepository.getVoltageLevelLoads(NETWORK_UUID, 1, "vl1"));

        Resource<LoadAttributes> expLoad2 = buildLoad(loadId2, 1, "vl3");
        assertEquals(List.of(expLoad1, expLoad2), networkStoreRepository.getLoads(NETWORK_UUID, 1));
    }

    private void createSourceNetwork(String networkId, int variantNum, String variantId, VariantMode variantMode) {
        Resource<NetworkAttributes> network1 = Resource.networkBuilder()
                .id(networkId)
                .variantNum(variantNum)
                .attributes(NetworkAttributes.builder()
                        .uuid(NETWORK_UUID)
                        .variantId(variantId)
                        .variantMode(variantMode)
                        .srcVariantNum(-1)
                        .build())
                .build();
        networkStoreRepository.createNetworks(List.of(network1));
    }

    private void createLineAndLoad(int variantNum, String loadId, String lineId, String voltageLevel1, String voltageLevel2) {
        Resource<LineAttributes> line1 = Resource.lineBuilder()
                .id(lineId)
                .variantNum(variantNum)
                .attributes(LineAttributes.builder()
                        .voltageLevelId1(voltageLevel1)
                        .voltageLevelId2(voltageLevel2)
                        .build())
                .build();
        Resource<LoadAttributes> load1 = buildLoad(loadId, variantNum, voltageLevel1);
        networkStoreRepository.createLines(NETWORK_UUID, List.of(line1));
        networkStoreRepository.createLoads(NETWORK_UUID, List.of(load1));
    }

    private void createPartialNetwork(String networkId, int variantNum, String variantId, VariantMode variantMode, int srcVariantNum) {
        Resource<NetworkAttributes> network1 = Resource.networkBuilder()
                .id(networkId)
                .variantNum(variantNum)
                .attributes(NetworkAttributes.builder()
                        .uuid(NETWORK_UUID)
                        .variantId(variantId)
                        .variantMode(variantMode)
                        .srcVariantNum(srcVariantNum)
                        .build())
                .build();
        networkStoreRepository.createNetworks(List.of(network1));
    }

    @Test
    void getIdentifiableFromPartialClone() {
        String networkId = "network1";
        String loadId1 = "load1";
        String lineId1 = "line1";
        String loadId2 = "load2";
        String lineId2 = "line2";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL);
        createLineAndLoad(0, loadId1, lineId1, "vl1", "vl2");
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);
        createLineAndLoad(1, loadId2, lineId2, "vl1", "vl2");

        List<String> identifiablesIds = networkStoreRepository.getIdentifiablesIds(NETWORK_UUID, 1);
        assertEquals(List.of(loadId1, lineId1, loadId2, lineId2), identifiablesIds);

        Resource<LoadAttributes> expLoad1 = buildLoad(loadId1, 1, "vl1");
        Resource<LoadAttributes> expLoad2 = buildLoad(loadId2, 1, "vl1");
        assertEquals(Optional.of(expLoad2), networkStoreRepository.getIdentifiable(NETWORK_UUID, 1, loadId2));
        assertEquals(Optional.of(expLoad2), networkStoreRepository.getLoad(NETWORK_UUID, 1, loadId2));

        assertEquals(List.of(expLoad1, expLoad2), networkStoreRepository.getVoltageLevelLoads(NETWORK_UUID, 1, "vl1"));

        assertEquals(List.of(expLoad1, expLoad2), networkStoreRepository.getLoads(NETWORK_UUID, 1));
    }

    @Test
    void getIdentifiableFromPartialCloneWithUpdatedIdentifiables() {
        String networkId = "network1";
        String loadId1 = "load1";
        String lineId1 = "line1";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL);
        createLineAndLoad(0, loadId1, lineId1, "vl1", "vl2");
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);
        createLineAndLoad(1, loadId1, lineId1, "vl2", "vl3");

        List<String> identifiablesIds = networkStoreRepository.getIdentifiablesIds(NETWORK_UUID, 1);
        assertEquals(List.of(loadId1, lineId1), identifiablesIds);

        Resource<LoadAttributes> expLoad = buildLoad(loadId1, 1, "vl2");
        assertEquals(Optional.of(expLoad), networkStoreRepository.getIdentifiable(NETWORK_UUID, 1, loadId1));
        assertEquals(Optional.of(expLoad), networkStoreRepository.getLoad(NETWORK_UUID, 1, loadId1));

        assertTrue(networkStoreRepository.getVoltageLevelLoads(NETWORK_UUID, 1, "vl1").isEmpty());
        assertEquals(List.of(expLoad), networkStoreRepository.getVoltageLevelLoads(NETWORK_UUID, 1, "vl2"));

        assertEquals(List.of(expLoad), networkStoreRepository.getLoads(NETWORK_UUID, 1));
    }

    @Test
    void getIdentifiableFromFullClone() {
        String networkId = "network1";
        String loadId1 = "load1";
        String lineId1 = "line1";
        createSourceNetwork(networkId, 2, "variant2", VariantMode.PARTIAL);
        createLineAndLoad(2, loadId1, lineId1, "vl1", "vl2");

        List<String> identifiablesIds = networkStoreRepository.getIdentifiablesIds(NETWORK_UUID, 2);
        assertEquals(List.of(loadId1, lineId1), identifiablesIds);

        Resource<LoadAttributes> expLoad = buildLoad(loadId1, 2, "vl1");
        assertEquals(Optional.of(expLoad), networkStoreRepository.getIdentifiable(NETWORK_UUID, 2, loadId1));
        assertEquals(Optional.of(expLoad), networkStoreRepository.getLoad(NETWORK_UUID, 2, loadId1));

        assertEquals(List.of(expLoad), networkStoreRepository.getVoltageLevelLoads(NETWORK_UUID, 2, "vl1"));

        assertEquals(List.of(expLoad), networkStoreRepository.getLoads(NETWORK_UUID, 2));
    }

    @Test
    void getIdentifiableFromPartialCloneWithTombstonedIdentifiable() {
        String networkId = "network1";
        String loadId1 = "load1";
        String lineId1 = "line1";
        String loadId2 = "load2";
        String lineId2 = "line2";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL);
        createLineAndLoad(0, loadId1, lineId1, "vl1", "vl2");
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);
        createLineAndLoad(1, loadId2, lineId2, "vl1", "vl2");
        networkStoreRepository.deleteIdentifiable(NETWORK_UUID, 0, lineId1, LINE_TABLE);
        networkStoreRepository.deleteIdentifiable(NETWORK_UUID, 1, loadId1, LOAD_TABLE);
        networkStoreRepository.deleteIdentifiable(NETWORK_UUID, 1, lineId2, LINE_TABLE);

        List<String> identifiablesIds = networkStoreRepository.getIdentifiablesIds(NETWORK_UUID, 1);
        assertEquals(List.of(loadId2), identifiablesIds);

        Resource<LoadAttributes> expLoad = buildLoad(loadId2, 1, "vl1");
        assertTrue(networkStoreRepository.getIdentifiable(NETWORK_UUID, 1, loadId1).isEmpty());
        assertEquals(Optional.of(expLoad), networkStoreRepository.getIdentifiable(NETWORK_UUID, 1, loadId2));
        assertTrue(networkStoreRepository.getLoad(NETWORK_UUID, 1, loadId1).isEmpty());
        assertEquals(Optional.of(expLoad), networkStoreRepository.getLoad(NETWORK_UUID, 1, loadId2));

        assertEquals(List.of(expLoad), networkStoreRepository.getVoltageLevelLoads(NETWORK_UUID, 1, "vl1"));

        assertEquals(List.of(expLoad), networkStoreRepository.getLoads(NETWORK_UUID, 1));
    }

    private static Resource<LoadAttributes> buildLoad(String loadId, int variantNum, String voltageLevel) {
        return Resource.loadBuilder()
                .id(loadId)
                .variantNum(variantNum)
                .attributes(LoadAttributes.builder()
                        .voltageLevelId(voltageLevel)
                        .build())
                .build();
    }

    @Test
    void getIdentifiableFromPartialCloneWithExternalAttributes() {
        String networkId = "network1";
        String lineId1 = "line1";
        String lineId2 = "line2";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL);
        Resource<LineAttributes> line1 = Resource.lineBuilder()
                .id(lineId1)
                .variantNum(0)
                .attributes(LineAttributes.builder()
                        .voltageLevelId1("vl1")
                        .voltageLevelId2("vl2")
                        .operationalLimitsGroups1(Map.of("group1", OperationalLimitsGroupAttributes.builder()
                                .id("group1")
                                .currentLimits(LimitsAttributes.builder().permanentLimit(30.).build())
                                .build()))
                        .build())
                .build();
        networkStoreRepository.createLines(NETWORK_UUID, List.of(line1));
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);
        Resource<LineAttributes> line2 = Resource.lineBuilder()
                .id(lineId2)
                .variantNum(1)
                .attributes(LineAttributes.builder()
                        .voltageLevelId1("vl1")
                        .voltageLevelId2("vl3")
                        .operationalLimitsGroups1(Map.of("group1", OperationalLimitsGroupAttributes.builder()
                                .id("group1")
                                .currentLimits(LimitsAttributes.builder().permanentLimit(20.).build())
                                .build()))
                        .build())
                .build();
        networkStoreRepository.createLines(NETWORK_UUID, List.of(line2));

        // Line1 is retrieved from variant 1 so variantNum must be 1
        Resource<LineAttributes> expLine1 = Resource.lineBuilder()
                .id(lineId1)
                .variantNum(1)
                .attributes(LineAttributes.builder()
                        .voltageLevelId1("vl1")
                        .voltageLevelId2("vl2")
                        .operationalLimitsGroups1(Map.of("group1", OperationalLimitsGroupAttributes.builder()
                                .id("group1")
                                .currentLimits(LimitsAttributes.builder().permanentLimit(30.).build())
                                .build()))
                        .build())
                .build();

        assertEquals(Optional.of(expLine1), networkStoreRepository.getIdentifiable(NETWORK_UUID, 1, lineId1));
        assertEquals(Optional.of(line2), networkStoreRepository.getIdentifiable(NETWORK_UUID, 1, lineId2));

        assertEquals(Optional.of(expLine1), networkStoreRepository.getLine(NETWORK_UUID, 1, lineId1));
        assertEquals(Optional.of(line2), networkStoreRepository.getLine(NETWORK_UUID, 1, lineId2));

        assertEquals(List.of(expLine1, line2), networkStoreRepository.getVoltageLevelLines(NETWORK_UUID, 1, "vl1"));

        assertEquals(List.of(expLine1, line2), networkStoreRepository.getLines(NETWORK_UUID, 1));
    }

    @Test
    void deleteIdentifiableWithoutNetwork() {
        String loadId1 = "load1";
        String lineId1 = "line1";
        createLineAndLoad(0, loadId1, lineId1, "vl1", "vl2");
        PowsyblException exception = assertThrows(PowsyblException.class, () -> networkStoreRepository.deleteIdentifiable(NETWORK_UUID, 0, loadId1, LOAD_TABLE));
        assertTrue(exception.getMessage().contains("Cannot retrieve source network attributes"));
    }

    @Test
    void deleteIdentifiableOnFullVariant() {
        String networkId = "network1";
        String loadId1 = "load1";
        String lineId1 = "line1";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL);
        createLineAndLoad(0, loadId1, lineId1, "vl1", "vl2");
        networkStoreRepository.deleteIdentifiable(NETWORK_UUID, 0, loadId1, LOAD_TABLE);

        assertEquals(List.of(lineId1), getStoredIdentifiableIdsInVariant(NETWORK_UUID, 0));
        assertTrue(getTombstonedIdentifiableIdsInVariant(NETWORK_UUID, 0).isEmpty());
    }

    @Test
    @Disabled("Not implemented")
    void deleteIdentifiableNotExistingOnFullVariant() {
        String networkId = "network1";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL);
        assertThrows(PowsyblException.class, () -> networkStoreRepository.deleteIdentifiable(NETWORK_UUID, 0, "notExistingId", LOAD_TABLE));
        assertTrue(getTombstonedIdentifiableIdsInVariant(NETWORK_UUID, 0).isEmpty());
    }

    @Test
    void deleteIdentifiableOnPartialVariant() {
        String networkId = "network1";
        String loadId1 = "load1";
        String lineId1 = "line1";
        createPartialNetwork(networkId, 1, "variant1", VariantMode.PARTIAL, 0);
        createLineAndLoad(1, loadId1, lineId1, "vl1", "vl2");
        networkStoreRepository.deleteIdentifiable(NETWORK_UUID, 1, loadId1, LOAD_TABLE);

        assertEquals(List.of(lineId1), getStoredIdentifiableIdsInVariant(NETWORK_UUID, 1));
        assertEquals(Set.of(loadId1), getTombstonedIdentifiableIdsInVariant(NETWORK_UUID, 1));
    }

    @Test
    @Disabled("Not implemented")
    void deleteIdentifiableNotExistingOnPartialVariant() {
        String networkId = "network1";
        createPartialNetwork(networkId, 1, "variant1", VariantMode.PARTIAL, 0);

        assertThrows(PowsyblException.class, () -> networkStoreRepository.deleteIdentifiable(NETWORK_UUID, 1, "notExistingId", LOAD_TABLE));
        assertTrue(getTombstonedIdentifiableIdsInVariant(NETWORK_UUID, 1).isEmpty());
    }

    @Test
    void createIdentifiablesInPartialVariant() {
        String networkId = "network1";
        createPartialNetwork(networkId, 1, "variant1", VariantMode.PARTIAL, 0);
        Resource<LineAttributes> line2 = Resource.lineBuilder()
                .id("line2")
                .variantNum(1)
                .attributes(LineAttributes.builder()
                        .voltageLevelId1("vl2")
                        .voltageLevelId2("vl3")
                        .build())
                .build();
        networkStoreRepository.createLines(NETWORK_UUID, List.of(line2));

        assertEquals(List.of(line2), getIdentifiablesForVariant(NETWORK_UUID, 1, mappings.getLineMappings()));
        assertEquals(List.of("line2"), getStoredIdentifiableIdsInVariant(NETWORK_UUID, 1));
    }

    @Test
    @Disabled("Not implemented")
    void createIdentifiablesInPartialVariantAlreadyExistingInFullVariantThrows() {
        String networkId = "network1";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL);
        String lineId1 = "line1";
        Resource<LineAttributes> line1Variant0 = Resource.lineBuilder()
                .id(lineId1)
                .variantNum(0)
                .attributes(LineAttributes.builder()
                        .voltageLevelId1("vl1")
                        .voltageLevelId2("vl2")
                        .build())
                .build();
        networkStoreRepository.createLines(NETWORK_UUID, List.of(line1Variant0));
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);
        Resource<LineAttributes> line1Variant1 = Resource.lineBuilder()
                .id(lineId1)
                .variantNum(1)
                .attributes(LineAttributes.builder()
                        .voltageLevelId1("vl2")
                        .voltageLevelId2("vl3")
                        .build())
                .build();
        assertThrows(IllegalArgumentException.class, () -> networkStoreRepository.createLines(NETWORK_UUID, List.of(line1Variant1)));
    }

    @Test
    void createIdentifiablesWithRecreatedTombstonedIdentifiable() {
        String networkId = "network1";
        // Variant 0
        createPartialNetwork(networkId, 1, "variant1", VariantMode.PARTIAL, 0);
        String lineId1 = "line1";
        Resource<LineAttributes> lineVariant1 = Resource.lineBuilder()
                .id(lineId1)
                .variantNum(1)
                .attributes(LineAttributes.builder()
                        .voltageLevelId1("vl1")
                        .voltageLevelId2("vl2")
                        .build())
                .build();
        networkStoreRepository.createLines(NETWORK_UUID, List.of(lineVariant1));
        networkStoreRepository.deleteIdentifiable(NETWORK_UUID, 1, lineId1, LINE_TABLE);
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 1, 2, "variant1", VariantMode.PARTIAL);
        // Variant 2
        Resource<LineAttributes> lineVariant2 = Resource.lineBuilder()
                .id(lineId1)
                .variantNum(2)
                .attributes(LineAttributes.builder()
                        .voltageLevelId1("vl2")
                        .voltageLevelId2("vl3")
                        .build())
                .build();
        networkStoreRepository.createLines(NETWORK_UUID, List.of(lineVariant2));

        // Variant 1 (removed line1)
        assertTrue(getIdentifiablesForVariant(NETWORK_UUID, 1, mappings.getLineMappings()).isEmpty());
        assertTrue(getStoredIdentifiableIdsInVariant(NETWORK_UUID, 1).isEmpty());
        assertEquals(Set.of(lineId1), getTombstonedIdentifiableIdsInVariant(NETWORK_UUID, 1));
        // Variant 2 (recreated line1 with different attributes)
        assertEquals(List.of(lineVariant2), getIdentifiablesForVariant(NETWORK_UUID, 2, mappings.getLineMappings()));
        assertEquals(List.of(lineId1), getStoredIdentifiableIdsInVariant(NETWORK_UUID, 2));
        assertTrue(getTombstonedIdentifiableIdsInVariant(NETWORK_UUID, 2).isEmpty());
    }

    @Test
    void updateIdentifiablesWithWhereClauseNotExistingInPartialVariant() {
        testUpdateIdentifiablesNotExistingInPartialVariant((networkId, resources) ->
                networkStoreRepository.updateIdentifiables(
                        networkId,
                        resources,
                        mappings.getLoadMappings(),
                        VOLTAGE_LEVEL_ID_COLUMN
                )
        );
    }

    @Test
    void updateIdentifiablesNotExistingInPartialVariant() {
        testUpdateIdentifiablesNotExistingInPartialVariant((networkId, resources) ->
                networkStoreRepository.updateIdentifiables(
                        networkId,
                        resources,
                        mappings.getLoadMappings()
                )
        );
    }

    private void testUpdateIdentifiablesNotExistingInPartialVariant(BiConsumer<UUID, List<Resource<LoadAttributes>>> updateMethod) {
        String networkId = "network1";
        String loadId = "load";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL);
        Resource<LoadAttributes> initialLoad = Resource.loadBuilder()
                .id(loadId)
                .variantNum(0)
                .attributes(LoadAttributes.builder()
                        .voltageLevelId("vl1")
                        .p(5.1)
                        .q(6.1)
                        .build())
                .build();
        networkStoreRepository.createLoads(NETWORK_UUID, List.of(initialLoad));
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);

        Resource<LoadAttributes> updatedLoad = Resource.loadBuilder()
                .id(loadId)
                .variantNum(1)
                .attributes(LoadAttributes.builder()
                        .voltageLevelId("vl1")
                        .p(5.6)
                        .q(6.6)
                        .build())
                .build();
        updateMethod.accept(NETWORK_UUID, List.of(updatedLoad));

        assertEquals(Optional.of(updatedLoad), networkStoreRepository.getIdentifiable(NETWORK_UUID, 1, loadId));
    }

    @Test
    void updateIdentifiablesSvNotExistingInPartialVariant() {
        String networkId = "network1";
        String loadId = "load";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL);
        Resource<LoadAttributes> initialLoad = Resource.loadBuilder()
                .id(loadId)
                .variantNum(0)
                .attributes(LoadAttributes.builder()
                        .voltageLevelId("vl1")
                        .p(5.1)
                        .q(6.1)
                        .build())
                .build();
        networkStoreRepository.createLoads(NETWORK_UUID, List.of(initialLoad));
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);

        InjectionSvAttributes injectionSvAttributes = InjectionSvAttributes.builder()
                .p(5.6)
                .q(6.6)
                .build();
        Resource<InjectionSvAttributes> updatedSvLoad = new Resource<>(ResourceType.LOAD, loadId, 1, AttributeFilter.SV, injectionSvAttributes);
        networkStoreRepository.updateLoadsSv(NETWORK_UUID, List.of(updatedSvLoad));

        Resource<LoadAttributes> expUpdatedLoad = Resource.loadBuilder()
                .id(loadId)
                .variantNum(1)
                .attributes(LoadAttributes.builder()
                        .voltageLevelId("vl1")
                        .p(5.6)
                        .q(6.6)
                        .build())
                .build();
        assertEquals(Optional.of(expUpdatedLoad), networkStoreRepository.getIdentifiable(NETWORK_UUID, 1, loadId));
    }

    @Test
    void updateIdentifiablesWithWhereClauseAlreadyExistingInPartialVariant() {
        testUpdateIdentifiablesAlreadyExistingInPartialVariant((networkId, resources) ->
                networkStoreRepository.updateIdentifiables(
                        networkId,
                        resources,
                        mappings.getLoadMappings(),
                        VOLTAGE_LEVEL_ID_COLUMN
                )
        );
    }

    @Test
    void updateIdentifiablesAlreadyExistingInPartialVariant() {
        testUpdateIdentifiablesAlreadyExistingInPartialVariant((networkId, resources) ->
                networkStoreRepository.updateIdentifiables(
                        networkId,
                        resources,
                        mappings.getLoadMappings()
                )
        );
    }

    private void testUpdateIdentifiablesAlreadyExistingInPartialVariant(BiConsumer<UUID, List<Resource<LoadAttributes>>> updateMethod) {
        String networkId = "network1";
        String loadId = "load";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL);
        Resource<LoadAttributes> initialLoad = Resource.loadBuilder()
                .id(loadId)
                .variantNum(0)
                .attributes(LoadAttributes.builder()
                        .voltageLevelId("vl1")
                        .p(5.1)
                        .q(6.1)
                        .build())
                .build();
        networkStoreRepository.createLoads(NETWORK_UUID, List.of(initialLoad));
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);
        Resource<LoadAttributes> updatedLoad = Resource.loadBuilder()
                .id(loadId)
                .variantNum(1)
                .attributes(LoadAttributes.builder()
                        .voltageLevelId("vl1")
                        .p(5.6)
                        .q(6.6)
                        .build())
                .build();
        updateMethod.accept(NETWORK_UUID, List.of(updatedLoad));

        updatedLoad = Resource.loadBuilder()
                .id(loadId)
                .variantNum(1)
                .attributes(LoadAttributes.builder()
                        .voltageLevelId("vl1")
                        .p(8.1)
                        .q(5.9)
                        .build())
                .build();
        updateMethod.accept(NETWORK_UUID, List.of(updatedLoad));

        assertEquals(Optional.of(updatedLoad), networkStoreRepository.getIdentifiable(NETWORK_UUID, 1, loadId));
    }

    @Test
    void updateIdentifiablesSvAlreadyExistingInPartialVariant() {
        String networkId = "network1";
        String loadId = "load";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL);
        Resource<LoadAttributes> initialLoad = Resource.loadBuilder()
                .id(loadId)
                .variantNum(0)
                .attributes(LoadAttributes.builder()
                        .voltageLevelId("vl1")
                        .p(5.1)
                        .q(6.1)
                        .build())
                .build();
        networkStoreRepository.createLoads(NETWORK_UUID, List.of(initialLoad));
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);
        InjectionSvAttributes injectionSvAttributes = InjectionSvAttributes.builder()
                .p(5.6)
                .q(6.6)
                .build();
        Resource<InjectionSvAttributes> updatedSvLoad = new Resource<>(ResourceType.LOAD, loadId, 1, AttributeFilter.SV, injectionSvAttributes);
        networkStoreRepository.updateLoadsSv(NETWORK_UUID, List.of(updatedSvLoad));

        injectionSvAttributes = InjectionSvAttributes.builder()
                .p(8.1)
                .q(5.9)
                .build();
        updatedSvLoad = new Resource<>(ResourceType.LOAD, loadId, 1, AttributeFilter.SV, injectionSvAttributes);
        networkStoreRepository.updateLoadsSv(NETWORK_UUID, List.of(updatedSvLoad));

        Resource<LoadAttributes> expUpdatedSvLoad = Resource.loadBuilder()
                .id(loadId)
                .variantNum(1)
                .attributes(LoadAttributes.builder()
                        .voltageLevelId("vl1")
                        .p(8.1)
                        .q(5.9)
                        .build())
                .build();
        assertEquals(Optional.of(expUpdatedSvLoad), networkStoreRepository.getIdentifiable(NETWORK_UUID, 1, loadId));
    }

    @Test
    void updateIdentifiablesWithWhereClauseNotExistingAndExistingInPartialVariant() {
        testUpdateIdentifiablesNotExistingAndExistingInPartialVariant((networkId, resources) ->
                networkStoreRepository.updateIdentifiables(
                        networkId,
                        resources,
                        mappings.getLoadMappings(),
                        VOLTAGE_LEVEL_ID_COLUMN
                )
        );
    }

    @Test
    void updateIdentifiablesNotExistingAndExistingInPartialVariant() {
        testUpdateIdentifiablesNotExistingAndExistingInPartialVariant((networkId, resources) ->
                networkStoreRepository.updateIdentifiables(
                        networkId,
                        resources,
                        mappings.getLoadMappings()
                )
        );
    }

    private void testUpdateIdentifiablesNotExistingAndExistingInPartialVariant(BiConsumer<UUID, List<Resource<LoadAttributes>>> updateMethod) {
        String networkId = "network1";
        String loadId1 = "load1";
        String loadId2 = "load2";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL);
        Resource<LoadAttributes> initialLoad1 = Resource.loadBuilder()
                .id(loadId1)
                .variantNum(0)
                .attributes(LoadAttributes.builder()
                        .voltageLevelId("vl1")
                        .p(5.1)
                        .q(6.1)
                        .build())
                .build();
        Resource<LoadAttributes> initialLoad2 = Resource.loadBuilder()
                .id(loadId2)
                .variantNum(0)
                .attributes(LoadAttributes.builder()
                        .voltageLevelId("vl1")
                        .p(7.1)
                        .q(3.1)
                        .build())
                .build();
        networkStoreRepository.createLoads(NETWORK_UUID, List.of(initialLoad1, initialLoad2));
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);
        Resource<LoadAttributes> updatedLoad2 = Resource.loadBuilder()
                .id(loadId2)
                .variantNum(1)
                .attributes(LoadAttributes.builder()
                        .voltageLevelId("vl1")
                        .p(5.4)
                        .q(6.6)
                        .build())
                .build();
        updateMethod.accept(NETWORK_UUID, List.of(updatedLoad2));

        Resource<LoadAttributes> updatedLoad1 = Resource.loadBuilder()
                .id(loadId1)
                .variantNum(1)
                .attributes(LoadAttributes.builder()
                        .voltageLevelId("vl1")
                        .p(5.9)
                        .q(6.4)
                        .build())
                .build();
        updatedLoad2 = Resource.loadBuilder()
                .id(loadId2)
                .variantNum(1)
                .attributes(LoadAttributes.builder()
                        .voltageLevelId("vl1")
                        .p(8.1)
                        .q(6.6)
                        .build())
                .build();
        updateMethod.accept(NETWORK_UUID, List.of(updatedLoad1, updatedLoad2));

        assertEquals(Optional.of(updatedLoad1), networkStoreRepository.getIdentifiable(NETWORK_UUID, 1, loadId1));
        assertEquals(Optional.of(updatedLoad2), networkStoreRepository.getIdentifiable(NETWORK_UUID, 1, loadId2));
    }

    @Test
    void updateIdentifiablesSvNotExistingAndExistingInPartialVariant() {
        String networkId = "network1";
        String loadId1 = "load1";
        String loadId2 = "load2";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL);
        Resource<LoadAttributes> initialLoad1 = Resource.loadBuilder()
                .id(loadId1)
                .variantNum(0)
                .attributes(LoadAttributes.builder()
                        .voltageLevelId("vl1")
                        .p(5.1)
                        .q(6.1)
                        .build())
                .build();
        Resource<LoadAttributes> initialLoad2 = Resource.loadBuilder()
                .id(loadId2)
                .variantNum(0)
                .attributes(LoadAttributes.builder()
                        .voltageLevelId("vl1")
                        .p(7.1)
                        .q(3.1)
                        .build())
                .build();
        networkStoreRepository.createLoads(NETWORK_UUID, List.of(initialLoad1, initialLoad2));
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);
        InjectionSvAttributes injectionSvAttributes = InjectionSvAttributes.builder()
                .p(5.6)
                .q(6.6)
                .build();
        Resource<InjectionSvAttributes> updatedSvLoad2 = new Resource<>(ResourceType.LOAD, loadId2, 1, AttributeFilter.SV, injectionSvAttributes);
        networkStoreRepository.updateLoadsSv(NETWORK_UUID, List.of(updatedSvLoad2));

        injectionSvAttributes = InjectionSvAttributes.builder()
                .p(2.1)
                .q(3.3)
                .build();
        Resource<InjectionSvAttributes> updatedSvLoad1 = new Resource<>(ResourceType.LOAD, loadId1, 1, AttributeFilter.SV, injectionSvAttributes);
        injectionSvAttributes = InjectionSvAttributes.builder()
                .p(8.1)
                .q(6.6)
                .build();
        updatedSvLoad2 = new Resource<>(ResourceType.LOAD, loadId2, 1, AttributeFilter.SV, injectionSvAttributes);
        networkStoreRepository.updateLoadsSv(NETWORK_UUID, List.of(updatedSvLoad1, updatedSvLoad2));

        Resource<LoadAttributes> expLoad1 = Resource.loadBuilder()
                .id(loadId1)
                .variantNum(1)
                .attributes(LoadAttributes.builder()
                        .voltageLevelId("vl1")
                        .p(2.1)
                        .q(3.3)
                        .build())
                .build();
        Resource<LoadAttributes> expLoad2 = Resource.loadBuilder()
                .id(loadId2)
                .variantNum(1)
                .attributes(LoadAttributes.builder()
                        .voltageLevelId("vl1")
                        .p(8.1)
                        .q(6.6)
                        .build())
                .build();
        assertEquals(Optional.of(expLoad1), networkStoreRepository.getIdentifiable(NETWORK_UUID, 1, loadId1));
        assertEquals(Optional.of(expLoad2), networkStoreRepository.getIdentifiable(NETWORK_UUID, 1, loadId2));
    }

    @Test
    void getTapChangerStepsWithoutNetwork() {
        PowsyblException exception = assertThrows(PowsyblException.class, () -> networkStoreRepository.getTapChangerSteps(NETWORK_UUID, 0, EQUIPMENT_ID_COLUMN, "unknownId", mappings.getLineMappings()));
        assertTrue(exception.getMessage().contains("Cannot retrieve source network attributes"));
    }

    @Test
    void getTapChangerStepsFromPartialCloneWithNoTapChangerStepsInPartialVariant() {
        String networkId = "network1";
        String lineId = "line1";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL);
        OwnerInfo ownerInfo = new OwnerInfo(lineId, ResourceType.LINE, NETWORK_UUID, 0);
        TapChangerStepAttributes ratioStepA1 = buildTapChangerStepAttributes(1., 0);
        TapChangerStepAttributes ratioStepB1 = buildTapChangerStepAttributes(2., 1);
        networkStoreRepository.insertTapChangerSteps(Map.of(ownerInfo, List.of(ratioStepA1, ratioStepB1)));
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);

        OwnerInfo expOwnerInfo1 = new OwnerInfo(lineId, ResourceType.LINE, NETWORK_UUID, 1);
        assertEquals(List.of(ratioStepA1, ratioStepB1), networkStoreRepository.getTapChangerSteps(NETWORK_UUID, 1, EQUIPMENT_ID_COLUMN, lineId, mappings.getLineMappings()).get(expOwnerInfo1));
    }

    @Test
    void getTapChangerStepsFromPartialClone() {
        String networkId = "network1";
        String lineId = "line1";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL);
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);
        OwnerInfo ownerInfo = new OwnerInfo(lineId, ResourceType.LINE, NETWORK_UUID, 1);
        TapChangerStepAttributes ratioStepA1 = buildTapChangerStepAttributes(1., 0);
        TapChangerStepAttributes ratioStepB1 = buildTapChangerStepAttributes(2., 1);
        networkStoreRepository.insertTapChangerSteps(Map.of(ownerInfo, List.of(ratioStepA1, ratioStepB1)));

        OwnerInfo expOwnerInfo1 = new OwnerInfo(lineId, ResourceType.LINE, NETWORK_UUID, 1);
        assertEquals(List.of(ratioStepA1, ratioStepB1), networkStoreRepository.getTapChangerSteps(NETWORK_UUID, 1, EQUIPMENT_ID_COLUMN, lineId, mappings.getLineMappings()).get(expOwnerInfo1));
    }

    @Test
    void getTapChangerStepsFromPartialCloneWithUpdatedTapChangerSteps() {
        String networkId = "network1";
        String lineId = "line";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL);
        OwnerInfo ownerInfo = new OwnerInfo(lineId, ResourceType.LINE, NETWORK_UUID, 0);
        TapChangerStepAttributes ratioStepA1 = buildTapChangerStepAttributes(1., 0);
        TapChangerStepAttributes ratioStepB1 = buildTapChangerStepAttributes(2., 1);
        networkStoreRepository.insertTapChangerSteps(Map.of(ownerInfo, List.of(ratioStepA1, ratioStepB1)));
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);
        ownerInfo = new OwnerInfo(lineId, ResourceType.LINE, NETWORK_UUID, 1);
        ratioStepA1 = buildTapChangerStepAttributes(3., 0);
        ratioStepB1 = buildTapChangerStepAttributes(4., 1);
        networkStoreRepository.insertTapChangerSteps(Map.of(ownerInfo, List.of(ratioStepA1, ratioStepB1)));

        OwnerInfo expOwnerInfo = new OwnerInfo(lineId, ResourceType.LINE, NETWORK_UUID, 1);
        assertEquals(List.of(ratioStepA1, ratioStepB1), networkStoreRepository.getTapChangerSteps(NETWORK_UUID, 1, EQUIPMENT_ID_COLUMN, lineId, mappings.getLineMappings()).get(expOwnerInfo));
    }

    @Test
    void getTapChangerStepsFromPartialCloneWithUpdatedIdentifiableWithoutTapChangerSteps() {
        String networkId = "network1";
        String lineId = "line";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL);
        OwnerInfo ownerInfo = new OwnerInfo(lineId, ResourceType.LINE, NETWORK_UUID, 0);
        TapChangerStepAttributes ratioStepA1 = buildTapChangerStepAttributes(1., 0);
        TapChangerStepAttributes ratioStepB1 = buildTapChangerStepAttributes(2., 1);
        networkStoreRepository.insertTapChangerSteps(Map.of(ownerInfo, List.of(ratioStepA1, ratioStepB1)));
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);

        assertFalse(networkStoreRepository.getTapChangerSteps(NETWORK_UUID, 1, EQUIPMENT_ID_COLUMN, lineId, mappings.getLineMappings()).isEmpty());
        Resource<LineAttributes> line1 = new Resource<>(ResourceType.LINE, lineId, 1, null, new LineAttributes());
        networkStoreRepository.updateIdentifiables(NETWORK_UUID, List.of(line1), mappings.getLineMappings());

        assertTrue(networkStoreRepository.getTapChangerSteps(NETWORK_UUID, 1, EQUIPMENT_ID_COLUMN, lineId, mappings.getLineMappings()).isEmpty());
    }

    @Test
    void getTapChangerStepsFromPartialCloneWithUpdatedIdentifiableSvWithTapChangerSteps() {
        String networkId = "network1";
        String lineId = "line";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL);
        createLineAndLoad(0, "load1", lineId, "vl1", "vl2");
        OwnerInfo ownerInfo = new OwnerInfo(lineId, ResourceType.LINE, NETWORK_UUID, 0);
        TapChangerStepAttributes ratioStepA1 = buildTapChangerStepAttributes(1., 0);
        TapChangerStepAttributes ratioStepB1 = buildTapChangerStepAttributes(2., 1);
        networkStoreRepository.insertTapChangerSteps(Map.of(ownerInfo, List.of(ratioStepA1, ratioStepB1)));
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);

        assertFalse(networkStoreRepository.getTapChangerSteps(NETWORK_UUID, 1, EQUIPMENT_ID_COLUMN, lineId, mappings.getLineMappings()).isEmpty());
        BranchSvAttributes branchSvAttributes = BranchSvAttributes.builder()
                .p1(5.6)
                .q1(6.6)
                .build();
        Resource<BranchSvAttributes> updatedSvLine = new Resource<>(ResourceType.LINE, lineId, 1, AttributeFilter.SV, branchSvAttributes);
        networkStoreRepository.updateLinesSv(NETWORK_UUID, List.of(updatedSvLine));

        assertFalse(networkStoreRepository.getTapChangerSteps(NETWORK_UUID, 1, EQUIPMENT_ID_COLUMN, lineId, mappings.getLineMappings()).isEmpty());
    }

    @Test
    void getTapChangerStepsFromFullClone() {
        String networkId = "network1";
        String lineId = "line1";
        createSourceNetwork(networkId, 2, "variant2", VariantMode.PARTIAL);
        OwnerInfo ownerInfo = new OwnerInfo(lineId, ResourceType.LINE, NETWORK_UUID, 2);
        TapChangerStepAttributes ratioStepA1 = buildTapChangerStepAttributes(1., 0);
        TapChangerStepAttributes ratioStepB1 = buildTapChangerStepAttributes(2., 1);
        networkStoreRepository.insertTapChangerSteps(Map.of(ownerInfo, List.of(ratioStepA1, ratioStepB1)));

        OwnerInfo expOwnerInfo = new OwnerInfo(lineId, ResourceType.LINE, NETWORK_UUID, 2);
        assertEquals(List.of(ratioStepA1, ratioStepB1), networkStoreRepository.getTapChangerSteps(NETWORK_UUID, 2, EQUIPMENT_ID_COLUMN, lineId, mappings.getLineMappings()).get(expOwnerInfo));
    }

    private static TapChangerStepAttributes buildTapChangerStepAttributes(double value, int index) {
        return TapChangerStepAttributes.builder()
                .rho(value)
                .r(value)
                .g(value)
                .b(value)
                .x(value)
                .side(0)
                .index(index)
                .type(TapChangerType.RATIO)
                .build();
    }

    @Test
    void getTapChangerStepsFromPartialCloneWithTombstonedIdentifiable() {
        String networkId = "network1";
        String lineId = "line1";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL);
        OwnerInfo ownerInfo = new OwnerInfo(lineId, ResourceType.LINE, NETWORK_UUID, 0);
        TapChangerStepAttributes ratioStepA1 = buildTapChangerStepAttributes(1., 0);
        TapChangerStepAttributes ratioStepB1 = buildTapChangerStepAttributes(2., 1);
        networkStoreRepository.insertTapChangerSteps(Map.of(ownerInfo, List.of(ratioStepA1, ratioStepB1)));
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);

        OwnerInfo expOwnerInfo = new OwnerInfo(lineId, ResourceType.LINE, NETWORK_UUID, 1);
        assertEquals(List.of(ratioStepA1, ratioStepB1), networkStoreRepository.getTapChangerSteps(NETWORK_UUID, 1, EQUIPMENT_ID_COLUMN, lineId, mappings.getLineMappings()).get(expOwnerInfo));
        networkStoreRepository.deleteIdentifiable(NETWORK_UUID, 1, lineId, LINE_TABLE);
        assertTrue(networkStoreRepository.getTapChangerSteps(NETWORK_UUID, 1, EQUIPMENT_ID_COLUMN, lineId, mappings.getLineMappings()).isEmpty());
    }

    @Test
    void getExtensionWithoutNetwork() {
        PowsyblException exception = assertThrows(PowsyblException.class, () -> networkStoreRepository.getExtensionAttributes(NETWORK_UUID, 0, EQUIPMENT_ID_COLUMN, "unknownExtension"));
        assertTrue(exception.getMessage().contains("Cannot retrieve source network attributes"));
        exception = assertThrows(PowsyblException.class, () -> networkStoreRepository.getAllExtensionsAttributesByResourceType(NETWORK_UUID, 0, ResourceType.LINE));
        assertTrue(exception.getMessage().contains("Cannot retrieve source network attributes"));
        exception = assertThrows(PowsyblException.class, () -> networkStoreRepository.getAllExtensionsAttributesByResourceTypeAndExtensionName(NETWORK_UUID, 0, ResourceType.LINE, "unknownExtension"));
        assertTrue(exception.getMessage().contains("Cannot retrieve source network attributes"));
        exception = assertThrows(PowsyblException.class, () -> networkStoreRepository.getAllExtensionsAttributesByIdentifiableId(NETWORK_UUID, 0, "unknownId"));
        assertTrue(exception.getMessage().contains("Cannot retrieve source network attributes"));
    }

    @Test
    void getExtensionFromPartialCloneWithNoExtensionInPartialVariant() {
        String networkId = "network1";
        String lineId1 = "line1";
        String lineId2 = "line2";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL);
        OwnerInfo ownerInfo1 = new OwnerInfo(lineId1, ResourceType.LINE, NETWORK_UUID, 0);
        Map<String, ExtensionAttributes> extensionAttributesMap1 = buildExtensionAttributesMap(5.6, "status1");
        OwnerInfo ownerInfo2 = new OwnerInfo(lineId2, ResourceType.LINE, NETWORK_UUID, 0);
        Map<String, ExtensionAttributes> extensionAttributesMap2 = buildExtensionAttributesMap(8.9, "status2");
        createExtensions(Map.of(ownerInfo1, extensionAttributesMap1, ownerInfo2, extensionAttributesMap2));
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);

        assertEquals(Optional.of(extensionAttributesMap1.get(ActivePowerControl.NAME)), networkStoreRepository.getExtensionAttributes(NETWORK_UUID, 1, lineId1, ActivePowerControl.NAME));
        assertEquals(extensionAttributesMap1, networkStoreRepository.getAllExtensionsAttributesByIdentifiableId(NETWORK_UUID, 1, lineId1));
        Map<String, ExtensionAttributes> expExtensionAttributesApcLine = Map.of(lineId1, buildActivePowerControlAttributes(5.6), lineId2, buildActivePowerControlAttributes(8.9));
        assertEquals(expExtensionAttributesApcLine, networkStoreRepository.getAllExtensionsAttributesByResourceTypeAndExtensionName(NETWORK_UUID, 1, ResourceType.LINE, ActivePowerControl.NAME));
        Map<String, Map<String, ExtensionAttributes>> expExtensionAttributesLine = Map.of(lineId1, extensionAttributesMap1, lineId2, extensionAttributesMap2);
        assertEquals(expExtensionAttributesLine, networkStoreRepository.getAllExtensionsAttributesByResourceType(NETWORK_UUID, 1, ResourceType.LINE));
    }

    private static Map<String, ExtensionAttributes> buildExtensionAttributesMap(double droop, String operatingStatus) {
        ExtensionAttributes activePowerControlAttributes = buildActivePowerControlAttributes(droop);
        ExtensionAttributes operatingStatusAttributes = buildOperatingStatusAttributes(operatingStatus);
        return Map.of(ActivePowerControl.NAME, activePowerControlAttributes, OperatingStatus.NAME, operatingStatusAttributes);
    }

    private static ExtensionAttributes buildOperatingStatusAttributes(String operatingStatus) {
        return OperatingStatusAttributes.builder()
                .operatingStatus(operatingStatus)
                .build();
    }

    private static ExtensionAttributes buildActivePowerControlAttributes(double droop) {
        return ActivePowerControlAttributes.builder()
                .droop(droop)
                .participate(false)
                .build();
    }

    @Test
    void getExtensionFromPartialClone() {
        String networkId = "network1";
        String lineId1 = "line1";
        String lineId2 = "line2";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL);
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);
        OwnerInfo ownerInfo1 = new OwnerInfo(lineId1, ResourceType.LINE, NETWORK_UUID, 1);
        Map<String, ExtensionAttributes> extensionAttributesMap1 = buildExtensionAttributesMap(5.6, "status1");
        OwnerInfo ownerInfo2 = new OwnerInfo(lineId2, ResourceType.LINE, NETWORK_UUID, 1);
        Map<String, ExtensionAttributes> extensionAttributesMap2 = buildExtensionAttributesMap(8.9, "status2");
        createExtensions(Map.of(ownerInfo1, extensionAttributesMap1, ownerInfo2, extensionAttributesMap2));

        assertEquals(Optional.of(extensionAttributesMap1.get(ActivePowerControl.NAME)), networkStoreRepository.getExtensionAttributes(NETWORK_UUID, 1, lineId1, ActivePowerControl.NAME));
        assertEquals(extensionAttributesMap1, networkStoreRepository.getAllExtensionsAttributesByIdentifiableId(NETWORK_UUID, 1, lineId1));
        Map<String, ExtensionAttributes> expExtensionAttributesApcLine = Map.of(lineId1, buildActivePowerControlAttributes(5.6), lineId2, buildActivePowerControlAttributes(8.9));
        assertEquals(expExtensionAttributesApcLine, networkStoreRepository.getAllExtensionsAttributesByResourceTypeAndExtensionName(NETWORK_UUID, 1, ResourceType.LINE, ActivePowerControl.NAME));
        Map<String, Map<String, ExtensionAttributes>> expExtensionAttributesLine = Map.of(lineId1, extensionAttributesMap1, lineId2, extensionAttributesMap2);
        assertEquals(expExtensionAttributesLine, networkStoreRepository.getAllExtensionsAttributesByResourceType(NETWORK_UUID, 1, ResourceType.LINE));
    }

    @Test
    void getExtensionFromPartialCloneWithUpdatedExtension() {
        String networkId = "network1";
        String lineId1 = "line1";
        String lineId2 = "line2";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL);
        OwnerInfo ownerInfo1 = new OwnerInfo(lineId1, ResourceType.LINE, NETWORK_UUID, 0);
        Map<String, ExtensionAttributes> extensionAttributesMap1 = buildExtensionAttributesMap(5.6, "status1");
        OwnerInfo ownerInfo2 = new OwnerInfo(lineId2, ResourceType.LINE, NETWORK_UUID, 0);
        Map<String, ExtensionAttributes> extensionAttributesMap2 = buildExtensionAttributesMap(8.9, "status2");
        createExtensions(Map.of(ownerInfo1, extensionAttributesMap1, ownerInfo2, extensionAttributesMap2));
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);

        ownerInfo1 = new OwnerInfo(lineId1, ResourceType.LINE, NETWORK_UUID, 1);
        extensionAttributesMap1 = buildExtensionAttributesMap(5.2, "statusUpdated1");
        createExtensions(Map.of(ownerInfo1, extensionAttributesMap1));

        assertEquals(Optional.of(extensionAttributesMap1.get(ActivePowerControl.NAME)), networkStoreRepository.getExtensionAttributes(NETWORK_UUID, 1, lineId1, ActivePowerControl.NAME));
        assertEquals(extensionAttributesMap1, networkStoreRepository.getAllExtensionsAttributesByIdentifiableId(NETWORK_UUID, 1, lineId1));
        Map<String, ExtensionAttributes> expExtensionAttributesApcLine = Map.of(lineId1, buildActivePowerControlAttributes(5.2), lineId2, buildActivePowerControlAttributes(8.9));
        assertEquals(expExtensionAttributesApcLine, networkStoreRepository.getAllExtensionsAttributesByResourceTypeAndExtensionName(NETWORK_UUID, 1, ResourceType.LINE, ActivePowerControl.NAME));
        Map<String, Map<String, ExtensionAttributes>> expExtensionAttributesLine = Map.of(lineId1, extensionAttributesMap1, lineId2, extensionAttributesMap2);
        assertEquals(expExtensionAttributesLine, networkStoreRepository.getAllExtensionsAttributesByResourceType(NETWORK_UUID, 1, ResourceType.LINE));
    }

    //NOTE: this does not exist with IIDM api for now...
    @Test
    @Disabled("To implement")
    void getExtensionFromPartialCloneWithRemovedExtension() throws SQLException {
        String networkId = "network1";
        String lineId1 = "line1";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL);
        OwnerInfo ownerInfo1 = new OwnerInfo(lineId1, ResourceType.LINE, NETWORK_UUID, 0);
        Map<String, ExtensionAttributes> extensionAttributesMap1 = buildExtensionAttributesMap(5.6, "status1");
        createExtensions(Map.of(ownerInfo1, extensionAttributesMap1));
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);

        try (var connection = dataSource.getConnection()) {
            LineAttributes attributes = new LineAttributes();
            attributes.setExtensionAttributes(Map.of("operatingStatus", buildOperatingStatusAttributes("tutu")));
            extensionHandler.updateExtensionsFromEquipments(connection, NETWORK_UUID, List.of(new Resource<>(ResourceType.LINE, lineId1, 1, null, attributes)));
        }
        assertEquals(Optional.empty(), networkStoreRepository.getExtensionAttributes(NETWORK_UUID, 1, lineId1, ActivePowerControl.NAME));
        assertEquals(extensionAttributesMap1, networkStoreRepository.getAllExtensionsAttributesByIdentifiableId(NETWORK_UUID, 1, lineId1));
        assertEquals(Map.of(), networkStoreRepository.getAllExtensionsAttributesByResourceTypeAndExtensionName(NETWORK_UUID, 1, ResourceType.LINE, ActivePowerControl.NAME));
        Map<String, Map<String, ExtensionAttributes>> expExtensionAttributesLine = Map.of(lineId1, Map.of());
        assertEquals(expExtensionAttributesLine, networkStoreRepository.getAllExtensionsAttributesByResourceType(NETWORK_UUID, 1, ResourceType.LINE));
    }

    @Test
    void getExtensionFromFullClone() {
        String networkId = "network1";
        String lineId1 = "line1";
        String lineId2 = "line2";
        createSourceNetwork(networkId, 2, "variant2", VariantMode.PARTIAL);
        OwnerInfo ownerInfo1 = new OwnerInfo(lineId1, ResourceType.LINE, NETWORK_UUID, 2);
        Map<String, ExtensionAttributes> extensionAttributesMap1 = buildExtensionAttributesMap(5.6, "status1");
        OwnerInfo ownerInfo2 = new OwnerInfo(lineId2, ResourceType.LINE, NETWORK_UUID, 2);
        Map<String, ExtensionAttributes> extensionAttributesMap2 = buildExtensionAttributesMap(8.9, "status2");
        createExtensions(Map.of(ownerInfo1, extensionAttributesMap1, ownerInfo2, extensionAttributesMap2));

        assertEquals(Optional.of(extensionAttributesMap1.get(ActivePowerControl.NAME)), networkStoreRepository.getExtensionAttributes(NETWORK_UUID, 2, lineId1, ActivePowerControl.NAME));
        assertEquals(extensionAttributesMap1, networkStoreRepository.getAllExtensionsAttributesByIdentifiableId(NETWORK_UUID, 2, lineId1));
        Map<String, ExtensionAttributes> expExtensionAttributesApcLine = Map.of(lineId1, buildActivePowerControlAttributes(5.6), lineId2, buildActivePowerControlAttributes(8.9));
        assertEquals(expExtensionAttributesApcLine, networkStoreRepository.getAllExtensionsAttributesByResourceTypeAndExtensionName(NETWORK_UUID, 2, ResourceType.LINE, ActivePowerControl.NAME));
        Map<String, Map<String, ExtensionAttributes>> expExtensionAttributesLine = Map.of(lineId1, extensionAttributesMap1, lineId2, extensionAttributesMap2);
        assertEquals(expExtensionAttributesLine, networkStoreRepository.getAllExtensionsAttributesByResourceType(NETWORK_UUID, 2, ResourceType.LINE));
    }

    @Test
    void getExtensionFromPartialCloneWithTombstonedIdentifiable() {
        String networkId = "network1";
        String lineId1 = "line1";
        String lineId2 = "line2";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL);
        OwnerInfo ownerInfo1 = new OwnerInfo(lineId1, ResourceType.LINE, NETWORK_UUID, 0);
        Map<String, ExtensionAttributes> extensionAttributesMap1 = buildExtensionAttributesMap(5.6, "status1");
        OwnerInfo ownerInfo2 = new OwnerInfo(lineId2, ResourceType.LINE, NETWORK_UUID, 0);
        Map<String, ExtensionAttributes> extensionAttributesMap2 = buildExtensionAttributesMap(8.9, "status2");
        createExtensions(Map.of(ownerInfo1, extensionAttributesMap1, ownerInfo2, extensionAttributesMap2));
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);

        assertEquals(Optional.of(extensionAttributesMap1.get(ActivePowerControl.NAME)), networkStoreRepository.getExtensionAttributes(NETWORK_UUID, 1, lineId1, ActivePowerControl.NAME));
        assertEquals(extensionAttributesMap1, networkStoreRepository.getAllExtensionsAttributesByIdentifiableId(NETWORK_UUID, 1, lineId1));
        Map<String, ExtensionAttributes> expExtensionAttributesApcLine = Map.of(lineId1, buildActivePowerControlAttributes(5.6), lineId2, buildActivePowerControlAttributes(8.9));
        assertEquals(expExtensionAttributesApcLine, networkStoreRepository.getAllExtensionsAttributesByResourceTypeAndExtensionName(NETWORK_UUID, 1, ResourceType.LINE, ActivePowerControl.NAME));
        Map<String, Map<String, ExtensionAttributes>> expExtensionAttributesLine = Map.of(lineId1, extensionAttributesMap1, lineId2, extensionAttributesMap2);
        assertEquals(expExtensionAttributesLine, networkStoreRepository.getAllExtensionsAttributesByResourceType(NETWORK_UUID, 1, ResourceType.LINE));

        networkStoreRepository.deleteIdentifiable(NETWORK_UUID, 1, lineId1, LINE_TABLE);

        assertEquals(Optional.empty(), networkStoreRepository.getExtensionAttributes(NETWORK_UUID, 1, lineId1, ActivePowerControl.NAME));
        assertEquals(Map.of(), networkStoreRepository.getAllExtensionsAttributesByIdentifiableId(NETWORK_UUID, 1, lineId1));
        expExtensionAttributesApcLine = Map.of(lineId2, buildActivePowerControlAttributes(8.9));
        assertEquals(expExtensionAttributesApcLine, networkStoreRepository.getAllExtensionsAttributesByResourceTypeAndExtensionName(NETWORK_UUID, 1, ResourceType.LINE, ActivePowerControl.NAME));
        expExtensionAttributesLine = Map.of(lineId2, extensionAttributesMap2);
        assertEquals(expExtensionAttributesLine, networkStoreRepository.getAllExtensionsAttributesByResourceType(NETWORK_UUID, 1, ResourceType.LINE));
    }

    @Test
    void getExtensionFromPartialCloneWithTombstonedExtension() {
        String networkId = "network1";
        String lineId1 = "line1";
        String lineId2 = "line2";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL);
        OwnerInfo ownerInfo1 = new OwnerInfo(lineId1, ResourceType.LINE, NETWORK_UUID, 0);
        Map<String, ExtensionAttributes> extensionAttributesMap1 = buildExtensionAttributesMap(5.6, "status1");
        OwnerInfo ownerInfo2 = new OwnerInfo(lineId2, ResourceType.LINE, NETWORK_UUID, 0);
        Map<String, ExtensionAttributes> extensionAttributesMap2 = buildExtensionAttributesMap(8.9, "status2");
        createExtensions(Map.of(ownerInfo1, extensionAttributesMap1, ownerInfo2, extensionAttributesMap2));
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);

        assertEquals(Optional.of(extensionAttributesMap1.get(ActivePowerControl.NAME)), networkStoreRepository.getExtensionAttributes(NETWORK_UUID, 1, lineId1, ActivePowerControl.NAME));
        assertEquals(extensionAttributesMap1, networkStoreRepository.getAllExtensionsAttributesByIdentifiableId(NETWORK_UUID, 1, lineId1));
        Map<String, ExtensionAttributes> expExtensionAttributesApcLine = Map.of(lineId1, buildActivePowerControlAttributes(5.6), lineId2, buildActivePowerControlAttributes(8.9));
        assertEquals(expExtensionAttributesApcLine, networkStoreRepository.getAllExtensionsAttributesByResourceTypeAndExtensionName(NETWORK_UUID, 1, ResourceType.LINE, ActivePowerControl.NAME));
        Map<String, Map<String, ExtensionAttributes>> expExtensionAttributesLine = Map.of(lineId1, extensionAttributesMap1, lineId2, extensionAttributesMap2);
        assertEquals(expExtensionAttributesLine, networkStoreRepository.getAllExtensionsAttributesByResourceType(NETWORK_UUID, 1, ResourceType.LINE));

        networkStoreRepository.removeExtensionAttributes(NETWORK_UUID, 1, lineId1, ActivePowerControl.NAME);
        networkStoreRepository.removeExtensionAttributes(NETWORK_UUID, 1, lineId1, OperatingStatus.NAME);
        networkStoreRepository.removeExtensionAttributes(NETWORK_UUID, 1, lineId2, ActivePowerControl.NAME);

        assertEquals(Optional.empty(), networkStoreRepository.getExtensionAttributes(NETWORK_UUID, 1, lineId1, ActivePowerControl.NAME));
        assertEquals(Map.of(), networkStoreRepository.getAllExtensionsAttributesByIdentifiableId(NETWORK_UUID, 1, lineId1));
        assertEquals(Map.of(OperatingStatus.NAME, buildOperatingStatusAttributes("status2")), networkStoreRepository.getAllExtensionsAttributesByIdentifiableId(NETWORK_UUID, 1, lineId2));
        Map<String, ExtensionAttributes> expExtensionAttributesOsLine = Map.of(lineId2, buildOperatingStatusAttributes("status2"));
        assertEquals(Map.of(), networkStoreRepository.getAllExtensionsAttributesByResourceTypeAndExtensionName(NETWORK_UUID, 1, ResourceType.LINE, ActivePowerControl.NAME));
        assertEquals(expExtensionAttributesOsLine, networkStoreRepository.getAllExtensionsAttributesByResourceTypeAndExtensionName(NETWORK_UUID, 1, ResourceType.LINE, OperatingStatus.NAME));
        expExtensionAttributesLine = Map.of(lineId2, Map.of(OperatingStatus.NAME, buildOperatingStatusAttributes("status2")));
        assertEquals(expExtensionAttributesLine, networkStoreRepository.getAllExtensionsAttributesByResourceType(NETWORK_UUID, 1, ResourceType.LINE));
    }

    @Test
    void removeExtensionWithoutNetwork() {
        PowsyblException exception = assertThrows(PowsyblException.class, () -> networkStoreRepository.removeExtensionAttributes(NETWORK_UUID, 0, "unknownId", "unknownExtension"));
        assertTrue(exception.getMessage().contains("Cannot retrieve source network attributes"));
    }

    @Test
    void removeExtensionOnFullVariant() {
        String networkId = "network1";
        String lineId = "line1";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL);
        OwnerInfo ownerInfo = new OwnerInfo(lineId, ResourceType.LINE, NETWORK_UUID, 0);
        Map<String, ExtensionAttributes> extensionAttributesMap = buildExtensionAttributesMap(5.6, "status1");
        createExtensions(Map.of(ownerInfo, extensionAttributesMap));

        networkStoreRepository.removeExtensionAttributes(NETWORK_UUID, 0, lineId, ActivePowerControl.NAME);

        assertEquals(Map.of(OperatingStatus.NAME, buildOperatingStatusAttributes("status1")), networkStoreRepository.getAllExtensionsAttributesByIdentifiableId(NETWORK_UUID, 0, lineId));
        assertTrue(getTombstonedExtensions(NETWORK_UUID, 0).isEmpty());
    }

    @Test
    @Disabled("Not implemented")
    void removeExtensionNotExistingOnFullVariant() {
        String networkId = "network1";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL);
        assertThrows(PowsyblException.class, () -> networkStoreRepository.removeExtensionAttributes(NETWORK_UUID, 0, "notExistingId", "notExistingExtension"));
        assertTrue(getTombstonedExtensions(NETWORK_UUID, 0).isEmpty());
    }

    @Test
    void removeExtensionOnPartialVariant() {
        String networkId = "network1";
        String lineId = "line1";
        createPartialNetwork(networkId, 1, "variant1", VariantMode.PARTIAL, 0);
        OwnerInfo ownerInfo = new OwnerInfo(lineId, ResourceType.LINE, NETWORK_UUID, 1);
        Map<String, ExtensionAttributes> extensionAttributesMap = buildExtensionAttributesMap(5.6, "status1");
        createExtensions(Map.of(ownerInfo, extensionAttributesMap));

        networkStoreRepository.removeExtensionAttributes(NETWORK_UUID, 1, lineId, ActivePowerControl.NAME);
        networkStoreRepository.removeExtensionAttributes(NETWORK_UUID, 1, lineId, OperatingStatus.NAME);

        assertEquals(Map.of(), networkStoreRepository.getAllExtensionsAttributesByIdentifiableId(NETWORK_UUID, 1, lineId));
        assertEquals(Map.of(lineId, Set.of(ActivePowerControl.NAME, OperatingStatus.NAME)), getTombstonedExtensions(NETWORK_UUID, 1));
    }

    @Test
    @Disabled("Not implemented")
    void removeExtensionNotExistingOnPartialVariant() {
        String networkId = "network1";
        createPartialNetwork(networkId, 1, "variant1", VariantMode.PARTIAL, 0);

        assertThrows(PowsyblException.class, () -> networkStoreRepository.removeExtensionAttributes(NETWORK_UUID, 1, "notExistingId", "notExistingExtension"));
        assertTrue(getTombstonedExtensions(NETWORK_UUID, 1).isEmpty());
    }

    @Test
    void createExtensionWithRecreatedTombstonedExtension() {
        String networkId = "network1";
        String lineId = "line1";
        // Variant 0
        createPartialNetwork(networkId, 1, "variant1", VariantMode.PARTIAL, 0);
        OwnerInfo ownerInfo1 = new OwnerInfo(lineId, ResourceType.LINE, NETWORK_UUID, 1);
        Map<String, ExtensionAttributes> extensionAttributesMap = buildExtensionAttributesMap(5.6, "status1");
        createExtensions(Map.of(ownerInfo1, extensionAttributesMap));
        networkStoreRepository.removeExtensionAttributes(NETWORK_UUID, 1, lineId, ActivePowerControl.NAME);
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 1, 2, "variant1", VariantMode.PARTIAL);
        // Variant 2
        OwnerInfo ownerInfo2 = new OwnerInfo(lineId, ResourceType.LINE, NETWORK_UUID, 2);
        extensionAttributesMap = Map.of(ActivePowerControl.NAME, buildActivePowerControlAttributes(8.4));
        createExtensions(Map.of(ownerInfo2, extensionAttributesMap));

        // Variant 1 (removed line1)
        assertEquals(Map.of(OperatingStatus.NAME, buildOperatingStatusAttributes("status1")), networkStoreRepository.getAllExtensionsAttributesByIdentifiableId(NETWORK_UUID, 1, lineId));
        assertEquals(Map.of(lineId, Set.of(ActivePowerControl.NAME)), getTombstonedExtensions(NETWORK_UUID, 1));
        // Variant 2 (recreated line1 with different attributes)
        assertEquals(Map.of(OperatingStatus.NAME, buildOperatingStatusAttributes("status1"), ActivePowerControl.NAME, buildActivePowerControlAttributes(8.4)), networkStoreRepository.getAllExtensionsAttributesByIdentifiableId(NETWORK_UUID, 2, lineId));
        assertEquals(Map.of(), getTombstonedExtensions(NETWORK_UUID, 2));
    }

    @Test
    void emptyCreateExtensionsDoesNotThrow() {
        assertDoesNotThrow(() -> createExtensions(Map.of()));
        assertDoesNotThrow(() -> createExtensions(Map.of(new OwnerInfo("id", ResourceType.LINE, NETWORK_UUID, 0), Map.of())));
    }

    @Test
    void emptyCreateIdentifiablesDoesNotThrow() {
        assertDoesNotThrow(() -> networkStoreRepository.createIdentifiables(NETWORK_UUID, List.of(), mappings.getLoadMappings()));
    }

    //TODO: getRegulatingEquipmentsForIdentifiable()

    //TODO: does it work well for other something like
    //TODO: getExtension activepowercontrol => not exist in partial but exist in source, should not retrieve it in source because partial was updated => need to check that!
    //TODO: this is a bit similar to in voltagelevelcontainer... ! even getIdentifiable ? or is it ok?
    //TODO: needed?
    private List<String> getStoredIdentifiableIdsInVariant(UUID networkUuid, int variantNum) {
        try (var connection = dataSource.getConnection()) {
            return NetworkStoreRepository.getIdentifiablesIdsForVariant(connection, networkUuid, variantNum);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    //TODO: needed?
    private List<Resource<IdentifiableAttributes>> getIdentifiablesForVariant(UUID networkUuid, int variantNum, TableMapping tableMapping) {
        try (var connection = dataSource.getConnection()) {
            return networkStoreRepository.getIdentifiablesForVariant(connection, networkUuid, variantNum, tableMapping, variantNum);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    //TODO: needed?
    private Set<String> getTombstonedIdentifiableIdsInVariant(UUID networkUuid, int variantNum) {
        try (var connection = dataSource.getConnection()) {
            return networkStoreRepository.getTombstonedIdentifiableIds(connection, networkUuid, variantNum);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    //TODO: needed? / naming?
    private Map<String, Set<String>> getTombstonedExtensions(UUID networkUuid, int variantNum) {
        try (var connection = dataSource.getConnection()) {
            return extensionHandler.getTombstonedExtensions(connection, networkUuid, variantNum);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private void createExtensions(Map<OwnerInfo, Map<String, ExtensionAttributes>> extensions) {
        try (var connection = dataSource.getConnection()) {
            extensionHandler.createExtensions(connection, extensions);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

}