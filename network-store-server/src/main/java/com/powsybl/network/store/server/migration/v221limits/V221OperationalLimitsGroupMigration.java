/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server.migration.v221limits;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Stopwatch;
import com.powsybl.network.store.model.ResourceType;
import com.powsybl.network.store.model.TemporaryLimitAttributes;
import com.powsybl.network.store.server.ExtensionHandler;
import com.powsybl.network.store.server.LimitsHandler;
import com.powsybl.network.store.server.Mappings;
import com.powsybl.network.store.server.NetworkStoreRepository;
import com.powsybl.network.store.server.dto.OwnerInfo;
import com.powsybl.network.store.server.dto.PermanentLimitAttributes;
import com.powsybl.network.store.server.exceptions.UncheckedSqlException;
import com.powsybl.network.store.server.json.PermanentLimitSqlData;
import com.powsybl.network.store.server.json.TemporaryLimitSqlData;
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
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.powsybl.network.store.server.QueryCatalog.EQUIPMENT_TYPE_COLUMN;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
public class V221OperationalLimitsGroupMigration implements CustomTaskChange {

    private static final Logger LOGGER = LoggerFactory.getLogger(V221OperationalLimitsGroupMigration.class);

    private NetworkStoreRepository repository;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public void init(Database database) {
        DataSource dataSource = new SingleConnectionDataSource(((JdbcConnection) database.getConnection()).getUnderlyingConnection(), true);
        Mappings mappings = new Mappings();
        this.repository = new NetworkStoreRepository(dataSource, MAPPER, new Mappings(), new ExtensionHandler(MAPPER), new LimitsHandler(dataSource, MAPPER, mappings));
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
                migrateV221OperationalLimitsGroupQuietly(repository, networkId, variantNum, exceptions);
            }
        } catch (Exception e) {
            throw new CustomChangeException("V2.20 limits migration : error when getting the variants list", e);
        }
        if (!exceptions.isEmpty()) {
            throw new CustomChangeException("V2.20 limits migration failed. " + exceptions.size() + " exceptions were thrown. First exception as cause : ", exceptions.getFirst());
        }
    }

    @Override
    public String getConfirmationMessage() {
        return "V2.21 limits were successfully migrated";
    }

    @Override
    public void setUp() {
        LOGGER.info("Set up migration for limits");
    }

    @Override
    public void setFileOpener(ResourceAccessor resourceAccessor) {
        LOGGER.info("Set file opener for limits migration");
    }

    @Override
    public ValidationErrors validate(Database database) {
        return new ValidationErrors();
    }

    public static Map<OwnerInfo, List<TemporaryLimitAttributes>> getV221TemporaryLimits(NetworkStoreRepository repository,
                                                                                        UUID networkUuid,
                                                                                        int variantNum,
                                                                                        String columnNameForWhereClause,
                                                                                        String valueForWhereClause) {
        try (Connection connection = repository.getDataSource().getConnection()) {
            var preparedStmt = connection.prepareStatement(V221LimitsQueryCatalog.buildGetV221TemporaryLimitQuery(columnNameForWhereClause));
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            preparedStmt.setString(3, valueForWhereClause);

            return innerGetV221TemporaryLimits(preparedStmt);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public static Map<OwnerInfo, List<PermanentLimitAttributes>> getV221PermanentLimits(NetworkStoreRepository repository,
                                                                                        UUID networkUuid,
                                                                                        int variantNum,
                                                                                        String columnNameForWhereClause,
                                                                                        String valueForWhereClause) {
        try (Connection connection = repository.getDataSource().getConnection()) {
            var preparedStmt = connection.prepareStatement(V221LimitsQueryCatalog.buildGetV221PermanentLimitQuery(columnNameForWhereClause));
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            preparedStmt.setString(3, valueForWhereClause);

            return innerGetV221PermanentLimits(preparedStmt);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public static void migrateV221OperationalLimitsGroupQuietly(NetworkStoreRepository repository,
                                                                UUID networkId,
                                                                int variantNum,
                                                                List<Exception> exceptions) {
        try {
            migrateV221OperationalLimitsGroup(repository, networkId, variantNum);
        } catch (Exception e) {
            LOGGER.error("V2.21 limits migration : failure for network " + networkId + "/variantNum=" + variantNum, e);
            exceptions.add(e);
        }
    }

    public static void migrateV221OperationalLimitsGroup(NetworkStoreRepository repository,
                                                         UUID networkId,
                                                         int variantNum) {
        Stopwatch stopwatch = Stopwatch.createStarted();

        migrateV221OperationalLimitsGroup(repository, networkId, variantNum, EQUIPMENT_TYPE_COLUMN, ResourceType.LINE.toString());
        migrateV221OperationalLimitsGroup(repository, networkId, variantNum, EQUIPMENT_TYPE_COLUMN, ResourceType.TWO_WINDINGS_TRANSFORMER.toString());
        migrateV221OperationalLimitsGroup(repository, networkId, variantNum, EQUIPMENT_TYPE_COLUMN, ResourceType.THREE_WINDINGS_TRANSFORMER.toString());
        migrateV221OperationalLimitsGroup(repository, networkId, variantNum, EQUIPMENT_TYPE_COLUMN, ResourceType.DANGLING_LINE.toString());

        stopwatch.stop();
        LOGGER.info("The limits of network {}/variantNum={} were migrated in {} ms.", networkId, variantNum, stopwatch.elapsed(TimeUnit.MILLISECONDS));
        LOGGER.info("=============================================================================================================\n\n\n\n");
    }

    public static void migrateV221OperationalLimitsGroup(NetworkStoreRepository repository,
                                                         UUID networkUuid,
                                                         int variantNum,
                                                         String columnNameForWhereClause,
                                                         String valueForWhereClause) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        Map<OwnerInfo, List<TemporaryLimitAttributes>> v221TemporaryLimits = getV221TemporaryLimits(repository, networkUuid, variantNum, columnNameForWhereClause, valueForWhereClause);
        Map<OwnerInfo, List<PermanentLimitAttributes>> v221PermanentLimits = getV221PermanentLimits(repository, networkUuid, variantNum, columnNameForWhereClause, valueForWhereClause);
        insertNewLimitsV221(repository, v221TemporaryLimits, v221PermanentLimits);
        stopwatch.stop();
        LOGGER.info("Limits of {}S of network {}/variantNum={} migrated in {} ms.", valueForWhereClause, networkUuid, variantNum, stopwatch.elapsed(TimeUnit.MILLISECONDS));
        LOGGER.info("       The permanent limits of {} {}S were migrated.", v221PermanentLimits.size(), valueForWhereClause);
        LOGGER.info("       The temporary limits of {} {}S were migrated.\n", v221TemporaryLimits.size(), valueForWhereClause);
    }

    private static void insertNewLimitsV221(NetworkStoreRepository repository,
                                            Map<OwnerInfo, List<TemporaryLimitAttributes>> v221TemporaryLimits,
                                            Map<OwnerInfo, List<PermanentLimitAttributes>> v221PermanentLimits) {
        if (!v221PermanentLimits.isEmpty() || !v221TemporaryLimits.isEmpty()) {
            repository.getLimitsHandler().insertOperationalLimitsGroupAttributes(v221PermanentLimits, v221TemporaryLimits, Map.of());
        }
    }

    public static Map<OwnerInfo, List<TemporaryLimitAttributes>> innerGetV221TemporaryLimits(PreparedStatement preparedStmt) throws SQLException {
        try (ResultSet resultSet = preparedStmt.executeQuery()) {
            Map<OwnerInfo, List<TemporaryLimitAttributes>> map = new HashMap<>();
            while (resultSet.next()) {
                // In order, from the QueryCatalog.buildGetV221TemporaryLimitQuery SQL query :
                // equipmentId, equipmentType, networkUuid, variantNum, temporaryLimits
                OwnerInfo owner = new OwnerInfo(resultSet.getString(1), ResourceType.valueOf(resultSet.getString(2)), resultSet.getObject(3, UUID.class), resultSet.getInt(4));

                String temporaryLimitData = resultSet.getString(5);
                List<TemporaryLimitSqlData> parsedTemporaryLimitData = MAPPER.readValue(temporaryLimitData, new TypeReference<>() { });
                List<TemporaryLimitAttributes> temporaryLimits = parsedTemporaryLimitData.stream().map(TemporaryLimitSqlData::toTemporaryLimitAttributes).toList();
                if (!temporaryLimits.isEmpty()) {
                    map.put(owner, temporaryLimits);
                }
            }
            return map;
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Map<OwnerInfo, List<PermanentLimitAttributes>> innerGetV221PermanentLimits(PreparedStatement preparedStmt) throws SQLException {
        try (ResultSet resultSet = preparedStmt.executeQuery()) {
            Map<OwnerInfo, List<PermanentLimitAttributes>> map = new HashMap<>();
            while (resultSet.next()) {
                // In order, from the QueryCatalog.buildGetV221PermanentLimitQuery SQL query :
                // equipmentId, equipmentType, networkUuid, variantNum, permanentLimits
                OwnerInfo owner = new OwnerInfo(resultSet.getString(1), ResourceType.valueOf(resultSet.getString(2)), resultSet.getObject(3, UUID.class), resultSet.getInt(4));

                String permanentLimitData = resultSet.getString(5);
                List<PermanentLimitSqlData> parsedPermanentLimitData = MAPPER.readValue(permanentLimitData, new TypeReference<>() { });
                List<PermanentLimitAttributes> permanentLimits = parsedPermanentLimitData.stream().map(PermanentLimitSqlData::toPermanentLimitAttributes).toList();
                if (!permanentLimits.isEmpty()) {
                    map.put(owner, permanentLimits);
                }
            }
            return map;
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
