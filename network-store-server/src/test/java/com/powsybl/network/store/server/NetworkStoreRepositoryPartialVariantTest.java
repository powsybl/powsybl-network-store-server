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
import java.sql.SQLException;
import java.util.*;

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

    @DynamicPropertySource
    static void makeTestDbSuffix(DynamicPropertyRegistry registry) {
        UUID uuid = UUID.randomUUID();
        registry.add("testDbSuffix", () -> uuid);
    }

    @Autowired
    private NetworkStoreRepository networkStoreRepository;

    @Autowired
    private DataSource dataSource;

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
    void cloneAllVariantsOfNetwork() {
        String networkId = "network1";
        String loadId1 = "load1";
        String lineId1 = "line1";
        String loadId2 = "load2";
        String lineId2 = "line2";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL, -1);
        createLineAndLoad(0, loadId1, lineId1);
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);
        createLineAndLoad(1, loadId2, lineId2);
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
        assertEquals(List.of(loadId1, lineId1), getStoredIdentifiablesInVariant(NETWORK_UUID, 0));

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
        assertEquals(List.of(loadId2, lineId2), getStoredIdentifiablesInVariant(NETWORK_UUID, 1));
    }

    @Test
    void cloneAllVariantsOfNetworkWithExternalAttributes() {
        String networkId = "network1";
        String lineId1 = "line1";
        String lineId2 = "line2";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL, -1);
        createLineWithExternalAttributes(0, lineId1);
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);
        createLineWithExternalAttributes(1, lineId2);
        UUID targetNetworkUuid = UUID.fromString("0dd45074-009d-49b8-877f-8ae648a8e8b4");

        networkStoreRepository.cloneNetwork(targetNetworkUuid, NETWORK_UUID, List.of("variant0", "variant1"));

        // Check variant 0
        assertEquals(List.of(lineId1), getStoredIdentifiablesInVariant(NETWORK_UUID, 0));
        verifyExternalAttributes(lineId1, 0, NETWORK_UUID);

        // Check variant 1
        assertEquals(List.of(lineId2), getStoredIdentifiablesInVariant(NETWORK_UUID, 1));
        verifyExternalAttributes(lineId2, 1, NETWORK_UUID);
    }

    private void verifyExternalAttributes(String lineId, int variantNum, UUID networkUuid) {
        OwnerInfo ownerInfo = new OwnerInfo(lineId, ResourceType.LINE, networkUuid, variantNum);

        // Tap Changer Steps
        List<TapChangerStepAttributes> tapChangerSteps = networkStoreRepository.getTapChangerSteps(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, lineId).get(ownerInfo);
        assertEquals(2, tapChangerSteps.size());
        assertEquals(1.0, tapChangerSteps.get(0).getRho());
        assertEquals(2.0, tapChangerSteps.get(1).getRho());

        // Temporary Limits
        List<TemporaryLimitAttributes> temporaryLimits = networkStoreRepository.getTemporaryLimits(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, lineId).get(ownerInfo);
        assertEquals(2, temporaryLimits.size());
        assertEquals(100, temporaryLimits.get(0).getAcceptableDuration());
        assertEquals(200, temporaryLimits.get(1).getAcceptableDuration());

        // Permanent Limits
        List<PermanentLimitAttributes> permanentLimits = networkStoreRepository.getPermanentLimits(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, lineId).get(ownerInfo);
        assertEquals(2, permanentLimits.size());
        assertEquals(2.5, permanentLimits.get(0).getValue());
        assertEquals(2.6, permanentLimits.get(1).getValue());

        // Reactive Capability Curve Points
        List<ReactiveCapabilityCurvePointAttributes> curvePoints = networkStoreRepository.getReactiveCapabilityCurvePoints(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, lineId).get(ownerInfo);
        assertEquals(2, curvePoints.size());
        assertEquals(-100.0, curvePoints.get(0).getMinQ());
        assertEquals(30.0, curvePoints.get(1).getMaxQ());

        // Regulating Points
        RegulatingPointAttributes regulatingPoint = networkStoreRepository.getRegulatingPoints(networkUuid, variantNum, ResourceType.LINE).get(ownerInfo);
        assertNotNull(regulatingPoint);
        assertEquals("regulationMode", regulatingPoint.getRegulationMode());

        // Extensions
        Map<String, ExtensionAttributes> extensions = extensionHandler.getAllExtensionsAttributesByIdentifiableId(networkUuid, variantNum, lineId);
        assertEquals(2, extensions.size());
        assertTrue(extensions.containsKey("activePowerControl"));
        assertTrue(extensions.containsKey("operatingStatus"));
        ActivePowerControlAttributes activePowerControl = (ActivePowerControlAttributes) extensions.get("activePowerControl");
        assertEquals(6.0, activePowerControl.getDroop());
        OperatingStatusAttributes operatingStatus = (OperatingStatusAttributes) extensions.get("operatingStatus");
        assertEquals("test12", operatingStatus.getOperatingStatus());
    }

    private void verifyEmptyExternalAttributes(String lineId, int variantNum, UUID networkUuid) {
        // Tap Changer Steps
        assertTrue(networkStoreRepository.getTapChangerStepsFromVariant(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, lineId, variantNum).isEmpty());

        // Temporary Limits
        assertTrue(networkStoreRepository.getTemporaryLimitsForVariant(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, lineId, variantNum).isEmpty());

        // Permanent Limits
        assertTrue(networkStoreRepository.getPermanentLimitsForVariant(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, lineId, variantNum).isEmpty());

        // Reactive Capability Curve Points
        assertTrue(networkStoreRepository.getReactiveCapabilityCurvePointsForVariant(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, lineId, variantNum).isEmpty());

        // Regulating Points
        OwnerInfo ownerInfo = new OwnerInfo(lineId, ResourceType.LINE, networkUuid, variantNum);
        assertNull(networkStoreRepository.getRegulatingPointsForVariant(networkUuid, variantNum, ResourceType.LINE, variantNum).get(ownerInfo));

        // Extensions
        assertTrue(extensionHandler.getAllExtensionsAttributesByIdentifiableId(networkUuid, variantNum, lineId).isEmpty());
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
        TapChangerStepAttributes ratioStepA1 = TapChangerStepAttributes.builder()
                .rho(1.)
                .r(1.)
                .g(1.)
                .b(1.)
                .x(1.)
                .side(0)
                .index(0)
                .type(TapChangerType.RATIO)
                .build();
        TapChangerStepAttributes ratioStepA2 = TapChangerStepAttributes.builder()
                .rho(2.)
                .r(2.)
                .g(2.)
                .b(2.)
                .x(2.)
                .side(0)
                .index(1)
                .type(TapChangerType.RATIO)
                .build();
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
        extensionHandler.insertExtensions(Map.of(ownerInfo, extensionAttributes));
    }

    @Test
    void cloneAllVariantsOfNetworkWithTombstoned() {
        String networkId = "network1";
        String loadId1 = "load1";
        String lineId1 = "line1";
        String loadId2 = "load2";
        String lineId2 = "line2";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL, -1);
        createLineAndLoad(0, loadId1, lineId1);
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);
        createLineAndLoad(1, loadId2, lineId2);
        networkStoreRepository.deleteIdentifiable(NETWORK_UUID, 0, lineId1, LINE_TABLE);
        networkStoreRepository.deleteIdentifiable(NETWORK_UUID, 1, loadId1, LOAD_TABLE);
        networkStoreRepository.deleteIdentifiable(NETWORK_UUID, 1, loadId2, LOAD_TABLE);
        UUID targetNetworkUuid = UUID.fromString("0dd45074-009d-49b8-877f-8ae648a8e8b4");

        networkStoreRepository.cloneNetwork(targetNetworkUuid, NETWORK_UUID, List.of("variant0", "variant1"));

        assertEquals(List.of(loadId1), getStoredIdentifiablesInVariant(NETWORK_UUID, 0));
        assertEquals(List.of(lineId2), getStoredIdentifiablesInVariant(NETWORK_UUID, 1));
        assertEquals(List.of(loadId1, loadId2), networkStoreRepository.getTombstonedIdentifiables(NETWORK_UUID, 1));
    }

    @Test
    void clonePartialVariantInPartialMode() {
        String networkId = "network1";
        String loadId2 = "load2";
        String lineId2 = "line2";
        createPartialNetwork(networkId, 1, "variant1", VariantMode.PARTIAL, 0);
        createLineAndLoad(1, loadId2, lineId2);
        networkStoreRepository.deleteIdentifiable(NETWORK_UUID, 1, loadId2, LOAD_TABLE);

        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 1, 2, "variant2", VariantMode.PARTIAL);

        assertEquals(List.of(lineId2), getStoredIdentifiablesInVariant(NETWORK_UUID, 2));
        assertEquals(List.of(loadId2), networkStoreRepository.getTombstonedIdentifiables(NETWORK_UUID, 2));
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
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL, -1);
        createLineAndLoad(0, loadId1, lineId1);
        networkStoreRepository.deleteIdentifiable(NETWORK_UUID, 0, loadId1, LOAD_TABLE);

        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);

        assertTrue(getStoredIdentifiablesInVariant(NETWORK_UUID, 1).isEmpty());
        assertTrue(networkStoreRepository.getTombstonedIdentifiables(NETWORK_UUID, 1).isEmpty());
    }

    @Test
    void cloneFullVariantInPartialModeWithExternalAttributes() {
        String networkId = "network1";
        String lineId1 = "line1";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL, -1);
        createLineWithExternalAttributes(1, lineId1);

        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);

        verifyEmptyExternalAttributes(lineId1, 2, NETWORK_UUID);
    }

    @Test
    void cloneFullVariantInFullMode() {
        String networkId = "network1";
        String loadId1 = "load1";
        String lineId1 = "line1";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.FULL, -1);
        createLineAndLoad(0, loadId1, lineId1);

        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.FULL);

        assertEquals(List.of(loadId1, lineId1), getStoredIdentifiablesInVariant(NETWORK_UUID, 1));
    }

    @Test
    void cloneFullVariantInFullModeWithExternalAttributes() {
        String networkId = "network1";
        String lineId1 = "line1";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.FULL, -1);
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
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL, -1);
        createLineAndLoad(0, loadId1, lineId1);
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);
        createLineAndLoad(1, loadId2, lineId2);
        networkStoreRepository.deleteIdentifiable(NETWORK_UUID, 0, loadId1, LOAD_TABLE);
        networkStoreRepository.deleteIdentifiable(NETWORK_UUID, 1, lineId1, LINE_TABLE);

        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 1, 2, "variant2", VariantMode.FULL);

        assertEquals(List.of(loadId2, lineId2), getStoredIdentifiablesInVariant(NETWORK_UUID, 2));
        assertTrue(networkStoreRepository.getTombstonedIdentifiables(NETWORK_UUID, 2).isEmpty());
    }

    @Test
    @Disabled("To implement")
    void clonePartialVariantInFullModeWithExternalAttributes() {
        String networkId = "network1";
        String lineId1 = "line1";
        String lineId2 = "line2";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL, -1);
        createLineWithExternalAttributes(0, lineId1);
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);
        createLineWithExternalAttributes(1, lineId2);

        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 1, 2, "variant2", VariantMode.FULL);

        verifyExternalAttributes(lineId1, 2, NETWORK_UUID);
        verifyExternalAttributes(lineId2, 2, NETWORK_UUID);
    }

    @Test
    void getIdentifiablesIdsWithoutNetwork() {
        PowsyblException exception = assertThrows(PowsyblException.class, () -> networkStoreRepository.getIdentifiablesIds(NETWORK_UUID, 0));
        assertTrue(exception.getMessage().contains("Cannot retrieve source network attributes"));
    }

    @Test
    void getIdentifiablesIdsFromPartialCloneWithoutCreatedIdentifiables() {
        String networkId = "network1";
        String loadId = "load1";
        String lineId = "line1";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL, -1);
        createLineAndLoad(0, loadId, lineId);
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);

        List<String> identifiablesIds = networkStoreRepository.getIdentifiablesIds(NETWORK_UUID, 1);

        assertEquals(List.of(loadId, lineId), identifiablesIds);
    }

    private void createSourceNetwork(String networkId, int variantNum, String variantId, VariantMode variantMode, int srcVariantNum) {
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

    private void createLineAndLoad(int variantNum, String loadId, String lineId) {
        Resource<LineAttributes> line1 = Resource.lineBuilder()
                .id(lineId)
                .variantNum(variantNum)
                .attributes(LineAttributes.builder()
                        .voltageLevelId1("vl1")
                        .voltageLevelId2("vl2")
                        .build())
                .build();
        Resource<LoadAttributes> load1 = Resource.loadBuilder()
                .id(loadId)
                .variantNum(variantNum)
                .attributes(LoadAttributes.builder()
                        .voltageLevelId("vl1")
                        .build())
                .build();
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
    void getIdentifiablesIdsFromPartialCloneWithCreatedIdentifiables() {
        String networkId = "network1";
        String loadId1 = "load1";
        String lineId1 = "line1";
        String loadId2 = "load2";
        String lineId2 = "line2";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL, -1);
        createLineAndLoad(0, loadId1, lineId1);
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);
        createLineAndLoad(1, loadId2, lineId2);

        List<String> identifiablesIds = networkStoreRepository.getIdentifiablesIds(NETWORK_UUID, 1);

        assertEquals(List.of(loadId1, lineId1, loadId2, lineId2), identifiablesIds);
    }

    @Test
    void getIdentifiablesIdsFromPartialCloneWithUpdatedIdentifiables() {
        String networkId = "network1";
        String loadId1 = "load1";
        String lineId1 = "line1";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL, -1);
        createLineAndLoad(0, loadId1, lineId1);
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);
        createLineAndLoad(1, loadId1, lineId1);

        List<String> identifiablesIds = networkStoreRepository.getIdentifiablesIds(NETWORK_UUID, 1);

        assertEquals(List.of(loadId1, lineId1), identifiablesIds);
    }

    @Test
    void getIdentifiablesIdsFromFullClone() {
        String networkId = "network1";
        String loadId1 = "load1";
        String lineId1 = "line1";
        createSourceNetwork(networkId, 2, "variant2", VariantMode.PARTIAL, -1);
        createLineAndLoad(2, loadId1, lineId1);

        List<String> identifiablesIds = networkStoreRepository.getIdentifiablesIds(NETWORK_UUID, 2);

        assertEquals(List.of(loadId1, lineId1), identifiablesIds);
    }

    @Test
    void getIdentifiablesIdsFromPartialCloneWithoutCreatedIdentifiablesWithTombstoned() {
        String networkId = "network1";
        String loadId = "load1";
        String lineId = "line1";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL, -1);
        createLineAndLoad(0, loadId, lineId);
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);
        networkStoreRepository.deleteIdentifiable(NETWORK_UUID, 1, loadId, LOAD_TABLE);

        List<String> identifiablesIds = networkStoreRepository.getIdentifiablesIds(NETWORK_UUID, 1);

        assertEquals(List.of(lineId), identifiablesIds);
    }

    @Test
    void getIdentifiablesIdsFromPartialCloneWithCreatedIdentifiablesWithTombstoned() {
        String networkId = "network1";
        String loadId1 = "load1";
        String lineId1 = "line1";
        String loadId2 = "load2";
        String lineId2 = "line2";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL, -1);
        createLineAndLoad(0, loadId1, lineId1);
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1", VariantMode.PARTIAL);
        createLineAndLoad(1, loadId2, lineId2);
        networkStoreRepository.deleteIdentifiable(NETWORK_UUID, 0, lineId1, LINE_TABLE);
        networkStoreRepository.deleteIdentifiable(NETWORK_UUID, 1, loadId1, LOAD_TABLE);
        networkStoreRepository.deleteIdentifiable(NETWORK_UUID, 1, loadId2, LOAD_TABLE);

        List<String> identifiablesIds = networkStoreRepository.getIdentifiablesIds(NETWORK_UUID, 1);

        assertEquals(List.of(lineId2), identifiablesIds);
    }

    @Test
    void deleteIdentifiableWithoutNetwork() {
        String loadId1 = "load1";
        String lineId1 = "line1";
        createLineAndLoad(0, loadId1, lineId1);
        PowsyblException exception = assertThrows(PowsyblException.class, () -> networkStoreRepository.deleteIdentifiable(NETWORK_UUID, 0, loadId1, LOAD_TABLE));
        assertTrue(exception.getMessage().contains("Cannot retrieve source network attributes"));
    }

    @Test
    void deleteIdentifiableOnFullVariant() {
        String networkId = "network1";
        String loadId1 = "load1";
        String lineId1 = "line1";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL, -1);
        createLineAndLoad(0, loadId1, lineId1);
        networkStoreRepository.deleteIdentifiable(NETWORK_UUID, 0, loadId1, LOAD_TABLE);

        assertEquals(List.of(lineId1), getStoredIdentifiablesInVariant(NETWORK_UUID, 0));
        assertTrue(networkStoreRepository.getTombstonedIdentifiables(NETWORK_UUID, 0).isEmpty());
    }

    @Test
    @Disabled("Not implemented")
    void deleteIdentifiableNotExistingOnFullVariant() {
        String networkId = "network1";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL, -1);
        assertThrows(PowsyblException.class, () -> networkStoreRepository.deleteIdentifiable(NETWORK_UUID, 0, "notExistingId", LOAD_TABLE));
        assertTrue(networkStoreRepository.getTombstonedIdentifiables(NETWORK_UUID, 0).isEmpty());
    }

    @Test
    void deleteIdentifiableOnPartialVariant() {
        String networkId = "network1";
        String loadId1 = "load1";
        String lineId1 = "line1";
        createPartialNetwork(networkId, 1, "variant1", VariantMode.PARTIAL, 0);
        createLineAndLoad(1, loadId1, lineId1);
        networkStoreRepository.deleteIdentifiable(NETWORK_UUID, 1, loadId1, LOAD_TABLE);

        assertEquals(List.of(lineId1), getStoredIdentifiablesInVariant(NETWORK_UUID, 1));
        assertEquals(List.of(loadId1), networkStoreRepository.getTombstonedIdentifiables(NETWORK_UUID, 1));
    }

    @Test
    @Disabled("Not implemented")
    void deleteIdentifiableNotExistingOnPartialVariant() {
        String networkId = "network1";
        createPartialNetwork(networkId, 1, "variant1", VariantMode.PARTIAL, 0);

        assertThrows(PowsyblException.class, () -> networkStoreRepository.deleteIdentifiable(NETWORK_UUID, 1, "notExistingId", LOAD_TABLE));
        assertTrue(networkStoreRepository.getTombstonedIdentifiables(NETWORK_UUID, 1).isEmpty());
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

        assertEquals(List.of(line2), networkStoreRepository.getIdentifiablesForVariant(NETWORK_UUID, 1, mappings.getLineMappings()));
        assertEquals(List.of("line2"), getStoredIdentifiablesInVariant(NETWORK_UUID, 1));
    }

    @Test
    @Disabled("Not implemented")
    void createIdentifiablesInPartialVariantAlreadyExistingInFullVariantThrows() {
        String networkId = "network1";
        createSourceNetwork(networkId, 0, "variant0", VariantMode.PARTIAL, -1);
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
    void createIdentifiablesWithRecreatedTombstoned() {
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
        assertTrue(networkStoreRepository.getIdentifiablesForVariant(NETWORK_UUID, 1, mappings.getLineMappings()).isEmpty());
        assertTrue(getStoredIdentifiablesInVariant(NETWORK_UUID, 1).isEmpty());
        assertEquals(List.of(lineId1), networkStoreRepository.getTombstonedIdentifiables(NETWORK_UUID, 1));
        // Variant 2 (recreated line1 with different attributes)
        assertEquals(List.of(lineVariant2), networkStoreRepository.getIdentifiablesForVariant(NETWORK_UUID, 2, mappings.getLineMappings()));
        assertEquals(List.of(lineId1), getStoredIdentifiablesInVariant(NETWORK_UUID, 2));
        assertTrue(networkStoreRepository.getTombstonedIdentifiables(NETWORK_UUID, 2).isEmpty());
    }

    //TODO: add tests for cloneNetwork without network? actually juste create a method to reuse everywhere
    private List<String> getStoredIdentifiablesInVariant(UUID networkUuid, int variantNum) {
        try (var connection = dataSource.getConnection()) {
            return NetworkStoreRepository.getIdentifiablesIdsForVariant(networkUuid, variantNum, connection);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }
}
