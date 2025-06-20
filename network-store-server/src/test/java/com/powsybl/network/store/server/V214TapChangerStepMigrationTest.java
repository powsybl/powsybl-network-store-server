/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.network.store.model.*;
import com.powsybl.network.store.server.dto.OwnerInfo;
import com.powsybl.network.store.server.exceptions.UncheckedSqlException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.powsybl.network.store.model.NetworkStoreApi.VERSION;
import static com.powsybl.network.store.server.QueryCatalog.*;
import static com.powsybl.network.store.server.Utils.BATCH_SIZE;
import static com.powsybl.network.store.server.Utils.bindValues;
import static com.powsybl.network.store.server.migration.v214tapchangersteps.V214TapChangerStepsMigration.V214_TAP_CHANGER_STEP_TABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @author Etienne Lesot <etienne.lesot at rte-france.com>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class V214TapChangerStepMigrationTest {

    private static final UUID NETWORK_UUID = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");

    @Autowired
    private NetworkStoreRepository networkStoreRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mvc;

    @DynamicPropertySource
    static void makeTestDbSuffix(DynamicPropertyRegistry registry) {
        UUID uuid = UUID.randomUUID();
        registry.add("testDbSuffix", () -> uuid);
    }

    @Test
    void migrateV214StepChangerStepsTest() throws Exception {
        createNetwork();
        create2WTLine();
        create3WTLine();
        // To simulate the state of a non migrated network, we first clean the limits created with the new code.
        truncateTable(TAP_CHANGER_STEP_TABLE);

        // Then we add the limits with the V2.11 model
        TapChangerAttributes tapChangerAttributes1 = createTapChangerAttributes1(0);
        TapChangerAttributes tapChangerAttributes2 = createTapChangerAttributes2(0);
        TapChangerAttributes tapChangerAttributes3 = createTapChangerAttributes1(3);
        TapChangerAttributes tapChangerAttributes4 = createTapChangerAttributes2(2);
        insertV214TapChangerSteps("2wt", ResourceType.TWO_WINDINGS_TRANSFORMER, tapChangerAttributes1, tapChangerAttributes2);
        insertV214TapChangerSteps("3wt", ResourceType.THREE_WINDINGS_TRANSFORMER, tapChangerAttributes3, tapChangerAttributes4);

        assertEquals(0, countRowsByEquipmentId("2wt", TAP_CHANGER_STEP_TABLE));
        assertEquals(0, countRowsByEquipmentId("3wt", TAP_CHANGER_STEP_TABLE));

        // Finally we migrate the network
        mvc.perform(MockMvcRequestBuilders.put("/" + VERSION + "/migration/v214tapChangeSteps/" + NETWORK_UUID + "/0")
                        .contentType(APPLICATION_JSON))
                .andExpect(status().isOk());

        assertEquals(2, countRowsByEquipmentId("2wt", TAP_CHANGER_STEP_TABLE));
        assertEquals(2, countRowsByEquipmentId("3wt", TAP_CHANGER_STEP_TABLE));

        mvc.perform(get("/" + VERSION + "/networks/" + NETWORK_UUID + "/" + Resource.INITIAL_VARIANT_NUM + "/2-windings-transformers")
                        .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("data[0].id").value("2wt"))
                .andExpect(jsonPath("data[0].attributes.phaseTapChangerAttributes.steps[0].rho").value(0.0))
                .andExpect(jsonPath("data[0].attributes.phaseTapChangerAttributes.steps[0].r").value(0.1))
                .andExpect(jsonPath("data[0].attributes.phaseTapChangerAttributes.steps[0].x").value(0.2))
                .andExpect(jsonPath("data[0].attributes.phaseTapChangerAttributes.steps[0].g").value(0.3))
                .andExpect(jsonPath("data[0].attributes.phaseTapChangerAttributes.steps[0].b").value(0.4))
                .andExpect(jsonPath("data[0].attributes.phaseTapChangerAttributes.steps[0].alpha").value(1.1))
                .andExpect(jsonPath("data[0].attributes.phaseTapChangerAttributes.steps[1].rho").value(0.0))
                .andExpect(jsonPath("data[0].attributes.phaseTapChangerAttributes.steps[1].r").value(0.1))
                .andExpect(jsonPath("data[0].attributes.phaseTapChangerAttributes.steps[1].x").value(0.2))
                .andExpect(jsonPath("data[0].attributes.phaseTapChangerAttributes.steps[1].g").value(0.3))
                .andExpect(jsonPath("data[0].attributes.phaseTapChangerAttributes.steps[1].b").value(0.4))
                .andExpect(jsonPath("data[0].attributes.phaseTapChangerAttributes.steps[1].alpha").value(1.01))
                .andExpect(jsonPath("data[0].attributes.phaseTapChangerAttributes.steps[2].rho").value(0.0))
                .andExpect(jsonPath("data[0].attributes.phaseTapChangerAttributes.steps[2].r").value(0.1))
                .andExpect(jsonPath("data[0].attributes.phaseTapChangerAttributes.steps[2].x").value(0.2))
                .andExpect(jsonPath("data[0].attributes.phaseTapChangerAttributes.steps[2].g").value(0.3))
                .andExpect(jsonPath("data[0].attributes.phaseTapChangerAttributes.steps[2].b").value(0.4))
                .andExpect(jsonPath("data[0].attributes.phaseTapChangerAttributes.steps[2].alpha").value(1.001))
                .andExpect(jsonPath("data[0].attributes.ratioTapChangerAttributes.steps[0].rho").value(1.1))
                .andExpect(jsonPath("data[0].attributes.ratioTapChangerAttributes.steps[0].r").value(0.1))
                .andExpect(jsonPath("data[0].attributes.ratioTapChangerAttributes.steps[0].x").value(0.2))
                .andExpect(jsonPath("data[0].attributes.ratioTapChangerAttributes.steps[0].g").value(0.3))
                .andExpect(jsonPath("data[0].attributes.ratioTapChangerAttributes.steps[0].b").value(0.4))
                .andExpect(jsonPath("data[0].attributes.ratioTapChangerAttributes.steps[1].rho").value(1.01))
                .andExpect(jsonPath("data[0].attributes.ratioTapChangerAttributes.steps[1].r").value(0.1))
                .andExpect(jsonPath("data[0].attributes.ratioTapChangerAttributes.steps[1].x").value(0.2))
                .andExpect(jsonPath("data[0].attributes.ratioTapChangerAttributes.steps[1].g").value(0.3))
                .andExpect(jsonPath("data[0].attributes.ratioTapChangerAttributes.steps[1].b").value(0.4))
                .andExpect(jsonPath("data[0].attributes.ratioTapChangerAttributes.steps[2].rho").value(1.001))
                .andExpect(jsonPath("data[0].attributes.ratioTapChangerAttributes.steps[2].r").value(0.1))
                .andExpect(jsonPath("data[0].attributes.ratioTapChangerAttributes.steps[2].x").value(0.2))
                .andExpect(jsonPath("data[0].attributes.ratioTapChangerAttributes.steps[2].g").value(0.3))
                .andExpect(jsonPath("data[0].attributes.ratioTapChangerAttributes.steps[2].b").value(0.4));

        mvc.perform(get("/" + VERSION + "/networks/" + NETWORK_UUID + "/" + Resource.INITIAL_VARIANT_NUM + "/3-windings-transformers")
                .contentType(APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
            .andExpect(jsonPath("data[0].id").value("3wt"))
            .andExpect(jsonPath("data[0].attributes.leg2.phaseTapChangerAttributes.steps[0].rho").value(0.0))
            .andExpect(jsonPath("data[0].attributes.leg2.phaseTapChangerAttributes.steps[0].r").value(0.1))
            .andExpect(jsonPath("data[0].attributes.leg2.phaseTapChangerAttributes.steps[0].x").value(0.2))
            .andExpect(jsonPath("data[0].attributes.leg2.phaseTapChangerAttributes.steps[0].g").value(0.3))
            .andExpect(jsonPath("data[0].attributes.leg2.phaseTapChangerAttributes.steps[0].b").value(0.4))
            .andExpect(jsonPath("data[0].attributes.leg2.phaseTapChangerAttributes.steps[0].alpha").value(1.1))
            .andExpect(jsonPath("data[0].attributes.leg2.phaseTapChangerAttributes.steps[1].rho").value(0.0))
            .andExpect(jsonPath("data[0].attributes.leg2.phaseTapChangerAttributes.steps[1].r").value(0.1))
            .andExpect(jsonPath("data[0].attributes.leg2.phaseTapChangerAttributes.steps[1].x").value(0.2))
            .andExpect(jsonPath("data[0].attributes.leg2.phaseTapChangerAttributes.steps[1].g").value(0.3))
            .andExpect(jsonPath("data[0].attributes.leg2.phaseTapChangerAttributes.steps[1].b").value(0.4))
            .andExpect(jsonPath("data[0].attributes.leg2.phaseTapChangerAttributes.steps[1].alpha").value(1.01))
            .andExpect(jsonPath("data[0].attributes.leg2.phaseTapChangerAttributes.steps[2].rho").value(0.0))
            .andExpect(jsonPath("data[0].attributes.leg2.phaseTapChangerAttributes.steps[2].r").value(0.1))
            .andExpect(jsonPath("data[0].attributes.leg2.phaseTapChangerAttributes.steps[2].x").value(0.2))
            .andExpect(jsonPath("data[0].attributes.leg2.phaseTapChangerAttributes.steps[2].g").value(0.3))
            .andExpect(jsonPath("data[0].attributes.leg2.phaseTapChangerAttributes.steps[2].b").value(0.4))
            .andExpect(jsonPath("data[0].attributes.leg2.phaseTapChangerAttributes.steps[2].alpha").value(1.001))
            .andExpect(jsonPath("data[0].attributes.leg3.ratioTapChangerAttributes.steps[0].rho").value(1.1))
            .andExpect(jsonPath("data[0].attributes.leg3.ratioTapChangerAttributes.steps[0].r").value(0.1))
            .andExpect(jsonPath("data[0].attributes.leg3.ratioTapChangerAttributes.steps[0].x").value(0.2))
            .andExpect(jsonPath("data[0].attributes.leg3.ratioTapChangerAttributes.steps[0].g").value(0.3))
            .andExpect(jsonPath("data[0].attributes.leg3.ratioTapChangerAttributes.steps[0].b").value(0.4))
            .andExpect(jsonPath("data[0].attributes.leg3.ratioTapChangerAttributes.steps[1].rho").value(1.01))
            .andExpect(jsonPath("data[0].attributes.leg3.ratioTapChangerAttributes.steps[1].r").value(0.1))
            .andExpect(jsonPath("data[0].attributes.leg3.ratioTapChangerAttributes.steps[1].x").value(0.2))
            .andExpect(jsonPath("data[0].attributes.leg3.ratioTapChangerAttributes.steps[1].g").value(0.3))
            .andExpect(jsonPath("data[0].attributes.leg3.ratioTapChangerAttributes.steps[1].b").value(0.4))
            .andExpect(jsonPath("data[0].attributes.leg3.ratioTapChangerAttributes.steps[2].rho").value(1.001))
            .andExpect(jsonPath("data[0].attributes.leg3.ratioTapChangerAttributes.steps[2].r").value(0.1))
            .andExpect(jsonPath("data[0].attributes.leg3.ratioTapChangerAttributes.steps[2].x").value(0.2))
            .andExpect(jsonPath("data[0].attributes.leg3.ratioTapChangerAttributes.steps[2].g").value(0.3))
            .andExpect(jsonPath("data[0].attributes.leg3.ratioTapChangerAttributes.steps[2].b").value(0.4));
    }

    private void truncateTable(String tableName) {
        try (Connection connection = networkStoreRepository.getDataSource().getConnection()) {
            try (var preparedStmt = connection.prepareStatement("delete from " + tableName)) {
                preparedStmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private TapChangerAttributes createTapChangerAttributes1(int side) {
        TapChangerAttributes tapChanger = new TapChangerAttributes();
        List<TapChangerStepAttributes> steps = new ArrayList<>();

        TapChangerStepAttributes ratioStepOne = TapChangerStepAttributes.builder()
            .side(side)
            .index(0)
            .r(0.1)
            .x(0.2)
            .g(0.3)
            .b(0.4)
            .rho(1.1)
            .type(TapChangerType.RATIO)
            .build();
        TapChangerStepAttributes ratioStepTwo = TapChangerStepAttributes.builder()
            .side(side)
            .index(1)
            .r(0.1)
            .x(0.2)
            .g(0.3)
            .b(0.4)
            .rho(1.01)
            .type(TapChangerType.RATIO)
            .build();
        TapChangerStepAttributes ratioStepThree = TapChangerStepAttributes.builder()
            .side(side)
            .index(2)
            .r(0.1)
            .x(0.2)
            .g(0.3)
            .b(0.4)
            .rho(1.001)
            .type(TapChangerType.RATIO)
            .build();
        steps.add(ratioStepOne);
        steps.add(ratioStepTwo);
        steps.add(ratioStepThree);
        tapChanger.setSteps(steps);

        return tapChanger;
    }

    private TapChangerAttributes createTapChangerAttributes2(int side) {
        TapChangerAttributes tapChanger = new TapChangerAttributes();
        List<TapChangerStepAttributes> steps = new ArrayList<>();
        TapChangerStepAttributes ratioStepOne = TapChangerStepAttributes.builder()
            .side(side)
            .index(0)
            .r(0.1)
            .x(0.2)
            .g(0.3)
            .b(0.4)
            .alpha(1.1)
            .type(TapChangerType.PHASE)
            .build();
        TapChangerStepAttributes ratioStepTwo = TapChangerStepAttributes.builder()
            .side(side)
            .index(1)
            .r(0.1)
            .x(0.2)
            .g(0.3)
            .b(0.4)
            .alpha(1.01)
            .type(TapChangerType.PHASE)
            .build();
        TapChangerStepAttributes ratioStepThree = TapChangerStepAttributes.builder()
            .side(side)
            .index(2)
            .r(0.1)
            .x(0.2)
            .g(0.3)
            .b(0.4)
            .alpha(1.001)
            .type(TapChangerType.PHASE)
            .build();
        steps.add(ratioStepOne);
        steps.add(ratioStepTwo);
        steps.add(ratioStepThree);
        tapChanger.setSteps(steps);

        return tapChanger;
    }

    private void insertV214TapChangerSteps(String equipmentId, ResourceType resourceType,
                                           TapChangerAttributes tapChangerAttributes1, TapChangerAttributes tapChangerAttributes2) {
        OwnerInfo ownerInfo = new OwnerInfo(equipmentId, resourceType, NETWORK_UUID, 0);

        insertV214TapChangerSteps(Map.of(ownerInfo, tapChangerAttributes1));
        insertV214TapChangerSteps(Map.of(ownerInfo, tapChangerAttributes2));
    }

    public void createNetwork() throws Exception {
        Resource<NetworkAttributes> n1 = Resource.networkBuilder()
                .id("n1")
                .variantNum(0)
                .attributes(NetworkAttributes.builder()
                        .uuid(NETWORK_UUID)
                        .variantId(VariantManagerConstants.INITIAL_VARIANT_ID)
                        .caseDate(ZonedDateTime.parse("2015-01-01T00:00:00.000Z"))
                        .build())
                .build();

        mvc.perform(post("/" + VERSION + "/networks")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Collections.singleton(n1))))
                .andExpect(status().isCreated());
    }

    public void createLine() throws Exception {
        Resource<LineAttributes> resLine = Resource.lineBuilder()
                .id("l1")
                .attributes(LineAttributes.builder().build())
                .build();

        mvc.perform(post("/" + VERSION + "/networks/" + NETWORK_UUID + "/lines")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Collections.singleton(resLine))))
                .andExpect(status().isCreated());
    }

    public void createDanglineLine() throws Exception {
        Resource<DanglingLineAttributes> danglingLine = Resource.danglingLineBuilder()
                .id("dl1")
                .attributes(DanglingLineAttributes.builder().build())
                .build();

        mvc.perform(post("/" + VERSION + "/networks/" + NETWORK_UUID + "/dangling-lines")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Collections.singleton(danglingLine))))
                .andExpect(status().isCreated());
    }

    public void create2WTLine() throws Exception {
        Resource<TwoWindingsTransformerAttributes> twoWindingsTransformer = Resource.twoWindingsTransformerBuilder()
                .id("2wt")
                .attributes(TwoWindingsTransformerAttributes.builder().build())
                .build();

        mvc.perform(post("/" + VERSION + "/networks/" + NETWORK_UUID + "/2-windings-transformers")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Collections.singleton(twoWindingsTransformer))))
                .andExpect(status().isCreated());
    }

    public void create3WTLine() throws Exception {
        Resource<ThreeWindingsTransformerAttributes> twoWindingsTransformer = Resource.threeWindingsTransformerBuilder()
                .id("3wt")
                .attributes(ThreeWindingsTransformerAttributes.builder()
                        .leg1(new LegAttributes())
                        .leg2(new LegAttributes())
                        .leg3(new LegAttributes())
                        .build())
                .build();

        mvc.perform(post("/" + VERSION + "/networks/" + NETWORK_UUID + "/3-windings-transformers")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Collections.singleton(twoWindingsTransformer))))
                .andExpect(status().isCreated());
    }

    public static String buildInsertV214TapChangerStepQuery() {
        return "insert into " + V214_TAP_CHANGER_STEP_TABLE +
            "(" +
            EQUIPMENT_ID_COLUMN + ", " +
            EQUIPMENT_TYPE_COLUMN + ", " +
            NETWORK_UUID_COLUMN + ", " +
            VARIANT_NUM_COLUMN + ", " +
            INDEX_COLUMN + ", " +
            SIDE_COLUMN + ", " +
            TAPCHANGER_TYPE_COLUMN + ", " +
            "rho" + ", " +
            "r" + ", " +
            "x" + ", " +
            "g" + ", " +
            "b" + ", " +
            ALPHA_COLUMN + ")" +
            " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    public int countRowsByEquipmentId(String equipmentId, String tableName) {
        try (var connection = networkStoreRepository.getDataSource().getConnection()) {
            try (var preparedStmt = connection.prepareStatement("select count(*) from " + tableName + " where " + EQUIPMENT_ID_COLUMN + " = ?")) {
                preparedStmt.setString(1, equipmentId);
                try (ResultSet resultSet = preparedStmt.executeQuery()) {
                    resultSet.next();
                    return resultSet.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public void insertV214TapChangerSteps(Map<OwnerInfo, TapChangerAttributes> tapChangerAttributesMap) {
        Map<OwnerInfo, List<TapChangerStepAttributes>> tapChangerSteps = tapChangerAttributesMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getSteps()));
        try (var connection = networkStoreRepository.getDataSource().getConnection()) {
            try (var preparedStmt = connection.prepareStatement(buildInsertV214TapChangerStepQuery())) {
                List<Object> values = new ArrayList<>(13);
                List<Map.Entry<OwnerInfo, List<TapChangerStepAttributes>>> list = new ArrayList<>(tapChangerSteps.entrySet());
                for (List<Map.Entry<OwnerInfo, List<TapChangerStepAttributes>>> subUnit : Lists.partition(list, BATCH_SIZE)) {
                    for (Map.Entry<OwnerInfo, List<TapChangerStepAttributes>> entry : subUnit) {
                        for (TapChangerStepAttributes tapChangerStep : entry.getValue()) {
                            values.clear();
                            // In order, from the QueryCatalog.buildInsertTemporaryLimitsQuery SQL query :
                            // equipmentId, equipmentType, networkUuid, variantNum, operationalLimitsGroupId, side, limitType, value
                            values.add(entry.getKey().getEquipmentId());
                            values.add(entry.getKey().getEquipmentType().toString());
                            values.add(entry.getKey().getNetworkUuid());
                            values.add(entry.getKey().getVariantNum());
                            values.add(tapChangerStep.getIndex());
                            values.add(tapChangerStep.getSide());
                            values.add(tapChangerStep.getType().toString());
                            values.add(tapChangerStep.getRho());
                            values.add(tapChangerStep.getR());
                            values.add(tapChangerStep.getX());
                            values.add(tapChangerStep.getG());
                            values.add(tapChangerStep.getB());
                            values.add(tapChangerStep.getAlpha());
                            bindValues(preparedStmt, values, objectMapper);
                            preparedStmt.addBatch();
                        }
                    }
                    preparedStmt.executeBatch();
                }
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }
}
