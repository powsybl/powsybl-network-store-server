/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.extensions.ActivePowerControl;
import com.powsybl.iidm.network.extensions.OperatingStatus;
import com.powsybl.network.store.model.*;
import com.powsybl.network.store.server.dto.OperationalLimitsGroupOwnerInfo;
import com.powsybl.network.store.server.dto.OwnerInfo;
import com.powsybl.network.store.server.dto.RegulatingOwnerInfo;
import com.powsybl.network.store.server.exceptions.UncheckedSqlException;
import org.junit.jupiter.api.Assertions;
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

import static com.powsybl.network.store.server.Mappings.*;
import static com.powsybl.network.store.server.QueryCatalog.*;
import static com.powsybl.network.store.server.utils.PartialVariantTestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Antoine Bouhours <antoine.bouhours at rte-france.com>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class NetworkStoreRepositoryPartialVariantExternalAttributesTest {

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
    void cloneAllVariantsOfNetworkWithExternalAttributes() {
        String networkId = "network1";
        String lineId1 = "line1";
        String genId1 = "gen1";
        String twoWTId1 = "twoWT1";
        String loadId1 = "load1";
        String lineId2 = "line2";
        String genId2 = "gen2";
        String twoWTId2 = "twoWT2";
        String loadId2 = "load2";
        String areaId1 = "area1";
        String areaId2 = "area2";
        createFullVariantNetwork(networkStoreRepository, NETWORK_UUID, networkId, 0, "variant0");
        createEquipmentsWithExternalAttributes(0, lineId1, genId1, twoWTId1, loadId1, areaId1);
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1");
        createEquipmentsWithExternalAttributes(1, lineId2, genId2, twoWTId2, loadId2, areaId2);
        UUID targetNetworkUuid = UUID.fromString("0dd45074-009d-49b8-877f-8ae648a8e8b4");

        networkStoreRepository.cloneNetwork(targetNetworkUuid, NETWORK_UUID, List.of("variant0", "variant1"));

        // Check variant 0
        verifyExternalAttributes(lineId1, genId1, twoWTId1, areaId1, 0, targetNetworkUuid);

        // Check variant 1
        verifyExternalAttributes(lineId2, genId2, twoWTId2, areaId2, 1, targetNetworkUuid);
    }

    private void createEquipmentsWithExternalAttributes(int variantNum, String lineId, String generatorId, String twoWTId, String loadId, String areaId) {
        createLine(networkStoreRepository, NETWORK_UUID, variantNum, lineId, "vl1", "vl2");
        createGeneratorAndLoadWithRegulatingAttributes(variantNum, generatorId, loadId, "vl1");
        createTwoWindingTransformer(variantNum, twoWTId, "vl1", "vl2");
        createArea(variantNum, areaId, "type");
        createExternalAttributes(variantNum, lineId, generatorId, twoWTId, areaId);
    }

    private void createExternalAttributes(int variantNum, String lineId, String generatorId, String twoWTId, String areaId) {
        // Tap changer steps
        OwnerInfo ownerInfoLine = new OwnerInfo(lineId, ResourceType.LINE, NETWORK_UUID, variantNum);
        OwnerInfo ownerInfoGen = new OwnerInfo(generatorId, ResourceType.GENERATOR, NETWORK_UUID, variantNum);
        OwnerInfo ownerInfoTwoWT = new OwnerInfo(twoWTId, ResourceType.TWO_WINDINGS_TRANSFORMER, NETWORK_UUID, variantNum);
        OwnerInfo ownerArea = new OwnerInfo(areaId, ResourceType.AREA, NETWORK_UUID, variantNum);
        TapChangerStepAttributes ratioStepA1 = buildTapChangerStepAttributes(1., 0);
        TapChangerStepAttributes ratioStepA2 = buildTapChangerStepAttributes(2., 1);
        networkStoreRepository.insertTapChangerSteps(Map.of(ownerInfoTwoWT, List.of(ratioStepA1, ratioStepA2)));
        // Temporary limits
        TreeMap<Integer, TemporaryLimitAttributes> temporaryLimits = new TreeMap<>();
        temporaryLimits.put(100, TemporaryLimitAttributes.builder()
                .acceptableDuration(100)
                .build());
        temporaryLimits.put(200, TemporaryLimitAttributes.builder()
                .acceptableDuration(200)
                .build());

        OperationalLimitsGroupAttributes operationalLimitsGroup1 = new OperationalLimitsGroupAttributes();
        operationalLimitsGroup1.setCurrentLimits(LimitsAttributes.builder().permanentLimit(2.5).build());
        operationalLimitsGroup1.setProperties(Map.of("prop1", "value1", "prop2", "value2"));
        OperationalLimitsGroupAttributes operationalLimitsGroup2 = new OperationalLimitsGroupAttributes();
        operationalLimitsGroup2.setCurrentLimits(LimitsAttributes.builder().permanentLimit(2.6).temporaryLimits(temporaryLimits).build());
        operationalLimitsGroup2.setProperties(Map.of("prop3", "value3", "prop4", "value4"));

        OperationalLimitsGroupOwnerInfo ownerInfoOlg1 = new OperationalLimitsGroupOwnerInfo(ownerInfoLine.getEquipmentId(), ownerInfoLine.getEquipmentType(), ownerInfoLine.getNetworkUuid(), ownerInfoLine.getVariantNum(), "group1", 1);
        OperationalLimitsGroupOwnerInfo ownerInfoOlg2 = new OperationalLimitsGroupOwnerInfo(ownerInfoLine.getEquipmentId(), ownerInfoLine.getEquipmentType(), ownerInfoLine.getNetworkUuid(), ownerInfoLine.getVariantNum(), "group1", 2);
        networkStoreRepository.getLimitsHandler().insertOperationalLimitsGroupAttributes(Map.of(ownerInfoOlg1, operationalLimitsGroup1, ownerInfoOlg2, operationalLimitsGroup2));
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
        networkStoreRepository.insertReactiveCapabilityCurvePoints(Map.of(ownerInfoGen, List.of(curvePointA, curvePointB)));
        // area boundaries
        List<AreaBoundaryAttributes> areaBoundaries = new ArrayList<>();
        areaBoundaries.add(AreaBoundaryAttributes.builder().areaId(areaId).boundaryDanglingLineId("danglingLine1").build());
        areaBoundaries.add(AreaBoundaryAttributes.builder().areaId(areaId).boundaryDanglingLineId("danglingLine2").build());
        networkStoreRepository.insertAreaBoundaries(Map.of(ownerArea, areaBoundaries));
        // Extensions
        Map<String, ExtensionAttributes> extensionAttributes = Map.of("activePowerControl", ActivePowerControlAttributes.builder().droop(6.0).participate(true).participationFactor(1.5).build(),
            "operatingStatus", OperatingStatusAttributes.builder().operatingStatus("test12").build());
        insertExtensions(Map.of(ownerInfoLine, extensionAttributes));
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

    private Resource<TwoWindingsTransformerAttributes> createTwoWindingTransformer(int variantNum, String twtId, String voltageLevel1, String voltageLevel2) {
        Resource<TwoWindingsTransformerAttributes> twoWT = Resource.twoWindingsTransformerBuilder()
            .id(twtId)
            .variantNum(variantNum)
            .attributes(TwoWindingsTransformerAttributes.builder()
                .voltageLevelId1(voltageLevel1)
                .voltageLevelId1(voltageLevel2)
                .build())
            .build();
        networkStoreRepository.createTwoWindingsTransformers(NETWORK_UUID, List.of(twoWT));
        return twoWT;
    }

    private Resource<AreaAttributes> createArea(int variantNum, String areaId, String areaType) {
        Resource<AreaAttributes> area = Resource.areaBuilder()
            .id(areaId)
            .variantNum(variantNum)
            .attributes(AreaAttributes.builder()
                .areaType(areaType)
                .build())
            .build();
        networkStoreRepository.createAreas(NETWORK_UUID, List.of(area));
        return area;
    }

    private void createGeneratorAndLoadWithRegulatingAttributes(int variantNum, String generatorId, String loadId, String voltageLevel) {
        Resource<GeneratorAttributes> gen = Resource.generatorBuilder()
            .id(generatorId)
            .variantNum(variantNum)
            .attributes(GeneratorAttributes.builder()
                .voltageLevelId(voltageLevel)
                .name(generatorId)
                .regulatingPoint(RegulatingPointAttributes.builder()
                    .localTerminal(TerminalRefAttributes.builder().connectableId(generatorId).build())
                    .regulatedResourceType(ResourceType.GENERATOR)
                    .regulatingEquipmentId(generatorId)
                    .regulatingTerminal(TerminalRefAttributes.builder().connectableId(generatorId).build())
                    .build())
                .build())
            .build();
        networkStoreRepository.createGenerators(NETWORK_UUID, List.of(gen));
        Resource<LoadAttributes> load1 = Resource.loadBuilder()
            .id(loadId)
            .variantNum(variantNum)
            .attributes(LoadAttributes.builder()
                .voltageLevelId(voltageLevel)
                .build())
            .build();
        networkStoreRepository.createLoads(NETWORK_UUID, List.of(load1));
    }

    private void verifyOperationalLimitsGroup(Map<Integer, Map<String, OperationalLimitsGroupAttributes>> limits) {
        List<OperationalLimitsGroupAttributes> operationalLimitsGroupAttributes1 = limits.get(1).values().stream().toList();
        List<OperationalLimitsGroupAttributes> operationalLimitsGroupAttributes2 = limits.get(2).values().stream().toList();
        assertEquals(1, operationalLimitsGroupAttributes1.size());
        assertEquals(1, operationalLimitsGroupAttributes2.size());
        LimitsAttributes currentLimits1 = operationalLimitsGroupAttributes1.getFirst().getCurrentLimits();
        LimitsAttributes currentLimits2 = operationalLimitsGroupAttributes2.getFirst().getCurrentLimits();
        assertEquals(2, currentLimits2.getTemporaryLimits().size());
        assertEquals(100, currentLimits2.getTemporaryLimits().get(100).getAcceptableDuration());
        assertEquals(200, currentLimits2.getTemporaryLimits().get(200).getAcceptableDuration());
        assertEquals(2.5, currentLimits1.getPermanentLimit());
        assertEquals(2.6, currentLimits2.getPermanentLimit());

        assertEquals(Map.of("prop1", "value1", "prop2", "value2"), operationalLimitsGroupAttributes1.getFirst().getProperties());
        assertEquals(Map.of("prop3", "value3", "prop4", "value4"), operationalLimitsGroupAttributes2.getFirst().getProperties());
    }

    private void verifyExternalAttributes(String lineId, String generatorId, String twoWTId, String areaId, int variantNum, UUID networkUuid) {
        OwnerInfo ownerInfoLine = new OwnerInfo(lineId, ResourceType.LINE, networkUuid, variantNum);
        OwnerInfo ownerInfoGen = new OwnerInfo(generatorId, ResourceType.GENERATOR, networkUuid, variantNum);
        OwnerInfo ownerInfoTwoWT = new OwnerInfo(twoWTId, ResourceType.TWO_WINDINGS_TRANSFORMER, networkUuid, variantNum);
        OwnerInfo ownerInfoArea = new OwnerInfo(areaId, null, networkUuid, variantNum);

        // Tap Changer Steps
        List<TapChangerStepAttributes> tapChangerSteps = networkStoreRepository.getTapChangerSteps(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, twoWTId).get(ownerInfoTwoWT);
        assertEquals(2, tapChangerSteps.size());
        assertEquals(1.0, tapChangerSteps.get(0).getRho());
        assertEquals(2.0, tapChangerSteps.get(1).getRho());

        tapChangerSteps = networkStoreRepository.getTapChangerStepsWithInClause(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, List.of(twoWTId)).get(ownerInfoTwoWT);
        assertEquals(2, tapChangerSteps.size());
        assertEquals(1.0, tapChangerSteps.get(0).getRho());
        assertEquals(2.0, tapChangerSteps.get(1).getRho());

        // Operational limits
        Map<Integer, Map<String, OperationalLimitsGroupAttributes>> limits = networkStoreRepository.getLimitsHandler().getOperationalLimitsGroup(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, lineId).get(ownerInfoLine);
        verifyOperationalLimitsGroup(limits);

        limits = networkStoreRepository.getLimitsHandler().getOperationalLimitsGroupWithInClause(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, List.of(lineId)).get(ownerInfoLine);
        verifyOperationalLimitsGroup(limits);

        // Reactive Capability Curve Points
        List<ReactiveCapabilityCurvePointAttributes> curvePoints = networkStoreRepository.getReactiveCapabilityCurvePoints(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, generatorId).get(ownerInfoGen);
        assertEquals(2, curvePoints.size());
        assertEquals(-100.0, curvePoints.get(0).getMinQ());
        assertEquals(30.0, curvePoints.get(1).getMaxQ());

        curvePoints = networkStoreRepository.getReactiveCapabilityCurvePointsWithInClause(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, List.of(generatorId)).get(ownerInfoGen);
        assertEquals(2, curvePoints.size());
        assertEquals(-100.0, curvePoints.get(0).getMinQ());
        assertEquals(30.0, curvePoints.get(1).getMaxQ());

        // Area Boundaries
        List<AreaBoundaryAttributes> areaBoundaries = networkStoreRepository.getAreaBoundaries(networkUuid, variantNum, AREA_ID_COLUMN, areaId).get(ownerInfoArea);
        assertEquals(2, areaBoundaries.size());
        assertEquals(areaId, areaBoundaries.get(0).getAreaId());
        assertEquals("danglingLine1", areaBoundaries.get(0).getBoundaryDanglingLineId());
        assertFalse(areaBoundaries.get(0).getAc());
        assertEquals(areaId, areaBoundaries.get(1).getAreaId());
        assertEquals("danglingLine2", areaBoundaries.get(1).getBoundaryDanglingLineId());
        assertFalse(areaBoundaries.get(1).getAc());
        areaBoundaries = networkStoreRepository.getAreaBoundariesWithInClause(networkUuid, variantNum, AREA_ID_COLUMN, List.of(areaId)).get(ownerInfoArea);
        assertEquals(2, areaBoundaries.size());
        assertEquals(areaId, areaBoundaries.get(0).getAreaId());
        assertEquals("danglingLine1", areaBoundaries.get(0).getBoundaryDanglingLineId());
        assertFalse(areaBoundaries.get(0).getAc());
        assertEquals(areaId, areaBoundaries.get(1).getAreaId());
        assertEquals("danglingLine2", areaBoundaries.get(1).getBoundaryDanglingLineId());
        assertFalse(areaBoundaries.get(1).getAc());

        // Regulating Points
        RegulatingOwnerInfo regulatingOwnerInfoGen = new RegulatingOwnerInfo(generatorId, ResourceType.GENERATOR, networkUuid, variantNum);
        RegulatingPointAttributes regulatingPoint = networkStoreRepository.getRegulatingPoints(networkUuid, variantNum, ResourceType.GENERATOR).get(regulatingOwnerInfoGen);
        assertNotNull(regulatingPoint);
        assertEquals(generatorId, regulatingPoint.getRegulatingEquipmentId());
        assertEquals(generatorId, regulatingPoint.getRegulatingTerminal().getConnectableId());
        assertEquals(generatorId, regulatingPoint.getLocalTerminal().getConnectableId());
        Set<RegulatingEquipmentIdentifier> regulatingEquipments = networkStoreRepository.getRegulatingEquipments(networkUuid, variantNum, ResourceType.GENERATOR).get(ownerInfoGen);
        assertEquals(1, regulatingEquipments.size());
        assertTrue(regulatingEquipments.contains(new RegulatingEquipmentIdentifier(generatorId, ResourceType.GENERATOR)));

        regulatingPoint = networkStoreRepository.getRegulatingPointsWithInClause(networkUuid, variantNum, REGULATING_EQUIPMENT_ID, List.of(generatorId), ResourceType.GENERATOR).get(regulatingOwnerInfoGen);
        assertNotNull(regulatingPoint);
        assertEquals(generatorId, regulatingPoint.getRegulatingEquipmentId());
        assertEquals(generatorId, regulatingPoint.getRegulatingTerminal().getConnectableId());
        assertEquals(generatorId, regulatingPoint.getLocalTerminal().getConnectableId());
        regulatingEquipments = networkStoreRepository.getRegulatingEquipmentsWithInClause(networkUuid, variantNum, "regulatingterminalconnectableid", List.of(generatorId), ResourceType.GENERATOR).get(ownerInfoGen);
        assertEquals(1, regulatingEquipments.size());
        assertTrue(regulatingEquipments.contains(new RegulatingEquipmentIdentifier(generatorId, ResourceType.GENERATOR)));

        regulatingEquipments = networkStoreRepository.getRegulatingEquipmentsForIdentifiable(networkUuid, variantNum, generatorId, ResourceType.GENERATOR);
        assertEquals(1, regulatingEquipments.size());
        assertTrue(regulatingEquipments.contains(new RegulatingEquipmentIdentifier(generatorId, ResourceType.GENERATOR)));

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

    @Test
    void cloneAllVariantsOfNetworkWithTombstonedExtension() {
        String networkId = "network1";
        String lineId1 = "line1";
        String lineId2 = "line2";
        createFullVariantNetwork(networkStoreRepository, NETWORK_UUID, networkId, 0, "variant0");
        OwnerInfo ownerInfo1 = new OwnerInfo(lineId1, ResourceType.LINE, NETWORK_UUID, 0);
        Map<String, ExtensionAttributes> extensionAttributesMap1 = buildExtensionAttributesMap(5.6, "status1");
        insertExtensions(Map.of(ownerInfo1, extensionAttributesMap1));
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1");
        OwnerInfo ownerInfo2 = new OwnerInfo(lineId2, ResourceType.LINE, NETWORK_UUID, 1);
        Map<String, ExtensionAttributes> extensionAttributesMap2 = buildExtensionAttributesMap(8.9, "status2");
        insertExtensions(Map.of(ownerInfo2, extensionAttributesMap2));
        networkStoreRepository.removeExtensionAttributes(NETWORK_UUID, 0, lineId1, ActivePowerControl.NAME);
        networkStoreRepository.removeExtensionAttributes(NETWORK_UUID, 1, lineId1, OperatingStatus.NAME);
        networkStoreRepository.removeExtensionAttributes(NETWORK_UUID, 1, lineId2, ActivePowerControl.NAME);
        UUID targetNetworkUuid = UUID.fromString("0dd45074-009d-49b8-877f-8ae648a8e8b4");

        networkStoreRepository.cloneNetwork(targetNetworkUuid, NETWORK_UUID, List.of("variant0", "variant1"));

        assertEquals(Map.of(lineId1, Set.of(OperatingStatus.NAME), lineId2, Set.of(ActivePowerControl.NAME)), getTombstonedExtensions(targetNetworkUuid, 1));
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
    void clonePartialVariantInPartialModeWithExternalAttributes() {
        String networkId = "network1";
        String lineId1 = "line1";
        String genId1 = "gen1";
        String twoWTId1 = "twoWT1";
        String loadId1 = "load1";
        String areaId1 = "area1";
        createNetwork(networkStoreRepository, NETWORK_UUID, networkId, 1, "variant1", 0);
        createEquipmentsWithExternalAttributes(1, lineId1, genId1, twoWTId1, loadId1, areaId1);

        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 1, 2, "variant2");

        verifyExternalAttributes(lineId1, genId1, twoWTId1, areaId1, 2, NETWORK_UUID);
    }

    @Test
    void cloneFullVariantInPartialModeWithExternalAttributes() {
        String networkId = "network1";
        String lineId1 = "line1";
        String genId1 = "gen1";
        String twoWTId1 = "twoWT1";
        String loadId1 = "load1";
        String areaId1 = "area1";
        createFullVariantNetwork(networkStoreRepository, NETWORK_UUID, networkId, 0, "variant0");
        createEquipmentsWithExternalAttributes(0, lineId1, genId1, twoWTId1, loadId1, areaId1);

        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1");

        verifyEmptyExternalAttributesForVariant(lineId1, genId1, twoWTId1, areaId1, 1, NETWORK_UUID);
    }

    private void verifyEmptyExternalAttributesForVariant(String lineId, String generatorId, String twoWTId, String areaId, int variantNum, UUID networkUuid) {
        try (Connection connection = dataSource.getConnection()) {
            // Tap Changer Steps
            assertTrue(networkStoreRepository.getTapChangerStepsForVariant(connection, networkUuid, variantNum, EQUIPMENT_ID_COLUMN, twoWTId, variantNum).isEmpty());

            // Operational Limits
            assertTrue(networkStoreRepository.getLimitsHandler().getOperationalLimitsGroupForVariant(connection, networkUuid, variantNum, EQUIPMENT_ID_COLUMN, lineId, variantNum).isEmpty());

            // Reactive Capability Curve Points
            assertTrue(networkStoreRepository.getReactiveCapabilityCurvePointsForVariant(connection, networkUuid, variantNum, EQUIPMENT_ID_COLUMN, generatorId, variantNum).isEmpty());

            // Regulating Points
            OwnerInfo ownerInfo = new OwnerInfo(generatorId, ResourceType.GENERATOR, networkUuid, variantNum);
            assertNull(networkStoreRepository.getRegulatingPointsForVariant(connection, networkUuid, variantNum, ResourceType.GENERATOR, variantNum).get(ownerInfo));

            // area boundaries
            ownerInfo = new OwnerInfo(generatorId, ResourceType.AREA, networkUuid, variantNum);
            assertNull(networkStoreRepository.getAreaBoundariesForVariant(connection, networkUuid, variantNum, AREA_ID_COLUMN, areaId, variantNum).get(ownerInfo));
            // Extensions
            assertTrue(extensionHandler.getAllExtensionsAttributesByIdentifiableIdForVariant(connection, networkUuid, variantNum, lineId).isEmpty());
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    @Test
    void getExternalAttributesWithoutNetwork() {
        List<Runnable> getExternalAttributesRunnables = List.of(
            () -> networkStoreRepository.getTapChangerSteps(NETWORK_UUID, 0, EQUIPMENT_ID_COLUMN, "unknownId"),
            () -> networkStoreRepository.getTapChangerStepsWithInClause(NETWORK_UUID, 0, EQUIPMENT_ID_COLUMN, List.of("unknownId")),
            () -> networkStoreRepository.getLimitsHandler().getOperationalLimitsGroup(NETWORK_UUID, 0, EQUIPMENT_ID_COLUMN, "unknownId"),
            () -> networkStoreRepository.getLimitsHandler().getOperationalLimitsGroupWithInClause(NETWORK_UUID, 0, EQUIPMENT_ID_COLUMN, List.of("unknownId")),
            () -> networkStoreRepository.getRegulatingPoints(NETWORK_UUID, 0, ResourceType.LINE),
            () -> networkStoreRepository.getRegulatingPointsWithInClause(NETWORK_UUID, 0, REGULATING_EQUIPMENT_ID, List.of("unknownId"), ResourceType.LINE),
            () -> networkStoreRepository.getRegulatingEquipments(NETWORK_UUID, 0, ResourceType.LINE),
            () -> networkStoreRepository.getRegulatingEquipmentsWithInClause(NETWORK_UUID, 0, REGULATING_EQUIPMENT_ID, List.of("unknownId"), ResourceType.LINE),
            () -> networkStoreRepository.getRegulatingEquipmentsForIdentifiable(NETWORK_UUID, 0, "unknownId", ResourceType.LINE),
            () -> networkStoreRepository.getReactiveCapabilityCurvePoints(NETWORK_UUID, 0, EQUIPMENT_ID_COLUMN, "unknownId"),
            () -> networkStoreRepository.getReactiveCapabilityCurvePointsWithInClause(NETWORK_UUID, 0, EQUIPMENT_ID_COLUMN, List.of("unknownId")),
            () -> networkStoreRepository.getAreaBoundaries(NETWORK_UUID, 0, AREA_ID_COLUMN, "unknownId"),
            () -> networkStoreRepository.getAreaBoundariesWithInClause(NETWORK_UUID, 0, AREA_ID_COLUMN, List.of("unknownId"))
        );

        getExternalAttributesRunnables.forEach(getExternalAttributesRunnable -> {
            PowsyblException exception = assertThrows(PowsyblException.class, getExternalAttributesRunnable::run);
            assertTrue(exception.getMessage().contains("Cannot retrieve source network attributes"));
        });
    }

    @Test
    void getExternalAttributesFromPartialCloneWithNoExternalAttributesInPartialVariant() {
        String networkId = "network1";
        String lineId = "line1";
        String genId = "gen1";
        String twoWTId = "twoWT1";
        String loadId = "load1";
        String areaId = "area1";
        createFullVariantNetwork(networkStoreRepository, NETWORK_UUID, networkId, 0, "variant0");
        createEquipmentsWithExternalAttributes(0, lineId, genId, twoWTId, loadId, areaId);
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1");

        verifyExternalAttributes(lineId, genId, twoWTId, areaId, 1, NETWORK_UUID);
    }

    @Test
    void getExternalAttributesFromPartialClone() {
        String networkId = "network1";
        String lineId = "line1";
        String genId = "gen1";
        String twoWTId = "twoWT1";
        String loadId = "load1";
        String areaId = "area1";
        createFullVariantNetwork(networkStoreRepository, NETWORK_UUID, networkId, 0, "variant0");
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1");
        createEquipmentsWithExternalAttributes(1, lineId, genId, twoWTId, loadId, areaId);

        verifyExternalAttributes(lineId, genId, twoWTId, areaId, 1, NETWORK_UUID);
    }

    @Test
    void getExternalAttributesFromPartialCloneWithUpdatedExternalAttributes() {
        String networkId = "network1";
        String lineId = "line";
        String genId = "gen1";
        String twoWTId = "twoWT1";
        String loadId = "load1";
        String areaId = "area1";
        createFullVariantNetwork(networkStoreRepository, NETWORK_UUID, networkId, 0, "variant0");
        createEquipmentsWithExternalAttributes(0, lineId, genId, twoWTId, loadId, areaId);
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1");
        updateExternalAttributes(1, lineId, genId, twoWTId, loadId);

        verifyUpdatedExternalAttributes(lineId, genId, twoWTId, loadId, 1, NETWORK_UUID);
    }

    private void updateExternalAttributes(int variantNum, String lineId, String generatorId, String twoWTId, String loadId) {
        // Equipements are updated too
        createLine(networkStoreRepository, NETWORK_UUID, variantNum, lineId, "vl1", "vl2");
        Resource<GeneratorAttributes> updatedGen = Resource.generatorBuilder()
            .id(generatorId)
            .variantNum(variantNum)
            .attributes(GeneratorAttributes.builder()
                .voltageLevelId("vl1")
                .name(generatorId)
                .regulatingPoint(RegulatingPointAttributes.builder()
                    .localTerminal(TerminalRefAttributes.builder().connectableId(generatorId).build())
                    .regulatingEquipmentId(generatorId)
                    .regulatingTerminal(TerminalRefAttributes.builder().connectableId(loadId).build())
                    .regulatedResourceType(ResourceType.LOAD)
                    .build())
                .build())
            .build();
        networkStoreRepository.updateGenerators(NETWORK_UUID, List.of(updatedGen));
        createTwoWindingTransformer(variantNum, twoWTId, "vl1", "vl2");
        // Tap changer steps
        OwnerInfo ownerInfoLine = new OwnerInfo(lineId, ResourceType.LINE, NETWORK_UUID, variantNum);
        OwnerInfo ownerInfoGen = new OwnerInfo(generatorId, ResourceType.GENERATOR, NETWORK_UUID, variantNum);
        OwnerInfo ownerInfoTwoWT = new OwnerInfo(twoWTId, ResourceType.TWO_WINDINGS_TRANSFORMER, NETWORK_UUID, variantNum);
        TapChangerStepAttributes ratioStepA1 = buildTapChangerStepAttributes(3., 0);
        networkStoreRepository.insertTapChangerSteps(Map.of(ownerInfoTwoWT, List.of(ratioStepA1)));
        // Temporary limits
        TemporaryLimitAttributes templimitA = TemporaryLimitAttributes.builder()
            .acceptableDuration(101)
            .build();
        TreeMap<Integer, TemporaryLimitAttributes> temporaryLimitAttributes = new TreeMap<>();
        temporaryLimitAttributes.put(101, templimitA);

        OperationalLimitsGroupAttributes operationalLimitGroup1 = new OperationalLimitsGroupAttributes();
        operationalLimitGroup1.setCurrentLimits(LimitsAttributes.builder().permanentLimit(2.8).build());
        operationalLimitGroup1.setProperties(Map.of("new_prop1", "new_value1", "new_prop2", "new_value2"));
        OperationalLimitsGroupAttributes operationalLimitGroup2 = new OperationalLimitsGroupAttributes();
        operationalLimitGroup2.setCurrentLimits(LimitsAttributes.builder().temporaryLimits(temporaryLimitAttributes).build());
        operationalLimitGroup2.setProperties(Map.of("new_prop3", "new_value3", "new_prop4", "new_value4"));

        OperationalLimitsGroupOwnerInfo ownerInfoOlg1 = new OperationalLimitsGroupOwnerInfo(ownerInfoLine.getEquipmentId(), ownerInfoLine.getEquipmentType(), ownerInfoLine.getNetworkUuid(), ownerInfoLine.getVariantNum(), "group1", 1);
        OperationalLimitsGroupOwnerInfo ownerInfoOlg2 = new OperationalLimitsGroupOwnerInfo(ownerInfoLine.getEquipmentId(), ownerInfoLine.getEquipmentType(), ownerInfoLine.getNetworkUuid(), ownerInfoLine.getVariantNum(), "group1", 2);
        networkStoreRepository.getLimitsHandler().insertOperationalLimitsGroupAttributes(Map.of(ownerInfoOlg1, operationalLimitGroup1, ownerInfoOlg2, operationalLimitGroup2));
        // Reactive capability curve points
        ReactiveCapabilityCurvePointAttributes curvePointA = ReactiveCapabilityCurvePointAttributes.builder()
            .minQ(-120.)
            .maxQ(100.)
            .p(0.)
            .build();
        networkStoreRepository.insertReactiveCapabilityCurvePoints(Map.of(ownerInfoGen, List.of(curvePointA)));
        // Extensions
        Map<String, ExtensionAttributes> extensionAttributes = Map.of("activePowerControl", ActivePowerControlAttributes.builder().droop(6.5).participate(true).participationFactor(1.5).build(),
            "operatingStatus", OperatingStatusAttributes.builder().operatingStatus("test123").build());
        insertExtensions(Map.of(ownerInfoLine, extensionAttributes));
    }

    private void verifyUpdatedOperationalLimitsGroup(Map<Integer, Map<String, OperationalLimitsGroupAttributes>> limits) {
        List<OperationalLimitsGroupAttributes> operationalLimitsGroupAttributes1 = limits.get(1).values().stream().toList();
        List<OperationalLimitsGroupAttributes> operationalLimitsGroupAttributes2 = limits.get(2).values().stream().toList();
        assertEquals(1, operationalLimitsGroupAttributes1.size());
        assertEquals(1, operationalLimitsGroupAttributes2.size());
        LimitsAttributes currentLimits1 = operationalLimitsGroupAttributes1.getFirst().getCurrentLimits();
        LimitsAttributes currentLimits2 = operationalLimitsGroupAttributes2.getFirst().getCurrentLimits();
        assertEquals(1, currentLimits2.getTemporaryLimits().size());
        assertEquals(101, currentLimits2.getTemporaryLimits().get(101).getAcceptableDuration());
        assertEquals(2.8, currentLimits1.getPermanentLimit());

        assertEquals(Map.of("new_prop1", "new_value1", "new_prop2", "new_value2"), operationalLimitsGroupAttributes1.getFirst().getProperties());
        assertEquals(Map.of("new_prop3", "new_value3", "new_prop4", "new_value4"), operationalLimitsGroupAttributes2.getFirst().getProperties());
    }

    private void verifyUpdatedExternalAttributes(String lineId, String generatorId, String twoWTId, String loadId, int variantNum, UUID networkUuid) {
        OwnerInfo ownerInfoLine = new OwnerInfo(lineId, ResourceType.LINE, NETWORK_UUID, variantNum);
        OwnerInfo ownerInfoGen = new OwnerInfo(generatorId, ResourceType.GENERATOR, NETWORK_UUID, variantNum);
        OwnerInfo ownerInfoTwoWT = new OwnerInfo(twoWTId, ResourceType.TWO_WINDINGS_TRANSFORMER, NETWORK_UUID, variantNum);
        OwnerInfo ownerInfoLoad = new OwnerInfo(loadId, ResourceType.LOAD, NETWORK_UUID, variantNum);

        // Tap Changer Steps
        List<TapChangerStepAttributes> tapChangerSteps = networkStoreRepository.getTapChangerSteps(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, twoWTId).get(ownerInfoTwoWT);
        assertEquals(1, tapChangerSteps.size());
        assertEquals(3.0, tapChangerSteps.get(0).getRho());

        tapChangerSteps = networkStoreRepository.getTapChangerStepsWithInClause(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, List.of(twoWTId)).get(ownerInfoTwoWT);
        assertEquals(1, tapChangerSteps.size());
        assertEquals(3.0, tapChangerSteps.get(0).getRho());

        // Temporary Limits
        Map<Integer, Map<String, OperationalLimitsGroupAttributes>> limits = networkStoreRepository.getLimitsHandler().getOperationalLimitsGroup(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, lineId).get(ownerInfoLine);
        verifyUpdatedOperationalLimitsGroup(limits);

        limits = networkStoreRepository.getLimitsHandler().getOperationalLimitsGroupWithInClause(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, List.of(lineId)).get(ownerInfoLine);
        verifyUpdatedOperationalLimitsGroup(limits);

        // Reactive Capability Curve Points
        List<ReactiveCapabilityCurvePointAttributes> curvePoints = networkStoreRepository.getReactiveCapabilityCurvePoints(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, generatorId).get(ownerInfoGen);
        assertEquals(1, curvePoints.size());
        assertEquals(-120.0, curvePoints.get(0).getMinQ());

        curvePoints = networkStoreRepository.getReactiveCapabilityCurvePointsWithInClause(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, List.of(generatorId)).get(ownerInfoGen);
        assertEquals(1, curvePoints.size());
        assertEquals(-120.0, curvePoints.get(0).getMinQ());

        // Regulating Points
        RegulatingOwnerInfo regulatingOwnerInfoGen = new RegulatingOwnerInfo(generatorId, ResourceType.GENERATOR, networkUuid, variantNum);
        RegulatingPointAttributes regulatingPoint = networkStoreRepository.getRegulatingPoints(networkUuid, variantNum, ResourceType.GENERATOR).get(regulatingOwnerInfoGen);
        assertNotNull(regulatingPoint);
        assertEquals(generatorId, regulatingPoint.getRegulatingEquipmentId());
        assertEquals(generatorId, regulatingPoint.getLocalTerminal().getConnectableId());
        assertEquals(loadId, regulatingPoint.getRegulatingTerminal().getConnectableId());
        Map<OwnerInfo, Set<RegulatingEquipmentIdentifier>> regulatingEquipmentsGen = networkStoreRepository.getRegulatingEquipments(networkUuid, variantNum, ResourceType.GENERATOR);
        assertTrue(regulatingEquipmentsGen.isEmpty());
        Set<RegulatingEquipmentIdentifier> regulatingEquipmentsLoad = networkStoreRepository.getRegulatingEquipments(networkUuid, variantNum, ResourceType.LOAD).get(ownerInfoLoad);
        assertEquals(1, regulatingEquipmentsLoad.size());
        assertTrue(regulatingEquipmentsLoad.contains(new RegulatingEquipmentIdentifier(generatorId, ResourceType.GENERATOR)));

        regulatingPoint = networkStoreRepository.getRegulatingPointsWithInClause(networkUuid, variantNum, REGULATING_EQUIPMENT_ID, List.of(generatorId), ResourceType.GENERATOR).get(regulatingOwnerInfoGen);
        assertNotNull(regulatingPoint);
        assertEquals(generatorId, regulatingPoint.getRegulatingEquipmentId());
        assertEquals(generatorId, regulatingPoint.getLocalTerminal().getConnectableId());
        assertEquals(loadId, regulatingPoint.getRegulatingTerminal().getConnectableId());
        regulatingEquipmentsGen = networkStoreRepository.getRegulatingEquipmentsWithInClause(networkUuid, variantNum, "regulatingterminalconnectableid", List.of(generatorId), ResourceType.GENERATOR);
        assertTrue(regulatingEquipmentsGen.isEmpty());
        regulatingEquipmentsLoad = networkStoreRepository.getRegulatingEquipmentsWithInClause(networkUuid, variantNum, "regulatingterminalconnectableid", List.of(loadId), ResourceType.LOAD).get(ownerInfoLoad);
        assertEquals(1, regulatingEquipmentsLoad.size());
        assertTrue(regulatingEquipmentsLoad.contains(new RegulatingEquipmentIdentifier(generatorId, ResourceType.GENERATOR)));

        assertTrue(networkStoreRepository.getRegulatingEquipmentsForIdentifiable(networkUuid, variantNum, generatorId, ResourceType.GENERATOR).isEmpty());
        regulatingEquipmentsLoad = networkStoreRepository.getRegulatingEquipmentsForIdentifiable(networkUuid, variantNum, loadId, ResourceType.LOAD);
        assertEquals(1, regulatingEquipmentsLoad.size());
        assertTrue(regulatingEquipmentsLoad.contains(new RegulatingEquipmentIdentifier(generatorId, ResourceType.GENERATOR)));

        // Extensions
        Map<String, ExtensionAttributes> extensions = networkStoreRepository.getAllExtensionsAttributesByIdentifiableId(networkUuid, variantNum, lineId);
        assertEquals(2, extensions.size());
        assertTrue(extensions.containsKey("activePowerControl"));
        assertTrue(extensions.containsKey("operatingStatus"));
        ActivePowerControlAttributes activePowerControl = (ActivePowerControlAttributes) extensions.get("activePowerControl");
        assertEquals(6.5, activePowerControl.getDroop());
        OperatingStatusAttributes operatingStatus = (OperatingStatusAttributes) extensions.get("operatingStatus");
        assertEquals("test123", operatingStatus.getOperatingStatus());
    }

    @Test
    void getExternalAttributesFromPartialCloneWithUpdatedSv() {
        String networkId = "network1";
        String lineId = "line";
        String genId = "gen1";
        String twoWTId = "twoWT1";
        String loadId = "load1";
        String areaId = "area1";
        createFullVariantNetwork(networkStoreRepository, NETWORK_UUID, networkId, 0, "variant0");
        createEquipmentsWithExternalAttributes(0, lineId, genId, twoWTId, loadId, areaId);
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1");
        createLine(networkStoreRepository, NETWORK_UUID, 1, lineId, "vl1", "vl2");

        verifyExternalAttributes(lineId, genId, twoWTId, areaId, 1, NETWORK_UUID);
    }

    @Test
    void getExternalAttributesFromPartialCloneWithUpdatedIdentifiableWithoutExternalAttributes() {
        String networkId = "network1";
        String lineId = "line";
        String genId = "gen1";
        String twoWTId = "twoWT1";
        String loadId = "load1";
        String areaId = "area1";
        createFullVariantNetwork(networkStoreRepository, NETWORK_UUID, networkId, 0, "variant0");
        createEquipmentsWithExternalAttributes(0, lineId, genId, twoWTId, loadId, areaId);
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1");

        verifyExternalAttributes(lineId, genId, twoWTId, areaId, 1, NETWORK_UUID);
        updateExternalAttributesWithTombstone(1, lineId, genId, twoWTId, areaId);
        OwnerInfo ownerInfoLine = new OwnerInfo(lineId, ResourceType.LINE, NETWORK_UUID, 1);
        OwnerInfo ownerInfoGen = new OwnerInfo(genId, ResourceType.GENERATOR, NETWORK_UUID, 1);
        OwnerInfo ownerInfoTwoWT = new OwnerInfo(twoWTId, ResourceType.TWO_WINDINGS_TRANSFORMER, NETWORK_UUID, 1);
        OwnerInfo ownerInfoArea = new OwnerInfo(areaId, null, NETWORK_UUID, 1);
        assertNull(networkStoreRepository.getTapChangerSteps(NETWORK_UUID, 1, EQUIPMENT_ID_COLUMN, twoWTId).get(ownerInfoTwoWT));
        assertNull(networkStoreRepository.getReactiveCapabilityCurvePoints(NETWORK_UUID, 1, EQUIPMENT_ID_COLUMN, genId).get(ownerInfoGen));
        assertNull(networkStoreRepository.getAreaBoundaries(NETWORK_UUID, 1, AREA_ID_COLUMN, genId).get(ownerInfoArea));
        // Set again a tombstone to verify that it does not throw
        updateExternalAttributesWithTombstone(1, lineId, genId, twoWTId, areaId);
        assertNull(networkStoreRepository.getTapChangerSteps(NETWORK_UUID, 1, EQUIPMENT_ID_COLUMN, twoWTId).get(ownerInfoTwoWT));
        assertNull(networkStoreRepository.getReactiveCapabilityCurvePoints(NETWORK_UUID, 1, EQUIPMENT_ID_COLUMN, genId).get(ownerInfoGen));
        assertNull(networkStoreRepository.getAreaBoundaries(NETWORK_UUID, 1, AREA_ID_COLUMN, genId).get(ownerInfoArea));
        // Recreate the external attributes and verify that the tombstone is ignored
        updateExternalAttributes(1, lineId, genId, twoWTId, loadId);
        verifyUpdatedExternalAttributes(lineId, genId, twoWTId, loadId, 1, NETWORK_UUID);
        // Set again a tombstone after recreating the external attributes
        updateExternalAttributesWithTombstone(1, lineId, genId, twoWTId, areaId);
        assertNull(networkStoreRepository.getTapChangerSteps(NETWORK_UUID, 1, EQUIPMENT_ID_COLUMN, twoWTId).get(ownerInfoTwoWT));
        assertNull(networkStoreRepository.getReactiveCapabilityCurvePoints(NETWORK_UUID, 1, EQUIPMENT_ID_COLUMN, genId).get(ownerInfoGen));
        assertNull(networkStoreRepository.getAreaBoundaries(NETWORK_UUID, 1, AREA_ID_COLUMN, genId).get(ownerInfoArea));
    }

    private void updateExternalAttributesWithTombstone(int variantNum, String lineId, String generatorId, String twoWTId, String areaId) {
        Resource<GeneratorAttributes> generator = new Resource<>(ResourceType.GENERATOR, generatorId, variantNum, null, new GeneratorAttributes());
        Resource<TwoWindingsTransformerAttributes> twoWT = new Resource<>(ResourceType.TWO_WINDINGS_TRANSFORMER, twoWTId, variantNum, null, new TwoWindingsTransformerAttributes());
        LineAttributes lineAttributes = new LineAttributes();
        Resource<LineAttributes> line = new Resource<>(ResourceType.LINE, lineId, variantNum, null, lineAttributes);
        Resource<AreaAttributes> area = new Resource<>(ResourceType.AREA, areaId, variantNum, null, new AreaAttributes());
        networkStoreRepository.updateTapChangerSteps(NETWORK_UUID, List.of(twoWT));
        networkStoreRepository.getLimitsHandler().updateOperationalLimitsGroup(NETWORK_UUID, List.of(line));
        networkStoreRepository.updateReactiveCapabilityCurvePoints(NETWORK_UUID, List.of(generator));
        networkStoreRepository.updateAreaBoundaries(NETWORK_UUID, List.of(area));
        // Regulating points and operational limits group can't be tombstoned for now so they're not tested
    }

    @Test
    void getExternalAttributesFromPartialCloneWithUpdatedIdentifiableSv() {
        String networkId = "network1";
        String lineId = "line";
        String genId = "gen1";
        String twoWTId = "twoWT1";
        String loadId = "load1";
        String areaId = "area1";
        createFullVariantNetwork(networkStoreRepository, NETWORK_UUID, networkId, 0, "variant0");
        createEquipmentsWithExternalAttributes(0, lineId, genId, twoWTId, loadId, areaId);
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1");

        BranchSvAttributes branchSvAttributes = BranchSvAttributes.builder()
            .p1(5.6)
            .q1(6.6)
            .build();
        Resource<BranchSvAttributes> updatedSvLine = new Resource<>(ResourceType.LINE, lineId, 1, AttributeFilter.SV, branchSvAttributes);
        Resource<BranchSvAttributes> updatedSvTwoWT = new Resource<>(ResourceType.TWO_WINDINGS_TRANSFORMER, twoWTId, 1, AttributeFilter.SV, branchSvAttributes);
        InjectionSvAttributes injectionSvAttributes = InjectionSvAttributes.builder()
            .p(5.6)
            .q(6.6)
            .build();
        Resource<InjectionSvAttributes> updatedSvGen = new Resource<>(ResourceType.GENERATOR, genId, 1, AttributeFilter.SV, injectionSvAttributes);
        networkStoreRepository.updateLinesSv(NETWORK_UUID, List.of(updatedSvLine));
        networkStoreRepository.updateGeneratorsSv(NETWORK_UUID, List.of(updatedSvGen));
        networkStoreRepository.updateTwoWindingsTransformersSv(NETWORK_UUID, List.of(updatedSvTwoWT));
        verifyExternalAttributes(lineId, genId, twoWTId, areaId, 1, NETWORK_UUID);
    }

    @Test
    void getExternalAttributesFromFullClone() {
        String networkId = "network1";
        String lineId = "line";
        String genId = "gen1";
        String twoWTId = "twoWT1";
        String loadId = "load1";
        String areaId = "area1";
        createFullVariantNetwork(networkStoreRepository, NETWORK_UUID, networkId, 2, "variant2");
        createEquipmentsWithExternalAttributes(2, lineId, genId, twoWTId, loadId, areaId);

        verifyExternalAttributes(lineId, genId, twoWTId, areaId, 2, NETWORK_UUID);
    }

    @Test
    void getExternalAttributesFromPartialCloneWithTombstonedIdentifiable() {
        String networkId = "network1";
        String lineId = "line1";
        String genId = "gen1";
        String twoWTId = "twoWT1";
        String loadId = "load1";
        String areaId = "area1";
        createFullVariantNetwork(networkStoreRepository, NETWORK_UUID, networkId, 0, "variant0");
        createEquipmentsWithExternalAttributes(0, lineId, genId, twoWTId, loadId, areaId);
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1");

        verifyExternalAttributes(lineId, genId, twoWTId, areaId, 0, NETWORK_UUID);
        networkStoreRepository.deleteIdentifiables(NETWORK_UUID, 1, Collections.singletonList(lineId), LINE_TABLE);
        networkStoreRepository.deleteIdentifiables(NETWORK_UUID, 1, Collections.singletonList(twoWTId), TWO_WINDINGS_TRANSFORMER_TABLE);
        networkStoreRepository.deleteIdentifiables(NETWORK_UUID, 1, Collections.singletonList(genId), GENERATOR_TABLE);
        verifyEmptyExternalAttributes(lineId, genId, twoWTId, 1, NETWORK_UUID);
    }

    private void verifyEmptyExternalAttributes(String lineId, String generatorId, String twoWTId, int variantNum, UUID networkUuid) {
        OwnerInfo ownerInfoLine = new OwnerInfo(lineId, ResourceType.LINE, networkUuid, variantNum);
        OwnerInfo ownerInfoGen = new OwnerInfo(generatorId, ResourceType.GENERATOR, networkUuid, variantNum);
        OwnerInfo ownerInfoTwoWT = new OwnerInfo(twoWTId, ResourceType.TWO_WINDINGS_TRANSFORMER, networkUuid, variantNum);

        // Tap Changer Steps
        assertNull(networkStoreRepository.getTapChangerSteps(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, twoWTId).get(ownerInfoTwoWT));

        // Operational limits
        assertNull(networkStoreRepository.getLimitsHandler().getOperationalLimitsGroup(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, lineId).get(ownerInfoLine));

        // Reactive Capability Curve Points
        assertNull(networkStoreRepository.getReactiveCapabilityCurvePoints(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, generatorId).get(ownerInfoGen));

        // Regulating Points
        assertNull(networkStoreRepository.getRegulatingPoints(networkUuid, variantNum, ResourceType.GENERATOR).get(ownerInfoGen));
    }

    @Test
    void getExtensionWithoutNetwork() {
        List<Runnable> getExtensionRunnables = List.of(
            () -> networkStoreRepository.getExtensionAttributes(NETWORK_UUID, 0, EQUIPMENT_ID_COLUMN, "unknownExtension"),
            () -> networkStoreRepository.getAllExtensionsAttributesByResourceType(NETWORK_UUID, 0, ResourceType.LINE),
            () -> networkStoreRepository.getAllExtensionsAttributesByResourceTypeAndExtensionName(NETWORK_UUID, 0, ResourceType.LINE, "unknownExtension"),
            () -> networkStoreRepository.getAllExtensionsAttributesByIdentifiableId(NETWORK_UUID, 0, "unknownId")
        );

        getExtensionRunnables.forEach(getExtensionRunnable -> {
            PowsyblException exception = assertThrows(PowsyblException.class, getExtensionRunnable::run);
            assertTrue(exception.getMessage().contains("Cannot retrieve source network attributes"));
        });
    }

    @Test
    void getExtensionFromPartialCloneWithNoExtensionInPartialVariant() {
        String networkId = "network1";
        String lineId1 = "line1";
        String lineId2 = "line2";
        createFullVariantNetwork(networkStoreRepository, NETWORK_UUID, networkId, 0, "variant0");
        OwnerInfo ownerInfo1 = new OwnerInfo(lineId1, ResourceType.LINE, NETWORK_UUID, 0);
        Map<String, ExtensionAttributes> extensionAttributesMap1 = buildExtensionAttributesMap(5.6, "status1");
        OwnerInfo ownerInfo2 = new OwnerInfo(lineId2, ResourceType.LINE, NETWORK_UUID, 0);
        Map<String, ExtensionAttributes> extensionAttributesMap2 = buildExtensionAttributesMap(8.9, "status2");
        insertExtensions(Map.of(ownerInfo1, extensionAttributesMap1, ownerInfo2, extensionAttributesMap2));
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1");

        Assertions.assertEquals(Optional.of(extensionAttributesMap1.get(ActivePowerControl.NAME)), networkStoreRepository.getExtensionAttributes(NETWORK_UUID, 1, lineId1, ActivePowerControl.NAME));
        Assertions.assertEquals(extensionAttributesMap1, networkStoreRepository.getAllExtensionsAttributesByIdentifiableId(NETWORK_UUID, 1, lineId1));
        Map<String, ExtensionAttributes> expExtensionAttributesApcLine = Map.of(lineId1, buildActivePowerControlAttributes(5.6), lineId2, buildActivePowerControlAttributes(8.9));
        Assertions.assertEquals(expExtensionAttributesApcLine, networkStoreRepository.getAllExtensionsAttributesByResourceTypeAndExtensionName(NETWORK_UUID, 1, ResourceType.LINE, ActivePowerControl.NAME));
        Map<String, Map<String, ExtensionAttributes>> expExtensionAttributesLine = Map.of(lineId1, extensionAttributesMap1, lineId2, extensionAttributesMap2);
        Assertions.assertEquals(expExtensionAttributesLine, networkStoreRepository.getAllExtensionsAttributesByResourceType(NETWORK_UUID, 1, ResourceType.LINE));
    }

    @Test
    void getExtensionFromPartialClone() {
        String networkId = "network1";
        String lineId1 = "line1";
        String lineId2 = "line2";
        createFullVariantNetwork(networkStoreRepository, NETWORK_UUID, networkId, 0, "variant0");
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1");
        OwnerInfo ownerInfo1 = new OwnerInfo(lineId1, ResourceType.LINE, NETWORK_UUID, 1);
        Map<String, ExtensionAttributes> extensionAttributesMap1 = buildExtensionAttributesMap(5.6, "status1");
        OwnerInfo ownerInfo2 = new OwnerInfo(lineId2, ResourceType.LINE, NETWORK_UUID, 1);
        Map<String, ExtensionAttributes> extensionAttributesMap2 = buildExtensionAttributesMap(8.9, "status2");
        insertExtensions(Map.of(ownerInfo1, extensionAttributesMap1, ownerInfo2, extensionAttributesMap2));

        Assertions.assertEquals(Optional.of(extensionAttributesMap1.get(ActivePowerControl.NAME)), networkStoreRepository.getExtensionAttributes(NETWORK_UUID, 1, lineId1, ActivePowerControl.NAME));
        Assertions.assertEquals(extensionAttributesMap1, networkStoreRepository.getAllExtensionsAttributesByIdentifiableId(NETWORK_UUID, 1, lineId1));
        Map<String, ExtensionAttributes> expExtensionAttributesApcLine = Map.of(lineId1, buildActivePowerControlAttributes(5.6), lineId2, buildActivePowerControlAttributes(8.9));
        Assertions.assertEquals(expExtensionAttributesApcLine, networkStoreRepository.getAllExtensionsAttributesByResourceTypeAndExtensionName(NETWORK_UUID, 1, ResourceType.LINE, ActivePowerControl.NAME));
        Map<String, Map<String, ExtensionAttributes>> expExtensionAttributesLine = Map.of(lineId1, extensionAttributesMap1, lineId2, extensionAttributesMap2);
        Assertions.assertEquals(expExtensionAttributesLine, networkStoreRepository.getAllExtensionsAttributesByResourceType(NETWORK_UUID, 1, ResourceType.LINE));
    }

    @Test
    void getExtensionFromPartialCloneWithUpdatedExtension() {
        String networkId = "network1";
        String lineId1 = "line1";
        String lineId2 = "line2";
        createFullVariantNetwork(networkStoreRepository, NETWORK_UUID, networkId, 0, "variant0");
        OwnerInfo ownerInfo1 = new OwnerInfo(lineId1, ResourceType.LINE, NETWORK_UUID, 0);
        Map<String, ExtensionAttributes> extensionAttributesMap1 = buildExtensionAttributesMap(5.6, "status1");
        OwnerInfo ownerInfo2 = new OwnerInfo(lineId2, ResourceType.LINE, NETWORK_UUID, 0);
        Map<String, ExtensionAttributes> extensionAttributesMap2 = buildExtensionAttributesMap(8.9, "status2");
        insertExtensions(Map.of(ownerInfo1, extensionAttributesMap1, ownerInfo2, extensionAttributesMap2));
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1");

        ownerInfo1 = new OwnerInfo(lineId1, ResourceType.LINE, NETWORK_UUID, 1);
        extensionAttributesMap1 = buildExtensionAttributesMap(5.2, "statusUpdated1");
        insertExtensions(Map.of(ownerInfo1, extensionAttributesMap1));

        Assertions.assertEquals(Optional.of(extensionAttributesMap1.get(ActivePowerControl.NAME)), networkStoreRepository.getExtensionAttributes(NETWORK_UUID, 1, lineId1, ActivePowerControl.NAME));
        Assertions.assertEquals(extensionAttributesMap1, networkStoreRepository.getAllExtensionsAttributesByIdentifiableId(NETWORK_UUID, 1, lineId1));
        Map<String, ExtensionAttributes> expExtensionAttributesApcLine = Map.of(lineId1, buildActivePowerControlAttributes(5.2), lineId2, buildActivePowerControlAttributes(8.9));
        Assertions.assertEquals(expExtensionAttributesApcLine, networkStoreRepository.getAllExtensionsAttributesByResourceTypeAndExtensionName(NETWORK_UUID, 1, ResourceType.LINE, ActivePowerControl.NAME));
        Map<String, Map<String, ExtensionAttributes>> expExtensionAttributesLine = Map.of(lineId1, extensionAttributesMap1, lineId2, extensionAttributesMap2);
        Assertions.assertEquals(expExtensionAttributesLine, networkStoreRepository.getAllExtensionsAttributesByResourceType(NETWORK_UUID, 1, ResourceType.LINE));
    }

    @Test
    void getExtensionFromFullClone() {
        String networkId = "network1";
        String lineId1 = "line1";
        String lineId2 = "line2";
        createFullVariantNetwork(networkStoreRepository, NETWORK_UUID, networkId, 2, "variant2");
        OwnerInfo ownerInfo1 = new OwnerInfo(lineId1, ResourceType.LINE, NETWORK_UUID, 2);
        Map<String, ExtensionAttributes> extensionAttributesMap1 = buildExtensionAttributesMap(5.6, "status1");
        OwnerInfo ownerInfo2 = new OwnerInfo(lineId2, ResourceType.LINE, NETWORK_UUID, 2);
        Map<String, ExtensionAttributes> extensionAttributesMap2 = buildExtensionAttributesMap(8.9, "status2");
        insertExtensions(Map.of(ownerInfo1, extensionAttributesMap1, ownerInfo2, extensionAttributesMap2));

        Assertions.assertEquals(Optional.of(extensionAttributesMap1.get(ActivePowerControl.NAME)), networkStoreRepository.getExtensionAttributes(NETWORK_UUID, 2, lineId1, ActivePowerControl.NAME));
        Assertions.assertEquals(extensionAttributesMap1, networkStoreRepository.getAllExtensionsAttributesByIdentifiableId(NETWORK_UUID, 2, lineId1));
        Map<String, ExtensionAttributes> expExtensionAttributesApcLine = Map.of(lineId1, buildActivePowerControlAttributes(5.6), lineId2, buildActivePowerControlAttributes(8.9));
        Assertions.assertEquals(expExtensionAttributesApcLine, networkStoreRepository.getAllExtensionsAttributesByResourceTypeAndExtensionName(NETWORK_UUID, 2, ResourceType.LINE, ActivePowerControl.NAME));
        Map<String, Map<String, ExtensionAttributes>> expExtensionAttributesLine = Map.of(lineId1, extensionAttributesMap1, lineId2, extensionAttributesMap2);
        Assertions.assertEquals(expExtensionAttributesLine, networkStoreRepository.getAllExtensionsAttributesByResourceType(NETWORK_UUID, 2, ResourceType.LINE));
    }

    @Test
    void getExtensionFromPartialCloneWithTombstonedIdentifiable() {
        String networkId = "network1";
        String lineId1 = "line1";
        String lineId2 = "line2";
        createFullVariantNetwork(networkStoreRepository, NETWORK_UUID, networkId, 0, "variant0");
        OwnerInfo ownerInfo1 = new OwnerInfo(lineId1, ResourceType.LINE, NETWORK_UUID, 0);
        Map<String, ExtensionAttributes> extensionAttributesMap1 = buildExtensionAttributesMap(5.6, "status1");
        OwnerInfo ownerInfo2 = new OwnerInfo(lineId2, ResourceType.LINE, NETWORK_UUID, 0);
        Map<String, ExtensionAttributes> extensionAttributesMap2 = buildExtensionAttributesMap(8.9, "status2");
        insertExtensions(Map.of(ownerInfo1, extensionAttributesMap1, ownerInfo2, extensionAttributesMap2));
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1");

        Assertions.assertEquals(Optional.of(extensionAttributesMap1.get(ActivePowerControl.NAME)), networkStoreRepository.getExtensionAttributes(NETWORK_UUID, 1, lineId1, ActivePowerControl.NAME));
        Assertions.assertEquals(extensionAttributesMap1, networkStoreRepository.getAllExtensionsAttributesByIdentifiableId(NETWORK_UUID, 1, lineId1));
        Map<String, ExtensionAttributes> expExtensionAttributesApcLine = Map.of(lineId1, buildActivePowerControlAttributes(5.6), lineId2, buildActivePowerControlAttributes(8.9));
        Assertions.assertEquals(expExtensionAttributesApcLine, networkStoreRepository.getAllExtensionsAttributesByResourceTypeAndExtensionName(NETWORK_UUID, 1, ResourceType.LINE, ActivePowerControl.NAME));
        Map<String, Map<String, ExtensionAttributes>> expExtensionAttributesLine = Map.of(lineId1, extensionAttributesMap1, lineId2, extensionAttributesMap2);
        Assertions.assertEquals(expExtensionAttributesLine, networkStoreRepository.getAllExtensionsAttributesByResourceType(NETWORK_UUID, 1, ResourceType.LINE));

        networkStoreRepository.deleteIdentifiables(NETWORK_UUID, 1, Collections.singletonList(lineId1), LINE_TABLE);

        Assertions.assertEquals(Optional.empty(), networkStoreRepository.getExtensionAttributes(NETWORK_UUID, 1, lineId1, ActivePowerControl.NAME));
        Assertions.assertEquals(Map.of(), networkStoreRepository.getAllExtensionsAttributesByIdentifiableId(NETWORK_UUID, 1, lineId1));
        expExtensionAttributesApcLine = Map.of(lineId2, buildActivePowerControlAttributes(8.9));
        Assertions.assertEquals(expExtensionAttributesApcLine, networkStoreRepository.getAllExtensionsAttributesByResourceTypeAndExtensionName(NETWORK_UUID, 1, ResourceType.LINE, ActivePowerControl.NAME));
        expExtensionAttributesLine = Map.of(lineId2, extensionAttributesMap2);
        Assertions.assertEquals(expExtensionAttributesLine, networkStoreRepository.getAllExtensionsAttributesByResourceType(NETWORK_UUID, 1, ResourceType.LINE));
    }

    @Test
    void getExtensionFromPartialCloneWithTombstonedExtension() {
        String networkId = "network1";
        String lineId1 = "line1";
        String lineId2 = "line2";
        createFullVariantNetwork(networkStoreRepository, NETWORK_UUID, networkId, 0, "variant0");
        OwnerInfo ownerInfo1 = new OwnerInfo(lineId1, ResourceType.LINE, NETWORK_UUID, 0);
        Map<String, ExtensionAttributes> extensionAttributesMap1 = buildExtensionAttributesMap(5.6, "status1");
        OwnerInfo ownerInfo2 = new OwnerInfo(lineId2, ResourceType.LINE, NETWORK_UUID, 0);
        Map<String, ExtensionAttributes> extensionAttributesMap2 = buildExtensionAttributesMap(8.9, "status2");
        insertExtensions(Map.of(ownerInfo1, extensionAttributesMap1, ownerInfo2, extensionAttributesMap2));
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1");

        Assertions.assertEquals(Optional.of(extensionAttributesMap1.get(ActivePowerControl.NAME)), networkStoreRepository.getExtensionAttributes(NETWORK_UUID, 1, lineId1, ActivePowerControl.NAME));
        Assertions.assertEquals(extensionAttributesMap1, networkStoreRepository.getAllExtensionsAttributesByIdentifiableId(NETWORK_UUID, 1, lineId1));
        Map<String, ExtensionAttributes> expExtensionAttributesApcLine = Map.of(lineId1, buildActivePowerControlAttributes(5.6), lineId2, buildActivePowerControlAttributes(8.9));
        Assertions.assertEquals(expExtensionAttributesApcLine, networkStoreRepository.getAllExtensionsAttributesByResourceTypeAndExtensionName(NETWORK_UUID, 1, ResourceType.LINE, ActivePowerControl.NAME));
        Map<String, Map<String, ExtensionAttributes>> expExtensionAttributesLine = Map.of(lineId1, extensionAttributesMap1, lineId2, extensionAttributesMap2);
        Assertions.assertEquals(expExtensionAttributesLine, networkStoreRepository.getAllExtensionsAttributesByResourceType(NETWORK_UUID, 1, ResourceType.LINE));

        networkStoreRepository.removeExtensionAttributes(NETWORK_UUID, 1, lineId1, ActivePowerControl.NAME);
        networkStoreRepository.removeExtensionAttributes(NETWORK_UUID, 1, lineId1, OperatingStatus.NAME);
        networkStoreRepository.removeExtensionAttributes(NETWORK_UUID, 1, lineId2, ActivePowerControl.NAME);

        Assertions.assertEquals(Optional.empty(), networkStoreRepository.getExtensionAttributes(NETWORK_UUID, 1, lineId1, ActivePowerControl.NAME));
        Assertions.assertEquals(Optional.of(extensionAttributesMap2.get(OperatingStatus.NAME)), networkStoreRepository.getExtensionAttributes(NETWORK_UUID, 1, lineId2, OperatingStatus.NAME));
        Assertions.assertEquals(Map.of(), networkStoreRepository.getAllExtensionsAttributesByIdentifiableId(NETWORK_UUID, 1, lineId1));
        Assertions.assertEquals(Map.of(OperatingStatus.NAME, buildOperatingStatusAttributes("status2")), networkStoreRepository.getAllExtensionsAttributesByIdentifiableId(NETWORK_UUID, 1, lineId2));
        Map<String, ExtensionAttributes> expExtensionAttributesOsLine = Map.of(lineId2, buildOperatingStatusAttributes("status2"));
        Assertions.assertEquals(Map.of(), networkStoreRepository.getAllExtensionsAttributesByResourceTypeAndExtensionName(NETWORK_UUID, 1, ResourceType.LINE, ActivePowerControl.NAME));
        Assertions.assertEquals(expExtensionAttributesOsLine, networkStoreRepository.getAllExtensionsAttributesByResourceTypeAndExtensionName(NETWORK_UUID, 1, ResourceType.LINE, OperatingStatus.NAME));
        expExtensionAttributesLine = Map.of(lineId2, Map.of(OperatingStatus.NAME, buildOperatingStatusAttributes("status2")));
        Assertions.assertEquals(expExtensionAttributesLine, networkStoreRepository.getAllExtensionsAttributesByResourceType(NETWORK_UUID, 1, ResourceType.LINE));
    }

    @Test
    void getExtensionFromPartialCloneWithRecreatedIdentifiable() {
        String networkId = "network1";
        String lineId1 = "line1";
        createFullVariantNetwork(networkStoreRepository, NETWORK_UUID, networkId, 0, "variant0");
        createLine(networkStoreRepository, NETWORK_UUID, 0, lineId1, "vl1", "vl2");
        OwnerInfo ownerInfo1 = new OwnerInfo(lineId1, ResourceType.LINE, NETWORK_UUID, 0);
        Map<String, ExtensionAttributes> extensionAttributesMap1 = buildExtensionAttributesMap(5.6, "status1");
        insertExtensions(Map.of(ownerInfo1, extensionAttributesMap1));
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1");

        Assertions.assertEquals(Optional.of(extensionAttributesMap1.get(ActivePowerControl.NAME)), networkStoreRepository.getExtensionAttributes(NETWORK_UUID, 1, lineId1, ActivePowerControl.NAME));
        Assertions.assertEquals(extensionAttributesMap1, networkStoreRepository.getAllExtensionsAttributesByIdentifiableId(NETWORK_UUID, 1, lineId1));
        Map<String, ExtensionAttributes> expExtensionAttributesOsLine = Map.of(lineId1, buildActivePowerControlAttributes(5.6));
        Assertions.assertEquals(expExtensionAttributesOsLine, networkStoreRepository.getAllExtensionsAttributesByResourceTypeAndExtensionName(NETWORK_UUID, 1, ResourceType.LINE, ActivePowerControl.NAME));
        Map<String, Map<String, ExtensionAttributes>> expExtensionAttributesLine = Map.of(lineId1, Map.of(OperatingStatus.NAME, buildOperatingStatusAttributes("status1"), ActivePowerControl.NAME, buildActivePowerControlAttributes(5.6)));
        Assertions.assertEquals(expExtensionAttributesLine, networkStoreRepository.getAllExtensionsAttributesByResourceType(NETWORK_UUID, 1, ResourceType.LINE));

        // Recreate identifiable without extensions
        networkStoreRepository.deleteIdentifiables(NETWORK_UUID, 1, Collections.singletonList(lineId1), LINE_TABLE);
        createLine(networkStoreRepository, NETWORK_UUID, 1, lineId1, "vl1", "vl2");

        Assertions.assertEquals(Optional.empty(), networkStoreRepository.getExtensionAttributes(NETWORK_UUID, 1, lineId1, ActivePowerControl.NAME));
        Assertions.assertEquals(Map.of(), networkStoreRepository.getAllExtensionsAttributesByIdentifiableId(NETWORK_UUID, 1, lineId1));
        Assertions.assertEquals(Map.of(), networkStoreRepository.getAllExtensionsAttributesByResourceTypeAndExtensionName(NETWORK_UUID, 1, ResourceType.LINE, ActivePowerControl.NAME));
        Assertions.assertEquals(Map.of(), networkStoreRepository.getAllExtensionsAttributesByResourceType(NETWORK_UUID, 1, ResourceType.LINE));

        ownerInfo1 = new OwnerInfo(lineId1, ResourceType.LINE, NETWORK_UUID, 1);
        insertExtensions(Map.of(ownerInfo1, extensionAttributesMap1));
        Assertions.assertEquals(Optional.of(extensionAttributesMap1.get(ActivePowerControl.NAME)), networkStoreRepository.getExtensionAttributes(NETWORK_UUID, 1, lineId1, ActivePowerControl.NAME));
        Assertions.assertEquals(extensionAttributesMap1, networkStoreRepository.getAllExtensionsAttributesByIdentifiableId(NETWORK_UUID, 1, lineId1));
        expExtensionAttributesOsLine = Map.of(lineId1, buildActivePowerControlAttributes(5.6));
        Assertions.assertEquals(expExtensionAttributesOsLine, networkStoreRepository.getAllExtensionsAttributesByResourceTypeAndExtensionName(NETWORK_UUID, 1, ResourceType.LINE, ActivePowerControl.NAME));
        expExtensionAttributesLine = Map.of(lineId1, Map.of(OperatingStatus.NAME, buildOperatingStatusAttributes("status1"), ActivePowerControl.NAME, buildActivePowerControlAttributes(5.6)));
        Assertions.assertEquals(expExtensionAttributesLine, networkStoreRepository.getAllExtensionsAttributesByResourceType(NETWORK_UUID, 1, ResourceType.LINE));
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
        createFullVariantNetwork(networkStoreRepository, NETWORK_UUID, networkId, 0, "variant0");
        OwnerInfo ownerInfo = new OwnerInfo(lineId, ResourceType.LINE, NETWORK_UUID, 0);
        Map<String, ExtensionAttributes> extensionAttributesMap = buildExtensionAttributesMap(5.6, "status1");
        insertExtensions(Map.of(ownerInfo, extensionAttributesMap));

        networkStoreRepository.removeExtensionAttributes(NETWORK_UUID, 0, lineId, ActivePowerControl.NAME);

        Assertions.assertEquals(Map.of(OperatingStatus.NAME, buildOperatingStatusAttributes("status1")), networkStoreRepository.getAllExtensionsAttributesByIdentifiableId(NETWORK_UUID, 0, lineId));
        Assertions.assertTrue(getTombstonedExtensions(NETWORK_UUID, 0).isEmpty());
    }

    @Test
    void removeExtensionOnPartialVariant() {
        String networkId = "network1";
        String lineId = "line1";
        createNetwork(networkStoreRepository, NETWORK_UUID, networkId, 1, "variant1", 0);
        OwnerInfo ownerInfo = new OwnerInfo(lineId, ResourceType.LINE, NETWORK_UUID, 1);
        Map<String, ExtensionAttributes> extensionAttributesMap = buildExtensionAttributesMap(5.6, "status1");
        insertExtensions(Map.of(ownerInfo, extensionAttributesMap));

        networkStoreRepository.removeExtensionAttributes(NETWORK_UUID, 1, lineId, ActivePowerControl.NAME);
        networkStoreRepository.removeExtensionAttributes(NETWORK_UUID, 1, lineId, OperatingStatus.NAME);

        Assertions.assertEquals(Map.of(), networkStoreRepository.getAllExtensionsAttributesByIdentifiableId(NETWORK_UUID, 1, lineId));
        Assertions.assertEquals(Map.of(lineId, Set.of(ActivePowerControl.NAME, OperatingStatus.NAME)), getTombstonedExtensions(NETWORK_UUID, 1));
    }

    @Test
    void createExtensionWithRecreatedTombstonedExtension() {
        String networkId = "network1";
        String lineId = "line1";
        // Variant 0
        createNetwork(networkStoreRepository, NETWORK_UUID, networkId, 1, "variant1", 0);
        OwnerInfo ownerInfo1 = new OwnerInfo(lineId, ResourceType.LINE, NETWORK_UUID, 1);
        Map<String, ExtensionAttributes> extensionAttributesMap = buildExtensionAttributesMap(5.6, "status1");
        insertExtensions(Map.of(ownerInfo1, extensionAttributesMap));
        networkStoreRepository.removeExtensionAttributes(NETWORK_UUID, 1, lineId, ActivePowerControl.NAME);
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 1, 2, "variant1");
        // Variant 2
        OwnerInfo ownerInfo2 = new OwnerInfo(lineId, ResourceType.LINE, NETWORK_UUID, 2);
        extensionAttributesMap = Map.of(ActivePowerControl.NAME, buildActivePowerControlAttributes(8.4));
        insertExtensions(Map.of(ownerInfo2, extensionAttributesMap));

        // Variant 1 (removed ActivePowerControl extensions)
        Assertions.assertEquals(Optional.empty(), networkStoreRepository.getExtensionAttributes(NETWORK_UUID, 1, lineId, ActivePowerControl.NAME));
        Assertions.assertEquals(Optional.of(buildOperatingStatusAttributes("status1")), networkStoreRepository.getExtensionAttributes(NETWORK_UUID, 1, lineId, OperatingStatus.NAME));
        Assertions.assertEquals(Map.of(lineId, Map.of(OperatingStatus.NAME, buildOperatingStatusAttributes("status1"))), networkStoreRepository.getAllExtensionsAttributesByResourceType(NETWORK_UUID, 1, ResourceType.LINE));
        Assertions.assertEquals(Map.of(), networkStoreRepository.getAllExtensionsAttributesByResourceTypeAndExtensionName(NETWORK_UUID, 1, ResourceType.LINE, ActivePowerControl.NAME));
        Assertions.assertEquals(Map.of(lineId, buildOperatingStatusAttributes("status1")), networkStoreRepository.getAllExtensionsAttributesByResourceTypeAndExtensionName(NETWORK_UUID, 1, ResourceType.LINE, OperatingStatus.NAME));
        Assertions.assertEquals(Map.of(OperatingStatus.NAME, buildOperatingStatusAttributes("status1")), networkStoreRepository.getAllExtensionsAttributesByIdentifiableId(NETWORK_UUID, 1, lineId));
        Assertions.assertEquals(Map.of(lineId, Set.of(ActivePowerControl.NAME)), getTombstonedExtensions(NETWORK_UUID, 1));
        // Variant 2 (recreated ActivePowerControl with different attributes)
        Assertions.assertEquals(Optional.of(buildActivePowerControlAttributes(8.4)), networkStoreRepository.getExtensionAttributes(NETWORK_UUID, 2, lineId, ActivePowerControl.NAME));
        Assertions.assertEquals(Optional.of(buildOperatingStatusAttributes("status1")), networkStoreRepository.getExtensionAttributes(NETWORK_UUID, 2, lineId, OperatingStatus.NAME));
        Assertions.assertEquals(Map.of(lineId, Map.of(OperatingStatus.NAME, buildOperatingStatusAttributes("status1"), ActivePowerControl.NAME, buildActivePowerControlAttributes(8.4))), networkStoreRepository.getAllExtensionsAttributesByResourceType(NETWORK_UUID, 2, ResourceType.LINE));
        Assertions.assertEquals(Map.of(lineId, buildActivePowerControlAttributes(8.4)), networkStoreRepository.getAllExtensionsAttributesByResourceTypeAndExtensionName(NETWORK_UUID, 2, ResourceType.LINE, ActivePowerControl.NAME));
        Assertions.assertEquals(Map.of(lineId, buildOperatingStatusAttributes("status1")), networkStoreRepository.getAllExtensionsAttributesByResourceTypeAndExtensionName(NETWORK_UUID, 2, ResourceType.LINE, OperatingStatus.NAME));
        Assertions.assertEquals(Map.of(OperatingStatus.NAME, buildOperatingStatusAttributes("status1"), ActivePowerControl.NAME, buildActivePowerControlAttributes(8.4)), networkStoreRepository.getAllExtensionsAttributesByIdentifiableId(NETWORK_UUID, 2, lineId));
        Assertions.assertEquals(Map.of(lineId, Set.of(ActivePowerControl.NAME)), getTombstonedExtensions(NETWORK_UUID, 2));
    }

    @Test
    void emptyCreateExtensionsDoesNotThrow() {
        assertDoesNotThrow(() -> insertExtensions(Map.of()));
        assertDoesNotThrow(() -> insertExtensions(Map.of(new OwnerInfo("id", ResourceType.LINE, NETWORK_UUID, 0), Map.of())));
    }

    @Test
    void getRegulatingEquipmentsFromTwoVariants() {
        String networkId = "network1";
        String generatorId1 = "generator1";
        String generatorId2 = "generator2";
        String loadId1 = "load1";
        // Variant 0
        createNetwork(networkStoreRepository, NETWORK_UUID, networkId, 0, "variant1", -1);
        Resource<GeneratorAttributes> gen = Resource.generatorBuilder()
            .id(generatorId1)
            .variantNum(0)
            .attributes(GeneratorAttributes.builder()
                .voltageLevelId("vl1")
                .name(generatorId1)
                .regulatingPoint(RegulatingPointAttributes.builder()
                    .localTerminal(TerminalRefAttributes.builder().connectableId(generatorId1).build())
                    .regulatedResourceType(ResourceType.LOAD)
                    .regulatingEquipmentId(loadId1)
                    .regulatingTerminal(TerminalRefAttributes.builder().connectableId(loadId1).build())
                    .build())
                .build())
            .build();
        networkStoreRepository.createGenerators(NETWORK_UUID, List.of(gen));
        Resource<LoadAttributes> load = Resource.loadBuilder()
            .id(loadId1)
            .variantNum(0)
            .attributes(LoadAttributes.builder()
                .voltageLevelId("vl1")
                .build())
            .build();
        networkStoreRepository.createLoads(NETWORK_UUID, List.of(load));
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1");
        // Variant 1
        Resource<GeneratorAttributes> gen2 = Resource.generatorBuilder()
            .id(generatorId2)
            .variantNum(1)
            .attributes(GeneratorAttributes.builder()
                .voltageLevelId("vl1")
                .name(generatorId2)
                .regulatingPoint(RegulatingPointAttributes.builder()
                    .localTerminal(TerminalRefAttributes.builder().connectableId(generatorId2).build())
                    .regulatedResourceType(ResourceType.LOAD)
                    .regulatingEquipmentId(loadId1)
                    .regulatingTerminal(TerminalRefAttributes.builder().connectableId(loadId1).build())
                    .build())
                .build())
            .build();
        networkStoreRepository.createGenerators(NETWORK_UUID, List.of(gen2));

        // getRegulatingEquipments
        Map<OwnerInfo, Set<RegulatingEquipmentIdentifier>> regulatingEquipments = networkStoreRepository.getRegulatingEquipments(NETWORK_UUID, 0, ResourceType.LOAD);
        assertEquals(1, regulatingEquipments.size());
        assertEquals(Set.of(new RegulatingEquipmentIdentifier(generatorId1, ResourceType.GENERATOR)), regulatingEquipments.get(new OwnerInfo(loadId1, ResourceType.LOAD, NETWORK_UUID, 0)));
        regulatingEquipments = networkStoreRepository.getRegulatingEquipments(NETWORK_UUID, 0, ResourceType.GENERATOR);
        assertEquals(0, regulatingEquipments.size());
        regulatingEquipments = networkStoreRepository.getRegulatingEquipments(NETWORK_UUID, 1, ResourceType.LOAD);
        assertEquals(1, regulatingEquipments.size());
        assertEquals(Set.of(new RegulatingEquipmentIdentifier(generatorId1, ResourceType.GENERATOR), new RegulatingEquipmentIdentifier(generatorId2, ResourceType.GENERATOR)), regulatingEquipments.get(new OwnerInfo(loadId1, ResourceType.LOAD, NETWORK_UUID, 1)));
        regulatingEquipments = networkStoreRepository.getRegulatingEquipments(NETWORK_UUID, 1, ResourceType.GENERATOR);
        assertEquals(0, regulatingEquipments.size());
        // getRegulatingEquipmentsWithInClause
        regulatingEquipments = networkStoreRepository.getRegulatingEquipmentsWithInClause(NETWORK_UUID, 0, "regulatingterminalconnectableid", List.of(loadId1), ResourceType.LOAD);
        assertEquals(1, regulatingEquipments.size());
        assertEquals(Set.of(new RegulatingEquipmentIdentifier(generatorId1, ResourceType.GENERATOR)), regulatingEquipments.get(new OwnerInfo(loadId1, ResourceType.LOAD, NETWORK_UUID, 0)));
        regulatingEquipments = networkStoreRepository.getRegulatingEquipmentsWithInClause(NETWORK_UUID, 0, "regulatingterminalconnectableid", List.of(generatorId1), ResourceType.GENERATOR);
        assertEquals(0, regulatingEquipments.size());
        regulatingEquipments = networkStoreRepository.getRegulatingEquipmentsWithInClause(NETWORK_UUID, 1, "regulatingterminalconnectableid", List.of(loadId1), ResourceType.LOAD);
        assertEquals(1, regulatingEquipments.size());
        assertEquals(Set.of(new RegulatingEquipmentIdentifier(generatorId1, ResourceType.GENERATOR), new RegulatingEquipmentIdentifier(generatorId2, ResourceType.GENERATOR)), regulatingEquipments.get(new OwnerInfo(loadId1, ResourceType.LOAD, NETWORK_UUID, 1)));
        regulatingEquipments = networkStoreRepository.getRegulatingEquipmentsWithInClause(NETWORK_UUID, 1, "regulatingterminalconnectableid", List.of(generatorId1), ResourceType.GENERATOR);
        assertEquals(0, regulatingEquipments.size());
        // getRegulatingEquipmentsForIdentifiable
        assertEquals(Set.of(new RegulatingEquipmentIdentifier(generatorId1, ResourceType.GENERATOR)), networkStoreRepository.getRegulatingEquipmentsForIdentifiable(NETWORK_UUID, 0, loadId1, ResourceType.LOAD));
        assertEquals(Set.of(), networkStoreRepository.getRegulatingEquipmentsForIdentifiable(NETWORK_UUID, 0, generatorId1, ResourceType.GENERATOR));
        assertEquals(Set.of(new RegulatingEquipmentIdentifier(generatorId1, ResourceType.GENERATOR), new RegulatingEquipmentIdentifier(generatorId2, ResourceType.GENERATOR)), networkStoreRepository.getRegulatingEquipmentsForIdentifiable(NETWORK_UUID, 1, loadId1, ResourceType.LOAD));
        assertEquals(Set.of(), networkStoreRepository.getRegulatingEquipmentsForIdentifiable(NETWORK_UUID, 1, generatorId1, ResourceType.GENERATOR));

        // Delete generatorId2 and check that it's not in regulating equipments anymore
        networkStoreRepository.deleteGenerators(NETWORK_UUID, 1, List.of(generatorId2));
        regulatingEquipments = networkStoreRepository.getRegulatingEquipments(NETWORK_UUID, 1, ResourceType.LOAD);
        assertEquals(Set.of(new RegulatingEquipmentIdentifier(generatorId1, ResourceType.GENERATOR)), regulatingEquipments.get(new OwnerInfo(loadId1, ResourceType.LOAD, NETWORK_UUID, 1)));
        regulatingEquipments = networkStoreRepository.getRegulatingEquipmentsWithInClause(NETWORK_UUID, 1, "regulatingterminalconnectableid", List.of(loadId1), ResourceType.LOAD);
        assertEquals(Set.of(new RegulatingEquipmentIdentifier(generatorId1, ResourceType.GENERATOR)), regulatingEquipments.get(new OwnerInfo(loadId1, ResourceType.LOAD, NETWORK_UUID, 1)));
        assertEquals(Set.of(new RegulatingEquipmentIdentifier(generatorId1, ResourceType.GENERATOR)), networkStoreRepository.getRegulatingEquipmentsForIdentifiable(NETWORK_UUID, 1, loadId1, ResourceType.LOAD));
    }

    private Map<String, Set<String>> getTombstonedExtensions(UUID networkUuid, int variantNum) {
        try (var connection = dataSource.getConnection()) {
            return extensionHandler.getTombstonedExtensions(connection, networkUuid, variantNum);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private void insertExtensions(Map<OwnerInfo, Map<String, ExtensionAttributes>> extensions) {
        try (var connection = dataSource.getConnection()) {
            extensionHandler.insertExtensions(connection, extensions);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    @Test
    void updateLineWithoutOperationalLimitGroup() {
        String networkId = "network1";
        String lineId = "line";
        String operationLimitGroupId1 = "olg1";
        String operationLimitGroupId2 = "olg2";
        createFullVariantNetwork(networkStoreRepository, NETWORK_UUID, networkId, 0, "variant0");
        OperationalLimitsGroupAttributes operationalLimitsGroupAttributes1 = buildOperationalLimitsGroup(operationLimitGroupId1, 1, 3, 12, 456);
        OperationalLimitsGroupAttributes operationalLimitsGroupAttributes2 = buildOperationalLimitsGroup(operationLimitGroupId2, 1, 4, 50, 457);
        createLineWithOperationalLimitGroups(Map.of(operationLimitGroupId1, operationalLimitsGroupAttributes1, operationLimitGroupId2, operationalLimitsGroupAttributes2), operationLimitGroupId1, lineId);
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1");

        Resource<LineAttributes> updatedLine = Resource.lineBuilder()
                .id(lineId)
                .variantNum(1)
                .attributes(LineAttributes.builder()
                        .voltageLevelId1("vl1")
                        .voltageLevelId2("vl2")
                        .b1(2.6)
                        .selectedOperationalLimitsGroupId1(operationLimitGroupId1)
                        .build())
                .build();
        networkStoreRepository.updateLines(NETWORK_UUID, List.of(updatedLine));

        // Variant 0
        assertEquals(operationalLimitsGroupAttributes1, networkStoreRepository.getOperationalLimitsGroup(NETWORK_UUID, 0, lineId, ResourceType.LINE, operationLimitGroupId1, 1).orElseThrow());
        assertEquals(2, networkStoreRepository.getAllOperationalLimitsGroupAttributesByResourceType(NETWORK_UUID, 0, ResourceType.LINE).get(lineId).get(1).size());
        assertEquals(operationalLimitsGroupAttributes1, networkStoreRepository.getAllOperationalLimitsGroupAttributesByResourceType(NETWORK_UUID, 0, ResourceType.LINE).get(lineId).get(1).get(operationLimitGroupId1));
        assertEquals(List.of(operationalLimitsGroupAttributes1, operationalLimitsGroupAttributes2), networkStoreRepository.getOperationalLimitsGroupAttributesForBranchSide(NETWORK_UUID, 0, ResourceType.LINE, lineId, 1));
        assertEquals(1, networkStoreRepository.getAllSelectedOperationalLimitsGroupAttributesByResourceType(NETWORK_UUID, 0, ResourceType.LINE).get(lineId).get(1).size());
        assertEquals(operationalLimitsGroupAttributes1, networkStoreRepository.getAllSelectedOperationalLimitsGroupAttributesByResourceType(NETWORK_UUID, 0, ResourceType.LINE).get(lineId).get(1).get(operationLimitGroupId1));

        // Variant 1
        assertEquals(operationalLimitsGroupAttributes1, networkStoreRepository.getOperationalLimitsGroup(NETWORK_UUID, 1, lineId, ResourceType.LINE, operationLimitGroupId1, 1).orElseThrow());
        assertEquals(2, networkStoreRepository.getAllOperationalLimitsGroupAttributesByResourceType(NETWORK_UUID, 1, ResourceType.LINE).get(lineId).get(1).size());
        assertEquals(operationalLimitsGroupAttributes1, networkStoreRepository.getAllOperationalLimitsGroupAttributesByResourceType(NETWORK_UUID, 1, ResourceType.LINE).get(lineId).get(1).get(operationLimitGroupId1));
        assertEquals(List.of(operationalLimitsGroupAttributes1, operationalLimitsGroupAttributes2), networkStoreRepository.getOperationalLimitsGroupAttributesForBranchSide(NETWORK_UUID, 1, ResourceType.LINE, lineId, 1));
        assertEquals(1, networkStoreRepository.getAllSelectedOperationalLimitsGroupAttributesByResourceType(NETWORK_UUID, 1, ResourceType.LINE).get(lineId).get(1).size());
        assertEquals(operationalLimitsGroupAttributes1, networkStoreRepository.getAllSelectedOperationalLimitsGroupAttributesByResourceType(NETWORK_UUID, 1, ResourceType.LINE).get(lineId).get(1).get(operationLimitGroupId1));
    }

    @Test
    void updateOperationalLimitGroup() {
        String networkId = "network1";
        String lineId = "line";
        String operationLimitGroupId1 = "olg1";
        String operationLimitGroupId2 = "olg2";
        createFullVariantNetwork(networkStoreRepository, NETWORK_UUID, networkId, 0, "variant0");
        OperationalLimitsGroupAttributes operationalLimitsGroupAttributes1 = buildOperationalLimitsGroup(operationLimitGroupId1, 1, 3, 12, 456);
        OperationalLimitsGroupAttributes operationalLimitsGroupAttributes2 = buildOperationalLimitsGroup(operationLimitGroupId2, 1, 4, 50, 457);
        createLineWithOperationalLimitGroups(Map.of(operationLimitGroupId1, operationalLimitsGroupAttributes1, operationLimitGroupId2, operationalLimitsGroupAttributes2), operationLimitGroupId1, lineId);
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1");

        OperationalLimitsGroupAttributes operationalLimitsGroupAttributes1Updated = buildOperationalLimitsGroup(operationLimitGroupId1, 1, 5, 255, 458);
        Resource<LineAttributes> updatedLine = Resource.lineBuilder()
                .id(lineId)
                .variantNum(1)
                .attributes(LineAttributes.builder()
                        .voltageLevelId1("vl1")
                        .voltageLevelId2("vl2")
                        .b1(2.6)
                        .selectedOperationalLimitsGroupId1(operationLimitGroupId1)
                        .operationalLimitsGroups1(Map.of(operationLimitGroupId1, operationalLimitsGroupAttributes1Updated))
                        .build())
                .build();
        networkStoreRepository.updateLines(NETWORK_UUID, List.of(updatedLine));

        // Variant 0
        assertEquals(operationalLimitsGroupAttributes1, networkStoreRepository.getOperationalLimitsGroup(NETWORK_UUID, 0, lineId, ResourceType.LINE, operationLimitGroupId1, 1).orElseThrow());
        assertEquals(2, networkStoreRepository.getAllOperationalLimitsGroupAttributesByResourceType(NETWORK_UUID, 0, ResourceType.LINE).get(lineId).get(1).size());
        assertEquals(operationalLimitsGroupAttributes1, networkStoreRepository.getAllOperationalLimitsGroupAttributesByResourceType(NETWORK_UUID, 0, ResourceType.LINE).get(lineId).get(1).get(operationLimitGroupId1));
        assertEquals(List.of(operationalLimitsGroupAttributes1, operationalLimitsGroupAttributes2), networkStoreRepository.getOperationalLimitsGroupAttributesForBranchSide(NETWORK_UUID, 0, ResourceType.LINE, lineId, 1));
        assertEquals(1, networkStoreRepository.getAllSelectedOperationalLimitsGroupAttributesByResourceType(NETWORK_UUID, 0, ResourceType.LINE).get(lineId).get(1).size());
        assertEquals(operationalLimitsGroupAttributes1, networkStoreRepository.getAllSelectedOperationalLimitsGroupAttributesByResourceType(NETWORK_UUID, 0, ResourceType.LINE).get(lineId).get(1).get(operationLimitGroupId1));

        // Variant 1
        assertEquals(operationalLimitsGroupAttributes1Updated, networkStoreRepository.getOperationalLimitsGroup(NETWORK_UUID, 1, lineId, ResourceType.LINE, operationLimitGroupId1, 1).orElseThrow());
        assertEquals(2, networkStoreRepository.getAllOperationalLimitsGroupAttributesByResourceType(NETWORK_UUID, 1, ResourceType.LINE).get(lineId).get(1).size());
        assertEquals(operationalLimitsGroupAttributes1Updated, networkStoreRepository.getAllOperationalLimitsGroupAttributesByResourceType(NETWORK_UUID, 1, ResourceType.LINE).get(lineId).get(1).get(operationLimitGroupId1));
        assertEquals(List.of(operationalLimitsGroupAttributes1Updated, operationalLimitsGroupAttributes2), networkStoreRepository.getOperationalLimitsGroupAttributesForBranchSide(NETWORK_UUID, 1, ResourceType.LINE, lineId, 1));
        assertEquals(1, networkStoreRepository.getAllSelectedOperationalLimitsGroupAttributesByResourceType(NETWORK_UUID, 1, ResourceType.LINE).get(lineId).get(1).size());
        assertEquals(operationalLimitsGroupAttributes1Updated, networkStoreRepository.getAllSelectedOperationalLimitsGroupAttributesByResourceType(NETWORK_UUID, 1, ResourceType.LINE).get(lineId).get(1).get(operationLimitGroupId1));
    }

    @Test
    void updateOperationalLimitGroup2() {
        String networkId = "network1";
        String lineId = "line";
        String lineId1 = "line1";
        String operationLimitGroupId1 = "olg1";
        String operationLimitGroupId2 = "olg2";
        createFullVariantNetwork(networkStoreRepository, NETWORK_UUID, networkId, 0, "variant0");
        OperationalLimitsGroupAttributes operationalLimitsGroupAttributes1 = buildOperationalLimitsGroup(operationLimitGroupId1, 1, 3, 12, 456);
        OperationalLimitsGroupAttributes operationalLimitsGroupAttributes2 = buildOperationalLimitsGroup(operationLimitGroupId2, 1, 4, 50, 457);
        createLineWithOperationalLimitGroups(Map.of(operationLimitGroupId1, operationalLimitsGroupAttributes1, operationLimitGroupId2, operationalLimitsGroupAttributes2), operationLimitGroupId1, lineId);
        OperationalLimitsGroupAttributes operationalLimitsGroupAttributes1bis = buildOperationalLimitsGroup(operationLimitGroupId1, 1, 2, 13, 458);
        OperationalLimitsGroupAttributes operationalLimitsGroupAttributes2bis = buildOperationalLimitsGroup(operationLimitGroupId2, 1, 5, 51, 459);
        createLineWithOperationalLimitGroups(Map.of(operationLimitGroupId1, operationalLimitsGroupAttributes1bis, operationLimitGroupId2, operationalLimitsGroupAttributes2bis), operationLimitGroupId1, lineId1);
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1");

        OperationalLimitsGroupAttributes newOperationalLimitsGroupAttributes = buildOperationalLimitsGroup(operationLimitGroupId1, 1, 5, 255, 460);
        Resource<LineAttributes> updatedLine = Resource.lineBuilder()
                .id(lineId)
                .variantNum(1)
                .attributes(LineAttributes.builder()
                        .voltageLevelId1("vl1")
                        .voltageLevelId2("vl2")
                        .b1(2.6)
                        .selectedOperationalLimitsGroupId1(operationLimitGroupId1)
                        .operationalLimitsGroups1(Map.of(operationLimitGroupId1, newOperationalLimitsGroupAttributes))
                        .build())
                .build();
        OperationalLimitsGroupAttributes newOperationalLimitsGroupAttributes2Bis = buildOperationalLimitsGroup(operationLimitGroupId2, 1, 7, 300, 441);
        Resource<LineAttributes> updatedLine1 = Resource.lineBuilder()
                .id(lineId1)
                .variantNum(1)
                .attributes(LineAttributes.builder()
                        .voltageLevelId1("vl1")
                        .voltageLevelId2("vl2")
                        .b1(2.6)
                        .selectedOperationalLimitsGroupId1(operationLimitGroupId1)
                        .operationalLimitsGroups1(Map.of(operationLimitGroupId2, newOperationalLimitsGroupAttributes2Bis))
                        .build())
                .build();
        networkStoreRepository.updateLines(NETWORK_UUID, List.of(updatedLine, updatedLine1));

        assertEquals(List.of(newOperationalLimitsGroupAttributes, operationalLimitsGroupAttributes2), networkStoreRepository.getOperationalLimitsGroupAttributesForBranchSide(NETWORK_UUID, 1, ResourceType.LINE, lineId, 1));
        assertEquals(List.of(operationalLimitsGroupAttributes1bis, newOperationalLimitsGroupAttributes2Bis), networkStoreRepository.getOperationalLimitsGroupAttributesForBranchSide(NETWORK_UUID, 1, ResourceType.LINE, lineId1, 1));
    }

    @Test
    void updateOperationalLimitGroupWithEmptyTemporaryLimits() {
        String networkId = "network1";
        String lineId = "line";
        String operationLimitGroupId1 = "olg1";
        String operationLimitGroupId2 = "olg2";
        createFullVariantNetwork(networkStoreRepository, NETWORK_UUID, networkId, 0, "variant0");
        OperationalLimitsGroupAttributes operationalLimitsGroupAttributes1 = buildOperationalLimitsGroup(operationLimitGroupId1, 1, 3, 12, 456);
        OperationalLimitsGroupAttributes operationalLimitsGroupAttributes2 = buildOperationalLimitsGroup(operationLimitGroupId2, 1, 4, 50, 457);
        createLineWithOperationalLimitGroups(Map.of(operationLimitGroupId1, operationalLimitsGroupAttributes1, operationLimitGroupId2, operationalLimitsGroupAttributes2), operationLimitGroupId1, lineId);
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1");

        OperationalLimitsGroupAttributes operationalLimitsGroupAttributes1Updated = OperationalLimitsGroupAttributes.builder()
                .id(operationLimitGroupId1)
                .currentLimits(LimitsAttributes.builder()
                        .permanentLimit(3)
                        .temporaryLimits(null)
                        .build())
                .build();
        Resource<LineAttributes> updatedLine = Resource.lineBuilder()
                .id(lineId)
                .variantNum(1)
                .attributes(LineAttributes.builder()
                        .voltageLevelId1("vl1")
                        .voltageLevelId2("vl2")
                        .b1(2.6)
                        .selectedOperationalLimitsGroupId1(operationLimitGroupId1)
                        .operationalLimitsGroups1(Map.of(operationLimitGroupId1, operationalLimitsGroupAttributes1Updated))
                        .build())
                .build();
        networkStoreRepository.updateLines(NETWORK_UUID, List.of(updatedLine));

        // Variant 0
        assertEquals(operationalLimitsGroupAttributes1, networkStoreRepository.getOperationalLimitsGroup(NETWORK_UUID, 0, lineId, ResourceType.LINE, operationLimitGroupId1, 1).orElseThrow());
        assertEquals(2, networkStoreRepository.getAllOperationalLimitsGroupAttributesByResourceType(NETWORK_UUID, 0, ResourceType.LINE).get(lineId).get(1).size());
        assertEquals(operationalLimitsGroupAttributes1, networkStoreRepository.getAllOperationalLimitsGroupAttributesByResourceType(NETWORK_UUID, 0, ResourceType.LINE).get(lineId).get(1).get(operationLimitGroupId1));
        assertEquals(List.of(operationalLimitsGroupAttributes1, operationalLimitsGroupAttributes2), networkStoreRepository.getOperationalLimitsGroupAttributesForBranchSide(NETWORK_UUID, 0, ResourceType.LINE, lineId, 1));
        assertEquals(1, networkStoreRepository.getAllSelectedOperationalLimitsGroupAttributesByResourceType(NETWORK_UUID, 0, ResourceType.LINE).get(lineId).get(1).size());
        assertEquals(operationalLimitsGroupAttributes1, networkStoreRepository.getAllSelectedOperationalLimitsGroupAttributesByResourceType(NETWORK_UUID, 0, ResourceType.LINE).get(lineId).get(1).get(operationLimitGroupId1));

        // Variant 1
        assertEquals(operationalLimitsGroupAttributes1Updated, networkStoreRepository.getOperationalLimitsGroup(NETWORK_UUID, 1, lineId, ResourceType.LINE, operationLimitGroupId1, 1).orElseThrow());
        assertEquals(2, networkStoreRepository.getAllOperationalLimitsGroupAttributesByResourceType(NETWORK_UUID, 1, ResourceType.LINE).get(lineId).get(1).size());
        assertEquals(operationalLimitsGroupAttributes1Updated, networkStoreRepository.getAllOperationalLimitsGroupAttributesByResourceType(NETWORK_UUID, 1, ResourceType.LINE).get(lineId).get(1).get(operationLimitGroupId1));
        assertEquals(List.of(operationalLimitsGroupAttributes1Updated, operationalLimitsGroupAttributes2), networkStoreRepository.getOperationalLimitsGroupAttributesForBranchSide(NETWORK_UUID, 1, ResourceType.LINE, lineId, 1));
        assertEquals(1, networkStoreRepository.getAllSelectedOperationalLimitsGroupAttributesByResourceType(NETWORK_UUID, 1, ResourceType.LINE).get(lineId).get(1).size());
        assertEquals(operationalLimitsGroupAttributes1Updated, networkStoreRepository.getAllSelectedOperationalLimitsGroupAttributesByResourceType(NETWORK_UUID, 1, ResourceType.LINE).get(lineId).get(1).get(operationLimitGroupId1));
    }

    @Test
    void updateOperationalLimitGroupTwice() {
        String networkId = "network1";
        String lineId = "line";
        String operationLimitGroupId1 = "olg1";
        String operationLimitGroupId2 = "olg2";
        createFullVariantNetwork(networkStoreRepository, NETWORK_UUID, networkId, 0, "variant0");
        OperationalLimitsGroupAttributes operationalLimitsGroupAttributes1 = buildOperationalLimitsGroup(operationLimitGroupId1, 1, 3, 12, 456);
        OperationalLimitsGroupAttributes operationalLimitsGroupAttributes2 = buildOperationalLimitsGroup(operationLimitGroupId2, 1, 4, 50, 457);
        createLineWithOperationalLimitGroups(Map.of(operationLimitGroupId1, operationalLimitsGroupAttributes1, operationLimitGroupId2, operationalLimitsGroupAttributes2), operationLimitGroupId1, lineId);
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1");

        OperationalLimitsGroupAttributes operationalLimitsGroupAttributes1Updated = buildOperationalLimitsGroup(operationLimitGroupId1, 1, 5, 255, 458);
        Resource<LineAttributes> updatedLine = Resource.lineBuilder()
                .id(lineId)
                .variantNum(1)
                .attributes(LineAttributes.builder()
                        .voltageLevelId1("vl1")
                        .voltageLevelId2("vl2")
                        .b1(2.6)
                        .selectedOperationalLimitsGroupId1(operationLimitGroupId1)
                        .operationalLimitsGroups1(Map.of(operationLimitGroupId1, operationalLimitsGroupAttributes1Updated))
                        .build())
                .build();
        networkStoreRepository.updateLines(NETWORK_UUID, List.of(updatedLine));

        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 1, 2, "variant2");
        OperationalLimitsGroupAttributes operationalLimitsGroupAttributes2Updated = buildOperationalLimitsGroup(operationLimitGroupId2, 1, 6, 123, 456);
        Resource<LineAttributes> updatedLineTwice = Resource.lineBuilder()
                .id(lineId)
                .variantNum(2)
                .attributes(LineAttributes.builder()
                        .voltageLevelId1("vl1")
                        .voltageLevelId2("vl2")
                        .b1(2.6)
                        .selectedOperationalLimitsGroupId1(operationLimitGroupId1)
                        .operationalLimitsGroups1(Map.of(operationLimitGroupId2, operationalLimitsGroupAttributes2Updated))
                        .build())
                .build();
        networkStoreRepository.updateLines(NETWORK_UUID, List.of(updatedLineTwice));

        // Variant 0
        assertEquals(operationalLimitsGroupAttributes1, networkStoreRepository.getOperationalLimitsGroup(NETWORK_UUID, 0, lineId, ResourceType.LINE, operationLimitGroupId1, 1).orElseThrow());
        assertEquals(operationalLimitsGroupAttributes2, networkStoreRepository.getOperationalLimitsGroup(NETWORK_UUID, 0, lineId, ResourceType.LINE, operationLimitGroupId2, 1).orElseThrow());

        // Variant 1
        assertEquals(operationalLimitsGroupAttributes1Updated, networkStoreRepository.getOperationalLimitsGroup(NETWORK_UUID, 1, lineId, ResourceType.LINE, operationLimitGroupId1, 1).orElseThrow());
        assertEquals(operationalLimitsGroupAttributes2, networkStoreRepository.getOperationalLimitsGroup(NETWORK_UUID, 1, lineId, ResourceType.LINE, operationLimitGroupId2, 1).orElseThrow());

        // Variant 2
        assertEquals(operationalLimitsGroupAttributes1Updated, networkStoreRepository.getOperationalLimitsGroup(NETWORK_UUID, 2, lineId, ResourceType.LINE, operationLimitGroupId1, 1).orElseThrow());
        assertEquals(operationalLimitsGroupAttributes2Updated, networkStoreRepository.getOperationalLimitsGroup(NETWORK_UUID, 2, lineId, ResourceType.LINE, operationLimitGroupId2, 1).orElseThrow());
    }

    @Test
    void updateOperationalLimitGroupTwiceSameVariant() {
        String networkId = "network1";
        String lineId = "line";
        String operationLimitGroupId1 = "olg1";
        String operationLimitGroupId2 = "olg2";
        createFullVariantNetwork(networkStoreRepository, NETWORK_UUID, networkId, 0, "variant0");
        OperationalLimitsGroupAttributes operationalLimitsGroupAttributes1 = buildOperationalLimitsGroup(operationLimitGroupId1, 1, 3, 12, 456);
        OperationalLimitsGroupAttributes operationalLimitsGroupAttributes2 = buildOperationalLimitsGroup(operationLimitGroupId2, 1, 4, 50, 457);
        createLineWithOperationalLimitGroups(Map.of(operationLimitGroupId1, operationalLimitsGroupAttributes1, operationLimitGroupId2, operationalLimitsGroupAttributes2), operationLimitGroupId1, lineId);
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1");

        OperationalLimitsGroupAttributes operationalLimitsGroupAttributes1Updated = buildOperationalLimitsGroup(operationLimitGroupId1, 1, 5, 255, 458);
        Resource<LineAttributes> updatedLine = Resource.lineBuilder()
                .id(lineId)
                .variantNum(1)
                .attributes(LineAttributes.builder()
                        .voltageLevelId1("vl1")
                        .voltageLevelId2("vl2")
                        .b1(2.6)
                        .selectedOperationalLimitsGroupId1(operationLimitGroupId1)
                        .operationalLimitsGroups1(Map.of(operationLimitGroupId1, operationalLimitsGroupAttributes1Updated))
                        .build())
                .build();
        networkStoreRepository.updateLines(NETWORK_UUID, List.of(updatedLine));

        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 1, 2, "variant2");
        operationalLimitsGroupAttributes1Updated = buildOperationalLimitsGroup(operationLimitGroupId1, 1, 9, 145, 456);
        Resource<LineAttributes> updatedLineTwice = Resource.lineBuilder()
                .id(lineId)
                .variantNum(1)
                .attributes(LineAttributes.builder()
                        .voltageLevelId1("vl1")
                        .voltageLevelId2("vl2")
                        .b1(2.6)
                        .selectedOperationalLimitsGroupId1(operationLimitGroupId1)
                        .operationalLimitsGroups1(Map.of(operationLimitGroupId1, operationalLimitsGroupAttributes1Updated))
                        .build())
                .build();
        networkStoreRepository.updateLines(NETWORK_UUID, List.of(updatedLineTwice));

        // Variant 0
        assertEquals(operationalLimitsGroupAttributes1, networkStoreRepository.getOperationalLimitsGroup(NETWORK_UUID, 0, lineId, ResourceType.LINE, operationLimitGroupId1, 1).orElseThrow());
        assertEquals(operationalLimitsGroupAttributes2, networkStoreRepository.getOperationalLimitsGroup(NETWORK_UUID, 0, lineId, ResourceType.LINE, operationLimitGroupId2, 1).orElseThrow());

        // Variant 1
        assertEquals(operationalLimitsGroupAttributes1Updated, networkStoreRepository.getOperationalLimitsGroup(NETWORK_UUID, 1, lineId, ResourceType.LINE, operationLimitGroupId1, 1).orElseThrow());
        assertEquals(operationalLimitsGroupAttributes2, networkStoreRepository.getOperationalLimitsGroup(NETWORK_UUID, 1, lineId, ResourceType.LINE, operationLimitGroupId2, 1).orElseThrow());
    }

    @Test
    void updateOperationalLimitGroupTwiceTwoSides() {
        String networkId = "network1";
        String lineId = "line";
        String operationLimitGroupId1 = "olg1";
        String operationLimitGroupId2 = "olg2";
        String operationLimitGroupId3 = "olg3";
        String operationLimitGroupId4 = "olg4";
        createFullVariantNetwork(networkStoreRepository, NETWORK_UUID, networkId, 0, "variant0");
        OperationalLimitsGroupAttributes operationalLimitsGroupAttributes1 = buildOperationalLimitsGroup(operationLimitGroupId1, 1, 3, 12, 456);
        OperationalLimitsGroupAttributes operationalLimitsGroupAttributes2 = buildOperationalLimitsGroup(operationLimitGroupId2, 1, 4, 50, 457);
        OperationalLimitsGroupAttributes operationalLimitsGroupAttributes3 = buildOperationalLimitsGroup(operationLimitGroupId3, 2, 6, 15, 458);
        OperationalLimitsGroupAttributes operationalLimitsGroupAttributes4 = buildOperationalLimitsGroup(operationLimitGroupId4, 2, 5, 52, 459);
        createLineWithOperationalLimitGroupsTwoSides(Map.of(operationLimitGroupId1, operationalLimitsGroupAttributes1, operationLimitGroupId2, operationalLimitsGroupAttributes2), Map.of(operationLimitGroupId3, operationalLimitsGroupAttributes3, operationLimitGroupId4, operationalLimitsGroupAttributes4), operationLimitGroupId1, operationLimitGroupId3, lineId);
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1");

        OperationalLimitsGroupAttributes operationalLimitsGroupAttributes1Updated = buildOperationalLimitsGroup(operationLimitGroupId1, 1, 5, 255, 451);
        OperationalLimitsGroupAttributes operationalLimitsGroupAttributes3Updated = buildOperationalLimitsGroup(operationLimitGroupId3, 2, 8, 300, 452);
        Resource<LineAttributes> updatedLine = Resource.lineBuilder()
                .id(lineId)
                .variantNum(1)
                .attributes(LineAttributes.builder()
                        .voltageLevelId1("vl1")
                        .voltageLevelId2("vl2")
                        .b1(2.6)
                        .selectedOperationalLimitsGroupId1(operationLimitGroupId1)
                        .operationalLimitsGroups1(Map.of(operationLimitGroupId1, operationalLimitsGroupAttributes1Updated))
                        .selectedOperationalLimitsGroupId2(operationLimitGroupId3)
                        .operationalLimitsGroups2(Map.of(operationLimitGroupId3, operationalLimitsGroupAttributes3Updated))
                        .build())
                .build();
        networkStoreRepository.updateLines(NETWORK_UUID, List.of(updatedLine));

        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 1, 2, "variant2");
        OperationalLimitsGroupAttributes operationalLimitsGroupAttributes2Updated = buildOperationalLimitsGroup(operationLimitGroupId2, 1, 6, 123, 456);
        OperationalLimitsGroupAttributes operationalLimitsGroupAttributes4Updated = buildOperationalLimitsGroup(operationLimitGroupId4, 2, 15, 456, 457);
        Resource<LineAttributes> updatedLineTwice = Resource.lineBuilder()
                .id(lineId)
                .variantNum(2)
                .attributes(LineAttributes.builder()
                        .voltageLevelId1("vl1")
                        .voltageLevelId2("vl2")
                        .b1(2.6)
                        .selectedOperationalLimitsGroupId1(operationLimitGroupId1)
                        .operationalLimitsGroups1(Map.of(operationLimitGroupId2, operationalLimitsGroupAttributes2Updated))
                        .selectedOperationalLimitsGroupId2(operationLimitGroupId4)
                        .operationalLimitsGroups2(Map.of(operationLimitGroupId4, operationalLimitsGroupAttributes4Updated))
                        .build())
                .build();
        networkStoreRepository.updateLines(NETWORK_UUID, List.of(updatedLineTwice));

        // Variant 0
        assertEquals(operationalLimitsGroupAttributes1, networkStoreRepository.getOperationalLimitsGroup(NETWORK_UUID, 0, lineId, ResourceType.LINE, operationLimitGroupId1, 1).orElseThrow());
        assertEquals(operationalLimitsGroupAttributes2, networkStoreRepository.getOperationalLimitsGroup(NETWORK_UUID, 0, lineId, ResourceType.LINE, operationLimitGroupId2, 1).orElseThrow());
        assertEquals(operationalLimitsGroupAttributes3, networkStoreRepository.getOperationalLimitsGroup(NETWORK_UUID, 0, lineId, ResourceType.LINE, operationLimitGroupId3, 2).orElseThrow());
        assertEquals(operationalLimitsGroupAttributes4, networkStoreRepository.getOperationalLimitsGroup(NETWORK_UUID, 0, lineId, ResourceType.LINE, operationLimitGroupId4, 2).orElseThrow());

        // Variant 1
        assertEquals(operationalLimitsGroupAttributes1Updated, networkStoreRepository.getOperationalLimitsGroup(NETWORK_UUID, 1, lineId, ResourceType.LINE, operationLimitGroupId1, 1).orElseThrow());
        assertEquals(operationalLimitsGroupAttributes2, networkStoreRepository.getOperationalLimitsGroup(NETWORK_UUID, 1, lineId, ResourceType.LINE, operationLimitGroupId2, 1).orElseThrow());
        assertEquals(operationalLimitsGroupAttributes3Updated, networkStoreRepository.getOperationalLimitsGroup(NETWORK_UUID, 1, lineId, ResourceType.LINE, operationLimitGroupId3, 2).orElseThrow());
        assertEquals(operationalLimitsGroupAttributes4, networkStoreRepository.getOperationalLimitsGroup(NETWORK_UUID, 1, lineId, ResourceType.LINE, operationLimitGroupId4, 2).orElseThrow());

        // Variant 2
        assertEquals(operationalLimitsGroupAttributes1Updated, networkStoreRepository.getOperationalLimitsGroup(NETWORK_UUID, 2, lineId, ResourceType.LINE, operationLimitGroupId1, 1).orElseThrow());
        assertEquals(operationalLimitsGroupAttributes2Updated, networkStoreRepository.getOperationalLimitsGroup(NETWORK_UUID, 2, lineId, ResourceType.LINE, operationLimitGroupId2, 1).orElseThrow());
        assertEquals(operationalLimitsGroupAttributes3Updated, networkStoreRepository.getOperationalLimitsGroup(NETWORK_UUID, 2, lineId, ResourceType.LINE, operationLimitGroupId3, 2).orElseThrow());
        assertEquals(operationalLimitsGroupAttributes4Updated, networkStoreRepository.getOperationalLimitsGroup(NETWORK_UUID, 2, lineId, ResourceType.LINE, operationLimitGroupId4, 2).orElseThrow());
    }

    @Test
    void addOperationalLimitGroup() {
        String networkId = "network1";
        String lineId = "line";
        String operationLimitGroupId1 = "olg1";
        String operationLimitGroupId2 = "olg2";
        String operationLimitGroupId3 = "olg3";
        createFullVariantNetwork(networkStoreRepository, NETWORK_UUID, networkId, 0, "variant0");
        OperationalLimitsGroupAttributes operationalLimitsGroupAttributes1 = buildOperationalLimitsGroup(operationLimitGroupId1, 1, 3, 12, 456);
        OperationalLimitsGroupAttributes operationalLimitsGroupAttributes2 = buildOperationalLimitsGroup(operationLimitGroupId2, 1, 4, 50, 457);
        createLineWithOperationalLimitGroups(Map.of(operationLimitGroupId1, operationalLimitsGroupAttributes1, operationLimitGroupId2, operationalLimitsGroupAttributes2), operationLimitGroupId1, lineId);
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1");

        OperationalLimitsGroupAttributes newOperationalLimitsGroupAttributes = buildOperationalLimitsGroup(operationLimitGroupId3, 1, 5, 255, 458);
        Resource<LineAttributes> updatedLine = Resource.lineBuilder()
                .id(lineId)
                .variantNum(1)
                .attributes(LineAttributes.builder()
                        .voltageLevelId1("vl1")
                        .voltageLevelId2("vl2")
                        .b1(2.6)
                        .selectedOperationalLimitsGroupId1(operationLimitGroupId3)
                        .operationalLimitsGroups1(Map.of(operationLimitGroupId3, newOperationalLimitsGroupAttributes))
                        .build())
                .build();
        networkStoreRepository.updateLines(NETWORK_UUID, List.of(updatedLine));

        assertEquals(newOperationalLimitsGroupAttributes, networkStoreRepository.getOperationalLimitsGroup(NETWORK_UUID, 1, lineId, ResourceType.LINE, operationLimitGroupId3, 1).orElseThrow());
        assertEquals(3, networkStoreRepository.getAllOperationalLimitsGroupAttributesByResourceType(NETWORK_UUID, 1, ResourceType.LINE).get(lineId).get(1).size());
        assertEquals(newOperationalLimitsGroupAttributes, networkStoreRepository.getAllOperationalLimitsGroupAttributesByResourceType(NETWORK_UUID, 1, ResourceType.LINE).get(lineId).get(1).get(operationLimitGroupId3));
        assertEquals(List.of(operationalLimitsGroupAttributes1, operationalLimitsGroupAttributes2, newOperationalLimitsGroupAttributes), networkStoreRepository.getOperationalLimitsGroupAttributesForBranchSide(NETWORK_UUID, 1, ResourceType.LINE, lineId, 1));
        assertEquals(1, networkStoreRepository.getAllSelectedOperationalLimitsGroupAttributesByResourceType(NETWORK_UUID, 1, ResourceType.LINE).get(lineId).get(1).size());
        assertEquals(newOperationalLimitsGroupAttributes, networkStoreRepository.getAllSelectedOperationalLimitsGroupAttributesByResourceType(NETWORK_UUID, 1, ResourceType.LINE).get(lineId).get(1).get(operationLimitGroupId3));
    }

    @Test
    void addOperationalLimitGroup2() {
        String networkId = "network1";
        String lineId = "line";
        String lineId1 = "line1";
        String operationLimitGroupId1 = "olg1";
        String operationLimitGroupId2 = "olg2";
        String operationLimitGroupId3 = "olg3";
        createFullVariantNetwork(networkStoreRepository, NETWORK_UUID, networkId, 0, "variant0");
        Resource<LineAttributes> line = Resource.lineBuilder()
                .id(lineId1)
                .variantNum(0)
                .attributes(LineAttributes.builder()
                        .voltageLevelId1("vl1")
                        .voltageLevelId2("vl2")
                        .build())
                .build();
        networkStoreRepository.createLines(NETWORK_UUID, List.of(line));
        OperationalLimitsGroupAttributes operationalLimitsGroupAttributes1 = buildOperationalLimitsGroup(operationLimitGroupId1, 1, 3, 12, 456);
        OperationalLimitsGroupAttributes operationalLimitsGroupAttributes2 = buildOperationalLimitsGroup(operationLimitGroupId2, 1, 4, 50, 457);
        createLineWithOperationalLimitGroups(Map.of(operationLimitGroupId1, operationalLimitsGroupAttributes1, operationLimitGroupId2, operationalLimitsGroupAttributes2), operationLimitGroupId1, lineId);
        networkStoreRepository.cloneNetworkVariant(NETWORK_UUID, 0, 1, "variant1");

        OperationalLimitsGroupAttributes newOperationalLimitsGroupAttributes = buildOperationalLimitsGroup(operationLimitGroupId3, 1, 5, 255, 458);
        Resource<LineAttributes> updatedLine = Resource.lineBuilder()
                .id(lineId)
                .variantNum(1)
                .attributes(LineAttributes.builder()
                        .voltageLevelId1("vl1")
                        .voltageLevelId2("vl2")
                        .b1(2.6)
                        .selectedOperationalLimitsGroupId1(operationLimitGroupId3)
                        .operationalLimitsGroups1(Map.of(operationLimitGroupId3, newOperationalLimitsGroupAttributes))
                        .build())
                .build();
        Resource<LineAttributes> updatedLine1 = Resource.lineBuilder()
                .id(lineId1)
                .variantNum(1)
                .attributes(LineAttributes.builder()
                        .voltageLevelId1("vl1")
                        .voltageLevelId2("vl2")
                        .b1(2.6)
                        .selectedOperationalLimitsGroupId1(operationLimitGroupId3)
                        .operationalLimitsGroups1(Map.of(operationLimitGroupId3, newOperationalLimitsGroupAttributes))
                        .build())
                .build();
        networkStoreRepository.updateLines(NETWORK_UUID, List.of(updatedLine, updatedLine1));

        assertEquals(List.of(operationalLimitsGroupAttributes1, operationalLimitsGroupAttributes2, newOperationalLimitsGroupAttributes), networkStoreRepository.getOperationalLimitsGroupAttributesForBranchSide(NETWORK_UUID, 1, ResourceType.LINE, lineId, 1));
        assertEquals(List.of(newOperationalLimitsGroupAttributes), networkStoreRepository.getOperationalLimitsGroupAttributesForBranchSide(NETWORK_UUID, 1, ResourceType.LINE, lineId1, 1));
    }

    private void createLineWithOperationalLimitGroups(Map<String, OperationalLimitsGroupAttributes> operationalLimitsGroupAttributes, String selectedOperationalLimitGroupId, String lineId) {
        Resource<LineAttributes> line = Resource.lineBuilder()
                .id(lineId)
                .variantNum(0)
                .attributes(LineAttributes.builder()
                        .voltageLevelId1("vl1")
                        .voltageLevelId2("vl2")
                        .selectedOperationalLimitsGroupId1(selectedOperationalLimitGroupId)
                        .operationalLimitsGroups1(operationalLimitsGroupAttributes)
                        .build())
                .build();
        networkStoreRepository.createLines(NETWORK_UUID, List.of(line));
    }

    private void createLineWithOperationalLimitGroupsTwoSides(Map<String, OperationalLimitsGroupAttributes> operationalLimitsGroupAttributes1, Map<String, OperationalLimitsGroupAttributes> operationalLimitsGroupAttributes2, String selectedOperationalLimitGroupId1, String selectedOperationalLimitGroupId2, String lineId) {
        Resource<LineAttributes> line = Resource.lineBuilder()
                .id(lineId)
                .variantNum(0)
                .attributes(LineAttributes.builder()
                        .voltageLevelId1("vl1")
                        .voltageLevelId2("vl2")
                        .selectedOperationalLimitsGroupId1(selectedOperationalLimitGroupId1)
                        .operationalLimitsGroups1(operationalLimitsGroupAttributes1)
                        .selectedOperationalLimitsGroupId2(selectedOperationalLimitGroupId2)
                        .operationalLimitsGroups2(operationalLimitsGroupAttributes2)
                        .build())
                .build();
        networkStoreRepository.createLines(NETWORK_UUID, List.of(line));
    }

    private static OperationalLimitsGroupAttributes buildOperationalLimitsGroup(String operationLimitGroupId, int side, int permLimitValue, int tempLimitValue1, int tempLimitValue2) {
        TreeMap<Integer, TemporaryLimitAttributes> temporaryLimits = new TreeMap<>(Map.of(
                10,
                TemporaryLimitAttributes.builder()
                .value(tempLimitValue1)
                .name("temporarylimit1")
                .acceptableDuration(10)
                .fictitious(false)
                .build(),
                15,
                TemporaryLimitAttributes.builder()
                .value(tempLimitValue2)
                .name("temporarylimit2")
                .acceptableDuration(15)
                .fictitious(false)
                .build()));
        return OperationalLimitsGroupAttributes.builder()
                .id(operationLimitGroupId)
                .currentLimits(LimitsAttributes.builder()
                        .permanentLimit(permLimitValue)
                        .temporaryLimits(temporaryLimits)
                        .build())
                .properties(Map.of("prop1", "value1", "prop2", "value2"))
                .build();
    }
}
