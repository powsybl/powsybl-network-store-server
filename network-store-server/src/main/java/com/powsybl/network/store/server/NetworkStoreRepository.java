/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.ReactiveLimitsKind;
import com.powsybl.iidm.network.ThreeSides;
import com.powsybl.network.store.model.*;
import com.powsybl.network.store.model.utils.VariantUtils;
import com.powsybl.network.store.server.dto.LimitsInfos;
import com.powsybl.network.store.server.dto.OwnerInfo;
import com.powsybl.network.store.server.dto.RegulatingOwnerInfo;
import com.powsybl.network.store.server.exceptions.JsonApiErrorResponseException;
import com.powsybl.network.store.server.exceptions.UncheckedSqlException;
import com.powsybl.network.store.server.json.TapChangerStepSqlData;
import com.powsybl.ws.commons.LogUtils;
import lombok.Getter;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import javax.sql.DataSource;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.powsybl.network.store.model.TapChangerType.PHASE;
import static com.powsybl.network.store.model.TapChangerType.RATIO;
import static com.powsybl.network.store.server.Mappings.*;
import static com.powsybl.network.store.server.QueryCatalog.*;
import static com.powsybl.network.store.server.Utils.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Repository
public class NetworkStoreRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkStoreRepository.class);

    public NetworkStoreRepository(DataSource dataSource, ObjectMapper mapper, Mappings mappings, ExtensionHandler extensionHandler, LimitsHandler limitsHandler) {
        this.dataSource = dataSource;
        this.mappings = mappings;
        this.mapper = mapper.registerModule(new JavaTimeModule())
                .configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false)
                .configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        this.extensionHandler = extensionHandler;
        this.limitsHandler = limitsHandler;
    }

    @Getter
    private final DataSource dataSource;

    private final ObjectMapper mapper;

    private final Mappings mappings;

    private final ExtensionHandler extensionHandler;

    @Getter
    private final LimitsHandler limitsHandler;

    private static final String SUBSTATION_ID = "substationid";

    // network

    /**
     * Get all networks infos.
     */
    public List<NetworkInfos> getNetworksInfos() {
        try (var connection = dataSource.getConnection()) {
            try (var stmt = connection.createStatement()) {
                try (ResultSet resultSet = stmt.executeQuery(QueryCatalog.buildGetNetworkInfos())) {
                    List<NetworkInfos> networksInfos = new ArrayList<>();
                    while (resultSet.next()) {
                        networksInfos.add(new NetworkInfos(resultSet.getObject(1, UUID.class),
                                                           resultSet.getString(2)));
                    }
                    return networksInfos;
                }
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public List<VariantInfos> getVariantsInfos(UUID networkUuid) {
        try (var connection = dataSource.getConnection()) {
            try (var preparedStmt = connection.prepareStatement(QueryCatalog.buildGetVariantsInfos())) {
                preparedStmt.setObject(1, networkUuid);
                try (ResultSet resultSet = preparedStmt.executeQuery()) {
                    List<VariantInfos> variantsInfos = new ArrayList<>();
                    while (resultSet.next()) {
                        variantsInfos.add(new VariantInfos(resultSet.getString(1), resultSet.getInt(2)));
                    }
                    return variantsInfos;
                }
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public List<String> getIdentifiablesIds(UUID networkUuid, int variantNum) {
        Stopwatch stopwatch = Stopwatch.createStarted();

        List<String> ids = new ArrayList<>();
        try (var connection = dataSource.getConnection()) {
            ids.addAll(PartialVariantUtils.getIdentifiables(
                    variantNum,
                    getNetworkAttributes(connection, networkUuid, variantNum, mappings, mapper).getFullVariantNum(),
                    () -> getTombstonedIdentifiableIds(connection, networkUuid, variantNum),
                    variant -> getIdentifiablesIdsForVariant(connection, networkUuid, variant),
                    Function.identity(),
                    () -> getIdentifiablesIdsForVariant(connection, networkUuid, variantNum)));
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }

        stopwatch.stop();
        LOGGER.info("Get identifiables IDs done in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));

        return ids;
    }

    static List<String> getIdentifiablesIdsForVariant(Connection connection, UUID networkUuid, int variantNum) {
        List<String> ids = new ArrayList<>();
        for (String table : ELEMENT_TABLES) {
            ids.addAll(getIdentifiablesIdsForVariantFromTable(connection, networkUuid, variantNum, table));
        }
        return ids;
    }

    private static List<String> getIdentifiablesIdsForVariantFromTable(Connection connection, UUID networkUuid, int variantNum, String table) {
        List<String> ids = new ArrayList<>();
        try (var preparedStmt = connection.prepareStatement(buildGetIdsQuery(table))) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setObject(2, variantNum);
            try (ResultSet resultSet = preparedStmt.executeQuery()) {
                while (resultSet.next()) {
                    ids.add(resultSet.getString(1));
                }
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
        return ids;
    }

    @FunctionalInterface
    interface SqlExecutor {

        void execute(Connection connection) throws SQLException;
    }

    private static void restoreAutoCommitQuietly(Connection connection) {
        try {
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            LOGGER.error("Exception during autocommit restoration, please check next exception", e);
        }
    }

    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException e) {
            LOGGER.error("Exception during rollback, please check next exception", e);
        }
    }

    private static void executeWithoutAutoCommit(Connection connection, SqlExecutor executor) throws SQLException {
        connection.setAutoCommit(false);
        try {
            executor.execute(connection);
            connection.commit();
        } catch (Exception e) {
            rollbackQuietly(connection);
            throw new RuntimeException(e);
        } finally {
            restoreAutoCommitQuietly(connection);
        }
    }

    private void executeWithoutAutoCommit(SqlExecutor executor) {
        try (var connection = dataSource.getConnection()) {
            executeWithoutAutoCommit(connection, executor);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public void createNetworks(List<Resource<NetworkAttributes>> resources) {
        executeWithoutAutoCommit(connection -> createNetworks(connection, resources));
    }

    private void createNetworks(Connection connection, List<Resource<NetworkAttributes>> resources) throws SQLException {
        var tableMapping = mappings.getNetworkMappings();
        try (var preparedStmt = connection.prepareStatement(QueryCatalog.buildInsertNetworkQuery(tableMapping.getTable(), tableMapping.getColumnsMapping().keySet()))) {
            List<Object> values = new ArrayList<>(2 + tableMapping.getColumnsMapping().size());
            for (List<Resource<NetworkAttributes>> subResources : Lists.partition(resources, BATCH_SIZE)) {
                for (Resource<NetworkAttributes> resource : subResources) {
                    NetworkAttributes attributes = resource.getAttributes();
                    values.clear();
                    values.add(resource.getVariantNum());
                    values.add(resource.getId());
                    for (var mapping : tableMapping.getColumnsMapping().values()) {
                        values.add(mapping.get(attributes));
                    }
                    bindValues(preparedStmt, values, mapper);
                    preparedStmt.addBatch();
                }
                preparedStmt.executeBatch();
            }
        }
        extensionHandler.insertExtensions(connection, extensionHandler.getExtensionsFromNetworks(resources));
    }

    public void updateNetworks(List<Resource<NetworkAttributes>> resources) {
        executeWithoutAutoCommit(connection -> {
            TableMapping networkMapping = mappings.getNetworkMappings();
            try (var preparedStmt = connection.prepareStatement(QueryCatalog.buildUpdateNetworkQuery(networkMapping.getColumnsMapping().keySet()))) {
                List<Object> values = new ArrayList<>(3 + networkMapping.getColumnsMapping().size());
                for (List<Resource<NetworkAttributes>> subResources : Lists.partition(resources, BATCH_SIZE)) {
                    for (Resource<NetworkAttributes> resource : subResources) {
                        NetworkAttributes attributes = resource.getAttributes();
                        values.clear();
                        values.add(resource.getId());
                        for (var e : networkMapping.getColumnsMapping().entrySet()) {
                            String columnName = e.getKey();
                            var mapping = e.getValue();
                            if (!columnName.equals(UUID_COLUMN) && !columnName.equals(VARIANT_ID_COLUMN)) {
                                values.add(mapping.get(attributes));
                            }
                        }
                        values.add(attributes.getUuid());
                        values.add(resource.getVariantNum());
                        bindValues(preparedStmt, values, mapper);
                        preparedStmt.addBatch();
                    }
                    preparedStmt.executeBatch();
                }
            }
            extensionHandler.updateExtensionsFromNetworks(connection, resources);
        });
    }

    public void deleteNetwork(UUID uuid) {
        try (var connection = dataSource.getConnection()) {
            deleteNetwork(uuid, connection);
            deleteIdentifiables(uuid, connection);
            deleteExternalAttributes(uuid, connection);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private static void deleteNetwork(UUID uuid, Connection connection) throws SQLException {
        try (var preparedStmt = connection.prepareStatement(QueryCatalog.buildDeleteNetworkQuery())) {
            preparedStmt.setObject(1, uuid);
            preparedStmt.executeUpdate();
        }
    }

    private static void deleteIdentifiables(UUID uuid, Connection connection) throws SQLException {
        for (String table : ELEMENT_TABLES) {
            try (var preparedStmt = connection.prepareStatement(QueryCatalog.buildDeleteIdentifiablesQuery(table))) {
                preparedStmt.setObject(1, uuid);
                preparedStmt.executeUpdate();
            }
        }
        // Delete tombstoned identifiables
        try (var preparedStmt = connection.prepareStatement(QueryCatalog.buildDeleteTombstonedIdentifiablesQuery())) {
            preparedStmt.setObject(1, uuid);
            preparedStmt.executeUpdate();
        }
    }

    private static void deleteExternalAttributes(UUID uuid, Connection connection) throws SQLException {
        List<String> deleteExternalAttributesQueries = List.of(
                QueryCatalog.buildDeleteTemporaryLimitsQuery(),
                QueryCatalog.buildDeletePermanentLimitsQuery(),
                QueryCatalog.buildDeleteReactiveCapabilityCurvePointsQuery(),
                QueryCatalog.buildDeleteAreaBoundariesQuery(),
                QueryCatalog.buildDeleteRegulatingPointsQuery(),
                QueryCatalog.buildDeleteTapChangerStepQuery(),
                QueryCatalog.buildDeleteTombstonedExternalAttributesQuery(),
                QueryExtensionCatalog.buildDeleteExtensionsQuery(),
                QueryExtensionCatalog.buildDeleteTombstonedExtensionsQuery()
        );

        for (String query : deleteExternalAttributesQueries) {
            try (var preparedStmt = connection.prepareStatement(query)) {
                preparedStmt.setObject(1, uuid);
                preparedStmt.executeUpdate();
            }
        }
    }

    /**
     * Just delete one variant of the network
     */
    public void deleteNetwork(UUID uuid, int variantNum) {
        if (variantNum == Resource.INITIAL_VARIANT_NUM) {
            throw new IllegalArgumentException("Cannot delete initial variant");
        }
        try (var connection = dataSource.getConnection()) {
            deleteNetworkVariant(uuid, variantNum, connection);
            deleteIdentifiablesVariant(uuid, variantNum, connection);
            deleteExternalAttributesVariant(uuid, variantNum, connection);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private static void deleteExternalAttributesVariant(UUID uuid, int variantNum, Connection connection) throws SQLException {
        List<String> deleteExternalAttributesVariantQueries = List.of(
                QueryCatalog.buildDeleteTemporaryLimitsVariantQuery(),
                QueryCatalog.buildDeletePermanentLimitsVariantQuery(),
                QueryCatalog.buildDeleteReactiveCapabilityCurvePointsVariantQuery(),
                QueryCatalog.buildDeleteAreaBoundariesVariantQuery(),
                QueryCatalog.buildDeleteRegulatingPointsVariantQuery(),
                QueryCatalog.buildDeleteTapChangerStepVariantQuery(),
                QueryCatalog.buildDeleteTombstonedExternalAttributesVariantQuery(),
                QueryExtensionCatalog.buildDeleteExtensionsVariantQuery(),
                QueryExtensionCatalog.buildDeleteTombstonedExtensionsVariantQuery()
        );

        for (String query : deleteExternalAttributesVariantQueries) {
            executeDeleteVariantQuery(uuid, variantNum, connection, query);
        }
    }

    private static void deleteIdentifiablesVariant(UUID uuid, int variantNum, Connection connection) throws SQLException {
        for (String table : ELEMENT_TABLES) {
            executeDeleteVariantQuery(uuid, variantNum, connection, QueryCatalog.buildDeleteIdentifiablesVariantQuery(table));
        }
        // Delete of tombstoned identifiables
        executeDeleteVariantQuery(uuid, variantNum, connection, QueryCatalog.buildDeleteTombstonedIdentifiablesVariantQuery());
    }

    private static void deleteNetworkVariant(UUID uuid, int variantNum, Connection connection) throws SQLException {
        executeDeleteVariantQuery(uuid, variantNum, connection, QueryCatalog.buildDeleteNetworkVariantQuery());
    }

    private static void executeDeleteVariantQuery(UUID uuid, int variantNum, Connection connection, String query) throws SQLException {
        try (var preparedStmt = connection.prepareStatement(query)) {
            preparedStmt.setObject(1, uuid);
            preparedStmt.setInt(2, variantNum);
            preparedStmt.executeUpdate();
        }
    }

    public void cloneNetwork(UUID targetNetworkUuid, UUID sourceNetworkUuid, List<String> targetVariantIds) {
        LOGGER.info("Cloning network {} to network {} with variants {}", sourceNetworkUuid, targetNetworkUuid,
                targetVariantIds.stream().map(LogUtils::sanitizeParam).collect(Collectors.toList()));

        var stopwatch = Stopwatch.createStarted();

        List<VariantInfos> variantsInfoList = getVariantsInfos(sourceNetworkUuid).stream()
                .filter(v -> targetVariantIds.contains(v.getId()))
                .sorted(Comparator.comparing(VariantInfos::getNum))
                .collect(Collectors.toList());

        Set<String> variantsNotFound = new HashSet<>(targetVariantIds);
        List<VariantInfos> newNetworkVariants = new ArrayList<>();

        executeWithoutAutoCommit(connection -> {
            for (VariantInfos variantInfos : variantsInfoList) {
                Resource<NetworkAttributes> sourceNetworkAttribute = Utils.getNetwork(sourceNetworkUuid, variantInfos.getNum(), dataSource, mappings, mapper).orElseThrow(() -> new PowsyblException("Cannot retrieve source network attributes uuid : " + sourceNetworkUuid + ", variantId : " + variantInfos.getId()));
                sourceNetworkAttribute.getAttributes().setUuid(targetNetworkUuid);
                sourceNetworkAttribute.getAttributes().setExtensionAttributes(Collections.emptyMap());
                sourceNetworkAttribute.setVariantNum(VariantUtils.findFistAvailableVariantNum(newNetworkVariants));

                newNetworkVariants.add(new VariantInfos(sourceNetworkAttribute.getAttributes().getVariantId(), sourceNetworkAttribute.getVariantNum()));
                variantsNotFound.remove(sourceNetworkAttribute.getAttributes().getVariantId());

                createNetworks(connection, List.of(sourceNetworkAttribute));
                // When cloning all variant of a network to another network, we clone all the identifiables, external attributes and tombstoned
                cloneNetworkElements(connection, sourceNetworkUuid, targetNetworkUuid, sourceNetworkAttribute.getVariantNum(), variantInfos.getNum(), true);
            }
        });

        variantsNotFound.forEach(variantNotFound -> LOGGER.warn("The network {} has no variant ID named : {}, thus it has not been cloned", sourceNetworkUuid, variantNotFound));

        stopwatch.stop();
        LOGGER.info("Network clone done in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    public void cloneNetworkVariant(UUID uuid, int sourceVariantNum, int targetVariantNum, String targetVariantId) {
        String nonNullTargetVariantId = targetVariantId == null ? "variant-" + UUID.randomUUID() : targetVariantId;
        var stopwatch = Stopwatch.createStarted();

        try (var connection = dataSource.getConnection()) {
            NetworkAttributes sourceNetwork = getNetworkAttributes(connection, uuid, sourceVariantNum, mappings, mapper);
            LOGGER.info("Cloning network {} variant {} to variant {}", uuid, sourceVariantNum, targetVariantNum);
            int fullVariantNum = getFullVariantNum(sourceVariantNum, sourceNetwork);
            try (var preparedStmt = connection.prepareStatement(buildCloneNetworksQuery(mappings.getNetworkMappings().getColumnsMapping().keySet()))) {
                preparedStmt.setInt(1, targetVariantNum);
                preparedStmt.setString(2, nonNullTargetVariantId);
                preparedStmt.setInt(3, fullVariantNum);
                preparedStmt.setObject(4, uuid);
                preparedStmt.setInt(5, sourceVariantNum);
                preparedStmt.execute();
            }
            boolean cloneNetworkElements = !sourceNetwork.isFullVariant();
            cloneNetworkElements(connection, uuid, uuid, sourceVariantNum, targetVariantNum, cloneNetworkElements);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }

        stopwatch.stop();
        LOGGER.info("Network variant clone done in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    private static int getFullVariantNum(int sourceVariantNum, NetworkAttributes sourceNetwork) {
        int fullVariantNum = sourceNetwork.getFullVariantNum();
        if (sourceNetwork.isFullVariant()) {
            // Override fullVariantNum when it's a clone from full to partial variant
            fullVariantNum = sourceVariantNum;
        }
        return fullVariantNum;
    }

    private void cloneNetworkElements(Connection connection, UUID uuid, UUID targetUuid, int sourceVariantNum, int targetVariantNum, boolean cloneNetworkElements) throws SQLException {
        if (cloneNetworkElements) {
            cloneIdentifiables(connection, uuid, targetUuid, sourceVariantNum, targetVariantNum);
            cloneExternalAttributes(connection, uuid, targetUuid, sourceVariantNum, targetVariantNum);
        }
        cloneTombstoned(connection, uuid, targetUuid, sourceVariantNum, targetVariantNum);
    }

    private void cloneExternalAttributes(Connection connection, UUID uuid, UUID targetUuid, int sourceVariantNum, int targetVariantNum) throws SQLException {
        Stopwatch stopwatch = Stopwatch.createStarted();
        List<String> externalAttributesQueries = List.of(
                QueryCatalog.buildCloneTemporaryLimitsQuery(),
                QueryCatalog.buildClonePermanentLimitsQuery(),
                QueryCatalog.buildCloneReactiveCapabilityCurvePointsQuery(),
                QueryCatalog.buildCloneAreaBoundariesQuery(),
                QueryCatalog.buildCloneRegulatingPointsQuery(),
                QueryCatalog.buildCloneTapChangerStepQuery(),
                QueryExtensionCatalog.buildCloneExtensionsQuery()
        );

        int totalExternalAttributesCloned = 0;
        for (String query : externalAttributesQueries) {
            try (var preparedStmt = connection.prepareStatement(query)) {
                preparedStmt.setObject(1, targetUuid);
                preparedStmt.setInt(2, targetVariantNum);
                preparedStmt.setObject(3, uuid);
                preparedStmt.setInt(4, sourceVariantNum);
                totalExternalAttributesCloned += preparedStmt.executeUpdate();
            }
        }
        LOGGER.info("Cloned {} external attributes in {}ms", totalExternalAttributesCloned, stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    private void cloneTombstoned(Connection connection, UUID uuid, UUID targetUuid, int sourceVariantNum, int targetVariantNum) throws SQLException {
        Stopwatch stopwatch = Stopwatch.createStarted();
        List<String> tombstonedQueries = List.of(
                QueryCatalog.buildCloneTombstonedIdentifiablesQuery(),
                QueryCatalog.buildCloneTombstonedExternalAttributesQuery(),
                QueryExtensionCatalog.buildCloneTombstonedExtensionsQuery()
        );

        int totalTombstonedCloned = 0;
        for (String query : tombstonedQueries) {
            try (var preparedStmt = connection.prepareStatement(query)) {
                preparedStmt.setObject(1, targetUuid);
                preparedStmt.setInt(2, targetVariantNum);
                preparedStmt.setObject(3, uuid);
                preparedStmt.setInt(4, sourceVariantNum);
                totalTombstonedCloned += preparedStmt.executeUpdate();
            }
        }
        LOGGER.info("Cloned {} tombstoned in {}ms", totalTombstonedCloned, stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    private void cloneIdentifiables(Connection connection, UUID uuid, UUID targetUuid, int sourceVariantNum, int targetVariantNum) throws SQLException {
        Stopwatch stopwatch = Stopwatch.createStarted();
        int totalIdentifiablesCloned = 0;
        for (String tableName : ELEMENT_TABLES) {
            try (var preparedStmt = connection.prepareStatement(buildCloneIdentifiablesQuery(tableName, mappings.getTableMapping(tableName.toLowerCase()).getColumnsMapping().keySet()))) {
                preparedStmt.setInt(1, targetVariantNum);
                preparedStmt.setObject(2, targetUuid);
                preparedStmt.setObject(3, uuid);
                preparedStmt.setInt(4, sourceVariantNum);
                totalIdentifiablesCloned += preparedStmt.executeUpdate();
            }
        }
        LOGGER.info("Cloned {} identifiables in {}ms", totalIdentifiablesCloned, stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    public void cloneNetwork(UUID networkUuid, String sourceVariantId, String targetVariantId, boolean mayOverwrite) {
        List<VariantInfos> variantsInfos = getVariantsInfos(networkUuid);
        Optional<VariantInfos> targetVariant = VariantUtils.getVariant(targetVariantId, variantsInfos);
        if (targetVariant.isPresent()) {
            if (!mayOverwrite) {
                throw new JsonApiErrorResponseException(ErrorObject.cloneOverExisting(targetVariantId));
            } else {
                if (Resource.INITIAL_VARIANT_NUM == targetVariant.get().getNum()) {
                    throw new JsonApiErrorResponseException(ErrorObject.cloneOverInitialForbidden());
                }
                deleteNetwork(networkUuid, targetVariant.get().getNum());
            }
        }
        int sourceVariantNum = VariantUtils.getVariantNum(sourceVariantId, variantsInfos);
        int targetVariantNum = VariantUtils.findFistAvailableVariantNum(variantsInfos);
        cloneNetworkVariant(networkUuid, sourceVariantNum, targetVariantNum, targetVariantId);
    }

    public <T extends IdentifiableAttributes> void createIdentifiables(UUID networkUuid, List<Resource<T>> resources,
                                                                       TableMapping tableMapping) {
        try (var connection = dataSource.getConnection()) {
            processInsertIdentifiables(networkUuid, resources, tableMapping, connection);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private <T extends IdentifiableAttributes> void processInsertIdentifiables(UUID networkUuid, List<Resource<T>> resources, TableMapping tableMapping, Connection connection) throws SQLException {
        insertIdentifiables(networkUuid, resources, tableMapping, connection);
        extensionHandler.insertExtensions(connection, extensionHandler.getExtensionsFromEquipments(networkUuid, resources));
    }

    private <T extends IdentifiableAttributes> void insertIdentifiables(UUID networkUuid, List<Resource<T>> resources, TableMapping tableMapping, Connection connection) throws SQLException {
        try (var preparedStmt = connection.prepareStatement(buildInsertIdentifiableQuery(tableMapping.getTable(), tableMapping.getColumnsMapping().keySet()))) {
            List<Object> values = new ArrayList<>(3 + tableMapping.getColumnsMapping().size());
            for (List<Resource<T>> subResources : Lists.partition(resources, BATCH_SIZE)) {
                for (Resource<T> resource : subResources) {
                    T attributes = resource.getAttributes();
                    values.clear();
                    values.add(networkUuid);
                    values.add(resource.getVariantNum());
                    values.add(resource.getId());
                    for (var mapping : tableMapping.getColumnsMapping().values()) {
                        values.add(mapping.get(attributes));
                    }
                    bindValues(preparedStmt, values, mapper);
                    preparedStmt.addBatch();
                }
                preparedStmt.executeBatch();
            }
        }
    }

    private <T extends IdentifiableAttributes> Optional<Resource<T>> getIdentifiable(UUID networkUuid, int variantNum, String equipmentId,
                                                                                     TableMapping tableMapping) {
        try (var connection = dataSource.getConnection()) {
            return PartialVariantUtils.getOptionalIdentifiable(
                    variantNum,
                    getNetworkAttributes(connection, networkUuid, variantNum, mappings, mapper).getFullVariantNum(),
                    () -> isTombstonedIdentifiable(connection, networkUuid, variantNum, equipmentId),
                    variant -> getIdentifiableForVariant(connection, networkUuid, variant, equipmentId, tableMapping, variantNum));
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private <T extends IdentifiableAttributes> Optional<Resource<T>> getIdentifiableForVariant(Connection connection, UUID networkUuid, int variantNum, String equipmentId,
                                                                                               TableMapping tableMapping, int variantNumOverride) {
        try (var preparedStmt = connection.prepareStatement(QueryCatalog.buildGetIdentifiableQuery(tableMapping.getTable(), tableMapping.getColumnsMapping().keySet()))) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            preparedStmt.setString(3, equipmentId);
            try (ResultSet resultSet = preparedStmt.executeQuery()) {
                if (resultSet.next()) {
                    T attributes = (T) tableMapping.getAttributesSupplier().get();
                    MutableInt columnIndex = new MutableInt(1);
                    tableMapping.getColumnsMapping().forEach((columnName, columnMapping) -> {
                        bindAttributes(resultSet, columnIndex.getValue(), columnMapping, attributes, mapper);
                        columnIndex.increment();
                    });
                    Resource.Builder<T> resourceBuilder = (Resource.Builder<T>) tableMapping.getResourceBuilderSupplier().get();
                    Resource<T> resource = resourceBuilder
                            .id(equipmentId)
                            .variantNum(variantNumOverride)
                            .attributes(attributes)
                            .build();
                    return Optional.of(completeResourceInfos(resource, networkUuid, variantNumOverride, equipmentId));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private <T extends IdentifiableAttributes> Resource<T> completeResourceInfos(Resource<T> resource, UUID networkUuid, int variantNum, String equipmentId) {
        insertRegulatingEquipmentsInto(networkUuid, variantNum, equipmentId, resource, resource.getType());
        return switch (resource.getType()) {
            case GENERATOR -> completeGeneratorInfos(resource, networkUuid, variantNum, equipmentId);
            case BATTERY -> completeBatteryInfos(resource, networkUuid, variantNum, equipmentId);
            case TWO_WINDINGS_TRANSFORMER ->
                completeTwoWindingsTransformerInfos(resource, networkUuid, variantNum, equipmentId);
            case THREE_WINDINGS_TRANSFORMER ->
                completeThreeWindingsTransformerInfos(resource, networkUuid, variantNum, equipmentId);
            case VSC_CONVERTER_STATION ->
                completeVscConverterStationInfos(resource, networkUuid, variantNum, equipmentId);
            case DANGLING_LINE -> completeDanglingLineInfos(resource, networkUuid, variantNum, equipmentId);
            case STATIC_VAR_COMPENSATOR -> completeStaticVarCompensatorInfos(resource, networkUuid, variantNum, equipmentId);
            case SHUNT_COMPENSATOR -> completeShuntCompensatorInfos(resource, networkUuid, variantNum, equipmentId);
            case AREA -> completeAreaInfos(resource, networkUuid, variantNum, equipmentId);
            default -> resource;
        };
    }

    private <T extends IdentifiableAttributes> Resource<T> completeAreaInfos(Resource<T> resource, UUID networkUuid, int variantNum, String areaId) {
        Resource<AreaAttributes> areaAttributesResource = (Resource<AreaAttributes>) resource;
        Map<OwnerInfo, List<AreaBoundaryAttributes>> areaBoundaries = getAreaBoundaries(networkUuid, variantNum, AREA_ID_COLUMN, areaId);
        insertAreaBoundariesInAreas(networkUuid, List.of(areaAttributesResource), areaBoundaries);
        return resource;
    }

    private <T extends IdentifiableAttributes> Resource<T> completeGeneratorInfos(Resource<T> resource, UUID networkUuid, int variantNum, String equipmentId) {
        Resource<GeneratorAttributes> generatorAttributesResource = (Resource<GeneratorAttributes>) resource;
        Map<OwnerInfo, List<ReactiveCapabilityCurvePointAttributes>> reactiveCapabilityCurvePoints = getReactiveCapabilityCurvePoints(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, equipmentId);
        insertReactiveCapabilityCurvePointsInEquipments(networkUuid, List.of(generatorAttributesResource), reactiveCapabilityCurvePoints);
        insertRegulatingPointIntoInjection(networkUuid, variantNum, equipmentId, generatorAttributesResource, ResourceType.GENERATOR);
        return resource;
    }

    private <T extends IdentifiableAttributes> Resource<T> completeBatteryInfos(Resource<T> resource, UUID networkUuid, int variantNum, String equipmentId) {
        Map<OwnerInfo, List<ReactiveCapabilityCurvePointAttributes>> reactiveCapabilityCurvePoints = getReactiveCapabilityCurvePoints(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, equipmentId);
        insertReactiveCapabilityCurvePointsInEquipments(networkUuid, List.of((Resource<BatteryAttributes>) resource), reactiveCapabilityCurvePoints);
        return resource;
    }

    private <T extends IdentifiableAttributes> Resource<T> completeTwoWindingsTransformerInfos(Resource<T> resource, UUID networkUuid, int variantNum, String equipmentId) {
        Resource<TwoWindingsTransformerAttributes> twoWindingsTransformerResource = (Resource<TwoWindingsTransformerAttributes>) resource;
        Map<OwnerInfo, List<TapChangerStepAttributes>> tapChangerSteps = getTapChangerSteps(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, equipmentId);
        insertTapChangerStepsInEquipments(networkUuid, List.of(twoWindingsTransformerResource), tapChangerSteps);
        insertRegulatingPointIntoTwoWindingsTransformer(networkUuid, variantNum, equipmentId, twoWindingsTransformerResource);
        return resource;
    }

    private <T extends IdentifiableAttributes> Resource<T> completeThreeWindingsTransformerInfos(Resource<T> resource, UUID networkUuid, int variantNum, String equipmentId) {
        Resource<ThreeWindingsTransformerAttributes> threeWindingsTransformerResource = (Resource<ThreeWindingsTransformerAttributes>) resource;
        Map<OwnerInfo, LimitsInfos> limitsInfos = limitsHandler.getLimitsInfos(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, equipmentId);
        limitsHandler.insertLimitsInEquipments(networkUuid, List.of(threeWindingsTransformerResource), limitsInfos);

        Map<OwnerInfo, List<TapChangerStepAttributes>> tapChangerSteps = getTapChangerSteps(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, equipmentId);
        insertTapChangerStepsInEquipments(networkUuid, List.of(threeWindingsTransformerResource), tapChangerSteps);
        insertRegulatingPointIntoThreeWindingsTransformer(networkUuid, variantNum, equipmentId, threeWindingsTransformerResource);
        return resource;
    }

    private <T extends IdentifiableAttributes> Resource<T> completeVscConverterStationInfos(Resource<T> resource, UUID networkUuid, int variantNum, String equipmentId) {
        Resource<VscConverterStationAttributes> vscConverterStationAttributesResource = (Resource<VscConverterStationAttributes>) resource;
        Map<OwnerInfo, List<ReactiveCapabilityCurvePointAttributes>> reactiveCapabilityCurvePoints = getReactiveCapabilityCurvePoints(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, equipmentId);
        insertReactiveCapabilityCurvePointsInEquipments(networkUuid, List.of(vscConverterStationAttributesResource), reactiveCapabilityCurvePoints);
        insertRegulatingPointIntoInjection(networkUuid, variantNum, equipmentId, vscConverterStationAttributesResource, ResourceType.VSC_CONVERTER_STATION);
        return resource;
    }

    private <T extends IdentifiableAttributes> Resource<T> completeDanglingLineInfos(Resource<T> resource, UUID networkUuid, int variantNum, String equipmentId) {
        Map<OwnerInfo, LimitsInfos> limitsInfos = limitsHandler.getLimitsInfos(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, equipmentId);
        limitsHandler.insertLimitsInEquipments(networkUuid, List.of((Resource<DanglingLineAttributes>) resource), limitsInfos);
        return resource;
    }

    private <T extends IdentifiableAttributes> Resource<T> completeStaticVarCompensatorInfos(Resource<T> resource, UUID networkUuid, int variantNum, String equipmentId) {
        Resource<StaticVarCompensatorAttributes> staticVarCompensatorAttributesResource = (Resource<StaticVarCompensatorAttributes>) resource;
        insertRegulatingPointIntoInjection(networkUuid, variantNum, equipmentId, staticVarCompensatorAttributesResource, ResourceType.STATIC_VAR_COMPENSATOR);
        return resource;
    }

    private <T extends IdentifiableAttributes> Resource<T> completeShuntCompensatorInfos(Resource<T> resource, UUID networkUuid, int variantNum, String equipmentId) {
        Resource<ShuntCompensatorAttributes> shuntCompensatorAttributesResource = (Resource<ShuntCompensatorAttributes>) resource;
        insertRegulatingPointIntoInjection(networkUuid, variantNum, equipmentId, shuntCompensatorAttributesResource, ResourceType.SHUNT_COMPENSATOR);
        return resource;
    }

    private <T extends IdentifiableAttributes> List<Resource<T>> getIdentifiablesInternal(int variantNum, PreparedStatement preparedStmt, TableMapping tableMapping) throws SQLException {
        try (ResultSet resultSet = preparedStmt.executeQuery()) {
            List<Resource<T>> resources = new ArrayList<>();
            while (resultSet.next()) {
                // first is ID
                String id = resultSet.getString(1);
                T attributes = (T) tableMapping.getAttributesSupplier().get();
                MutableInt columnIndex = new MutableInt(2);
                tableMapping.getColumnsMapping().forEach((columnName, columnMapping) -> {
                    bindAttributes(resultSet, columnIndex.getValue(), columnMapping, attributes, mapper);
                    columnIndex.increment();
                });
                Resource.Builder<T> resourceBuilder = (Resource.Builder<T>) tableMapping.getResourceBuilderSupplier().get();
                resources.add(resourceBuilder
                        .id(id)
                        .variantNum(variantNum)
                        .attributes(attributes)
                        .build());
            }
            return resources;
        }
    }

    <T extends IdentifiableAttributes> List<Resource<T>> getIdentifiablesForVariant(Connection connection, UUID networkUuid, int variantNum,
                                                                                              TableMapping tableMapping, int variantNumOverride) {
        List<Resource<T>> identifiables;
        try (var preparedStmt = connection.prepareStatement(QueryCatalog.buildGetIdentifiablesQuery(tableMapping.getTable(), tableMapping.getColumnsMapping().keySet()))) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            identifiables = getIdentifiablesInternal(variantNumOverride, preparedStmt, tableMapping);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
        return identifiables;
    }

    private <T extends IdentifiableAttributes> List<Resource<T>> getIdentifiablesWithInClauseForVariant(Connection connection, UUID networkUuid, int variantNum, TableMapping tableMapping, List<String> valuesForInClause, int variantNumOverride) {
        if (valuesForInClause.isEmpty()) {
            return Collections.emptyList();
        }
        try (var preparedStmt = connection.prepareStatement(buildGetIdentifiablesWithInClauseQuery(tableMapping.getTable(), tableMapping.getColumnsMapping().keySet(), valuesForInClause.size()))) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            for (int i = 0; i < valuesForInClause.size(); i++) {
                preparedStmt.setString(3 + i, valuesForInClause.get(i));
            }

            return getIdentifiablesInternal(variantNumOverride, preparedStmt, tableMapping);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private <T extends IdentifiableAttributes> List<Resource<T>> getIdentifiablesInContainer(UUID networkUuid, int variantNum, String containerId,
                                                                                             Set<String> containerColumns,
                                                                                             TableMapping tableMapping) {
        try (var connection = dataSource.getConnection()) {
            return PartialVariantUtils.getIdentifiables(
                    variantNum,
                    getNetworkAttributes(connection, networkUuid, variantNum, mappings, mapper).getFullVariantNum(),
                    () -> getTombstonedIdentifiableIds(connection, networkUuid, variantNum),
                    variant -> getIdentifiablesInContainerForVariant(connection, networkUuid, variant, containerId, containerColumns, tableMapping, variantNum),
                    Resource::getId,
                    () -> getIdentifiablesIdsForVariantFromTable(connection, networkUuid, variantNum, tableMapping.getTable()));
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private <T extends IdentifiableAttributes> List<Resource<T>> getIdentifiablesInContainerForVariant(Connection connection, UUID networkUuid, int variantNum, String containerId,
                                                                                                       Set<String> containerColumns,
                                                                                                       TableMapping tableMapping, int variantNumOverride) {
        List<Resource<T>> identifiables;
        try (var preparedStmt = connection.prepareStatement(QueryCatalog.buildGetIdentifiablesInContainerQuery(tableMapping.getTable(), tableMapping.getColumnsMapping().keySet(), containerColumns))) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            for (int i = 0; i < containerColumns.size(); i++) {
                preparedStmt.setString(3 + i, containerId);
            }
            identifiables = getIdentifiablesInternal(variantNumOverride, preparedStmt, tableMapping);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
        return identifiables;
    }

    private <T extends IdentifiableAttributes> List<Resource<T>> getIdentifiablesInVoltageLevel(UUID networkUuid, int variantNum, String voltageLevelId, TableMapping tableMapping) {
        return getIdentifiablesInContainer(networkUuid, variantNum, voltageLevelId, tableMapping.getVoltageLevelIdColumns(), tableMapping);
    }

    public <T extends IdentifiableAttributes & Contained> void updateIdentifiables(UUID networkUuid, List<Resource<T>> resources,
                                                                                   TableMapping tableMapping, String columnToAddToWhereClause) {
        try (var connection = dataSource.getConnection()) {
            Map<Boolean, List<Resource<T>>> partitionResourcesByExistenceInVariant = partitionResourcesByExistenceInVariant(connection, networkUuid, resources, tableMapping.getTable());
            processInsertIdentifiables(networkUuid, partitionResourcesByExistenceInVariant.get(false), tableMapping, connection);
            processUpdateIdentifiables(connection, networkUuid, partitionResourcesByExistenceInVariant.get(true), tableMapping, columnToAddToWhereClause);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private <T extends Attributes> Map<Boolean, List<Resource<T>>> partitionResourcesByExistenceInVariant(Connection connection, UUID networkUuid, List<Resource<T>> resources, String tableName) {
        Map<Integer, Set<String>> existingIdsByVariant = resources.stream()
                .map(Resource::getVariantNum)
                .distinct()
                .collect(Collectors.toMap(
                        variantNum -> variantNum,
                        variantNum -> new HashSet<>(getIdentifiablesIdsForVariantFromTable(connection, networkUuid, variantNum, tableName))
                ));

        return resources.stream()
                .collect(Collectors.partitioningBy(
                        resource -> existingIdsByVariant.get(resource.getVariantNum()).contains(resource.getId())
                ));
    }

    private <T extends IdentifiableAttributes & Contained> void processUpdateIdentifiables(Connection connection, UUID networkUuid, List<Resource<T>> resources,
                                                                                          TableMapping tableMapping, String columnToAddToWhereClause) throws SQLException {
        try (var preparedStmt = connection.prepareStatement(QueryCatalog.buildUpdateIdentifiableQuery(tableMapping.getTable(), tableMapping.getColumnsMapping().keySet(), columnToAddToWhereClause))) {
            List<Object> values = new ArrayList<>(4 + tableMapping.getColumnsMapping().size());
            for (List<Resource<T>> subResources : Lists.partition(resources, BATCH_SIZE)) {
                for (Resource<T> resource : subResources) {
                    T attributes = resource.getAttributes();
                    values.clear();
                    for (var e : tableMapping.getColumnsMapping().entrySet()) {
                        String columnName = e.getKey();
                        var mapping = e.getValue();
                        if (!columnName.equals(columnToAddToWhereClause)) {
                            values.add(mapping.get(attributes));
                        }
                    }
                    values.add(networkUuid);
                    values.add(resource.getVariantNum());
                    values.add(resource.getId());
                    values.add(resource.getAttributes().getContainerIds().iterator().next());
                    bindValues(preparedStmt, values, mapper);
                    preparedStmt.addBatch();
                }
                preparedStmt.executeBatch();
            }
        }
        extensionHandler.updateExtensionsFromEquipments(connection, networkUuid, resources);
    }

    private void updateInjectionsSv(UUID networkUuid, List<Resource<InjectionSvAttributes>> resources, String tableName, TableMapping tableMapping) {
        updateIdentifiablesSv(
                networkUuid,
                resources,
                tableMapping,
                buildUpdateInjectionSvQuery(tableName),
                NetworkStoreRepository::updateInjectionSvAttributes,
                NetworkStoreRepository::bindInjectionSvAttributes
        );
    }

    static void bindInjectionSvAttributes(InjectionSvAttributes attributes, List<Object> values) {
        values.add(attributes.getP());
        values.add(attributes.getQ());
    }

    static void updateInjectionSvAttributes(InjectionAttributes existingAttributes, InjectionSvAttributes newAttributes) {
        existingAttributes.setP(newAttributes.getP());
        existingAttributes.setQ(newAttributes.getQ());
    }

    private <T extends IdentifiableAttributes, U extends Attributes> void updateIdentifiablesSv(
            UUID networkUuid,
            List<Resource<U>> updatedSvResources,
            TableMapping tableMapping,
            String updateQuery,
            BiConsumer<T, U> svAttributeUpdater,
            BiConsumer<U, List<Object>> svAttributeBinder
    ) {
        try (var connection = dataSource.getConnection()) {
            Map<Boolean, List<Resource<U>>> partitionedResourcesByExistenceInVariant = partitionResourcesByExistenceInVariant(connection, networkUuid, updatedSvResources, tableMapping.getTable());
            processUpdateIdentifiablesSv(networkUuid, partitionedResourcesByExistenceInVariant.get(true), updateQuery, svAttributeBinder, connection);
            processInsertUpdatedIdentifiablesSv(networkUuid, tableMapping, partitionedResourcesByExistenceInVariant.get(false), connection, svAttributeUpdater);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private <U extends Attributes> void processUpdateIdentifiablesSv(UUID networkUuid, List<Resource<U>> updatedSvResources, String updateQuery, BiConsumer<U, List<Object>> attributeBinder, Connection connection) throws SQLException {
        try (var preparedStmt = connection.prepareStatement(updateQuery)) {
            List<Object> values = new ArrayList<>();
            for (List<Resource<U>> subResources : Lists.partition(updatedSvResources, BATCH_SIZE)) {
                for (Resource<U> resource : subResources) {
                    int variantNum = resource.getVariantNum();
                    String resourceId = resource.getId();
                    U attributes = resource.getAttributes();
                    values.clear();
                    attributeBinder.accept(attributes, values);
                    values.add(networkUuid);
                    values.add(variantNum);
                    values.add(resourceId);
                    bindValues(preparedStmt, values, mapper);
                    preparedStmt.addBatch();
                }
                preparedStmt.executeBatch();
            }
        }
    }

    private <T extends IdentifiableAttributes, U extends Attributes> void processInsertUpdatedIdentifiablesSv(
            UUID networkUuid,
            TableMapping tableMapping,
            List<Resource<U>> svResources,
            Connection connection,
            BiConsumer<T, U> svAttributesUpdater
    ) throws SQLException {
        if (svResources.isEmpty()) {
            return;
        }

        Map<Integer, Map<String, Resource<U>>> svResourcesByVariant = svResources.stream()
                .collect(Collectors.groupingBy(
                        Resource::getVariantNum,
                        Collectors.toMap(Resource::getId, Function.identity())
                ));

        // Get resources from full variant
        List<Resource<T>> resourcesToUpdate = retrieveResourcesFromFullVariant(networkUuid, tableMapping, svResourcesByVariant, connection);

        // Update identifiables from full variant with SV values
        List<Resource<T>> resourcesUpdatedSv = updateSvResourcesFromFullVariant(svAttributesUpdater, resourcesToUpdate, svResourcesByVariant);

        // Insert updated identifiables
        insertIdentifiables(networkUuid, resourcesUpdatedSv, tableMapping, connection);
    }

    private static <T extends IdentifiableAttributes, U extends Attributes> List<Resource<T>> updateSvResourcesFromFullVariant(BiConsumer<T, U> svAttributesUpdater, List<Resource<T>> resourcesToUpdate, Map<Integer, Map<String, Resource<U>>> updatedSvResourcesByVariant) {
        for (Resource<T> resource : resourcesToUpdate) {
            Resource<U> svResource = updatedSvResourcesByVariant.get(resource.getVariantNum()).get(resource.getId());
            svAttributesUpdater.accept(resource.getAttributes(), svResource.getAttributes());
        }
        return resourcesToUpdate;
    }

    private <T extends IdentifiableAttributes, U extends Attributes> List<Resource<T>> retrieveResourcesFromFullVariant(UUID networkUuid, TableMapping tableMapping,
                                                                                                                        Map<Integer, Map<String, Resource<U>>> svResourcesByVariant,
                                                                                                                        Connection connection) {
        List<Resource<T>> fullVariantResources = new ArrayList<>();
        for (var entry : svResourcesByVariant.entrySet()) {
            int variantNum = entry.getKey();
            List<String> equipmentIds = new ArrayList<>(entry.getValue().keySet());
            NetworkAttributes network = getNetworkAttributes(connection, networkUuid, variantNum, mappings, mapper);
            int fullVariantNum = network.getFullVariantNum();
            fullVariantResources.addAll(getIdentifiablesWithInClauseForVariant(connection, networkUuid, fullVariantNum, tableMapping, equipmentIds, variantNum));
        }
        return fullVariantResources;
    }

    private void updateBranchesSv(UUID networkUuid, List<Resource<BranchSvAttributes>> resources, String tableName, TableMapping tableMapping) {
        updateIdentifiablesSv(
                networkUuid,
                resources,
                tableMapping,
                buildUpdateBranchSvQuery(tableName),
                NetworkStoreRepository::updateBranchSvAttributes,
                NetworkStoreRepository::bindBranchSvAttributes
        );
    }

    static void bindBranchSvAttributes(BranchSvAttributes attributes, List<Object> values) {
        values.add(attributes.getP1());
        values.add(attributes.getQ1());
        values.add(attributes.getP2());
        values.add(attributes.getQ2());
    }

    static void updateBranchSvAttributes(BranchAttributes existingAttributes, BranchSvAttributes newAttributes) {
        existingAttributes.setP1(newAttributes.getP1());
        existingAttributes.setQ1(newAttributes.getQ1());
        existingAttributes.setP2(newAttributes.getP2());
        existingAttributes.setQ2(newAttributes.getQ2());
    }

    private <T extends IdentifiableAttributes> void processUpdateIdentifiables(Connection connection, UUID networkUuid, List<Resource<T>> resources,
                                                                       TableMapping tableMapping) throws SQLException {
        try (var preparedStmt = connection.prepareStatement(QueryCatalog.buildUpdateIdentifiableQuery(tableMapping.getTable(), tableMapping.getColumnsMapping().keySet(), null))) {
            List<Object> values = new ArrayList<>(3 + tableMapping.getColumnsMapping().size());
            for (List<Resource<T>> subResources : Lists.partition(resources, BATCH_SIZE)) {
                for (Resource<T> resource : subResources) {
                    T attributes = resource.getAttributes();
                    values.clear();
                    for (var mapping : tableMapping.getColumnsMapping().values()) {
                        values.add(mapping.get(attributes));
                    }
                    values.add(networkUuid);
                    values.add(resource.getVariantNum());
                    values.add(resource.getId());
                    bindValues(preparedStmt, values, mapper);
                    preparedStmt.addBatch();
                }
                preparedStmt.executeBatch();
            }
        }
        extensionHandler.updateExtensionsFromEquipments(connection, networkUuid, resources);
    }

    public <T extends IdentifiableAttributes> void updateIdentifiables(UUID networkUuid, List<Resource<T>> resources,
                                                                       TableMapping tableMapping) {
        executeWithoutAutoCommit(connection -> {
            Map<Boolean, List<Resource<T>>> partitionResourcesByExistenceInVariant = partitionResourcesByExistenceInVariant(connection, networkUuid, resources, tableMapping.getTable());
            processInsertIdentifiables(networkUuid, partitionResourcesByExistenceInVariant.get(false), tableMapping, connection);
            processUpdateIdentifiables(connection, networkUuid, partitionResourcesByExistenceInVariant.get(true), tableMapping);
        });
    }

    public void deleteIdentifiables(UUID networkUuid, int variantNum, List<String> ids, String tableName) {
        if (CollectionUtils.isEmpty(ids)) {
            throw new IllegalArgumentException("The list of IDs to delete cannot be null or empty");
        }

        try (var connection = dataSource.getConnection()) {
            try (var preparedStmt = connection.prepareStatement(QueryCatalog.buildDeleteIdentifiablesQuery(tableName, ids.size()))) {
                for (List<String> idsPartition : Lists.partition(ids, BATCH_SIZE)) {
                    preparedStmt.setObject(1, networkUuid);
                    preparedStmt.setInt(2, variantNum);

                    for (int i = 0; i < idsPartition.size(); i++) {
                        preparedStmt.setString(3 + i, idsPartition.get(i));
                    }

                    preparedStmt.executeUpdate();
                }
            }
            NetworkAttributes network = getNetworkAttributes(connection, networkUuid, variantNum, mappings, mapper);
            if (!network.isFullVariant()) {
                try (var preparedStmt = connection.prepareStatement(buildInsertTombstonedIdentifiablesQuery())) {
                    for (List<String> idsPartition : Lists.partition(ids, BATCH_SIZE)) {
                        for (String id : idsPartition) {
                            preparedStmt.setObject(1, networkUuid);
                            preparedStmt.setInt(2, variantNum);
                            preparedStmt.setString(3, id);
                            preparedStmt.addBatch();
                        }
                        preparedStmt.executeBatch();
                    }
                }
            }
            extensionHandler.deleteExtensionsFromIdentifiables(connection, networkUuid, variantNum, ids);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    // substation

    public List<Resource<SubstationAttributes>> getSubstations(UUID networkUuid, int variantNum) {
        return getIdentifiables(networkUuid, variantNum, mappings.getSubstationMappings());
    }

    public Optional<Resource<SubstationAttributes>> getSubstation(UUID networkUuid, int variantNum, String substationId) {
        return getIdentifiable(networkUuid, variantNum, substationId, mappings.getSubstationMappings());
    }

    public void createSubstations(UUID networkUuid, List<Resource<SubstationAttributes>> resources) {
        createIdentifiables(networkUuid, resources, mappings.getSubstationMappings());
    }

    public void updateSubstations(UUID networkUuid, List<Resource<SubstationAttributes>> resources) {
        updateIdentifiables(networkUuid, resources, mappings.getSubstationMappings());
    }

    public void deleteSubstations(UUID networkUuid, int variantNum, List<String> substationIds) {
        deleteIdentifiables(networkUuid, variantNum, substationIds, SUBSTATION_TABLE);
    }

    // voltage level

    public void createVoltageLevels(UUID networkUuid, List<Resource<VoltageLevelAttributes>> resources) {
        createIdentifiables(networkUuid, resources, mappings.getVoltageLevelMappings());
    }

    public void updateVoltageLevels(UUID networkUuid, List<Resource<VoltageLevelAttributes>> resources) {
        updateIdentifiables(networkUuid, resources, mappings.getVoltageLevelMappings(), SUBSTATION_ID);
    }

    public void updateVoltageLevelsSv(UUID networkUuid, List<Resource<VoltageLevelSvAttributes>> resources) {
        updateIdentifiablesSv(
                networkUuid,
                resources,
                mappings.getVoltageLevelMappings(),
                buildUpdateVoltageLevelSvQuery(),
                NetworkStoreRepository::updateVoltageLevelSvAttributes,
                NetworkStoreRepository::bindVoltageLevelSvAttributes
        );
    }

    static void bindVoltageLevelSvAttributes(VoltageLevelSvAttributes attributes, List<Object> values) {
        values.add(attributes.getCalculatedBusesForBusView());
        values.add(attributes.getCalculatedBusesForBusBreakerView());
    }

    static void updateVoltageLevelSvAttributes(VoltageLevelAttributes existingAttributes, VoltageLevelSvAttributes newAttributes) {
        existingAttributes.setCalculatedBusesForBusView(newAttributes.getCalculatedBusesForBusView());
        existingAttributes.setCalculatedBusesForBusBreakerView(newAttributes.getCalculatedBusesForBusBreakerView());
    }

    public List<Resource<VoltageLevelAttributes>> getVoltageLevels(UUID networkUuid, int variantNum, String substationId) {
        return getIdentifiablesInContainer(networkUuid, variantNum, substationId, Set.of(SUBSTATION_ID), mappings.getVoltageLevelMappings());
    }

    public Optional<Resource<VoltageLevelAttributes>> getVoltageLevel(UUID networkUuid, int variantNum, String voltageLevelId) {
        return getIdentifiable(networkUuid, variantNum, voltageLevelId, mappings.getVoltageLevelMappings());
    }

    public List<Resource<VoltageLevelAttributes>> getVoltageLevels(UUID networkUuid, int variantNum) {
        return getIdentifiables(networkUuid, variantNum, mappings.getVoltageLevelMappings());
    }

    public void deleteVoltageLevels(UUID networkUuid, int variantNum, List<String> voltageLevelIds) {
        deleteIdentifiables(networkUuid, variantNum, voltageLevelIds, VOLTAGE_LEVEL_TABLE);
    }

    // generator

    public void createGenerators(UUID networkUuid, List<Resource<GeneratorAttributes>> resources) {
        createIdentifiables(networkUuid, resources, mappings.getGeneratorMappings());

        // Now that generators are created, we will insert in the database the corresponding reactive capability curve points.
        insertReactiveCapabilityCurvePoints(getReactiveCapabilityCurvePointsFromEquipments(networkUuid, resources));
        insertRegulatingPoints(getRegulatingPointFromEquipments(networkUuid, resources));
    }

    public Optional<Resource<GeneratorAttributes>> getGenerator(UUID networkUuid, int variantNum, String generatorId) {
        return getIdentifiable(networkUuid, variantNum, generatorId, mappings.getGeneratorMappings());
    }

    public List<Resource<GeneratorAttributes>> getGenerators(UUID networkUuid, int variantNum) {
        List<Resource<GeneratorAttributes>> generators = getIdentifiables(networkUuid, variantNum, mappings.getGeneratorMappings());

        //  reactive capability curves
        Map<OwnerInfo, List<ReactiveCapabilityCurvePointAttributes>> reactiveCapabilityCurvePoints = getReactiveCapabilityCurvePoints(networkUuid, variantNum, EQUIPMENT_TYPE_COLUMN, ResourceType.GENERATOR.toString());
        insertReactiveCapabilityCurvePointsInEquipments(networkUuid, generators, reactiveCapabilityCurvePoints);

        // regulating points
        setRegulatingPointAndRegulatingEquipments(generators, networkUuid, variantNum, ResourceType.GENERATOR);
        return generators;
    }

    public List<Resource<GeneratorAttributes>> getVoltageLevelGenerators(UUID networkUuid, int variantNum, String voltageLevelId) {
        List<Resource<GeneratorAttributes>> generators = getIdentifiablesInVoltageLevel(networkUuid, variantNum, voltageLevelId, mappings.getGeneratorMappings());

        List<String> equipmentsIds = generators.stream().map(Resource::getId).collect(Collectors.toList());

        // regulating points
        setRegulatingPointAndRegulatingEquipmentsWithIds(generators, networkUuid, variantNum, ResourceType.GENERATOR);

        //  reactive capability curves
        Map<OwnerInfo, List<ReactiveCapabilityCurvePointAttributes>> reactiveCapabilityCurvePoints = getReactiveCapabilityCurvePointsWithInClause(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, equipmentsIds);
        insertReactiveCapabilityCurvePointsInEquipments(networkUuid, generators, reactiveCapabilityCurvePoints);

        return generators;
    }

    public void updateGenerators(UUID networkUuid, List<Resource<GeneratorAttributes>> resources) {
        updateIdentifiables(networkUuid, resources, mappings.getGeneratorMappings(), VOLTAGE_LEVEL_ID_COLUMN);

        updateReactiveCapabilityCurvePoints(networkUuid, resources);
        updateRegulatingPoints(networkUuid, resources, ResourceType.GENERATOR, getRegulatingPointFromEquipments(networkUuid, resources));
    }

    public <T extends IdentifiableAttributes & ReactiveLimitHolder> void updateReactiveCapabilityCurvePoints(UUID networkUuid, List<Resource<T>> resources) {
        deleteReactiveCapabilityCurvePoints(networkUuid, resources);
        Map<OwnerInfo, List<ReactiveCapabilityCurvePointAttributes>> reactiveCapabilityCurvePointsToInsert = getReactiveCapabilityCurvePointsFromEquipments(networkUuid, resources);
        insertReactiveCapabilityCurvePoints(reactiveCapabilityCurvePointsToInsert);
        insertTombstonedReactiveCapabilityCurvePoints(networkUuid, reactiveCapabilityCurvePointsToInsert, resources);
    }

    public void updateGeneratorsSv(UUID networkUuid, List<Resource<InjectionSvAttributes>> resources) {
        updateInjectionsSv(networkUuid, resources, GENERATOR_TABLE, mappings.getGeneratorMappings());
    }

    public void deleteGenerators(UUID networkUuid, int variantNum, List<String> generatorId) {
        deleteIdentifiables(networkUuid, variantNum, generatorId, GENERATOR_TABLE);
        deleteReactiveCapabilityCurvePoints(networkUuid, variantNum, generatorId);
        deleteRegulatingPoints(networkUuid, variantNum, generatorId, ResourceType.GENERATOR);
    }

    private <T extends IdentifiableAttributes> List<Resource<T>> getIdentifiables(UUID networkUuid, int variantNum, TableMapping tableMapping) {
        try (var connection = dataSource.getConnection()) {
            return PartialVariantUtils.getIdentifiables(
                    variantNum,
                    getNetworkAttributes(connection, networkUuid, variantNum, mappings, mapper).getFullVariantNum(),
                    () -> getTombstonedIdentifiableIds(connection, networkUuid, variantNum),
                    variant -> getIdentifiablesForVariant(connection, networkUuid, variant, tableMapping, variantNum),
                    Resource::getId,
                    null);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private Set<String> getTombstonedTapChangerStepsIds(Connection connection, UUID networkUuid, int variantNum) {
        Set<String> identifiableIds = new HashSet<>();
        try (var preparedStmt = connection.prepareStatement(buildGetTombstonedExternalAttributesIdsQuery())) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            preparedStmt.setString(3, ExternalAttributesType.TAP_CHANGER_STEP.toString());
            try (var resultSet = preparedStmt.executeQuery()) {
                while (resultSet.next()) {
                    identifiableIds.add(resultSet.getString(EQUIPMENT_ID_COLUMN));
                }
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
        return identifiableIds;
    }

    private Set<String> getTombstonedTemporaryLimitsIds(Connection connection, UUID networkUuid, int variantNum) {
        Set<String> identifiableIds = new HashSet<>();
        try (var preparedStmt = connection.prepareStatement(buildGetTombstonedExternalAttributesIdsQuery())) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            preparedStmt.setString(3, ExternalAttributesType.TEMPORARY_LIMIT.toString());
            try (var resultSet = preparedStmt.executeQuery()) {
                while (resultSet.next()) {
                    identifiableIds.add(resultSet.getString(EQUIPMENT_ID_COLUMN));
                }
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
        return identifiableIds;
    }

    private Set<String> getTombstonedPermanentLimitsIds(Connection connection, UUID networkUuid, int variantNum) {
        Set<String> identifiableIds = new HashSet<>();
        try (var preparedStmt = connection.prepareStatement(buildGetTombstonedExternalAttributesIdsQuery())) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            preparedStmt.setString(3, ExternalAttributesType.PERMANENT_LIMIT.toString());
            try (var resultSet = preparedStmt.executeQuery()) {
                while (resultSet.next()) {
                    identifiableIds.add(resultSet.getString(EQUIPMENT_ID_COLUMN));
                }
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
        return identifiableIds;
    }

    public Set<String> getTombstonedIdentifiableIds(Connection connection, UUID networkUuid, int variantNum) {
        Set<String> tombstonedIdentifiableIds = new HashSet<>();
        try (var preparedStmt = connection.prepareStatement(buildGetTombstonedIdentifiablesIdsQuery())) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            try (var resultSet = preparedStmt.executeQuery()) {
                while (resultSet.next()) {
                    tombstonedIdentifiableIds.add(resultSet.getString(EQUIPMENT_ID_COLUMN));
                }
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
        return tombstonedIdentifiableIds;
    }

    private boolean isTombstonedIdentifiable(Connection connection, UUID networkUuid, int variantNum, String equipmentId) {
        try (var preparedStmt = connection.prepareStatement(buildIsTombstonedIdentifiableQuery())) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            preparedStmt.setString(3, equipmentId);
            try (var resultSet = preparedStmt.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private <T extends IdentifiableAttributes & ReactiveLimitHolder> void insertTombstonedReactiveCapabilityCurvePoints(UUID networkUuid, Map<OwnerInfo, List<ReactiveCapabilityCurvePointAttributes>> reactiveCapabilityCurvePointsToInsert, List<Resource<T>> resources) {
        try (var connection = dataSource.getConnection()) {
            Map<Integer, List<String>> resourcesByVariant = resources.stream()
                    .collect(Collectors.groupingBy(
                            Resource::getVariantNum,
                            Collectors.mapping(Resource::getId, Collectors.toList())
                    ));
            Set<OwnerInfo> tombstonedReactiveCapabilityCurvePoints = PartialVariantUtils.getExternalAttributesToTombstone(
                    resourcesByVariant,
                    variantNum -> getNetworkAttributes(connection, networkUuid, variantNum, mappings, mapper),
                    (fullVariantNum, variantNum, ids) -> getReactiveCapabilityCurvePointsWithInClauseForVariant(connection, networkUuid, fullVariantNum, EQUIPMENT_ID_COLUMN, ids, variantNum).keySet(),
                    variantNum -> getTombstonedReactiveCapabilityCurvePointsIds(connection, networkUuid, variantNum),
                    getExternalAttributesListToTombstoneFromEquipment(networkUuid, reactiveCapabilityCurvePointsToInsert, resources)
            );

            try (var preparedStmt = connection.prepareStatement(buildInsertTombstonedExternalAttributesQuery())) {
                for (OwnerInfo reactiveCapabilityCurvePoint : tombstonedReactiveCapabilityCurvePoints) {
                    preparedStmt.setObject(1, reactiveCapabilityCurvePoint.getNetworkUuid());
                    preparedStmt.setInt(2, reactiveCapabilityCurvePoint.getVariantNum());
                    preparedStmt.setString(3, reactiveCapabilityCurvePoint.getEquipmentId());
                    preparedStmt.setString(4, ExternalAttributesType.REACTIVE_CAPABILITY_CURVE_POINT.toString());
                    preparedStmt.addBatch();
                }
                preparedStmt.executeBatch();
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private Set<String> getTombstonedReactiveCapabilityCurvePointsIds(Connection connection, UUID networkUuid, int variantNum) {
        Set<String> identifiableIds = new HashSet<>();
        try (var preparedStmt = connection.prepareStatement(buildGetTombstonedExternalAttributesIdsQuery())) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            preparedStmt.setString(3, ExternalAttributesType.REACTIVE_CAPABILITY_CURVE_POINT.toString());
            try (var resultSet = preparedStmt.executeQuery()) {
                while (resultSet.next()) {
                    identifiableIds.add(resultSet.getString(EQUIPMENT_ID_COLUMN));
                }
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
        return identifiableIds;
    }

    private void insertTombstonedAreaBoundaries(UUID networkUuid, Map<OwnerInfo, List<AreaBoundaryAttributes>> areaBoundariesToInsert, List<Resource<AreaAttributes>> resources) {
        try (var connection = dataSource.getConnection()) {
            Map<Integer, List<String>> resourcesByVariant = resources.stream()
                .collect(Collectors.groupingBy(
                    Resource::getVariantNum,
                    Collectors.mapping(Resource::getId, Collectors.toList())
                ));
            Set<OwnerInfo> tombstonedAreaBoundaries = PartialVariantUtils.getExternalAttributesToTombstone(
                resourcesByVariant,
                variantNum -> getNetworkAttributes(connection, networkUuid, variantNum, mappings, mapper),
                (fullVariantNum, variantNum, ids) -> getAreaBoundariesWithInClauseForVariant(connection, networkUuid, fullVariantNum, AREA_ID_COLUMN, ids, variantNum).keySet(),
                variantNum -> getTombstonedAreaBoundariesIds(connection, networkUuid, variantNum),
                getExternalAttributesListToTombstoneFromEquipment(networkUuid, areaBoundariesToInsert, resources)
            );

            try (var preparedStmt = connection.prepareStatement(buildInsertTombstonedExternalAttributesQuery())) {
                for (OwnerInfo areaBoundary : tombstonedAreaBoundaries) {
                    preparedStmt.setObject(1, areaBoundary.getNetworkUuid());
                    preparedStmt.setInt(2, areaBoundary.getVariantNum());
                    preparedStmt.setString(3, areaBoundary.getEquipmentId());
                    preparedStmt.setString(4, ExternalAttributesType.AREA_BOUNDARIES.toString());
                    preparedStmt.addBatch();
                }
                preparedStmt.executeBatch();
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private Set<String> getTombstonedAreaBoundariesIds(Connection connection, UUID networkUuid, int variantNum) {
        Set<String> identifiableIds = new HashSet<>();
        try (var preparedStmt = connection.prepareStatement(buildGetTombstonedExternalAttributesIdsQuery())) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            preparedStmt.setString(3, ExternalAttributesType.AREA_BOUNDARIES.toString());
            try (var resultSet = preparedStmt.executeQuery()) {
                while (resultSet.next()) {
                    identifiableIds.add(resultSet.getString(EQUIPMENT_ID_COLUMN));
                }
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
        return identifiableIds;
    }

    // battery

    public void createBatteries(UUID networkUuid, List<Resource<BatteryAttributes>> resources) {
        createIdentifiables(networkUuid, resources, mappings.getBatteryMappings());

        // Now that batteries are created, we will insert in the database the corresponding reactive capability curve points.
        insertReactiveCapabilityCurvePoints(getReactiveCapabilityCurvePointsFromEquipments(networkUuid, resources));
    }

    public Optional<Resource<BatteryAttributes>> getBattery(UUID networkUuid, int variantNum, String batteryId) {
        return getIdentifiable(networkUuid, variantNum, batteryId, mappings.getBatteryMappings());
    }

    public List<Resource<BatteryAttributes>> getBatteries(UUID networkUuid, int variantNum) {
        List<Resource<BatteryAttributes>> batteries = getIdentifiables(networkUuid, variantNum, mappings.getBatteryMappings());

        Map<OwnerInfo, List<ReactiveCapabilityCurvePointAttributes>> reactiveCapabilityCurvePoints = getReactiveCapabilityCurvePoints(networkUuid, variantNum, EQUIPMENT_TYPE_COLUMN, ResourceType.BATTERY.toString());

        insertReactiveCapabilityCurvePointsInEquipments(networkUuid, batteries, reactiveCapabilityCurvePoints);
        setRegulatingEquipments(batteries, networkUuid, variantNum, ResourceType.BATTERY);

        return batteries;
    }

    public List<Resource<BatteryAttributes>> getVoltageLevelBatteries(UUID networkUuid, int variantNum, String voltageLevelId) {
        List<Resource<BatteryAttributes>> batteries = getIdentifiablesInVoltageLevel(networkUuid, variantNum, voltageLevelId, mappings.getBatteryMappings());

        List<String> equipmentsIds = batteries.stream().map(Resource::getId).collect(Collectors.toList());

        Map<OwnerInfo, List<ReactiveCapabilityCurvePointAttributes>> reactiveCapabilityCurvePoints = getReactiveCapabilityCurvePointsWithInClause(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, equipmentsIds);

        insertReactiveCapabilityCurvePointsInEquipments(networkUuid, batteries, reactiveCapabilityCurvePoints);
        setRegulatingEquipmentsWithIds(batteries, networkUuid, variantNum, ResourceType.BATTERY, equipmentsIds);
        return batteries;
    }

    public void updateBatteries(UUID networkUuid, List<Resource<BatteryAttributes>> resources) {
        updateIdentifiables(networkUuid, resources, mappings.getBatteryMappings(), VOLTAGE_LEVEL_ID_COLUMN);

        updateReactiveCapabilityCurvePoints(networkUuid, resources);
    }

    public void updateBatteriesSv(UUID networkUuid, List<Resource<InjectionSvAttributes>> resources) {
        updateInjectionsSv(networkUuid, resources, BATTERY_TABLE, mappings.getBatteryMappings());
    }

    public void deleteBatteries(UUID networkUuid, int variantNum, List<String> batteryIds) {
        deleteIdentifiables(networkUuid, variantNum, batteryIds, BATTERY_TABLE);
        deleteReactiveCapabilityCurvePoints(networkUuid, variantNum, batteryIds);
    }

    // load

    public void createLoads(UUID networkUuid, List<Resource<LoadAttributes>> resources) {
        createIdentifiables(networkUuid, resources, mappings.getLoadMappings());
    }

    public Optional<Resource<LoadAttributes>> getLoad(UUID networkUuid, int variantNum, String loadId) {
        return getIdentifiable(networkUuid, variantNum, loadId, mappings.getLoadMappings());
    }

    public List<Resource<LoadAttributes>> getLoads(UUID networkUuid, int variantNum) {
        List<Resource<LoadAttributes>> loads = getIdentifiables(networkUuid, variantNum, mappings.getLoadMappings());
        setRegulatingEquipments(loads, networkUuid, variantNum, ResourceType.LOAD);
        return loads;
    }

    public List<Resource<LoadAttributes>> getVoltageLevelLoads(UUID networkUuid, int variantNum, String voltageLevelId) {
        List<Resource<LoadAttributes>> loads = getIdentifiablesInVoltageLevel(networkUuid, variantNum, voltageLevelId, mappings.getLoadMappings());
        setRegulatingEquipmentsWithIds(loads, networkUuid, variantNum, ResourceType.LOAD);
        return loads;
    }

    public void updateLoads(UUID networkUuid, List<Resource<LoadAttributes>> resources) {
        updateIdentifiables(networkUuid, resources, mappings.getLoadMappings(), VOLTAGE_LEVEL_ID_COLUMN);
    }

    public void updateLoadsSv(UUID networkUuid, List<Resource<InjectionSvAttributes>> resources) {
        updateInjectionsSv(networkUuid, resources, LOAD_TABLE, mappings.getLoadMappings());
    }

    public void deleteLoads(UUID networkUuid, int variantNum, List<String> loadIds) {
        deleteIdentifiables(networkUuid, variantNum, loadIds, LOAD_TABLE);
    }

    // shunt compensator
    public void createShuntCompensators(UUID networkUuid, List<Resource<ShuntCompensatorAttributes>> resources) {
        createIdentifiables(networkUuid, resources, mappings.getShuntCompensatorMappings());
        insertRegulatingPoints(getRegulatingPointFromEquipments(networkUuid, resources));
    }

    public Optional<Resource<ShuntCompensatorAttributes>> getShuntCompensator(UUID networkUuid, int variantNum, String shuntCompensatorId) {
        return getIdentifiable(networkUuid, variantNum, shuntCompensatorId, mappings.getShuntCompensatorMappings());
    }

    public List<Resource<ShuntCompensatorAttributes>> getShuntCompensators(UUID networkUuid, int variantNum) {
        List<Resource<ShuntCompensatorAttributes>> shuntCompensators = getIdentifiables(networkUuid, variantNum, mappings.getShuntCompensatorMappings());

        // regulating points
        setRegulatingPointAndRegulatingEquipments(shuntCompensators, networkUuid, variantNum, ResourceType.SHUNT_COMPENSATOR);

        return shuntCompensators;
    }

    public List<Resource<ShuntCompensatorAttributes>> getVoltageLevelShuntCompensators(UUID networkUuid, int variantNum, String voltageLevelId) {
        List<Resource<ShuntCompensatorAttributes>> shuntCompensators = getIdentifiablesInVoltageLevel(networkUuid, variantNum, voltageLevelId, mappings.getShuntCompensatorMappings());

        // regulating points
        setRegulatingPointAndRegulatingEquipmentsWithIds(shuntCompensators, networkUuid, variantNum, ResourceType.SHUNT_COMPENSATOR);
        return shuntCompensators;
    }

    public void updateShuntCompensators(UUID networkUuid, List<Resource<ShuntCompensatorAttributes>> resources) {
        updateIdentifiables(networkUuid, resources, mappings.getShuntCompensatorMappings(), VOLTAGE_LEVEL_ID_COLUMN);

        // regulating points
        updateRegulatingPoints(networkUuid, resources, ResourceType.SHUNT_COMPENSATOR, getRegulatingPointFromEquipments(networkUuid, resources));
    }

    public void updateShuntCompensatorsSv(UUID networkUuid, List<Resource<InjectionSvAttributes>> resources) {
        updateInjectionsSv(networkUuid, resources, SHUNT_COMPENSATOR_TABLE, mappings.getShuntCompensatorMappings());
    }

    public void deleteShuntCompensators(UUID networkUuid, int variantNum, List<String> shuntCompensatorIds) {
        deleteRegulatingPoints(networkUuid, variantNum, shuntCompensatorIds, ResourceType.SHUNT_COMPENSATOR);
        deleteIdentifiables(networkUuid, variantNum, shuntCompensatorIds, SHUNT_COMPENSATOR_TABLE);
    }

    // VSC converter station

    public void createVscConverterStations(UUID networkUuid, List<Resource<VscConverterStationAttributes>> resources) {
        createIdentifiables(networkUuid, resources, mappings.getVscConverterStationMappings());

        // Now that vsc converter stations are created, we will insert in the database the corresponding reactive capability curve points.
        insertReactiveCapabilityCurvePoints(getReactiveCapabilityCurvePointsFromEquipments(networkUuid, resources));
        insertRegulatingPoints(getRegulatingPointFromEquipments(networkUuid, resources));
    }

    public Optional<Resource<VscConverterStationAttributes>> getVscConverterStation(UUID networkUuid, int variantNum, String vscConverterStationId) {
        return getIdentifiable(networkUuid, variantNum, vscConverterStationId, mappings.getVscConverterStationMappings());
    }

    public List<Resource<VscConverterStationAttributes>> getVscConverterStations(UUID networkUuid, int variantNum) {
        List<Resource<VscConverterStationAttributes>> vscConverterStations = getIdentifiables(networkUuid, variantNum, mappings.getVscConverterStationMappings());

        Map<OwnerInfo, List<ReactiveCapabilityCurvePointAttributes>> reactiveCapabilityCurvePoints = getReactiveCapabilityCurvePoints(networkUuid, variantNum, EQUIPMENT_TYPE_COLUMN, ResourceType.VSC_CONVERTER_STATION.toString());

        insertReactiveCapabilityCurvePointsInEquipments(networkUuid, vscConverterStations, reactiveCapabilityCurvePoints);

        // regulating points
        setRegulatingPointAndRegulatingEquipments(vscConverterStations, networkUuid, variantNum, ResourceType.VSC_CONVERTER_STATION);
        return vscConverterStations;
    }

    public List<Resource<VscConverterStationAttributes>> getVoltageLevelVscConverterStations(UUID networkUuid, int variantNum, String voltageLevelId) {
        List<Resource<VscConverterStationAttributes>> vscConverterStations = getIdentifiablesInVoltageLevel(networkUuid, variantNum, voltageLevelId, mappings.getVscConverterStationMappings());

        List<String> equipmentsIds = vscConverterStations.stream().map(Resource::getId).collect(Collectors.toList());

        Map<OwnerInfo, List<ReactiveCapabilityCurvePointAttributes>> reactiveCapabilityCurvePoints = getReactiveCapabilityCurvePointsWithInClause(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, equipmentsIds);
        insertReactiveCapabilityCurvePointsInEquipments(networkUuid, vscConverterStations, reactiveCapabilityCurvePoints);

        // regulating points
        setRegulatingPointAndRegulatingEquipmentsWithIds(vscConverterStations, networkUuid, variantNum, ResourceType.VSC_CONVERTER_STATION);
        return vscConverterStations;
    }

    public void updateVscConverterStations(UUID networkUuid, List<Resource<VscConverterStationAttributes>> resources) {
        updateIdentifiables(networkUuid, resources, mappings.getVscConverterStationMappings(), VOLTAGE_LEVEL_ID_COLUMN);

        updateReactiveCapabilityCurvePoints(networkUuid, resources);
        updateRegulatingPoints(networkUuid, resources, ResourceType.VSC_CONVERTER_STATION, getRegulatingPointFromEquipments(networkUuid, resources));
    }

    public void updateVscConverterStationsSv(UUID networkUuid, List<Resource<InjectionSvAttributes>> resources) {
        updateInjectionsSv(networkUuid, resources, VSC_CONVERTER_STATION_TABLE, mappings.getVscConverterStationMappings());
    }

    public void deleteVscConverterStations(UUID networkUuid, int variantNum, List<String> vscConverterStationIds) {
        deleteIdentifiables(networkUuid, variantNum, vscConverterStationIds, VSC_CONVERTER_STATION_TABLE);
        deleteReactiveCapabilityCurvePoints(networkUuid, variantNum, vscConverterStationIds);
        deleteRegulatingPoints(networkUuid, variantNum, vscConverterStationIds, ResourceType.VSC_CONVERTER_STATION);
    }

    // LCC converter station

    public void createLccConverterStations(UUID networkUuid, List<Resource<LccConverterStationAttributes>> resources) {
        createIdentifiables(networkUuid, resources, mappings.getLccConverterStationMappings());
    }

    public Optional<Resource<LccConverterStationAttributes>> getLccConverterStation(UUID networkUuid, int variantNum, String lccConverterStationId) {
        return getIdentifiable(networkUuid, variantNum, lccConverterStationId, mappings.getLccConverterStationMappings());
    }

    public List<Resource<LccConverterStationAttributes>> getLccConverterStations(UUID networkUuid, int variantNum) {
        return getIdentifiables(networkUuid, variantNum, mappings.getLccConverterStationMappings());
    }

    public List<Resource<LccConverterStationAttributes>> getVoltageLevelLccConverterStations(UUID networkUuid, int variantNum, String voltageLevelId) {
        return getIdentifiablesInVoltageLevel(networkUuid, variantNum, voltageLevelId, mappings.getLccConverterStationMappings());
    }

    public void updateLccConverterStations(UUID networkUuid, List<Resource<LccConverterStationAttributes>> resources) {
        updateIdentifiables(networkUuid, resources, mappings.getLccConverterStationMappings(), VOLTAGE_LEVEL_ID_COLUMN);
    }

    public void updateLccConverterStationsSv(UUID networkUuid, List<Resource<InjectionSvAttributes>> resources) {
        updateInjectionsSv(networkUuid, resources, LCC_CONVERTER_STATION_TABLE, mappings.getLccConverterStationMappings());
    }

    public void deleteLccConverterStations(UUID networkUuid, int variantNum, List<String> lccConverterStationIds) {
        deleteIdentifiables(networkUuid, variantNum, lccConverterStationIds, LCC_CONVERTER_STATION_TABLE);
    }

    // static var compensators
    public void createStaticVarCompensators(UUID networkUuid, List<Resource<StaticVarCompensatorAttributes>> resources) {
        createIdentifiables(networkUuid, resources, mappings.getStaticVarCompensatorMappings());
        insertRegulatingPoints(getRegulatingPointFromEquipments(networkUuid, resources));
    }

    public Optional<Resource<StaticVarCompensatorAttributes>> getStaticVarCompensator(UUID networkUuid, int variantNum, String staticVarCompensatorId) {
        return getIdentifiable(networkUuid, variantNum, staticVarCompensatorId, mappings.getStaticVarCompensatorMappings());

    }

    public List<Resource<StaticVarCompensatorAttributes>> getStaticVarCompensators(UUID networkUuid, int variantNum) {
        List<Resource<StaticVarCompensatorAttributes>> staticVarCompensators = getIdentifiables(networkUuid, variantNum, mappings.getStaticVarCompensatorMappings());

        // regulating points
        setRegulatingPointAndRegulatingEquipments(staticVarCompensators, networkUuid, variantNum, ResourceType.STATIC_VAR_COMPENSATOR);
        return staticVarCompensators;
    }

    public List<Resource<StaticVarCompensatorAttributes>> getVoltageLevelStaticVarCompensators(UUID networkUuid, int variantNum, String voltageLevelId) {
        List<Resource<StaticVarCompensatorAttributes>> staticVarCompensators = getIdentifiablesInVoltageLevel(networkUuid, variantNum, voltageLevelId, mappings.getStaticVarCompensatorMappings());

        // regulating points
        setRegulatingPointAndRegulatingEquipmentsWithIds(staticVarCompensators, networkUuid, variantNum, ResourceType.STATIC_VAR_COMPENSATOR);
        return staticVarCompensators;
    }

    public void updateStaticVarCompensators(UUID networkUuid, List<Resource<StaticVarCompensatorAttributes>> resources) {
        updateIdentifiables(networkUuid, resources, mappings.getStaticVarCompensatorMappings(), VOLTAGE_LEVEL_ID_COLUMN);

        // regulating points
        updateRegulatingPoints(networkUuid, resources, ResourceType.STATIC_VAR_COMPENSATOR, getRegulatingPointFromEquipments(networkUuid, resources));
    }

    public void updateStaticVarCompensatorsSv(UUID networkUuid, List<Resource<InjectionSvAttributes>> resources) {
        updateInjectionsSv(networkUuid, resources, STATIC_VAR_COMPENSATOR_TABLE, mappings.getStaticVarCompensatorMappings());
    }

    public void deleteStaticVarCompensators(UUID networkUuid, int variantNum, List<String> staticVarCompensatorIds) {
        deleteRegulatingPoints(networkUuid, variantNum, staticVarCompensatorIds, ResourceType.STATIC_VAR_COMPENSATOR);
        deleteIdentifiables(networkUuid, variantNum, staticVarCompensatorIds, STATIC_VAR_COMPENSATOR_TABLE);
    }

    // busbar section

    public void createBusbarSections(UUID networkUuid, List<Resource<BusbarSectionAttributes>> resources) {
        createIdentifiables(networkUuid, resources, mappings.getBusbarSectionMappings());
    }

    public void updateBusbarSections(UUID networkUuid, List<Resource<BusbarSectionAttributes>> resources) {
        updateIdentifiables(networkUuid, resources, mappings.getBusbarSectionMappings(), VOLTAGE_LEVEL_ID_COLUMN);
    }

    public Optional<Resource<BusbarSectionAttributes>> getBusbarSection(UUID networkUuid, int variantNum, String busbarSectionId) {
        return getIdentifiable(networkUuid, variantNum, busbarSectionId, mappings.getBusbarSectionMappings());
    }

    public List<Resource<BusbarSectionAttributes>> getBusbarSections(UUID networkUuid, int variantNum) {
        List<Resource<BusbarSectionAttributes>> busbars = getIdentifiables(networkUuid, variantNum, mappings.getBusbarSectionMappings());
        setRegulatingEquipments(busbars, networkUuid, variantNum, ResourceType.BUSBAR_SECTION);
        return busbars;
    }

    public List<Resource<BusbarSectionAttributes>> getVoltageLevelBusbarSections(UUID networkUuid, int variantNum, String voltageLevelId) {
        List<Resource<BusbarSectionAttributes>> busbars = getIdentifiablesInVoltageLevel(networkUuid, variantNum, voltageLevelId, mappings.getBusbarSectionMappings());
        setRegulatingEquipmentsWithIds(busbars, networkUuid, variantNum, ResourceType.BUSBAR_SECTION);
        return busbars;
    }

    public void deleteBusBarSections(UUID networkUuid, int variantNum, List<String> busBarSectionIds) {
        deleteIdentifiables(networkUuid, variantNum, busBarSectionIds, BUSBAR_SECTION_TABLE);
    }

    // switch

    public void createSwitches(UUID networkUuid, List<Resource<SwitchAttributes>> resources) {
        createIdentifiables(networkUuid, resources, mappings.getSwitchMappings());
    }

    public Optional<Resource<SwitchAttributes>> getSwitch(UUID networkUuid, int variantNum, String switchId) {
        return getIdentifiable(networkUuid, variantNum, switchId, mappings.getSwitchMappings());
    }

    public List<Resource<SwitchAttributes>> getSwitches(UUID networkUuid, int variantNum) {
        return getIdentifiables(networkUuid, variantNum, mappings.getSwitchMappings());
    }

    public List<Resource<SwitchAttributes>> getVoltageLevelSwitches(UUID networkUuid, int variantNum, String voltageLevelId) {
        return getIdentifiablesInVoltageLevel(networkUuid, variantNum, voltageLevelId, mappings.getSwitchMappings());
    }

    public void updateSwitches(UUID networkUuid, List<Resource<SwitchAttributes>> resources) {
        updateIdentifiables(networkUuid, resources, mappings.getSwitchMappings(), VOLTAGE_LEVEL_ID_COLUMN);
    }

    public void deleteSwitches(UUID networkUuid, int variantNum, List<String> switchIds) {
        deleteIdentifiables(networkUuid, variantNum, switchIds, SWITCH_TABLE);
    }

    // 2 windings transformer

    public void createTwoWindingsTransformers(UUID networkUuid, List<Resource<TwoWindingsTransformerAttributes>> resources) {
        createIdentifiables(networkUuid, resources, mappings.getTwoWindingsTransformerMappings());

        // Now that twowindingstransformers are created, we will insert in the database the corresponding temporary limits.
        Map<OwnerInfo, LimitsInfos> limitsInfos = limitsHandler.getLimitsInfosFromEquipments(networkUuid, resources);
        limitsHandler.insertTemporaryLimits(limitsInfos);
        limitsHandler.insertPermanentLimits(limitsInfos);
        insertRegulatingPoints(getRegulatingPointFromTwoWindingTransformers(networkUuid, resources));

        // Now that twowindingstransformers are created, we will insert in the database the corresponding tap Changer steps.
        insertTapChangerSteps(getTapChangerStepsFromEquipment(networkUuid, resources));
    }

    public Optional<Resource<TwoWindingsTransformerAttributes>> getTwoWindingsTransformer(UUID networkUuid, int variantNum, String twoWindingsTransformerId) {
        return getIdentifiable(networkUuid, variantNum, twoWindingsTransformerId, mappings.getTwoWindingsTransformerMappings());
    }

    public List<Resource<TwoWindingsTransformerAttributes>> getTwoWindingsTransformers(UUID networkUuid, int variantNum) {
        List<Resource<TwoWindingsTransformerAttributes>> twoWindingsTransformers = getIdentifiables(networkUuid, variantNum, mappings.getTwoWindingsTransformerMappings());

        Map<OwnerInfo, List<TapChangerStepAttributes>> tapChangerSteps = getTapChangerSteps(networkUuid, variantNum, EQUIPMENT_TYPE_COLUMN, ResourceType.TWO_WINDINGS_TRANSFORMER.toString());
        insertTapChangerStepsInEquipments(networkUuid, twoWindingsTransformers, tapChangerSteps);
        // regulating points
        setRegulatingPointAndRegulatingEquipmentsForTwoWindingsTransformers(twoWindingsTransformers, networkUuid, variantNum);

        return twoWindingsTransformers;
    }

    public List<Resource<TwoWindingsTransformerAttributes>> getVoltageLevelTwoWindingsTransformers(UUID networkUuid, int variantNum, String voltageLevelId) {
        List<Resource<TwoWindingsTransformerAttributes>> twoWindingsTransformers = getIdentifiablesInVoltageLevel(networkUuid, variantNum, voltageLevelId, mappings.getTwoWindingsTransformerMappings());

        List<String> equipmentsIds = twoWindingsTransformers.stream().map(Resource::getId).collect(Collectors.toList());

        Map<OwnerInfo, List<TapChangerStepAttributes>> tapChangerSteps = getTapChangerStepsWithInClause(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, equipmentsIds);
        insertTapChangerStepsInEquipments(networkUuid, twoWindingsTransformers, tapChangerSteps);

        // regulating points
        setRegulatingPointAndRegulatingEquipmentsWithIdsForTwoWindingsTransformers(twoWindingsTransformers, networkUuid, variantNum);

        return twoWindingsTransformers;
    }

    public void updateTwoWindingsTransformers(UUID networkUuid, List<Resource<TwoWindingsTransformerAttributes>> resources) {
        updateIdentifiables(networkUuid, resources, mappings.getTwoWindingsTransformerMappings());

        Map<OwnerInfo, LimitsInfos> limitsInfos = limitsHandler.getLimitsInfosFromEquipments(networkUuid, resources);
        limitsHandler.updateTemporaryLimits(networkUuid, resources, limitsInfos);
        limitsHandler.updatePermanentLimits(networkUuid, resources, limitsInfos);
        updateTapChangerSteps(networkUuid, resources);
        updateRegulatingPoints(networkUuid, resources, ResourceType.TWO_WINDINGS_TRANSFORMER, getRegulatingPointFromTwoWindingTransformers(networkUuid, resources));
    }

    public <T extends IdentifiableAttributes> void updateTapChangerSteps(UUID networkUuid, List<Resource<T>> resources) {
        deleteTapChangerSteps(networkUuid, resources);
        Map<OwnerInfo, List<TapChangerStepAttributes>> tapChangerStepsToInsert = getTapChangerStepsFromEquipment(networkUuid, resources);
        insertTapChangerSteps(tapChangerStepsToInsert);
        insertTombstonedTapChangerSteps(networkUuid, tapChangerStepsToInsert, resources);
    }

    private <T extends IdentifiableAttributes> void insertTombstonedTapChangerSteps(UUID networkUuid, Map<OwnerInfo, List<TapChangerStepAttributes>> tapChangerStepsToInsert, List<Resource<T>> resources) {
        try (var connection = dataSource.getConnection()) {
            Map<Integer, List<String>> resourcesByVariant = resources.stream()
                    .collect(Collectors.groupingBy(
                            Resource::getVariantNum,
                            Collectors.mapping(Resource::getId, Collectors.toList())
                    ));
            Set<OwnerInfo> tombstonedTapChangerSteps = PartialVariantUtils.getExternalAttributesToTombstone(
                    resourcesByVariant,
                    variantNum -> getNetworkAttributes(connection, networkUuid, variantNum, mappings, mapper),
                    (fullVariantNum, variantNum, ids) -> getTapChangerStepsWithInClauseForVariant(connection, networkUuid, fullVariantNum, EQUIPMENT_ID_COLUMN, ids, variantNum).keySet(),
                    variantNum -> getTombstonedTapChangerStepsIds(connection, networkUuid, variantNum),
                    getExternalAttributesListToTombstoneFromEquipment(networkUuid, tapChangerStepsToInsert, resources)
            );

            try (var preparedStmt = connection.prepareStatement(buildInsertTombstonedExternalAttributesQuery())) {
                for (OwnerInfo tapChangerStep : tombstonedTapChangerSteps) {
                    preparedStmt.setObject(1, tapChangerStep.getNetworkUuid());
                    preparedStmt.setInt(2, tapChangerStep.getVariantNum());
                    preparedStmt.setString(3, tapChangerStep.getEquipmentId());
                    preparedStmt.setString(4, ExternalAttributesType.TAP_CHANGER_STEP.toString());
                    preparedStmt.addBatch();
                }
                preparedStmt.executeBatch();
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private <T extends IdentifiableAttributes, U> Set<OwnerInfo> getExternalAttributesListToTombstoneFromEquipment(UUID networkUuid, Map<OwnerInfo, List<U>> externalAttributesToInsert, List<Resource<T>> resources) {
        Set<OwnerInfo> externalAttributesToTombstoneFromEquipment = new HashSet<>();
        for (Resource<T> resource : resources) {
            OwnerInfo ownerInfo = new OwnerInfo(resource.getId(), resource.getType(), networkUuid, resource.getVariantNum());
            if (!externalAttributesToInsert.containsKey(ownerInfo) || externalAttributesToInsert.get(ownerInfo).isEmpty()) {
                externalAttributesToTombstoneFromEquipment.add(ownerInfo);
            }
        }
        return externalAttributesToTombstoneFromEquipment;
    }

    public void updateTwoWindingsTransformersSv(UUID networkUuid, List<Resource<BranchSvAttributes>> resources) {
        updateBranchesSv(networkUuid, resources, TWO_WINDINGS_TRANSFORMER_TABLE, mappings.getTwoWindingsTransformerMappings());
    }

    public void deleteTwoWindingsTransformers(UUID networkUuid, int variantNum, List<String> twoWindingsTransformerIds) {
        deleteIdentifiables(networkUuid, variantNum, twoWindingsTransformerIds, TWO_WINDINGS_TRANSFORMER_TABLE);
        limitsHandler.deleteTemporaryLimits(networkUuid, variantNum, twoWindingsTransformerIds);
        limitsHandler.deletePermanentLimits(networkUuid, variantNum, twoWindingsTransformerIds);
        deleteTapChangerSteps(networkUuid, variantNum, twoWindingsTransformerIds);
        deleteRegulatingPoints(networkUuid, variantNum, twoWindingsTransformerIds, ResourceType.TWO_WINDINGS_TRANSFORMER);
    }

    // 3 windings transformer
    public void createThreeWindingsTransformers(UUID networkUuid, List<Resource<ThreeWindingsTransformerAttributes>> resources) {
        createIdentifiables(networkUuid, resources, mappings.getThreeWindingsTransformerMappings());
        insertRegulatingPoints(getRegulatingPointFromThreeWindingTransformers(networkUuid, resources));

        // Now that threewindingstransformers are created, we will insert in the database the corresponding temporary limits.
        Map<OwnerInfo, LimitsInfos> limitsInfos = limitsHandler.getLimitsInfosFromEquipments(networkUuid, resources);
        limitsHandler.insertTemporaryLimits(limitsInfos);
        limitsHandler.insertPermanentLimits(limitsInfos);

        // Now that threewindingstransformers are created, we will insert in the database the corresponding tap Changer steps.
        insertTapChangerSteps(getTapChangerStepsFromEquipment(networkUuid, resources));
    }

    public Optional<Resource<ThreeWindingsTransformerAttributes>> getThreeWindingsTransformer(UUID networkUuid, int variantNum, String threeWindingsTransformerId) {
        return getIdentifiable(networkUuid, variantNum, threeWindingsTransformerId, mappings.getThreeWindingsTransformerMappings());
    }

    public List<Resource<ThreeWindingsTransformerAttributes>> getThreeWindingsTransformers(UUID networkUuid, int variantNum) {
        List<Resource<ThreeWindingsTransformerAttributes>> threeWindingsTransformers = getIdentifiables(networkUuid, variantNum, mappings.getThreeWindingsTransformerMappings());

        Map<OwnerInfo, LimitsInfos> limitsInfos = limitsHandler.getLimitsInfos(networkUuid, variantNum, EQUIPMENT_TYPE_COLUMN, ResourceType.THREE_WINDINGS_TRANSFORMER.toString());
        limitsHandler.insertLimitsInEquipments(networkUuid, threeWindingsTransformers, limitsInfos);

        Map<OwnerInfo, List<TapChangerStepAttributes>> tapChangerSteps = getTapChangerSteps(networkUuid, variantNum, EQUIPMENT_TYPE_COLUMN, ResourceType.THREE_WINDINGS_TRANSFORMER.toString());
        insertTapChangerStepsInEquipments(networkUuid, threeWindingsTransformers, tapChangerSteps);

        setRegulatingPointAndRegulatingEquipmentsForThreeWindingsTransformers(threeWindingsTransformers, networkUuid, variantNum);
        return threeWindingsTransformers;
    }

    public List<Resource<ThreeWindingsTransformerAttributes>> getVoltageLevelThreeWindingsTransformers(UUID networkUuid, int variantNum, String voltageLevelId) {
        List<Resource<ThreeWindingsTransformerAttributes>> threeWindingsTransformers = getIdentifiablesInVoltageLevel(networkUuid, variantNum, voltageLevelId, mappings.getThreeWindingsTransformerMappings());

        List<String> equipmentsIds = threeWindingsTransformers.stream().map(Resource::getId).collect(Collectors.toList());

        Map<OwnerInfo, LimitsInfos> limitsInfos = limitsHandler.getLimitsInfosWithInClause(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, equipmentsIds);
        limitsHandler.insertLimitsInEquipments(networkUuid, threeWindingsTransformers, limitsInfos);

        Map<OwnerInfo, List<TapChangerStepAttributes>> tapChangerSteps = getTapChangerStepsWithInClause(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, equipmentsIds);
        insertTapChangerStepsInEquipments(networkUuid, threeWindingsTransformers, tapChangerSteps);
        setRegulatingPointAndRegulatingEquipmentsForThreeWindingsTransformersWithIds(threeWindingsTransformers, networkUuid, variantNum);
        return threeWindingsTransformers;
    }

    public void updateThreeWindingsTransformers(UUID networkUuid, List<Resource<ThreeWindingsTransformerAttributes>> resources) {
        updateIdentifiables(networkUuid, resources, mappings.getThreeWindingsTransformerMappings());

        Map<OwnerInfo, LimitsInfos> limitsInfos = limitsHandler.getLimitsInfosFromEquipments(networkUuid, resources);
        limitsHandler.updateTemporaryLimits(networkUuid, resources, limitsInfos);
        limitsHandler.updatePermanentLimits(networkUuid, resources, limitsInfos);
        updateTapChangerSteps(networkUuid, resources);
        updateRegulatingPoints(networkUuid, resources, ResourceType.THREE_WINDINGS_TRANSFORMER, getRegulatingPointFromThreeWindingTransformers(networkUuid, resources));
    }

    public void updateThreeWindingsTransformersSv(UUID networkUuid, List<Resource<ThreeWindingsTransformerSvAttributes>> resources) {
        updateIdentifiablesSv(
                networkUuid,
                resources,
                mappings.getThreeWindingsTransformerMappings(),
                buildUpdateThreeWindingsTransformerSvQuery(),
                NetworkStoreRepository::updateThreeWindingsTransformerSvAttributes,
                NetworkStoreRepository::bindThreeWindingsTransformerSvAttributes
        );
    }

    static void bindThreeWindingsTransformerSvAttributes(ThreeWindingsTransformerSvAttributes attributes, List<Object> values) {
        values.add(attributes.getP1());
        values.add(attributes.getQ1());
        values.add(attributes.getP2());
        values.add(attributes.getQ2());
        values.add(attributes.getP3());
        values.add(attributes.getQ3());
    }

    static void updateThreeWindingsTransformerSvAttributes(ThreeWindingsTransformerAttributes existingAttributes, ThreeWindingsTransformerSvAttributes newAttributes) {
        existingAttributes.setP1(newAttributes.getP1());
        existingAttributes.setQ1(newAttributes.getQ1());
        existingAttributes.setP2(newAttributes.getP2());
        existingAttributes.setQ2(newAttributes.getQ2());
        existingAttributes.setP3(newAttributes.getP3());
        existingAttributes.setQ3(newAttributes.getQ3());
    }

    public void deleteThreeWindingsTransformers(UUID networkUuid, int variantNum, List<String> threeWindingsTransformerIds) {
        deleteIdentifiables(networkUuid, variantNum, threeWindingsTransformerIds, THREE_WINDINGS_TRANSFORMER_TABLE);
        limitsHandler.deleteTemporaryLimits(networkUuid, variantNum, threeWindingsTransformerIds);
        limitsHandler.deletePermanentLimits(networkUuid, variantNum, threeWindingsTransformerIds);
        deleteTapChangerSteps(networkUuid, variantNum, threeWindingsTransformerIds);
        deleteRegulatingPoints(networkUuid, variantNum, threeWindingsTransformerIds, ResourceType.THREE_WINDINGS_TRANSFORMER);
    }

    // line

    public void createLines(UUID networkUuid, List<Resource<LineAttributes>> resources) {
        createIdentifiables(networkUuid, resources, mappings.getLineMappings());

        // Now that lines are created, we will insert in the database the corresponding temporary limits.
        Map<OwnerInfo, LimitsInfos> limitsInfos = limitsHandler.getLimitsInfosFromEquipments(networkUuid, resources);
        limitsHandler.insertTemporaryLimits(limitsInfos);
        limitsHandler.insertPermanentLimits(limitsInfos);
    }

    public Optional<Resource<LineAttributes>> getLine(UUID networkUuid, int variantNum, String lineId) {
        return getIdentifiable(networkUuid, variantNum, lineId, mappings.getLineMappings());
    }

    public List<Resource<LineAttributes>> getLines(UUID networkUuid, int variantNum) {
        List<Resource<LineAttributes>> lines = getIdentifiables(networkUuid, variantNum, mappings.getLineMappings());

        setRegulatingEquipments(lines, networkUuid, variantNum, ResourceType.LINE);

        return lines;
    }

    public List<Resource<LineAttributes>> getVoltageLevelLines(UUID networkUuid, int variantNum, String voltageLevelId) {
        List<Resource<LineAttributes>> lines = getIdentifiablesInVoltageLevel(networkUuid, variantNum, voltageLevelId, mappings.getLineMappings());

        List<String> equipmentsIds = lines.stream().map(Resource::getId).collect(Collectors.toList());

        setRegulatingEquipmentsWithIds(lines, networkUuid, variantNum, ResourceType.LINE, equipmentsIds);

        return lines;
    }

    public void updateLines(UUID networkUuid, List<Resource<LineAttributes>> resources) {
        updateIdentifiables(networkUuid, resources, mappings.getLineMappings());

        Map<OwnerInfo, LimitsInfos> limitsInfos = limitsHandler.getLimitsInfosFromEquipments(networkUuid, resources);
        limitsHandler.updateTemporaryLimits(networkUuid, resources, limitsInfos);
        limitsHandler.updatePermanentLimits(networkUuid, resources, limitsInfos);
    }

    private <T extends IdentifiableAttributes, U> Set<RegulatingOwnerInfo> getRegulatingPointsToTombstoneFromEquipment(UUID networkUuid, Map<RegulatingOwnerInfo, U> externalAttributesToInsert, List<Resource<T>> resources) {
        Set<RegulatingOwnerInfo> externalAttributesToTombstoneFromEquipment = new HashSet<>();
        for (Resource<T> resource : resources) {
            RegulatingOwnerInfo ownerInfo = new RegulatingOwnerInfo(resource.getId(), resource.getType(), networkUuid, resource.getVariantNum());
            if (!externalAttributesToInsert.containsKey(ownerInfo)) {
                externalAttributesToTombstoneFromEquipment.add(ownerInfo);
            }
        }
        return externalAttributesToTombstoneFromEquipment;
    }

    public void updateLinesSv(UUID networkUuid, List<Resource<BranchSvAttributes>> resources) {
        updateBranchesSv(networkUuid, resources, LINE_TABLE, mappings.getLineMappings());
    }

    public void deleteLines(UUID networkUuid, int variantNum, List<String> lineIds) {
        deleteIdentifiables(networkUuid, variantNum, lineIds, LINE_TABLE);
        limitsHandler.deleteTemporaryLimits(networkUuid, variantNum, lineIds);
        limitsHandler.deletePermanentLimits(networkUuid, variantNum, lineIds);
    }

    // Hvdc line

    public List<Resource<HvdcLineAttributes>> getHvdcLines(UUID networkUuid, int variantNum) {
        return getIdentifiables(networkUuid, variantNum, mappings.getHvdcLineMappings());
    }

    public Optional<Resource<HvdcLineAttributes>> getHvdcLine(UUID networkUuid, int variantNum, String hvdcLineId) {
        return getIdentifiable(networkUuid, variantNum, hvdcLineId, mappings.getHvdcLineMappings());
    }

    public void createHvdcLines(UUID networkUuid, List<Resource<HvdcLineAttributes>> resources) {
        createIdentifiables(networkUuid, resources, mappings.getHvdcLineMappings());
    }

    public void updateHvdcLines(UUID networkUuid, List<Resource<HvdcLineAttributes>> resources) {
        updateIdentifiables(networkUuid, resources, mappings.getHvdcLineMappings());
    }

    public void deleteHvdcLines(UUID networkUuid, int variantNum, List<String> hvdcLineIds) {
        deleteIdentifiables(networkUuid, variantNum, hvdcLineIds, HVDC_LINE_TABLE);
    }

    // Dangling line
    public void createDanglingLines(UUID networkUuid, List<Resource<DanglingLineAttributes>> resources) {
        createIdentifiables(networkUuid, resources, mappings.getDanglingLineMappings());

        // Now that the dangling lines are created, we will insert in the database the corresponding temporary limits.
        Map<OwnerInfo, LimitsInfos> limitsInfos = limitsHandler.getLimitsInfosFromEquipments(networkUuid, resources);
        limitsHandler.insertTemporaryLimits(limitsInfos);
        limitsHandler.insertPermanentLimits(limitsInfos);
    }

    public Optional<Resource<DanglingLineAttributes>> getDanglingLine(UUID networkUuid, int variantNum, String danglingLineId) {
        return getIdentifiable(networkUuid, variantNum, danglingLineId, mappings.getDanglingLineMappings());
    }

    public List<Resource<DanglingLineAttributes>> getDanglingLines(UUID networkUuid, int variantNum) {
        List<Resource<DanglingLineAttributes>> danglingLines = getIdentifiables(networkUuid, variantNum, mappings.getDanglingLineMappings());

        Map<OwnerInfo, LimitsInfos> limitsInfos = limitsHandler.getLimitsInfos(networkUuid, variantNum, EQUIPMENT_TYPE_COLUMN, ResourceType.DANGLING_LINE.toString());
        limitsHandler.insertLimitsInEquipments(networkUuid, danglingLines, limitsInfos);
        setRegulatingEquipments(danglingLines, networkUuid, variantNum, ResourceType.DANGLING_LINE);

        return danglingLines;
    }

    public List<Resource<DanglingLineAttributes>> getVoltageLevelDanglingLines(UUID networkUuid, int variantNum, String voltageLevelId) {
        List<Resource<DanglingLineAttributes>> danglingLines = getIdentifiablesInVoltageLevel(networkUuid, variantNum, voltageLevelId, mappings.getDanglingLineMappings());

        List<String> equipmentsIds = danglingLines.stream().map(Resource::getId).collect(Collectors.toList());

        Map<OwnerInfo, LimitsInfos> limitsInfos = limitsHandler.getLimitsInfosWithInClause(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, equipmentsIds);
        limitsHandler.insertLimitsInEquipments(networkUuid, danglingLines, limitsInfos);

        setRegulatingEquipmentsWithIds(danglingLines, networkUuid, variantNum, ResourceType.DANGLING_LINE, equipmentsIds);
        return danglingLines;
    }

    public void deleteDanglingLines(UUID networkUuid, int variantNum, List<String> danglingLineIds) {
        deleteIdentifiables(networkUuid, variantNum, danglingLineIds, DANGLING_LINE_TABLE);
        limitsHandler.deleteTemporaryLimits(networkUuid, variantNum, danglingLineIds);
        limitsHandler.deletePermanentLimits(networkUuid, variantNum, danglingLineIds);
    }

    public void updateDanglingLines(UUID networkUuid, List<Resource<DanglingLineAttributes>> resources) {
        updateIdentifiables(networkUuid, resources, mappings.getDanglingLineMappings(), VOLTAGE_LEVEL_ID_COLUMN);

        Map<OwnerInfo, LimitsInfos> limitsInfos = limitsHandler.getLimitsInfosFromEquipments(networkUuid, resources);
        limitsHandler.updateTemporaryLimits(networkUuid, resources, limitsInfos);
        limitsHandler.updatePermanentLimits(networkUuid, resources, limitsInfos);
    }

    public void updateDanglingLinesSv(UUID networkUuid, List<Resource<InjectionSvAttributes>> resources) {
        updateInjectionsSv(networkUuid, resources, DANGLING_LINE_TABLE, mappings.getDanglingLineMappings());
    }

    // Grounds
    public void createGrounds(UUID networkUuid, List<Resource<GroundAttributes>> resources) {
        createIdentifiables(networkUuid, resources, mappings.getGroundMappings());
    }

    public Optional<Resource<GroundAttributes>> getGround(UUID networkUuid, int variantNum, String groundId) {
        return getIdentifiable(networkUuid, variantNum, groundId, mappings.getGroundMappings());
    }

    public List<Resource<GroundAttributes>> getGrounds(UUID networkUuid, int variantNum) {
        return getIdentifiables(networkUuid, variantNum, mappings.getGroundMappings());
    }

    public List<Resource<GroundAttributes>> getVoltageLevelGrounds(UUID networkUuid, int variantNum, String voltageLevelId) {
        return getIdentifiablesInVoltageLevel(networkUuid, variantNum, voltageLevelId, mappings.getGroundMappings());
    }

    public void updateGrounds(UUID networkUuid, List<Resource<GroundAttributes>> resources) {
        updateIdentifiables(networkUuid, resources, mappings.getGroundMappings(), VOLTAGE_LEVEL_ID_COLUMN);
    }

    public void deleteGrounds(UUID networkUuid, int variantNum, List<String> groundIds) {
        deleteIdentifiables(networkUuid, variantNum, groundIds, GROUND_TABLE);
    }

    // Tie lines

    public List<Resource<TieLineAttributes>> getTieLines(UUID networkUuid, int variantNum) {
        return getIdentifiables(networkUuid, variantNum, mappings.getTieLineMappings());
    }

    public Optional<Resource<TieLineAttributes>> getTieLine(UUID networkUuid, int variantNum, String tieLineId) {
        return getIdentifiable(networkUuid, variantNum, tieLineId, mappings.getTieLineMappings());
    }

    public void createTieLines(UUID networkUuid, List<Resource<TieLineAttributes>> resources) {
        createIdentifiables(networkUuid, resources, mappings.getTieLineMappings());
    }

    public void deleteTieLines(UUID networkUuid, int variantNum, List<String> tieLineIds) {
        deleteIdentifiables(networkUuid, variantNum, tieLineIds, TIE_LINE_TABLE);
        limitsHandler.deleteTemporaryLimits(networkUuid, variantNum, tieLineIds);
        limitsHandler.deletePermanentLimits(networkUuid, variantNum, tieLineIds);
    }

    public void updateTieLines(UUID networkUuid, List<Resource<TieLineAttributes>> resources) {
        updateIdentifiables(networkUuid, resources, mappings.getTieLineMappings());
    }

    // Areas
    public List<Resource<AreaAttributes>> getAreas(UUID networkUuid, int variantNum) {
        List<Resource<AreaAttributes>> areas = getIdentifiables(networkUuid, variantNum, mappings.getAreaMappings());
        Map<OwnerInfo, List<AreaBoundaryAttributes>> areaBoundaries = getAreaBoundaries(networkUuid, variantNum, null, null);
        insertAreaBoundariesInAreas(networkUuid, areas, areaBoundaries);
        return areas;
    }

    public Optional<Resource<AreaAttributes>> getArea(UUID networkUuid, int variantNum, String areaId) {
        return getIdentifiable(networkUuid, variantNum, areaId, mappings.getAreaMappings());
    }

    public void createAreas(UUID networkUuid, List<Resource<AreaAttributes>> resources) {
        createIdentifiables(networkUuid, resources, mappings.getAreaMappings());
        Map<OwnerInfo, List<AreaBoundaryAttributes>> areaBoundaries = new HashMap<>();
        resources.stream()
            .filter(area -> area.getAttributes().getAreaBoundaries() != null
                && !area.getAttributes().getAreaBoundaries().isEmpty())
            .forEach(area ->
            areaBoundaries.put(new OwnerInfo(area.getId(), null, networkUuid, area.getVariantNum()),
                area.getAttributes().getAreaBoundaries()));
        insertAreaBoundaries(areaBoundaries);
    }

    public void deleteAreas(UUID networkUuid, int variantNum, List<String> areaIds) {
        deleteIdentifiables(networkUuid, variantNum, areaIds, AREA_TABLE);
        deleteAreaBoundaries(networkUuid, variantNum, areaIds);
    }

    public void updateAreas(UUID networkUuid, List<Resource<AreaAttributes>> resources) {
        updateIdentifiables(networkUuid, resources, mappings.getAreaMappings());
        updateAreaBoundaries(networkUuid, resources);
    }

    // configured buses
    public void createBuses(UUID networkUuid, List<Resource<ConfiguredBusAttributes>> resources) {
        createIdentifiables(networkUuid, resources, mappings.getConfiguredBusMappings());
    }

    public Optional<Resource<ConfiguredBusAttributes>> getConfiguredBus(UUID networkUuid, int variantNum, String busId) {
        return getIdentifiable(networkUuid, variantNum, busId, mappings.getConfiguredBusMappings());
    }

    public List<Resource<ConfiguredBusAttributes>> getConfiguredBuses(UUID networkUuid, int variantNum) {
        return getIdentifiables(networkUuid, variantNum, mappings.getConfiguredBusMappings());
    }

    public List<Resource<ConfiguredBusAttributes>> getVoltageLevelBuses(UUID networkUuid, int variantNum, String voltageLevelId) {
        return getIdentifiablesInVoltageLevel(networkUuid, variantNum, voltageLevelId, mappings.getConfiguredBusMappings());
    }

    public void updateBuses(UUID networkUuid, List<Resource<ConfiguredBusAttributes>> resources) {
        updateIdentifiables(networkUuid, resources, mappings.getConfiguredBusMappings(), VOLTAGE_LEVEL_ID_COLUMN);
    }

    public void deleteBuses(UUID networkUuid, int variantNum, List<String> configuredBusId) {
        deleteIdentifiables(networkUuid, variantNum, configuredBusId, CONFIGURED_BUS_TABLE);
    }

    private static String getNonEmptyTable(ResultSet resultSet) throws SQLException {
        var metaData = resultSet.getMetaData();
        for (int col = 4; col <= metaData.getColumnCount(); col++) { // skip 3 first columns corresponding to first inner select
            if (metaData.getColumnName(col).equalsIgnoreCase(ID_COLUMN) && resultSet.getObject(col) != null) {
                return metaData.getTableName(col).toLowerCase();
            }
        }
        return null;
    }

    private static Map<Pair<String, String>, Integer> getColumnIndexByTableNameAndColumnName(ResultSet resultSet, String tableName) throws SQLException {
        Map<Pair<String, String>, Integer> columnIndexes = new HashMap<>();
        var metaData = resultSet.getMetaData();
        for (int col = 1; col <= metaData.getColumnCount(); col++) {
            if (metaData.getTableName(col).equalsIgnoreCase(tableName)) {
                columnIndexes.put(Pair.of(tableName, metaData.getColumnName(col).toLowerCase()), col);
            }
        }
        return columnIndexes;
    }

    public Optional<Resource<IdentifiableAttributes>> getIdentifiable(UUID networkUuid, int variantNum, String id) {
        try (var connection = dataSource.getConnection()) {
            return PartialVariantUtils.getOptionalIdentifiable(
                    variantNum,
                    getNetworkAttributes(connection, networkUuid, variantNum, mappings, mapper).getFullVariantNum(),
                    () -> isTombstonedIdentifiable(connection, networkUuid, variantNum, id),
                    variant -> getIdentifiableForVariant(connection, networkUuid, variant, id, variantNum));
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private Optional<Resource<IdentifiableAttributes>> getIdentifiableForVariant(Connection connection, UUID networkUuid, int variantNum, String id, int variantNumOverride) {
        try (var preparedStmt = connection.prepareStatement(buildGetIdentifiableForAllTablesQuery())) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            preparedStmt.setString(3, id);
            try (ResultSet resultSet = preparedStmt.executeQuery()) {
                if (resultSet.next()) {
                    String tableName = getNonEmptyTable(resultSet);
                    if (tableName != null) {
                        TableMapping tableMapping = mappings.getTableMapping(tableName);
                        var columnIndexByTableAndColumnName = getColumnIndexByTableNameAndColumnName(resultSet, tableName);

                        IdentifiableAttributes attributes = tableMapping.getAttributesSupplier().get();
                        tableMapping.getColumnsMapping().forEach((columnName, columnMapping) -> {
                            Integer columnIndex = columnIndexByTableAndColumnName.get(Pair.of(tableName, columnName.toLowerCase()));
                            if (columnIndex == null) {
                                throw new PowsyblException("Column '" + columnName.toLowerCase() + "' of table '" + tableName + "' not found");
                            }
                            bindAttributes(resultSet, columnIndex, columnMapping, attributes, mapper);
                        });

                        Resource<IdentifiableAttributes> resource = new Resource.Builder<>(tableMapping.getResourceType())
                                .id(id)
                                .variantNum(variantNumOverride)
                                .attributes(attributes)
                                .build();
                        return Optional.of(completeResourceInfos(resource, networkUuid, variantNumOverride, id));
                    }
                }
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
        return Optional.empty();
    }

    // Temporary Limits

    // Regulating Points
    public void insertRegulatingPoints(Map<RegulatingOwnerInfo, RegulatingPointAttributes> regulatingPoints) {
        try (var connection = dataSource.getConnection()) {
            try (var preparedStmt = connection.prepareStatement(QueryCatalog.buildInsertRegulatingPointsQuery())) {
                List<Object> values = new ArrayList<>(12);
                List<Map.Entry<RegulatingOwnerInfo, RegulatingPointAttributes>> list = new ArrayList<>(regulatingPoints.entrySet());
                for (List<Map.Entry<RegulatingOwnerInfo, RegulatingPointAttributes>> subUnit : Lists.partition(list, BATCH_SIZE)) {
                    for (Map.Entry<RegulatingOwnerInfo, RegulatingPointAttributes> attributes : subUnit) {
                        values.clear();
                        values.add(attributes.getKey().getNetworkUuid());
                        values.add(attributes.getKey().getVariantNum());
                        values.add(attributes.getKey().getEquipmentId());
                        values.add(attributes.getKey().getEquipmentType().toString());
                        values.add(attributes.getKey().getRegulatingTapChangerType().toString());
                        if (attributes.getValue() != null) {
                            values.add(attributes.getValue().getRegulationMode());
                            values.add(attributes.getValue().getLocalTerminal() != null
                                ? attributes.getValue().getLocalTerminal().getConnectableId()
                                : null);
                            values.add(attributes.getValue().getLocalTerminal() != null
                                ? attributes.getValue().getLocalTerminal().getSide()
                                : null);
                            values.add(attributes.getValue().getRegulatingTerminal() != null
                                ? attributes.getValue().getRegulatingTerminal().getConnectableId()
                                : null);
                            values.add(attributes.getValue().getRegulatingTerminal() != null
                                ? attributes.getValue().getRegulatingTerminal().getSide()
                                : null);
                            values.add(attributes.getValue().getRegulatedResourceType() != null
                                ? attributes.getValue().getRegulatedResourceType().toString()
                                : null);
                            values.add(attributes.getValue().getRegulating() != null
                                ? attributes.getValue().getRegulating() : null);
                        } else {
                            values.add(null);
                            values.add(attributes.getKey().getEquipmentId());
                            for (int i = 0; i < 4; i++) {
                                values.add(null);
                            }
                            values.add(false);
                        }
                        bindValues(preparedStmt, values, mapper);
                        preparedStmt.addBatch();
                    }
                    preparedStmt.executeBatch();
                }
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private <T extends IdentifiableAttributes> void updateRegulatingPoints(UUID networkUuid, List<Resource<T>> resources, ResourceType resourceType, Map<RegulatingOwnerInfo, RegulatingPointAttributes> regulatingPointToInsert) {
        deleteRegulatingPoints(networkUuid, resources, resourceType);
        insertRegulatingPoints(regulatingPointToInsert);
        insertTombstonedRegulatingPoints(networkUuid, regulatingPointToInsert, resources, resourceType);
    }

    private <T extends IdentifiableAttributes> void insertTombstonedRegulatingPoints(UUID networkUuid, Map<RegulatingOwnerInfo, RegulatingPointAttributes> regulatingPointToInsert, List<Resource<T>> resources, ResourceType resourceType) {
        try (var connection = dataSource.getConnection()) {
            Map<Integer, List<String>> resourcesByVariant = resources.stream()
                    .collect(Collectors.groupingBy(
                            Resource::getVariantNum,
                            Collectors.mapping(Resource::getId, Collectors.toList())
                    ));
            Set<RegulatingOwnerInfo> tombstonedRegulatingPoints = PartialVariantUtils.getExternalAttributesToTombstone(
                    resourcesByVariant,
                    variantNum -> getNetworkAttributes(connection, networkUuid, variantNum, mappings, mapper),
                    (fullVariantNum, variantNum, ids) -> getRegulatingPointsWithInClauseForVariant(connection, networkUuid, fullVariantNum, REGULATING_EQUIPMENT_ID, ids, resourceType, variantNum).keySet(),
                    variantNum -> getTombstonedRegulatingPointsIds(connection, networkUuid, variantNum),
                    getRegulatingPointsToTombstoneFromEquipment(networkUuid, regulatingPointToInsert, resources)
            );

            try (var preparedStmt = connection.prepareStatement(buildInsertTombstonedExternalAttributesQuery())) {
                for (RegulatingOwnerInfo regulatingPoint : tombstonedRegulatingPoints) {
                    preparedStmt.setObject(1, regulatingPoint.getNetworkUuid());
                    preparedStmt.setInt(2, regulatingPoint.getVariantNum());
                    preparedStmt.setString(3, regulatingPoint.getEquipmentId());
                    preparedStmt.setString(4, ExternalAttributesType.REGULATING_POINT.toString());
                    preparedStmt.addBatch();
                }
                preparedStmt.executeBatch();
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private Set<String> getRegulatingPointsIdentifiableIdsForVariant(Connection connection, UUID networkUuid, int variantNum) {
        Set<String> identifiableIds = new HashSet<>();
        try (var preparedStmt = connection.prepareStatement(buildRegulatingPointsIdsQuery())) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            try (var resultSet = preparedStmt.executeQuery()) {
                while (resultSet.next()) {
                    identifiableIds.add(resultSet.getString(REGULATING_EQUIPMENT_ID));
                }
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
        return identifiableIds;
    }

    private Set<String> getTombstonedRegulatingPointsIds(Connection connection, UUID networkUuid, int variantNum) {
        Set<String> identifiableIds = new HashSet<>();
        try (var preparedStmt = connection.prepareStatement(buildGetTombstonedExternalAttributesIdsQuery())) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            preparedStmt.setString(3, ExternalAttributesType.REGULATING_POINT.toString());
            try (var resultSet = preparedStmt.executeQuery()) {
                while (resultSet.next()) {
                    identifiableIds.add(resultSet.getString(EQUIPMENT_ID_COLUMN));
                }
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
        return identifiableIds;
    }

    private <T extends IdentifiableAttributes> void deleteRegulatingPoints(UUID networkUuid, List<Resource<T>> resources, ResourceType type) {
        Map<Integer, List<String>> resourceIdsByVariant = new HashMap<>();
        for (Resource<T> resource : resources) {
            List<String> resourceIds = resourceIdsByVariant.get(resource.getVariantNum());
            if (resourceIds != null) {
                resourceIds.add(resource.getId());
            } else {
                resourceIds = new ArrayList<>();
                resourceIds.add(resource.getId());
            }
            resourceIdsByVariant.put(resource.getVariantNum(), resourceIds);
        }
        resourceIdsByVariant.forEach((k, v) -> deleteRegulatingPoints(networkUuid, k, v, type));
    }

    public Map<RegulatingOwnerInfo, RegulatingPointAttributes> getRegulatingPoints(UUID networkUuid, int variantNum, ResourceType type) {
        try (var connection = dataSource.getConnection()) {
            return PartialVariantUtils.getExternalAttributes(
                    variantNum,
                    getNetworkAttributes(connection, networkUuid, variantNum, mappings, mapper).getFullVariantNum(),
                    () -> getTombstonedRegulatingPointsIds(connection, networkUuid, variantNum),
                    () -> getTombstonedIdentifiableIds(connection, networkUuid, variantNum),
                variant -> getRegulatingPointsForVariant(connection, networkUuid, variant, type, variantNum),
                    RegulatingOwnerInfo::getEquipmentId);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public Map<RegulatingOwnerInfo, RegulatingPointAttributes> getRegulatingPointsForVariant(Connection connection, UUID networkUuid, int variantNum, ResourceType type, int variantNumOverride) {
        try (var preparedStmt = connection.prepareStatement(buildRegulatingPointsQuery())) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            preparedStmt.setString(3, type.toString());

            return innerGetRegulatingPoints(preparedStmt, type, variantNumOverride);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public Map<RegulatingOwnerInfo, RegulatingPointAttributes> getRegulatingPointsWithInClause(UUID networkUuid, int variantNum, String columnNameForWhereClause, List<String> valuesForInClause, ResourceType type) {
        if (valuesForInClause.isEmpty()) {
            return Collections.emptyMap();
        }
        try (var connection = dataSource.getConnection()) {
            return PartialVariantUtils.getExternalAttributes(
                    variantNum,
                    getNetworkAttributes(connection, networkUuid, variantNum, mappings, mapper).getFullVariantNum(),
                    () -> getTombstonedRegulatingPointsIds(connection, networkUuid, variantNum),
                    () -> getTombstonedIdentifiableIds(connection, networkUuid, variantNum),
                variant -> getRegulatingPointsWithInClauseForVariant(connection, networkUuid, variant, columnNameForWhereClause, valuesForInClause, type, variantNum),
                    RegulatingOwnerInfo::getEquipmentId);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private Map<RegulatingOwnerInfo, RegulatingPointAttributes> getRegulatingPointsWithInClauseForVariant(Connection connection, UUID networkUuid, int variantNum, String columnNameForWhereClause, List<String> valuesForInClause, ResourceType type, int variantNumOverride) {
        try (var preparedStmt = connection.prepareStatement(buildRegulatingPointsWithInClauseQuery(columnNameForWhereClause, valuesForInClause.size()))) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            preparedStmt.setString(3, type.toString());
            for (int i = 0; i < valuesForInClause.size(); i++) {
                preparedStmt.setString(4 + i, valuesForInClause.get(i));
            }

            return innerGetRegulatingPoints(preparedStmt, type, variantNumOverride);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private void deleteRegulatingPoints(UUID networkUuid, int variantNum, List<String> equipmentIds, ResourceType type) {
        try (var connection = dataSource.getConnection()) {
            try (var preparedStmt = connection.prepareStatement(buildDeleteRegulatingPointsVariantEquipmentINQuery(equipmentIds.size()))) {
                preparedStmt.setObject(1, networkUuid);
                preparedStmt.setInt(2, variantNum);
                preparedStmt.setObject(3, type.toString());
                for (int i = 0; i < equipmentIds.size(); i++) {
                    preparedStmt.setString(4 + i, equipmentIds.get(i));
                }
                preparedStmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    // Reactive Capability Curve Points
    public void insertReactiveCapabilityCurvePoints(Map<OwnerInfo, List<ReactiveCapabilityCurvePointAttributes>> reactiveCapabilityCurvePoints) {
        try (var connection = dataSource.getConnection()) {
            try (var preparedStmt = connection.prepareStatement(buildInsertReactiveCapabilityCurvePointsQuery())) {
                List<Object> values = new ArrayList<>(7);
                List<Map.Entry<OwnerInfo, List<ReactiveCapabilityCurvePointAttributes>>> list = new ArrayList<>(reactiveCapabilityCurvePoints.entrySet());
                for (List<Map.Entry<OwnerInfo, List<ReactiveCapabilityCurvePointAttributes>>> subUnit : Lists.partition(list, BATCH_SIZE)) {
                    for (Map.Entry<OwnerInfo, List<ReactiveCapabilityCurvePointAttributes>> myPair : subUnit) {
                        for (ReactiveCapabilityCurvePointAttributes reactiveCapabilityCurvePoint : myPair.getValue()) {
                            values.clear();
                            // In order, from the QueryCatalog.buildInsertReactiveCapabilityCurvePointsQuery SQL query :
                            // equipmentId, equipmentType, networkUuid, variantNum, minQ, maxQ, p
                            values.add(myPair.getKey().getEquipmentId());
                            values.add(myPair.getKey().getEquipmentType().toString());
                            values.add(myPair.getKey().getNetworkUuid());
                            values.add(myPair.getKey().getVariantNum());
                            values.add(reactiveCapabilityCurvePoint.getMinQ());
                            values.add(reactiveCapabilityCurvePoint.getMaxQ());
                            values.add(reactiveCapabilityCurvePoint.getP());
                            bindValues(preparedStmt, values, mapper);
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

    public Map<OwnerInfo, List<ReactiveCapabilityCurvePointAttributes>> getReactiveCapabilityCurvePointsWithInClause(UUID networkUuid, int variantNum, String columnNameForWhereClause, List<String> valuesForInClause) {
        try (var connection = dataSource.getConnection()) {
            return PartialVariantUtils.getExternalAttributes(
                    variantNum,
                    getNetworkAttributes(connection, networkUuid, variantNum, mappings, mapper).getFullVariantNum(),
                    () -> getTombstonedReactiveCapabilityCurvePointsIds(connection, networkUuid, variantNum),
                    () -> getTombstonedIdentifiableIds(connection, networkUuid, variantNum),
                variant -> getReactiveCapabilityCurvePointsWithInClauseForVariant(connection, networkUuid, variant, columnNameForWhereClause, valuesForInClause, variantNum),
                    OwnerInfo::getEquipmentId);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private Map<OwnerInfo, List<ReactiveCapabilityCurvePointAttributes>> getReactiveCapabilityCurvePointsWithInClauseForVariant(Connection connection, UUID networkUuid, int variantNum, String columnNameForWhereClause, List<String> valuesForInClause, int variantNumOverride) {
        if (valuesForInClause.isEmpty()) {
            return Collections.emptyMap();
        }
        try (var preparedStmt = connection.prepareStatement(buildReactiveCapabilityCurvePointWithInClauseQuery(columnNameForWhereClause, valuesForInClause.size()))) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            for (int i = 0; i < valuesForInClause.size(); i++) {
                preparedStmt.setString(3 + i, valuesForInClause.get(i));
            }

            return innerGetReactiveCapabilityCurvePoints(preparedStmt, variantNumOverride);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public Map<OwnerInfo, List<ReactiveCapabilityCurvePointAttributes>> getReactiveCapabilityCurvePoints(UUID networkUuid, int variantNum, String columnNameForWhereClause, String valueForWhereClause) {
        try (var connection = dataSource.getConnection()) {
            return PartialVariantUtils.getExternalAttributes(
                    variantNum,
                    getNetworkAttributes(connection, networkUuid, variantNum, mappings, mapper).getFullVariantNum(),
                    () -> getTombstonedReactiveCapabilityCurvePointsIds(connection, networkUuid, variantNum),
                    () -> getTombstonedIdentifiableIds(connection, networkUuid, variantNum),
                    variant -> getReactiveCapabilityCurvePointsForVariant(connection, networkUuid, variant, columnNameForWhereClause, valueForWhereClause, variantNum),
                    OwnerInfo::getEquipmentId);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public Map<OwnerInfo, List<ReactiveCapabilityCurvePointAttributes>> getReactiveCapabilityCurvePointsForVariant(Connection connection, UUID networkUuid, int variantNum, String columnNameForWhereClause, String valueForWhereClause, int variantNumOverride) {
        try (var preparedStmt = connection.prepareStatement(buildReactiveCapabilityCurvePointQuery(columnNameForWhereClause))) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            preparedStmt.setString(3, valueForWhereClause);

            return innerGetReactiveCapabilityCurvePoints(preparedStmt, variantNumOverride);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private Map<OwnerInfo, List<ReactiveCapabilityCurvePointAttributes>> innerGetReactiveCapabilityCurvePoints(PreparedStatement preparedStmt, int variantNumOverride) throws SQLException {
        try (ResultSet resultSet = preparedStmt.executeQuery()) {
            Map<OwnerInfo, List<ReactiveCapabilityCurvePointAttributes>> map = new HashMap<>();
            while (resultSet.next()) {

                OwnerInfo owner = new OwnerInfo();
                ReactiveCapabilityCurvePointAttributes reactiveCapabilityCurvePoint = new ReactiveCapabilityCurvePointAttributes();
                // In order, from the QueryCatalog.buildReactiveCapabilityCurvePointQuery SQL query :
                // equipmentId, equipmentType, networkUuid, variantNum, minQ, maxQ, p
                owner.setEquipmentId(resultSet.getString(1));
                owner.setEquipmentType(ResourceType.valueOf(resultSet.getString(2)));
                owner.setNetworkUuid(UUID.fromString(resultSet.getString(3)));
                owner.setVariantNum(variantNumOverride);
                reactiveCapabilityCurvePoint.setMinQ(resultSet.getDouble(5));
                reactiveCapabilityCurvePoint.setMaxQ(resultSet.getDouble(6));
                reactiveCapabilityCurvePoint.setP(resultSet.getDouble(7));

                map.computeIfAbsent(owner, k -> new ArrayList<>());
                map.get(owner).add(reactiveCapabilityCurvePoint);
            }
            return map;
        }
    }

    // Area Boundaries
    public void updateAreaBoundaries(UUID networkUuid, List<Resource<AreaAttributes>> resources) {
        deleteAreaBoundaries(networkUuid, resources);
        Map<OwnerInfo, List<AreaBoundaryAttributes>> areaBoundariesToInsert = getAreaBoundariesFromEquipments(networkUuid, resources);
        insertAreaBoundaries(areaBoundariesToInsert);
        insertTombstonedAreaBoundaries(networkUuid, areaBoundariesToInsert, resources);
    }

    public void insertAreaBoundaries(Map<OwnerInfo, List<AreaBoundaryAttributes>> areaBoundaries) {
        try (var connection = dataSource.getConnection()) {
            try (var preparedStmt = connection.prepareStatement(buildInsertAreaBoundariesQuery())) {
                List<Object> values = new ArrayList<>(7);
                List<Map.Entry<OwnerInfo, List<AreaBoundaryAttributes>>> list = new ArrayList<>(areaBoundaries.entrySet());
                for (List<Map.Entry<OwnerInfo, List<AreaBoundaryAttributes>>> subUnit : Lists.partition(list, BATCH_SIZE)) {
                    for (Map.Entry<OwnerInfo, List<AreaBoundaryAttributes>> myPair : subUnit) {
                        for (AreaBoundaryAttributes areaBoundary : myPair.getValue()) {
                            values.clear();
                            // In order, from the QueryCatalog.buildInsertAreaBoundariesQuery SQL query :
                            // equipmentId (areaId), networkUuid, variantNum, boundarydanglinglineid, terminal connectable id, terminal side, ac
                            values.add(myPair.getKey().getEquipmentId());
                            values.add(myPair.getKey().getNetworkUuid());
                            values.add(myPair.getKey().getVariantNum());
                            values.add(areaBoundary.getBoundaryDanglingLineId());
                            if (areaBoundary.getTerminal() != null) {
                                values.add(areaBoundary.getTerminal().getConnectableId());
                                values.add(areaBoundary.getTerminal().getSide());
                            } else {
                                values.add(null);
                                values.add(null);
                            }
                            values.add(areaBoundary.getAc());
                            bindValues(preparedStmt, values, mapper);
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

    public Map<OwnerInfo, List<AreaBoundaryAttributes>> getAreaBoundariesWithInClause(UUID networkUuid, int variantNum, String columnNameForWhereClause, List<String> valuesForInClause) {
        try (var connection = dataSource.getConnection()) {
            return PartialVariantUtils.getExternalAttributes(
                variantNum,
                getNetworkAttributes(connection, networkUuid, variantNum, mappings, mapper).getFullVariantNum(),
                () -> getTombstonedAreaBoundariesIds(connection, networkUuid, variantNum),
                () -> getTombstonedIdentifiableIds(connection, networkUuid, variantNum),
                variant -> getAreaBoundariesWithInClauseForVariant(connection, networkUuid, variant, columnNameForWhereClause, valuesForInClause, variantNum),
                OwnerInfo::getEquipmentId);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private Map<OwnerInfo, List<AreaBoundaryAttributes>> getAreaBoundariesWithInClauseForVariant(Connection connection, UUID networkUuid, int variantNum, String columnNameForWhereClause, List<String> valuesForInClause, int variantNumOverride) {
        if (valuesForInClause.isEmpty()) {
            return Collections.emptyMap();
        }
        try (var preparedStmt = connection.prepareStatement(buildAreaBoundaryWithInClauseQuery(columnNameForWhereClause, valuesForInClause.size()))) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            for (int i = 0; i < valuesForInClause.size(); i++) {
                preparedStmt.setString(3 + i, valuesForInClause.get(i));
            }
            return innerGetAreaBoundaries(preparedStmt, variantNumOverride);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public Map<OwnerInfo, List<AreaBoundaryAttributes>> getAreaBoundaries(UUID networkUuid, int variantNum, String columnNameForWhereClause, String valueForWhereClause) {
        try (var connection = dataSource.getConnection()) {
            return PartialVariantUtils.getExternalAttributes(
                variantNum,
                getNetworkAttributes(connection, networkUuid, variantNum, mappings, mapper).getFullVariantNum(),
                () -> getTombstonedAreaBoundariesIds(connection, networkUuid, variantNum),
                () -> getTombstonedIdentifiableIds(connection, networkUuid, variantNum),
                variant -> getAreaBoundariesForVariant(connection, networkUuid, variant, columnNameForWhereClause, valueForWhereClause, variantNum),
                OwnerInfo::getEquipmentId);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public Map<OwnerInfo, List<AreaBoundaryAttributes>> getAreaBoundariesForVariant(Connection connection, UUID networkUuid, int variantNum, String columnNameForWhereClause, String valueForWhereClause, int variantNumOverride) {
        try (var preparedStmt = connection.prepareStatement(buildAreaBoundaryQuery(columnNameForWhereClause))) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            if (valueForWhereClause != null) {
                preparedStmt.setString(3, valueForWhereClause);
            }
            return innerGetAreaBoundaries(preparedStmt, variantNumOverride);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private Map<OwnerInfo, List<AreaBoundaryAttributes>> innerGetAreaBoundaries(PreparedStatement preparedStmt, int variantNumOverride) throws SQLException {
        try (ResultSet resultSet = preparedStmt.executeQuery()) {
            Map<OwnerInfo, List<AreaBoundaryAttributes>> map = new HashMap<>();
            while (resultSet.next()) {
                OwnerInfo owner = new OwnerInfo();
                AreaBoundaryAttributes areaBoundary = new AreaBoundaryAttributes();
                // In order, from the QueryCatalog.buildAreaBoundariesQuery SQL query :
                // areaId, networkUuid, boundarydanglinglineid, terminalconnectableid, terminalside, ac
                owner.setEquipmentId(resultSet.getString(1));
                areaBoundary.setAreaId(resultSet.getString(1));
                owner.setNetworkUuid(UUID.fromString(resultSet.getString(2)));
                owner.setVariantNum(variantNumOverride);
                areaBoundary.setBoundaryDanglingLineId(resultSet.getString(3));
                Optional<String> connectableId = Optional.ofNullable(resultSet.getString(4));
                if (connectableId.isPresent()) {
                    areaBoundary.setTerminal(new TerminalRefAttributes(connectableId.get(), resultSet.getString(5)));
                }
                areaBoundary.setAc(resultSet.getBoolean(6));
                map.computeIfAbsent(owner, k -> new ArrayList<>());
                map.get(owner).add(areaBoundary);
            }
            return map;
        }
    }

    // using the request on a small number of ids and not on all elements
    private <T extends AbstractRegulatingEquipmentAttributes & RegulatedEquipmentAttributes> void setRegulatingPointAndRegulatingEquipmentsWithIds(List<Resource<T>> elements, UUID networkUuid, int variantNum, ResourceType type) {
        // regulating points
        List<String> elementIds = elements.stream().map(Resource::getId).toList();
        Map<RegulatingOwnerInfo, RegulatingPointAttributes> regulatingPointAttributes = getRegulatingPointsWithInClause(networkUuid, variantNum,
            REGULATING_EQUIPMENT_ID, elementIds, type);
        Map<OwnerInfo, Set<RegulatingEquipmentIdentifier>> regulatingEquipments = getRegulatingEquipmentsWithInClause(networkUuid, variantNum, "regulatingterminalconnectableid", elementIds, type);
        elements.forEach(element -> {
            OwnerInfo ownerInfo = new OwnerInfo(element.getId(), type, networkUuid, variantNum);
            RegulatingOwnerInfo regulatingOwnerInfo = new RegulatingOwnerInfo(element.getId(), type, RegulatingTapChangerType.NONE, networkUuid, variantNum);
            element.getAttributes().setRegulatingPoint(
                regulatingPointAttributes.get(regulatingOwnerInfo));
            element.getAttributes().setRegulatingEquipments(regulatingEquipments.get(ownerInfo));
        });
    }

    // on all elements of the network
    private <T extends AbstractRegulatingEquipmentAttributes & RegulatedEquipmentAttributes> void setRegulatingPointAndRegulatingEquipments(List<Resource<T>> elements, UUID networkUuid, int variantNum, ResourceType type) {
        // regulating points
        Map<RegulatingOwnerInfo, RegulatingPointAttributes> regulatingPointAttributes = getRegulatingPoints(networkUuid, variantNum, type);
        Map<OwnerInfo, Set<RegulatingEquipmentIdentifier>> regulatingEquipments = getRegulatingEquipments(networkUuid, variantNum, type);
        elements.forEach(element -> {
            OwnerInfo ownerInfo = new OwnerInfo(element.getId(), type, networkUuid, variantNum);
            RegulatingOwnerInfo regulatingOwnerInfo = new RegulatingOwnerInfo(element.getId(), type, RegulatingTapChangerType.NONE, networkUuid, variantNum);
            element.getAttributes().setRegulatingPoint(
                regulatingPointAttributes.get(regulatingOwnerInfo));
            element.getAttributes().setRegulatingEquipments(regulatingEquipments.get(ownerInfo));
        });
    }

    // for two winding transformers
    // using the request on a small number of ids and not on all elements
    private void setRegulatingPointAndRegulatingEquipmentsWithIdsForTwoWindingsTransformers(List<Resource<TwoWindingsTransformerAttributes>> elements, UUID networkUuid, int variantNum) {
        // regulating points
        List<String> elementIds = elements.stream().map(Resource::getId).toList();
        Map<RegulatingOwnerInfo, RegulatingPointAttributes> twoWindingsTransformerTapChangerRegulatingPointAttributes = getRegulatingPointsWithInClause(networkUuid, variantNum,
            REGULATING_EQUIPMENT_ID, elementIds, ResourceType.TWO_WINDINGS_TRANSFORMER);
        Map<OwnerInfo, Set<RegulatingEquipmentIdentifier>> regulatingEquipments = getRegulatingEquipmentsWithInClause(networkUuid, variantNum,
            "regulatingterminalconnectableid", elementIds, ResourceType.TWO_WINDINGS_TRANSFORMER);
        elements.forEach(element -> {
            PhaseTapChangerAttributes phaseTapChangerAttributes = element.getAttributes().getPhaseTapChangerAttributes();
            RatioTapChangerAttributes ratioTapChangerAttributes = element.getAttributes().getRatioTapChangerAttributes();
            setRegulatingPointForTapChanger(ratioTapChangerAttributes, phaseTapChangerAttributes,
                RegulatingTapChangerType.RATIO_TAP_CHANGER, RegulatingTapChangerType.PHASE_TAP_CHANGER,
                twoWindingsTransformerTapChangerRegulatingPointAttributes, element.getId(), ResourceType.TWO_WINDINGS_TRANSFORMER, networkUuid, variantNum);
            element.getAttributes().setRegulatingEquipments(regulatingEquipments.get(
                new OwnerInfo(element.getId(), ResourceType.TWO_WINDINGS_TRANSFORMER, networkUuid, variantNum)));
        });
    }

    // on all elements of the network
    private void setRegulatingPointAndRegulatingEquipmentsForTwoWindingsTransformers(List<Resource<TwoWindingsTransformerAttributes>> twoWindingTransformers, UUID networkUuid, int variantNum) {
        // regulating points
        Map<RegulatingOwnerInfo, RegulatingPointAttributes> twoWindingsTransformerRegulatingPointAttributes = getRegulatingPoints(networkUuid, variantNum, ResourceType.TWO_WINDINGS_TRANSFORMER);
        Map<OwnerInfo, Set<RegulatingEquipmentIdentifier>> regulatingEquipments = getRegulatingEquipments(networkUuid, variantNum, ResourceType.TWO_WINDINGS_TRANSFORMER);
        twoWindingTransformers.forEach(element -> {
            PhaseTapChangerAttributes phaseTapChangerAttributes = element.getAttributes().getPhaseTapChangerAttributes();
            RatioTapChangerAttributes ratioTapChangerAttributes = element.getAttributes().getRatioTapChangerAttributes();
            setRegulatingPointForTapChanger(ratioTapChangerAttributes, phaseTapChangerAttributes,
                RegulatingTapChangerType.RATIO_TAP_CHANGER, RegulatingTapChangerType.PHASE_TAP_CHANGER,
                twoWindingsTransformerRegulatingPointAttributes, element.getId(), ResourceType.TWO_WINDINGS_TRANSFORMER, networkUuid, variantNum);
            element.getAttributes().setRegulatingEquipments(regulatingEquipments.get(
                new OwnerInfo(element.getId(), ResourceType.TWO_WINDINGS_TRANSFORMER, networkUuid, variantNum)));
        });
    }

    // three windings transformers
    private void setRegulatingPointAndRegulatingEquipmentsForThreeWindingsTransformers(List<Resource<ThreeWindingsTransformerAttributes>> threeWindingTransformers, UUID networkUuid, int variantNum) {
        Map<RegulatingOwnerInfo, RegulatingPointAttributes> threeWindingsTransformerRegulatingPointAttributes = getRegulatingPoints(networkUuid, variantNum, ResourceType.THREE_WINDINGS_TRANSFORMER);
        Map<OwnerInfo, Set<RegulatingEquipmentIdentifier>> regulatingEquipments = getRegulatingEquipments(networkUuid, variantNum, ResourceType.THREE_WINDINGS_TRANSFORMER);
        threeWindingTransformers.forEach(threeWindingsTransformer -> {
            for (ThreeSides side : ThreeSides.values()) {
                PhaseTapChangerAttributes phaseTapChangerAttributes = threeWindingsTransformer.getAttributes().getLeg(side.getNum()).getPhaseTapChangerAttributes();
                RatioTapChangerAttributes ratioTapChangerAttributes = threeWindingsTransformer.getAttributes().getLeg(side.getNum()).getRatioTapChangerAttributes();
                setRegulatingPointForTapChanger(ratioTapChangerAttributes, phaseTapChangerAttributes,
                    RegulatingTapChangerType.getThreeWindingsTransformerTapChangerType(side, RegulatingTapChangerType.RATIO_TAP_CHANGER),
                    RegulatingTapChangerType.getThreeWindingsTransformerTapChangerType(side, RegulatingTapChangerType.PHASE_TAP_CHANGER),
                    threeWindingsTransformerRegulatingPointAttributes, threeWindingsTransformer.getId(), ResourceType.THREE_WINDINGS_TRANSFORMER, networkUuid, variantNum);
            }
            threeWindingsTransformer.getAttributes().setRegulatingEquipments(regulatingEquipments.get(
                new OwnerInfo(threeWindingsTransformer.getId(), ResourceType.THREE_WINDINGS_TRANSFORMER, networkUuid, variantNum)));
        });
    }

    private void setRegulatingPointAndRegulatingEquipmentsForThreeWindingsTransformersWithIds(List<Resource<ThreeWindingsTransformerAttributes>> threeWindingTransformers, UUID networkUuid, int variantNum) {
        List<String> elementIds = threeWindingTransformers.stream().map(Resource::getId).toList();
        Map<RegulatingOwnerInfo, RegulatingPointAttributes> threeWindingsTransformerRegulatingPointAttributes = getRegulatingPointsWithInClause(networkUuid, variantNum,
            REGULATING_EQUIPMENT_ID, elementIds, ResourceType.THREE_WINDINGS_TRANSFORMER);
        Map<OwnerInfo, Set<RegulatingEquipmentIdentifier>> regulatingEquipments = getRegulatingEquipmentsWithInClause(networkUuid, variantNum,
            "regulatingterminalconnectableid", elementIds, ResourceType.THREE_WINDINGS_TRANSFORMER);
        threeWindingTransformers.forEach(element -> {
            for (ThreeSides side : ThreeSides.values()) {
                PhaseTapChangerAttributes phaseTapChangerAttributes = element.getAttributes().getLeg(side.getNum()).getPhaseTapChangerAttributes();
                RatioTapChangerAttributes ratioTapChangerAttributes = element.getAttributes().getLeg(side.getNum()).getRatioTapChangerAttributes();
                setRegulatingPointForTapChanger(ratioTapChangerAttributes, phaseTapChangerAttributes,
                    RegulatingTapChangerType.getThreeWindingsTransformerTapChangerType(side, RegulatingTapChangerType.RATIO_TAP_CHANGER),
                    RegulatingTapChangerType.getThreeWindingsTransformerTapChangerType(side, RegulatingTapChangerType.PHASE_TAP_CHANGER),
                    threeWindingsTransformerRegulatingPointAttributes, element.getId(), ResourceType.THREE_WINDINGS_TRANSFORMER, networkUuid, variantNum);
            }
            element.getAttributes().setRegulatingEquipments(regulatingEquipments.get(
                new OwnerInfo(element.getId(), ResourceType.THREE_WINDINGS_TRANSFORMER, networkUuid, variantNum)));
        });
    }

    private void setRegulatingPointForTapChanger(RatioTapChangerAttributes ratioTapChangerAttributes, PhaseTapChangerAttributes phaseTapChangerAttributes,
                                                 RegulatingTapChangerType ratioRegulatingTapChangerType, RegulatingTapChangerType phaseRegulatingTapChangerType,
                                                 Map<RegulatingOwnerInfo, RegulatingPointAttributes> map, String transformerId, ResourceType resourceType,
                                                 UUID networkUuid, int variantNum) {
        if (ratioTapChangerAttributes != null) {
            RegulatingOwnerInfo ratioTapChangerRegulatingOwnerInfo = new RegulatingOwnerInfo(
                transformerId, resourceType,
                ratioRegulatingTapChangerType, networkUuid, variantNum);
            ratioTapChangerAttributes.setRegulatingPoint(map.get(ratioTapChangerRegulatingOwnerInfo));
        }
        if (phaseTapChangerAttributes != null) {
            RegulatingOwnerInfo phaseTapChangerRegulatingOwnerInfo = new RegulatingOwnerInfo(
                transformerId, resourceType,
                phaseRegulatingTapChangerType, networkUuid, variantNum);
            phaseTapChangerAttributes.setRegulatingPoint(map.get(phaseTapChangerRegulatingOwnerInfo));
        }
    }

    private <T extends RegulatedEquipmentAttributes> void setRegulatingEquipmentsWithIds(List<Resource<T>> elements, UUID networkUuid, int variantNum, ResourceType type) {
        List<String> elementIds = elements.stream().map(Resource::getId).toList();
        setRegulatingEquipmentsWithIds(elements, networkUuid, variantNum, type, elementIds);
    }

    // using the request on a small number of ids and not on all elements
    private <T extends RegulatedEquipmentAttributes> void setRegulatingEquipmentsWithIds(List<Resource<T>> elements, UUID networkUuid, int variantNum, ResourceType type, List<String> elementIds) {
        // regulating equipments
        Map<OwnerInfo, Set<RegulatingEquipmentIdentifier>> regulatingEquipments = getRegulatingEquipmentsWithInClause(networkUuid, variantNum, "regulatingterminalconnectableid", elementIds, type);
        elements.forEach(element -> {
            OwnerInfo ownerInfo = new OwnerInfo(element.getId(), type, networkUuid, variantNum);
            element.getAttributes().setRegulatingEquipments(regulatingEquipments.getOrDefault(ownerInfo, Set.of()));
        });
    }

    // on all elements of the network
    private <T extends RegulatedEquipmentAttributes> void setRegulatingEquipments(List<Resource<T>> elements, UUID networkUuid, int variantNum, ResourceType type) {
        // regulating equipments
        Map<OwnerInfo, Set<RegulatingEquipmentIdentifier>> regulatingEquipments = getRegulatingEquipments(networkUuid, variantNum, type);
        elements.forEach(element -> {
            OwnerInfo ownerInfo = new OwnerInfo(element.getId(), type, networkUuid, variantNum);
            element.getAttributes().setRegulatingEquipments(regulatingEquipments.getOrDefault(ownerInfo, Set.of()));
        });
    }

    protected <T extends AbstractRegulatingEquipmentAttributes> Map<RegulatingOwnerInfo, RegulatingPointAttributes> getRegulatingPointFromEquipments(UUID networkUuid, List<Resource<T>> resources) {
        Map<RegulatingOwnerInfo, RegulatingPointAttributes> map = new HashMap<>();
        if (!resources.isEmpty()) {
            for (Resource<T> resource : resources) {
                RegulatingOwnerInfo info = new RegulatingOwnerInfo(
                    resource.getId(),
                    resource.getType(),
                    RegulatingTapChangerType.NONE,
                    networkUuid,
                    resource.getVariantNum()
                );
                map.put(info, resource.getAttributes().getRegulatingPoint());
            }
        }
        return map;
    }

    protected Map<RegulatingOwnerInfo, RegulatingPointAttributes> getRegulatingPointFromTwoWindingTransformers(UUID networkUuid, List<Resource<TwoWindingsTransformerAttributes>> twoWindingTransformers) {
        Map<RegulatingOwnerInfo, RegulatingPointAttributes> map = new HashMap<>();
        if (!twoWindingTransformers.isEmpty()) {
            for (Resource<TwoWindingsTransformerAttributes> twoWindingsTransformer : twoWindingTransformers) {
                RatioTapChangerAttributes ratioTapChangerAttributes = twoWindingsTransformer.getAttributes().getRatioTapChangerAttributes();
                PhaseTapChangerAttributes phaseTapChangerAttributes = twoWindingsTransformer.getAttributes().getPhaseTapChangerAttributes();
                addTapChangerRegulatingPoint(ratioTapChangerAttributes, phaseTapChangerAttributes, RegulatingTapChangerType.RATIO_TAP_CHANGER,
                    RegulatingTapChangerType.PHASE_TAP_CHANGER, map, twoWindingsTransformer.getId(),
                    ResourceType.TWO_WINDINGS_TRANSFORMER, networkUuid, twoWindingsTransformer.getVariantNum());
            }
        }
        return map;
    }

    protected Map<RegulatingOwnerInfo, RegulatingPointAttributes> getRegulatingPointFromThreeWindingTransformers(UUID networkUuid, List<Resource<ThreeWindingsTransformerAttributes>> threeWindingTransformers) {
        Map<RegulatingOwnerInfo, RegulatingPointAttributes> map = new HashMap<>();
        if (!threeWindingTransformers.isEmpty()) {
            for (Resource<ThreeWindingsTransformerAttributes> threeWindingsTransformer : threeWindingTransformers) {
                for (ThreeSides side : ThreeSides.values()) {
                    LegAttributes legAttributes = threeWindingsTransformer.getAttributes().getLeg(side.getNum());
                    if (legAttributes != null) {
                        RatioTapChangerAttributes ratioTapChangerAttributes = legAttributes.getRatioTapChangerAttributes();
                        PhaseTapChangerAttributes phaseTapChangerAttributes = legAttributes.getPhaseTapChangerAttributes();
                        addTapChangerRegulatingPoint(ratioTapChangerAttributes, phaseTapChangerAttributes,
                            RegulatingTapChangerType.getThreeWindingsTransformerTapChangerType(side, RegulatingTapChangerType.RATIO_TAP_CHANGER),
                            RegulatingTapChangerType.getThreeWindingsTransformerTapChangerType(side, RegulatingTapChangerType.PHASE_TAP_CHANGER),
                            map, threeWindingsTransformer.getId(), ResourceType.THREE_WINDINGS_TRANSFORMER, networkUuid, threeWindingsTransformer.getVariantNum());
                    }
                }
            }
        }
        return map;
    }

    private void addTapChangerRegulatingPoint(RatioTapChangerAttributes ratioTapChangerAttributes, PhaseTapChangerAttributes phaseTapChangerAttributes,
                                              RegulatingTapChangerType ratioRegulatingTapChangerType, RegulatingTapChangerType phaseRegulatingTapChangerType,
                                              Map<RegulatingOwnerInfo, RegulatingPointAttributes> map, String transformerId, ResourceType resourceType,
                                              UUID networkUuid, int variantNum) {
        if (ratioTapChangerAttributes != null) {
            RegulatingOwnerInfo ratioTapChangerRegulatingOwnerInfo = new RegulatingOwnerInfo(
                transformerId, resourceType,
                ratioRegulatingTapChangerType, networkUuid, variantNum);
            map.put(ratioTapChangerRegulatingOwnerInfo, ratioTapChangerAttributes.getRegulatingPoint());
        }
        if (phaseTapChangerAttributes != null) {
            RegulatingOwnerInfo phaseTapChangerRegulatingOwnerInfo = new RegulatingOwnerInfo(
                transformerId, resourceType,
                phaseRegulatingTapChangerType, networkUuid, variantNum);
            map.put(phaseTapChangerRegulatingOwnerInfo, phaseTapChangerAttributes.getRegulatingPoint());
        }
    }

    private Map<RegulatingOwnerInfo, RegulatingPointAttributes> innerGetRegulatingPoints(PreparedStatement preparedStmt, ResourceType type, int variantNumOverride) throws SQLException {
        try (ResultSet resultSet = preparedStmt.executeQuery()) {
            Map<RegulatingOwnerInfo, RegulatingPointAttributes> map = new HashMap<>();
            while (resultSet.next()) {
                RegulatingOwnerInfo owner = new RegulatingOwnerInfo();
                RegulatingPointAttributes regulatingPointAttributes = new RegulatingPointAttributes();
                // In order, from the QueryCatalog.buildRegulatingPointQuery SQL query :
                // equipmentId, networkUuid, variantNum, regulatingEquipmentId, localTerminal and regulatingTerminal
                String regulatingEquipmentId = resultSet.getString(3);
                owner.setEquipmentId(regulatingEquipmentId);
                owner.setNetworkUuid(UUID.fromString(resultSet.getString(1)));
                owner.setVariantNum(variantNumOverride);
                owner.setEquipmentType(type);
                String regulatingTapChangerType = resultSet.getString(4);
                // regulatingTapChangerType can not be null because it is part of primary key of table RegulatingPoint
                // it will be NONE for injection
                owner.setRegulatingTapChangerType(RegulatingTapChangerType.valueOf(regulatingTapChangerType));
                regulatingPointAttributes.setRegulatingEquipmentId(regulatingEquipmentId);
                regulatingPointAttributes.setRegulationMode(resultSet.getString(5));
                regulatingPointAttributes.setRegulatingResourceType(type);
                regulatingPointAttributes.setRegulatingTapChangerType(RegulatingTapChangerType.valueOf(regulatingTapChangerType));
                Optional<String> localConnectableId = Optional.ofNullable(resultSet.getString(6));
                if (localConnectableId.isPresent()) {
                    regulatingPointAttributes.setLocalTerminal(new TerminalRefAttributes(localConnectableId.get(), resultSet.getString(7)));
                }
                Optional<String> regulatingConnectableId = Optional.ofNullable(resultSet.getString(8));
                if (regulatingConnectableId.isPresent()) {
                    regulatingPointAttributes.setRegulatingTerminal(new TerminalRefAttributes(resultSet.getString(8), resultSet.getString(9)));
                }
                regulatingPointAttributes.setRegulating(resultSet.getBoolean(10));
                map.put(owner, regulatingPointAttributes);
            }
            return map;
        }
    }

    public Map<OwnerInfo, Set<RegulatingEquipmentIdentifier>> getRegulatingEquipments(UUID networkUuid, int variantNum, ResourceType type) {
        try (var connection = dataSource.getConnection()) {
            return PartialVariantUtils.getRegulatingEquipments(
                    variantNum,
                    getNetworkAttributes(connection, networkUuid, variantNum, mappings, mapper).getFullVariantNum(),
                    () -> getTombstonedRegulatingPointsIds(connection, networkUuid, variantNum),
                    () -> getTombstonedIdentifiableIds(connection, networkUuid, variantNum),
                    () -> getRegulatingPointsIdentifiableIdsForVariant(connection, networkUuid, variantNum),
                    variant -> getRegulatingEquipmentsForVariant(connection, networkUuid, variant, type, variantNum));
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private Map<OwnerInfo, Set<RegulatingEquipmentIdentifier>> getRegulatingEquipmentsForVariant(Connection connection, UUID networkUuid, int variantNum, ResourceType type, int variantNumOverride) {
        try (var preparedStmt = connection.prepareStatement(buildRegulatingEquipmentsQuery())) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            preparedStmt.setString(3, type.toString());

            return innerGetRegulatingEquipments(preparedStmt, type, variantNumOverride);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public Map<OwnerInfo, Set<RegulatingEquipmentIdentifier>> getRegulatingEquipmentsWithInClause(UUID networkUuid, int variantNum, String columnNameForWhereClause, List<String> valuesForInClause, ResourceType type) {
        if (valuesForInClause.isEmpty()) {
            return Collections.emptyMap();
        }
        try (var connection = dataSource.getConnection()) {
            return PartialVariantUtils.getRegulatingEquipments(
                    variantNum,
                    getNetworkAttributes(connection, networkUuid, variantNum, mappings, mapper).getFullVariantNum(),
                    () -> getTombstonedRegulatingPointsIds(connection, networkUuid, variantNum),
                    () -> getTombstonedIdentifiableIds(connection, networkUuid, variantNum),
                    () -> getRegulatingPointsIdentifiableIdsForVariant(connection, networkUuid, variantNum),
                    variant -> getRegulatingEquipmentsWithInClauseForVariant(connection, networkUuid, variant, columnNameForWhereClause, valuesForInClause, type, variantNum));
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private Map<OwnerInfo, Set<RegulatingEquipmentIdentifier>> getRegulatingEquipmentsWithInClauseForVariant(Connection connection, UUID networkUuid, int variantNum, String columnNameForWhereClause, List<String> valuesForInClause, ResourceType type, int variantNumOverride) {
        try (var preparedStmt = connection.prepareStatement(buildRegulatingEquipmentsWithInClauseQuery(columnNameForWhereClause, valuesForInClause.size()))) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            preparedStmt.setString(3, type.toString());
            for (int i = 0; i < valuesForInClause.size(); i++) {
                preparedStmt.setString(4 + i, valuesForInClause.get(i));
            }

            return innerGetRegulatingEquipments(preparedStmt, type, variantNumOverride);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public Map<OwnerInfo, Set<RegulatingEquipmentIdentifier>> innerGetRegulatingEquipments(PreparedStatement preparedStmt, ResourceType type, int variantNumOverride) throws SQLException {
        try (ResultSet resultSet = preparedStmt.executeQuery()) {
            Map<OwnerInfo, Set<RegulatingEquipmentIdentifier>> map = new HashMap<>();
            while (resultSet.next()) {
                OwnerInfo owner = new OwnerInfo();
                String regulatingEquipmentId = resultSet.getString(3);
                String regulatedConnectableId = resultSet.getString(4);
                ResourceType regulatingEquipmentType = ResourceType.valueOf(resultSet.getString(5));
                owner.setEquipmentId(regulatedConnectableId);
                owner.setNetworkUuid(UUID.fromString(resultSet.getString(1)));
                owner.setVariantNum(variantNumOverride);
                owner.setEquipmentType(type);
                String regulatingTapChangerType = resultSet.getString(6);
                RegulatingEquipmentIdentifier identifier = new RegulatingEquipmentIdentifier(regulatingEquipmentId, regulatingEquipmentType, RegulatingTapChangerType.valueOf(regulatingTapChangerType));
                if (map.containsKey(owner)) {
                    map.get(owner).add(identifier);
                } else {
                    Set<RegulatingEquipmentIdentifier> regulatedEquipmentIds = new HashSet<>();
                    regulatedEquipmentIds.add(identifier);
                    map.put(owner, regulatedEquipmentIds);
                }
            }
            return map;
        }
    }

    private <T extends IdentifiableAttributes> void insertRegulatingEquipmentsInto(UUID networkUuid, int variantNum,
                                                                                   String equipmentId, Resource<T> resource,
                                                                                   ResourceType type) {
        Set<RegulatingEquipmentIdentifier> regulatingEquipments = getRegulatingEquipmentsForIdentifiable(networkUuid, variantNum, equipmentId, type);
        IdentifiableAttributes identifiableAttributes = resource.getAttributes();
        if (identifiableAttributes instanceof RegulatedEquipmentAttributes regulatedEquipmentAttributes) {
            regulatedEquipmentAttributes.setRegulatingEquipments(regulatingEquipments);
        }
    }

    public Set<RegulatingEquipmentIdentifier> getRegulatingEquipmentsForIdentifiable(UUID networkUuid, int variantNum, String equipmentId, ResourceType type) {
        try (var connection = dataSource.getConnection()) {
            int fullVariantNum = getNetworkAttributes(connection, networkUuid, variantNum, mappings, mapper).getFullVariantNum();
            if (NetworkAttributes.isFullVariant(fullVariantNum)) {
                // If the variant is full, retrieve regulating equipments for the specified variant directly
                return getRegulatingEquipmentsForIdentifiableForVariant(connection, networkUuid, variantNum, equipmentId, type);
            }

            Set<RegulatingEquipmentIdentifier> regulatingEquipmentsByIdentifiableId = new HashSet<>();
            Set<String> updatedRegulatingPointsIds = getRegulatingPointsIdentifiableIdsForVariant(connection, networkUuid, variantNum);
            if (!isTombstonedIdentifiable(connection, networkUuid, variantNum, equipmentId) && !updatedRegulatingPointsIds.contains(equipmentId)) {
                // Add regulating equipments of identifiable from full variant if not tombstoned nor updated
                regulatingEquipmentsByIdentifiableId.addAll(getRegulatingEquipmentsForIdentifiableForVariant(connection, networkUuid, fullVariantNum, equipmentId, type));
            }

            // Remove tombstoned regulating points
            Set<String> tombstonedRegulatingPointsIds = getTombstonedRegulatingPointsIds(connection, networkUuid, variantNum);
            regulatingEquipmentsByIdentifiableId.removeIf(regulatingEquipmentIdentifier -> tombstonedRegulatingPointsIds.contains(regulatingEquipmentIdentifier.getEquipmentId()));

            // Retrieve regulating equipments in partial variant
            Set<RegulatingEquipmentIdentifier> partialVariantRegulatingEquipmentsByIdentifiableId = getRegulatingEquipmentsForIdentifiableForVariant(connection, networkUuid, variantNum, equipmentId, type);

            // Combine regulating equipments from full and partial variants
            regulatingEquipmentsByIdentifiableId.addAll(partialVariantRegulatingEquipmentsByIdentifiableId);
            return regulatingEquipmentsByIdentifiableId;
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private Set<RegulatingEquipmentIdentifier> getRegulatingEquipmentsForIdentifiableForVariant(Connection connection, UUID networkUuid, int variantNum, String equipmentId, ResourceType type) {
        Set<RegulatingEquipmentIdentifier> regulatingEquipments = new HashSet<>();
        try (var preparedStmt = connection.prepareStatement(QueryCatalog.buildRegulatingEquipmentsForOneEquipmentQuery())) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            preparedStmt.setString(3, type.toString());
            preparedStmt.setObject(4, equipmentId);
            regulatingEquipments.addAll(getRegulatingEquipments(preparedStmt));
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
        return regulatingEquipments;
    }

    private Set<RegulatingEquipmentIdentifier> getRegulatingEquipments(PreparedStatement preparedStmt) throws SQLException {
        try (ResultSet resultSet = preparedStmt.executeQuery()) {
            Set<RegulatingEquipmentIdentifier> regulatingEquipements = new HashSet<>();
            while (resultSet.next()) {
                String regulatingEquipmentId = resultSet.getString(1);
                ResourceType regulatingEquipmentType = ResourceType.valueOf(resultSet.getString(2));
                String regulatingTapChangerType = resultSet.getString(3);
                RegulatingEquipmentIdentifier identifier = new RegulatingEquipmentIdentifier(regulatingEquipmentId, regulatingEquipmentType, RegulatingTapChangerType.valueOf(regulatingTapChangerType));
                regulatingEquipements.add(identifier);
            }
            return regulatingEquipements;
        }
    }

    protected <T extends ReactiveLimitHolder & IdentifiableAttributes> Map<OwnerInfo, List<ReactiveCapabilityCurvePointAttributes>> getReactiveCapabilityCurvePointsFromEquipments(UUID networkUuid, List<Resource<T>> resources) {
        Map<OwnerInfo, List<ReactiveCapabilityCurvePointAttributes>> map = new HashMap<>();

        if (!resources.isEmpty()) {
            for (Resource<T> resource : resources) {

                ReactiveLimitsAttributes reactiveLimits = resource.getAttributes().getReactiveLimits();
                if (reactiveLimits != null
                        && reactiveLimits.getKind() == ReactiveLimitsKind.CURVE
                        && ((ReactiveCapabilityCurveAttributes) reactiveLimits).getPoints() != null) {

                    OwnerInfo info = new OwnerInfo(
                            resource.getId(),
                            resource.getType(),
                            networkUuid,
                            resource.getVariantNum()
                    );
                    map.put(info, new ArrayList<>(((ReactiveCapabilityCurveAttributes) reactiveLimits).getPoints().values()));
                }
            }
        }
        return map;
    }

    protected Map<OwnerInfo, List<AreaBoundaryAttributes>> getAreaBoundariesFromEquipments(UUID networkUuid, List<Resource<AreaAttributes>> resources) {
        Map<OwnerInfo, List<AreaBoundaryAttributes>> map = new HashMap<>();

        if (!resources.isEmpty()) {
            for (Resource<AreaAttributes> resource : resources) {
                if (resource.getAttributes().getAreaBoundaries() != null && !resource.getAttributes().getAreaBoundaries().isEmpty()) {
                    OwnerInfo info = new OwnerInfo(
                        resource.getId(),
                        null,
                        networkUuid,
                        resource.getVariantNum()
                    );
                    map.put(info, resource.getAttributes().getAreaBoundaries());
                }
            }
        }
        return map;
    }

    private <T extends AbstractRegulatingEquipmentAttributes> void insertRegulatingPointIntoInjection(UUID networkUuid, int variantNum, String equipmentId, Resource<T> resource, ResourceType resourceType) {
        Map<RegulatingOwnerInfo, RegulatingPointAttributes> regulatingPointAttributes = getRegulatingPointsWithInClause(networkUuid, variantNum,
            REGULATING_EQUIPMENT_ID, Collections.singletonList(equipmentId), resourceType);
        if (regulatingPointAttributes.size() != 1) {
            throw new PowsyblException("a regulating injection must have one regulating point");
        }
        regulatingPointAttributes.values().forEach(regulatingPointAttribute ->
                resource.getAttributes().setRegulatingPoint(regulatingPointAttribute));
    }

    private void insertRegulatingPointIntoTwoWindingsTransformer(UUID networkUuid, int variantNum, String equipmentId, Resource<TwoWindingsTransformerAttributes> resource) {
        Map<RegulatingOwnerInfo, RegulatingPointAttributes> twoWindingsTransformerRegulatingPointAttributes = getRegulatingPointsWithInClause(networkUuid, variantNum,
            REGULATING_EQUIPMENT_ID, Collections.singletonList(equipmentId), ResourceType.TWO_WINDINGS_TRANSFORMER);
        RatioTapChangerAttributes ratioTapChangerAttributes = resource.getAttributes().getRatioTapChangerAttributes();
        setTapChangerRegulatingPoint(ratioTapChangerAttributes, twoWindingsTransformerRegulatingPointAttributes, new RegulatingOwnerInfo(equipmentId,
            ResourceType.TWO_WINDINGS_TRANSFORMER, RegulatingTapChangerType.RATIO_TAP_CHANGER, networkUuid, variantNum));
        PhaseTapChangerAttributes phaseTapChangerAttributes = resource.getAttributes().getPhaseTapChangerAttributes();
        setTapChangerRegulatingPoint(phaseTapChangerAttributes, twoWindingsTransformerRegulatingPointAttributes, new RegulatingOwnerInfo(equipmentId,
            ResourceType.TWO_WINDINGS_TRANSFORMER, RegulatingTapChangerType.PHASE_TAP_CHANGER, networkUuid, variantNum));
    }

    private void insertRegulatingPointIntoThreeWindingsTransformer(UUID networkUuid, int variantNum, String equipmentId, Resource<ThreeWindingsTransformerAttributes> resource) {
        Map<RegulatingOwnerInfo, RegulatingPointAttributes> threeWindingsTransformerRegulatingPointAttributes = getRegulatingPointsWithInClause(networkUuid, variantNum,
            REGULATING_EQUIPMENT_ID, Collections.singletonList(equipmentId), ResourceType.THREE_WINDINGS_TRANSFORMER);
        for (ThreeSides side : ThreeSides.values()) {
            RatioTapChangerAttributes ratioTapChangerAttributes = resource.getAttributes().getLeg(side.getNum()).getRatioTapChangerAttributes();
            setTapChangerRegulatingPoint(ratioTapChangerAttributes, threeWindingsTransformerRegulatingPointAttributes, new RegulatingOwnerInfo(equipmentId, ResourceType.THREE_WINDINGS_TRANSFORMER,
                RegulatingTapChangerType.getThreeWindingsTransformerTapChangerType(side, RegulatingTapChangerType.RATIO_TAP_CHANGER), networkUuid, variantNum));
            PhaseTapChangerAttributes phaseTapChangerAttributes = resource.getAttributes().getLeg(side.getNum()).getPhaseTapChangerAttributes();
            setTapChangerRegulatingPoint(phaseTapChangerAttributes, threeWindingsTransformerRegulatingPointAttributes, new RegulatingOwnerInfo(equipmentId, ResourceType.THREE_WINDINGS_TRANSFORMER,
                RegulatingTapChangerType.getThreeWindingsTransformerTapChangerType(side, RegulatingTapChangerType.PHASE_TAP_CHANGER), networkUuid, variantNum));
        }
    }

    private void setTapChangerRegulatingPoint(TapChangerAttributes tapChangerAttributes, Map<RegulatingOwnerInfo, RegulatingPointAttributes> transformerRegulatingPointAttributes, RegulatingOwnerInfo regulatingOwnerInfo) {
        if (tapChangerAttributes != null) {
            tapChangerAttributes.setRegulatingPoint(
                transformerRegulatingPointAttributes.get(regulatingOwnerInfo)
            );
        }
    }

    protected <T extends ReactiveLimitHolder & IdentifiableAttributes> void insertReactiveCapabilityCurvePointsInEquipments(UUID networkUuid, List<Resource<T>> equipments, Map<OwnerInfo, List<ReactiveCapabilityCurvePointAttributes>> reactiveCapabilityCurvePoints) {

        if (!reactiveCapabilityCurvePoints.isEmpty() && !equipments.isEmpty()) {
            for (Resource<T> equipmentAttributesResource : equipments) {
                OwnerInfo owner = new OwnerInfo(
                        equipmentAttributesResource.getId(),
                        equipmentAttributesResource.getType(),
                        networkUuid,
                        equipmentAttributesResource.getVariantNum()
                );
                if (reactiveCapabilityCurvePoints.containsKey(owner)) {
                    T equipment = equipmentAttributesResource.getAttributes();
                    for (ReactiveCapabilityCurvePointAttributes reactiveCapabilityCurvePoint : reactiveCapabilityCurvePoints.get(owner)) {
                        insertReactiveCapabilityCurvePointInEquipment(equipment, reactiveCapabilityCurvePoint);
                    }
                }
            }
        }
    }

    private <T extends ReactiveLimitHolder> void insertReactiveCapabilityCurvePointInEquipment(T equipment, ReactiveCapabilityCurvePointAttributes reactiveCapabilityCurvePoint) {

        if (equipment.getReactiveLimits() == null) {
            equipment.setReactiveLimits(new ReactiveCapabilityCurveAttributes());
        }
        ReactiveLimitsAttributes reactiveLimitsAttributes = equipment.getReactiveLimits();
        if (reactiveLimitsAttributes instanceof ReactiveCapabilityCurveAttributes reactiveCapabilityCurveAttributes) {
            if (reactiveCapabilityCurveAttributes.getPoints() == null) {
                reactiveCapabilityCurveAttributes.setPoints(new TreeMap<>());
            }
            reactiveCapabilityCurveAttributes.getPoints().put(reactiveCapabilityCurvePoint.getP(), reactiveCapabilityCurvePoint);
        }
    }

    private void deleteReactiveCapabilityCurvePoints(UUID networkUuid, int variantNum, List<String> equipmentIds) {
        try (var connection = dataSource.getConnection()) {
            try (var preparedStmt = connection.prepareStatement(QueryCatalog.buildDeleteReactiveCapabilityCurvePointsVariantEquipmentINQuery(equipmentIds.size()))) {
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

    private <T extends IdentifiableAttributes> void deleteReactiveCapabilityCurvePoints(UUID networkUuid, List<Resource<T>> resources) {
        Map<Integer, List<String>> resourceIdsByVariant = new HashMap<>();
        for (Resource<T> resource : resources) {
            List<String> resourceIds = resourceIdsByVariant.get(resource.getVariantNum());
            if (resourceIds != null) {
                resourceIds.add(resource.getId());
            } else {
                resourceIds = new ArrayList<>();
                resourceIds.add(resource.getId());
            }
            resourceIdsByVariant.put(resource.getVariantNum(), resourceIds);
        }
        resourceIdsByVariant.forEach((k, v) -> deleteReactiveCapabilityCurvePoints(networkUuid, k, v));
    }

    // area boundaries
    protected void insertAreaBoundariesInAreas(UUID networkUuid, List<Resource<AreaAttributes>> areas, Map<OwnerInfo, List<AreaBoundaryAttributes>> areaBoundaries) {

        if (!areaBoundaries.isEmpty() && !areas.isEmpty()) {
            for (Resource<AreaAttributes> areaResource : areas) {
                OwnerInfo owner = new OwnerInfo(
                    areaResource.getId(),
                    null,
                    networkUuid,
                    areaResource.getVariantNum()
                );
                if (areaBoundaries.containsKey(owner)) {
                    AreaAttributes area = areaResource.getAttributes();
                    area.setAreaBoundaries(areaBoundaries.get(owner));
                }
            }
        }
    }

    private void deleteAreaBoundaries(UUID networkUuid, int variantNum, List<String> areaIds) {
        try (var connection = dataSource.getConnection()) {
            try (var preparedStmt = connection.prepareStatement(QueryCatalog.buildDeleteAreaBoundariesVariantEquipmentINQuery(areaIds.size()))) {
                preparedStmt.setObject(1, networkUuid);
                preparedStmt.setInt(2, variantNum);
                for (int i = 0; i < areaIds.size(); i++) {
                    preparedStmt.setString(3 + i, areaIds.get(i));
                }
                preparedStmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private void deleteAreaBoundaries(UUID networkUuid, List<Resource<AreaAttributes>> resources) {
        Map<Integer, List<String>> resourceIdsByVariant = new HashMap<>();
        for (Resource<AreaAttributes> resource : resources) {
            List<String> resourceIds = resourceIdsByVariant.get(resource.getVariantNum());
            if (resourceIds != null) {
                resourceIds.add(resource.getId());
            } else {
                resourceIds = new ArrayList<>();
                resourceIds.add(resource.getId());
            }
            resourceIdsByVariant.put(resource.getVariantNum(), resourceIds);
        }
        resourceIdsByVariant.forEach((k, v) -> deleteAreaBoundaries(networkUuid, k, v));
    }

    // TapChanger Steps
    public Map<OwnerInfo, List<TapChangerStepAttributes>> getTapChangerStepsWithInClause(UUID networkUuid, int variantNum, String columnNameForWhereClause, List<String> valuesForInClause) {
        try (var connection = dataSource.getConnection()) {
            return PartialVariantUtils.getExternalAttributes(
                    variantNum,
                    getNetworkAttributes(connection, networkUuid, variantNum, mappings, mapper).getFullVariantNum(),
                    () -> getTombstonedTapChangerStepsIds(connection, networkUuid, variantNum),
                    () -> getTombstonedIdentifiableIds(connection, networkUuid, variantNum),
                variant -> getTapChangerStepsWithInClauseForVariant(connection, networkUuid, variant, columnNameForWhereClause, valuesForInClause, variantNum),
                    OwnerInfo::getEquipmentId);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private Map<OwnerInfo, List<TapChangerStepAttributes>> getTapChangerStepsWithInClauseForVariant(Connection connection, UUID networkUuid, int variantNum, String columnNameForWhereClause, List<String> valuesForInClause, int variantNumOverride) {
        if (valuesForInClause.isEmpty()) {
            return Collections.emptyMap();
        }
        return getTapChangerStepsWithInClause(connection, networkUuid, variantNum, columnNameForWhereClause, valuesForInClause, variantNumOverride);
    }

    private Map<OwnerInfo, List<TapChangerStepAttributes>> getTapChangerStepsWithInClause(Connection connection, UUID networkUuid, int variantNum, String columnNameForWhereClause, List<String> valuesForInClause, int variantNumOverride) {
        try (var preparedStmt = connection.prepareStatement(buildTapChangerStepWithInClauseQuery(columnNameForWhereClause, valuesForInClause.size()))) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            for (int i = 0; i < valuesForInClause.size(); i++) {
                preparedStmt.setString(3 + i, valuesForInClause.get(i));
            }
            return innerGetTapChangerSteps(preparedStmt, variantNumOverride);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public Map<OwnerInfo, List<TapChangerStepAttributes>> getTapChangerSteps(UUID networkUuid, int variantNum, String columnNameForWhereClause, String valueForWhereClause) {
        try (var connection = dataSource.getConnection()) {
            return PartialVariantUtils.getExternalAttributes(
                    variantNum,
                    getNetworkAttributes(connection, networkUuid, variantNum, mappings, mapper).getFullVariantNum(),
                    () -> getTombstonedTapChangerStepsIds(connection, networkUuid, variantNum),
                    () -> getTombstonedIdentifiableIds(connection, networkUuid, variantNum),
                    variant -> getTapChangerStepsForVariant(connection, networkUuid, variant, columnNameForWhereClause, valueForWhereClause, variantNum),
                    OwnerInfo::getEquipmentId);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public Map<OwnerInfo, List<TapChangerStepAttributes>> getTapChangerStepsForVariant(Connection connection, UUID networkUuid, int variantNum, String columnNameForWhereClause, String valueForWhereClause, int variantNumOverride) {
        try (var preparedStmt = connection.prepareStatement(QueryCatalog.buildTapChangerStepQuery(columnNameForWhereClause))) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            preparedStmt.setString(3, valueForWhereClause);

            return innerGetTapChangerSteps(preparedStmt, variantNumOverride);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private Map<OwnerInfo, List<TapChangerStepAttributes>> innerGetTapChangerSteps(PreparedStatement preparedStmt, int variantNumOverride) throws SQLException {
        try (ResultSet resultSet = preparedStmt.executeQuery()) {
            Map<OwnerInfo, List<TapChangerStepAttributes>> map = new HashMap<>();
            while (resultSet.next()) {

                OwnerInfo owner = new OwnerInfo();
                // In order, from the QueryCatalog.buildTapChangerStepQuery SQL query :
                // equipmentId, equipmentType, networkUuid, variantNum, tapChangerType, tapchangers (info to be parsed)
                owner.setEquipmentId(resultSet.getString(1));
                owner.setEquipmentType(ResourceType.valueOf(resultSet.getString(2)));
                owner.setNetworkUuid(resultSet.getObject(3, UUID.class));
                owner.setVariantNum(variantNumOverride);

                TapChangerType tapChangerType = TapChangerType.valueOf(resultSet.getString(5));
                String tapChangerStepData = resultSet.getString(6);
                List<TapChangerStepSqlData> parsedTapChangerStepSqlData = mapper.readValue(tapChangerStepData, new TypeReference<>() { });
                List<TapChangerStepAttributes> tapChangerStepAttributesList = parsedTapChangerStepSqlData.stream()
                    .map(data -> data.toTapChangerStepAttributes(tapChangerType)).collect(Collectors.toList());
                if (!tapChangerStepAttributesList.isEmpty()) {
                    if (map.containsKey(owner)) {
                        map.get(owner).addAll(tapChangerStepAttributesList);
                    } else {
                        map.put(owner, tapChangerStepAttributesList);
                    }
                }
            }
            return map;
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private <T extends IdentifiableAttributes> List<TapChangerStepAttributes> getTapChangerSteps(T equipment) {
        if (equipment instanceof TwoWindingsTransformerAttributes twoWindingsTransformerAttributes) {
            return getTapChangerStepsTwoWindingsTransformer(twoWindingsTransformerAttributes);
        }
        if (equipment instanceof ThreeWindingsTransformerAttributes threeWindingsTransformerAttributes) {
            return getTapChangerStepsThreeWindingsTransformer(threeWindingsTransformerAttributes);
        }
        throw new UnsupportedOperationException("equipmentAttributes type invalid");
    }

    private List<TapChangerStepAttributes> getTapChangerStepsTwoWindingsTransformer(TwoWindingsTransformerAttributes equipment) {
        List<TapChangerStepAttributes> steps = new ArrayList<>();
        steps.addAll(getTapChangerStepsFromTapChangerAttributes(equipment.getRatioTapChangerAttributes(), 0, RATIO));
        steps.addAll(getTapChangerStepsFromTapChangerAttributes(equipment.getPhaseTapChangerAttributes(), 0, PHASE));
        return steps;
    }

    private List<TapChangerStepAttributes> getTapChangerStepsThreeWindingsTransformer(ThreeWindingsTransformerAttributes equipment) {
        List<TapChangerStepAttributes> steps = new ArrayList<>();
        for (Integer legNum : Set.of(1, 2, 3)) {
            steps.addAll(getTapChangerStepsFromTapChangerAttributes(equipment.getLeg(legNum).getRatioTapChangerAttributes(), legNum, RATIO));
            steps.addAll(getTapChangerStepsFromTapChangerAttributes(equipment.getLeg(legNum).getPhaseTapChangerAttributes(), legNum, PHASE));
        }
        return steps;
    }

    private List<TapChangerStepAttributes> getTapChangerStepsFromTapChangerAttributes(TapChangerAttributes tapChanger, Integer side, TapChangerType type) {
        if (tapChanger != null && tapChanger.getSteps() != null) {
            List<TapChangerStepAttributes> steps = tapChanger.getSteps();
            for (int i = 0; i < steps.size(); i++) {
                steps.get(i).setIndex(i);
                steps.get(i).setSide(side);
                steps.get(i).setType(type);
            }
            return steps;
        }
        return Collections.emptyList();
    }

    private <T extends IdentifiableAttributes>
        Map<OwnerInfo, List<TapChangerStepAttributes>> getTapChangerStepsFromEquipment(UUID networkUuid, List<Resource<T>> resources) {
        if (!resources.isEmpty()) {
            Map<OwnerInfo, List<TapChangerStepAttributes>> map = new HashMap<>();
            for (Resource<T> resource : resources) {
                T equipment = resource.getAttributes();
                List<TapChangerStepAttributes> steps = getTapChangerSteps(equipment);
                if (!steps.isEmpty()) {
                    OwnerInfo info = new OwnerInfo(
                        resource.getId(),
                        resource.getType(),
                        networkUuid,
                        resource.getVariantNum()
                    );
                    map.put(info, steps);
                }
            }
            return map;
        }
        return Collections.emptyMap();
    }

    public <T extends TapChangerStepAttributes> void insertTapChangerSteps(Map<OwnerInfo, List<T>> tapChangerSteps) {
        try (var connection = dataSource.getConnection()) {
            try (var preparedStmt = connection.prepareStatement(QueryCatalog.buildInsertTapChangerStepQuery())) {
                List<Object> values = new ArrayList<>(6);

                List<Map.Entry<OwnerInfo, List<T>>> list = new ArrayList<>(tapChangerSteps.entrySet());
                for (List<Map.Entry<OwnerInfo, List<T>>> subTapChangerSteps : Lists.partition(list, BATCH_SIZE)) {
                    for (Map.Entry<OwnerInfo, List<T>> entry : subTapChangerSteps) {
                        addTapChangerBatch(values, entry, preparedStmt, RATIO);
                        addTapChangerBatch(values, entry, preparedStmt, PHASE);
                    }
                    preparedStmt.executeBatch();
                }
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private <T extends TapChangerStepAttributes> void addTapChangerBatch(List<Object> values, Map.Entry<OwnerInfo, List<T>> entry,
                                                                         PreparedStatement preparedStmt, TapChangerType type) throws SQLException {
        List<TapChangerStepSqlData> tapChangerStepSqlDataList = entry.getValue().stream().filter(tapChanger -> tapChanger.getType().equals(type))
            .map(TapChangerStepSqlData::of).toList();
        if (!tapChangerStepSqlDataList.isEmpty()) {
            values.clear();
            values.add(entry.getKey().getEquipmentId());
            values.add(entry.getKey().getEquipmentType().toString());
            values.add(entry.getKey().getNetworkUuid());
            values.add(entry.getKey().getVariantNum());
            values.add(type.toString());
            values.add(tapChangerStepSqlDataList);
            bindValues(preparedStmt, values, mapper);
            preparedStmt.addBatch();
        }
    }

    protected <T extends IdentifiableAttributes> void insertTapChangerStepsInEquipments(UUID networkUuid,
                                                                                        List<Resource<T>> equipments,
                                                                                        Map<OwnerInfo, List<TapChangerStepAttributes>> tapChangerSteps) {

        if (tapChangerSteps.isEmpty() || equipments.isEmpty()) {
            return;
        }
        for (Resource<T> equipmentAttributesResource : equipments) {
            OwnerInfo owner = new OwnerInfo(
                equipmentAttributesResource.getId(),
                equipmentAttributesResource.getType(),
                networkUuid,
                equipmentAttributesResource.getVariantNum()
            );
            if (!tapChangerSteps.containsKey(owner)) {
                continue;
            }

            T equipment = equipmentAttributesResource.getAttributes();
            if (equipment instanceof TwoWindingsTransformerAttributes twoWindingsTransformerAttributes) {
                for (TapChangerStepAttributes tapChangerStep : tapChangerSteps.get(owner)) {
                    insertTapChangerStepInEquipment(twoWindingsTransformerAttributes, tapChangerStep);
                }
            } else if (equipment instanceof ThreeWindingsTransformerAttributes threeWindingsTransformerAttributes) {
                for (TapChangerStepAttributes tapChangerStep : tapChangerSteps.get(owner)) {
                    LegAttributes leg = threeWindingsTransformerAttributes.getLeg(tapChangerStep.getSide());
                    insertTapChangerStepInEquipment(leg, tapChangerStep);
                }
            }
        }
    }

    private <T extends TapChangerParentAttributes> void insertTapChangerStepInEquipment(T tapChangerParent, TapChangerStepAttributes tapChangerStep) {
        if (tapChangerStep == null) {
            return;
        }
        TapChangerType type = tapChangerStep.getType();

        if (type == RATIO) {
            if (tapChangerParent.getRatioTapChangerAttributes() == null) {
                tapChangerParent.setRatioTapChangerAttributes(new RatioTapChangerAttributes());
            }
            if (tapChangerParent.getRatioTapChangerAttributes().getSteps() == null) {
                tapChangerParent.getRatioTapChangerAttributes().setSteps(new ArrayList<>());
            }
            tapChangerParent.getRatioTapChangerAttributes().getSteps().add(tapChangerStep); // check side value here ?
        } else { // PHASE
            if (tapChangerParent.getPhaseTapChangerAttributes() == null) {
                tapChangerParent.setPhaseTapChangerAttributes(new PhaseTapChangerAttributes());
            }
            if (tapChangerParent.getPhaseTapChangerAttributes().getSteps() == null) {
                tapChangerParent.getPhaseTapChangerAttributes().setSteps(new ArrayList<>());
            }
            tapChangerParent.getPhaseTapChangerAttributes().getSteps().add(tapChangerStep);
        }
    }

    private void deleteTapChangerSteps(UUID networkUuid, int variantNum, List<String> equipmentIds) {
        try (var connection = dataSource.getConnection()) {

            try (var preparedStmt = connection.prepareStatement(QueryCatalog.buildDeleteTapChangerStepVariantEquipmentINQuery(equipmentIds.size()))) {
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

    private <T extends IdentifiableAttributes> void deleteTapChangerSteps(UUID networkUuid, List<Resource<T>> resources) {
        Map<Integer, List<String>> resourceIdsByVariant = new HashMap<>();
        for (Resource<T> resource : resources) {
            List<String> resourceIds = resourceIdsByVariant.get(resource.getVariantNum());
            if (resourceIds != null) {
                resourceIds.add(resource.getId());
            } else {
                resourceIds = new ArrayList<>();
                resourceIds.add(resource.getId());
            }
            resourceIdsByVariant.put(resource.getVariantNum(), resourceIds);
        }
        resourceIdsByVariant.forEach((k, v) -> deleteTapChangerSteps(networkUuid, k, v));
    }

    public Optional<ExtensionAttributes> getExtensionAttributes(UUID networkId, int variantNum, String identifiableId, String extensionName) {
        try (var connection = dataSource.getConnection()) {
            int fullVariantNum = getNetworkAttributes(connection, networkId, variantNum, mappings, mapper).getFullVariantNum();
            return extensionHandler.getExtensionAttributes(
                    connection,
                    networkId,
                    variantNum,
                    identifiableId,
                    extensionName,
                    fullVariantNum,
                    () -> isTombstonedIdentifiable(connection, networkId, variantNum, identifiableId));
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public Map<String, ExtensionAttributes> getAllExtensionsAttributesByResourceTypeAndExtensionName(UUID networkId, int variantNum, ResourceType type, String extensionName) {
        try (var connection = dataSource.getConnection()) {
            int fullVariantNum = getNetworkAttributes(connection, networkId, variantNum, mappings, mapper).getFullVariantNum();
            return extensionHandler.getAllExtensionsAttributesByResourceTypeAndExtensionName(
                    connection,
                    networkId,
                    variantNum,
                    type.toString(),
                    extensionName,
                    fullVariantNum,
                    () -> getTombstonedIdentifiableIds(connection, networkId, variantNum));
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public Map<String, ExtensionAttributes> getAllExtensionsAttributesByIdentifiableId(UUID networkId, int variantNum, String identifiableId) {
        try (var connection = dataSource.getConnection()) {
            int fullVariantNum = getNetworkAttributes(connection, networkId, variantNum, mappings, mapper).getFullVariantNum();
            return extensionHandler.getAllExtensionsAttributesByIdentifiableId(
                    connection,
                    networkId,
                    variantNum,
                    identifiableId,
                    fullVariantNum,
                    () -> isTombstonedIdentifiable(connection, networkId, variantNum, identifiableId));
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public Map<String, Map<String, ExtensionAttributes>> getAllExtensionsAttributesByResourceType(UUID networkId, int variantNum, ResourceType type) {
        try (var connection = dataSource.getConnection()) {
            int fullVariantNum = getNetworkAttributes(connection, networkId, variantNum, mappings, mapper).getFullVariantNum();
            return extensionHandler.getAllExtensionsAttributesByResourceType(
                    connection,
                    networkId,
                    variantNum,
                    type,
                    fullVariantNum,
                    () -> getTombstonedIdentifiableIds(connection, networkId, variantNum));
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public void removeExtensionAttributes(UUID networkId, int variantNum, String identifiableId, String extensionName) {
        try (var connection = dataSource.getConnection()) {
            boolean isPartialVariant = !getNetworkAttributes(connection, networkId, variantNum, mappings, mapper).isFullVariant();
            extensionHandler.deleteAndTombstoneExtensions(connection, networkId, variantNum, Map.of(extensionName, Set.of(identifiableId)), isPartialVariant);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    // operational limits groups
    public Optional<OperationalLimitsGroupAttributes> getOperationalLimitsGroup(UUID networkId, int variantNum, String branchId, ResourceType type, String operationalLimitsGroupName, int side) {
        return limitsHandler.getOperationalLimitsGroup(networkId, variantNum, branchId, type, operationalLimitsGroupName, side);
    }

    public Map<OperationalLimitsGroupIdentifier, OperationalLimitsGroupAttributes> getAllOperationalLimitsGroupAttributesByResourceType(
        UUID networkId, int variantNum, ResourceType type) {
        Map<OwnerInfo, LimitsInfos> limitsInfos = limitsHandler.getLimitsInfos(networkId, variantNum, EQUIPMENT_TYPE_COLUMN, type.toString());
        Map<OperationalLimitsGroupIdentifier, OperationalLimitsGroupAttributes> map = new HashMap<>();
        limitsInfos.forEach((owner, limitsInfo) ->
            map.putAll(limitsHandler.convertLimitInfosToOperationalLimitsGroupMap(owner.getEquipmentId(), limitsInfo)));
        return map;
    }

    public Map<OperationalLimitsGroupIdentifier, OperationalLimitsGroupAttributes> getAllSelectedOperationalLimitsGroupAttributesByResourceType(
        UUID networkId, int variantNum, ResourceType type) {
        try (var connection = dataSource.getConnection()) {
            int fullVariantNum = getNetworkAttributes(connection, networkId, variantNum, mappings, mapper).getFullVariantNum();
            return limitsHandler.getAllSelectedOperationalLimitsGroupAttributesByResourceType(networkId, variantNum, type, fullVariantNum,
                getTombstonedIdentifiableIds(connection, networkId, variantNum));
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public List<OperationalLimitsGroupAttributes> getOperationalLimitsGroupAttributesForBranchSide(
        UUID networkId, int variantNum, ResourceType type, String branchId, int side) {
        return limitsHandler.getAllOperationalLimitsGroupAttributesForBranchSide(networkId, variantNum, type, branchId, side);
    }

    public Optional<Resource<NetworkAttributes>> getNetwork(UUID uuid, int variantNum) {
        return Utils.getNetwork(uuid, variantNum, dataSource, mappings, mapper);
    }
}
