/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server.migration.v214tapchangersteps;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Stopwatch;
import com.powsybl.network.store.model.ResourceType;
import com.powsybl.network.store.model.TapChangerStepAttributes;
import com.powsybl.network.store.model.TapChangerType;
import com.powsybl.network.store.server.ExtensionHandler;
import com.powsybl.network.store.server.Mappings;
import com.powsybl.network.store.server.NetworkStoreRepository;
import com.powsybl.network.store.server.dto.OwnerInfo;
import com.powsybl.network.store.server.exceptions.UncheckedSqlException;
import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.powsybl.network.store.server.QueryCatalog.EQUIPMENT_TYPE_COLUMN;

/**
 * @author Etienne Lesot <etienne.lesot at rte-france.com>
 */
public final class V214TapChangerStepsMigration implements CustomTaskChange {

    private static final Logger LOGGER = LoggerFactory.getLogger(V214TapChangerStepsMigration.class);

    private NetworkStoreRepository repository;

    public static final String V214_TAP_CHANGER_STEP_TABLE = "v214tapchangerstep";

    public void init(Database database) {
        DataSource dataSource = new SingleConnectionDataSource(((JdbcConnection) database.getConnection()).getUnderlyingConnection(), true);
        ObjectMapper mapper = new ObjectMapper();
        this.repository = new NetworkStoreRepository(dataSource, mapper, new Mappings(), new ExtensionHandler(mapper));
    }

    @Override
    public void execute(Database database) throws CustomChangeException {
        init(database);
        JdbcConnection connection = (JdbcConnection) database.getConnection();
        List<Exception> exceptions = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement("select uuid, variantnum from network ")) {
            ResultSet variants = stmt.executeQuery();
            while (variants.next()) {
                UUID networkId = UUID.fromString(variants.getString(1));
                int variantNum = variants.getInt(2);
                migrateV214TapChangerStepsQuietly(repository, networkId, variantNum, exceptions);
            }
        } catch (Exception e) {
            throw new CustomChangeException("V2.14 tap changer steps migration : error when getting the variants list", e);
        }
        if (!exceptions.isEmpty()) {
            throw new CustomChangeException("V2.14 tap changer steps migration failed. " + exceptions.size() + " exceptions were thrown. First exception as cause : ", exceptions.get(0));
        }
    }

    public static void migrateV214TapChangerStepsQuietly(NetworkStoreRepository repository, UUID networkId, int variantNum, List<Exception> exceptions) {
        try {
            migrateV214TapChangerSteps(repository, networkId, variantNum);
        } catch (Exception e) {
            LOGGER.error("V2.14 tap changer steps migration : failure for network " + networkId + "/variantNum=" + variantNum, e);
            exceptions.add(e);
        }
    }

    public static void migrateV214TapChangerSteps(NetworkStoreRepository repository, UUID networkId, int variantNum) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        migrateV214TapChangerSteps(repository, networkId, variantNum, EQUIPMENT_TYPE_COLUMN, ResourceType.TWO_WINDINGS_TRANSFORMER.toString());
        migrateV214TapChangerSteps(repository, networkId, variantNum, EQUIPMENT_TYPE_COLUMN, ResourceType.THREE_WINDINGS_TRANSFORMER.toString());
        stopwatch.stop();
        LOGGER.info("The tap changer steps of network {}/variantNum={} were migrated in {} ms.", networkId, variantNum, stopwatch.elapsed(TimeUnit.MILLISECONDS));
        LOGGER.info("=============================================================================================================\n\n\n\n");
    }

    private static void migrateV214TapChangerSteps(NetworkStoreRepository repository, UUID networkUuid, int variantNum, String columnNameForWhereClause, String valueForWhereClause) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        Map<OwnerInfo, List<TapChangerStepAttributes>> v214TapChangerSteps = getV214TapChangerSteps(repository, networkUuid, variantNum, columnNameForWhereClause, valueForWhereClause);
        insertNewTapChangerStepsAndDeleteV214(repository, networkUuid, variantNum, v214TapChangerSteps);
        stopwatch.stop();
        LOGGER.info("Tap changer steps of {}S of network {}/variantNum={} migrated in {} ms.", valueForWhereClause, networkUuid, variantNum, stopwatch.elapsed(TimeUnit.MILLISECONDS));
        LOGGER.info("The tap changer steps of {} {}S were migrated.", v214TapChangerSteps.keySet().size(), valueForWhereClause);
    }

    private static void insertNewTapChangerStepsAndDeleteV214(NetworkStoreRepository repository, UUID networkUuid, int variantNum, Map<OwnerInfo, List<TapChangerStepAttributes>> v214TapChangerSteps) {
        try (Connection connection = repository.getDataSource().getConnection()) {
            if (!v214TapChangerSteps.keySet().isEmpty()) {
                repository.insertTapChangerSteps(v214TapChangerSteps);
                deleteV214TapChangerSteps(connection, networkUuid, variantNum, v214TapChangerSteps.keySet().stream().map(OwnerInfo::getEquipmentId).toList());
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    @Override
    public String getConfirmationMessage() {
        return "V2.14 tap changer steps were successfully migrated";
    }

    @Override
    public void setUp() {
        LOGGER.info("Set up migration for tap changer steps");
    }

    @Override
    public void setFileOpener(ResourceAccessor resourceAccessor) {
        LOGGER.info("Set file opener for tap changer steps migration");
    }

    @Override
    public ValidationErrors validate(Database database) {
        return new ValidationErrors();
    }

    public static Map<OwnerInfo, List<TapChangerStepAttributes>> getV214TapChangerStepsWithInClause(NetworkStoreRepository repository, UUID networkUuid, int variantNum, String columnNameForWhereClause, List<String> valuesForInClause, int variantNumOverride) {
        if (valuesForInClause.isEmpty()) {
            return Collections.emptyMap();
        }
        try (var connection = repository.getDataSource().getConnection()) {
            var preparedStmt = connection.prepareStatement(V214TapChangerStepsQueryCatalog.buildV214TapChangerStepWithInClauseQuery(columnNameForWhereClause, valuesForInClause.size()));
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            for (int i = 0; i < valuesForInClause.size(); i++) {
                preparedStmt.setString(3 + i, valuesForInClause.get(i));
            }
            return innerGetV214TapChangerSteps(preparedStmt, variantNumOverride);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public static Map<OwnerInfo, List<TapChangerStepAttributes>> getV214TapChangerSteps(NetworkStoreRepository repository, UUID networkUuid, int variantNum, String columnNameForWhereClause, String valueForWhereClause) {
        try (var connection = repository.getDataSource().getConnection()) {
            var preparedStmt = connection.prepareStatement(V214TapChangerStepsQueryCatalog.buildGetV214TapChangerStepQuery(columnNameForWhereClause));
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            preparedStmt.setString(3, valueForWhereClause);
            return innerGetV214TapChangerSteps(preparedStmt, variantNum);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public static Map<OwnerInfo, List<TapChangerStepAttributes>> getV214TapChangerStepsForVariant(Connection connection, UUID networkUuid, int variantNum, String columnNameForWhereClause, String valueForWhereClause, int variantNumOverride) {
        try (var preparedStmt = connection.prepareStatement(V214TapChangerStepsQueryCatalog.buildGetV214TapChangerStepQuery(columnNameForWhereClause))) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            preparedStmt.setString(3, valueForWhereClause);

            return innerGetV214TapChangerSteps(preparedStmt, variantNumOverride);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private static Map<OwnerInfo, List<TapChangerStepAttributes>> innerGetV214TapChangerSteps(PreparedStatement preparedStmt, int variantNumOverride) throws SQLException {
        try (ResultSet resultSet = preparedStmt.executeQuery()) {
            Map<OwnerInfo, List<TapChangerStepAttributes>> map = new HashMap<>();
            while (resultSet.next()) {

                OwnerInfo owner = new OwnerInfo();
                // In order, from the QueryCatalog.buildTapChangerStepQuery SQL query :
                // equipmentId, equipmentType, networkUuid, variantNum, "side", "tapChangerType", "rho", "r", "x", "g", "b", "alpha"
                owner.setEquipmentId(resultSet.getString(1));
                owner.setEquipmentType(ResourceType.valueOf(resultSet.getString(2)));
                owner.setNetworkUuid(resultSet.getObject(3, UUID.class));
                owner.setVariantNum(variantNumOverride);
                TapChangerStepAttributes tapChangerStep = new TapChangerStepAttributes();
                if (TapChangerType.valueOf(resultSet.getString(7)) == TapChangerType.RATIO) {
                    tapChangerStep.setType(TapChangerType.RATIO);
                } else {
                    tapChangerStep.setType(TapChangerType.PHASE);
                    tapChangerStep.setAlpha(resultSet.getDouble(13));
                }
                tapChangerStep.setIndex(resultSet.getInt(5));
                tapChangerStep.setSide(resultSet.getInt(6));
                tapChangerStep.setRho(resultSet.getDouble(8));
                tapChangerStep.setR(resultSet.getDouble(9));
                tapChangerStep.setX(resultSet.getDouble(10));
                tapChangerStep.setG(resultSet.getDouble(11));
                tapChangerStep.setB(resultSet.getDouble(12));

                map.computeIfAbsent(owner, k -> new ArrayList<>());
                map.get(owner).add(tapChangerStep);
            }
            return map;
        }
    }

    public static void deleteV214TapChangerSteps(Connection connection, UUID networkUuid, int variantNum, List<String> equipmentIds) {
        try {
            try (var preparedStmt = connection.prepareStatement(V214TapChangerStepsQueryCatalog.buildDeleteV214TapChangerStepVariantEquipmentINQuery(equipmentIds.size()))) {
                preparedStmt.setObject(1, networkUuid);
                preparedStmt.setInt(2, variantNum);
                for (int i = 0; i < equipmentIds.size(); i++) {
                    preparedStmt.setString(3 + i, equipmentIds.get(i));
                }
                preparedStmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }
}
