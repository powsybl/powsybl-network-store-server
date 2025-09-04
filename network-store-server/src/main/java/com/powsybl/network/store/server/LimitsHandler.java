/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.powsybl.network.store.model.*;
import com.powsybl.network.store.server.dto.OperationalLimitsGroupOwnerInfo;
import com.powsybl.network.store.server.dto.OwnerInfo;
import com.powsybl.network.store.server.exceptions.UncheckedSqlException;
import com.powsybl.network.store.server.json.OperationalLimitsGroupAttributesSqlData;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import static com.powsybl.network.store.server.QueryCatalog.EQUIPMENT_ID_COLUMN;
import static com.powsybl.network.store.server.QueryLimitsCatalog.*;
import static com.powsybl.network.store.server.Utils.*;

/**
 * @author Etienne Lesot <etienne.lesot at rte-france.com>
 */
@Component
public class LimitsHandler {
    private final DataSource dataSource;
    private final ObjectMapper mapper;
    private final Mappings mappings;

    public LimitsHandler(DataSource dataSource, ObjectMapper mapper, Mappings mappings) {
        this.dataSource = dataSource;
        this.mapper = mapper;
        this.mappings = mappings;
    }

    public Map<OwnerInfo, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> getOperationalLimitsGroups(UUID networkUuid, int variantNum, String columnNameForWhereClause, String valueForWhereClause) {
        try (var connection = dataSource.getConnection()) {
            Map<OperationalLimitsGroupOwnerInfo, OperationalLimitsGroupAttributes> result = PartialVariantUtils.getExternalAttributes(
                    variantNum,
                    getNetworkAttributes(connection, networkUuid, variantNum, mappings, mapper).getFullVariantNum(),
                    () -> getTombstonedIdentifiableIds(connection, networkUuid, variantNum),
                    Set::of,
                variant -> getOperationalLimitsGroupsForVariant(connection, networkUuid, variant, columnNameForWhereClause, valueForWhereClause, variantNum),
                    OperationalLimitsGroupOwnerInfo::getEquipmentId);
            return convertOperationalLimitsGroupsMap(result);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private static Map<OwnerInfo, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> convertOperationalLimitsGroupsMap(Map<OperationalLimitsGroupOwnerInfo, OperationalLimitsGroupAttributes> map) {
        Map<OwnerInfo, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> result = new HashMap<>();

        map.forEach((ownerInfo, attributes) -> {
            OwnerInfo owner = new OwnerInfo(ownerInfo.getEquipmentId(), ownerInfo.getEquipmentType(),
                    ownerInfo.getNetworkUuid(), ownerInfo.getVariantNum());
            result.computeIfAbsent(owner, k -> new HashMap<>())
                    .computeIfAbsent(ownerInfo.getSide(), k -> new HashMap<>())
                    .put(ownerInfo.getOperationalLimitsGroupId(), attributes);
        });

        return result;
    }

    public Map<OwnerInfo, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> getOperationalLimitsGroupsWithInClause(UUID networkUuid, int variantNum, String columnNameForWhereClause, List<String> valuesForInClause) {
        try (var connection = dataSource.getConnection()) {
            Map<OperationalLimitsGroupOwnerInfo, OperationalLimitsGroupAttributes> result = PartialVariantUtils.getExternalAttributes(
                variantNum,
                getNetworkAttributes(connection, networkUuid, variantNum, mappings, mapper).getFullVariantNum(),
                () -> getTombstonedIdentifiableIds(connection, networkUuid, variantNum),
                Set::of,
                variant -> getOperationalLimitsGroupsWithInClauseForVariant(connection, networkUuid, variant, columnNameForWhereClause, valuesForInClause, variantNum),
                OperationalLimitsGroupOwnerInfo::getEquipmentId);
            return convertOperationalLimitsGroupsMap(result);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public Map<OperationalLimitsGroupOwnerInfo, OperationalLimitsGroupAttributes> getOperationalLimitsGroupsForVariant(Connection connection, UUID networkUuid, int variantNum, String columnNameForWhereClause, String valueForWhereClause, int variantNumOverride) {
        try (var preparedStmt = connection.prepareStatement(buildOperationalLimitsGroupQuery(columnNameForWhereClause))) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            preparedStmt.setString(3, valueForWhereClause);

            return innerGetOperationalLimitsGroups(preparedStmt, variantNumOverride);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public Map<OperationalLimitsGroupOwnerInfo, OperationalLimitsGroupAttributes> getOperationalLimitsGroupsWithInClauseForVariant(Connection connection, UUID networkUuid, int variantNum, String columnNameForWhereClause, List<String> valuesForInClause, int variantNumOverride) {
        if (valuesForInClause.isEmpty()) {
            return Collections.emptyMap();
        }
        try (var preparedStmt = connection.prepareStatement(buildOperationalLimitsGroupWithInClauseQuery(columnNameForWhereClause, valuesForInClause.size()))) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            for (int i = 0; i < valuesForInClause.size(); i++) {
                preparedStmt.setString(3 + i, valuesForInClause.get(i));
            }

            return innerGetOperationalLimitsGroups(preparedStmt, variantNumOverride);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private Map<OperationalLimitsGroupOwnerInfo, OperationalLimitsGroupAttributes> innerGetOperationalLimitsGroups(PreparedStatement preparedStmt, int variantNumOverride) throws SQLException {
        try (ResultSet resultSet = preparedStmt.executeQuery()) {
            Map<OperationalLimitsGroupOwnerInfo, OperationalLimitsGroupAttributes> map = new HashMap<>();
            while (resultSet.next()) {
                OperationalLimitsGroupOwnerInfo owner = new OperationalLimitsGroupOwnerInfo();
                // In order, from the QueryCatalog.buildOperationalLimitsGroupQuery SQL query :
                // equipmentId, equipmentType, networkUuid, variantNum, side, operationallimitgroupid,
                // current_limits_permanent_limit, current_limits_temporary_limits,
                // apparent_power_limits_permanent_limit, apparent_power_limits_temporary_limits,
                // active_power_limits_permanent_limit, active_power_limits_temporary_limits, properties
                owner.setEquipmentId(resultSet.getString(1));
                owner.setEquipmentType(ResourceType.valueOf(resultSet.getString(2)));
                owner.setNetworkUuid(UUID.fromString(resultSet.getString(3)));
                owner.setVariantNum(variantNumOverride);
                owner.setSide(resultSet.getInt(5));
                String operationalLimitsGroupId = resultSet.getString(6);
                owner.setOperationalLimitsGroupId(operationalLimitsGroupId);

                OperationalLimitsGroupAttributes operationalLimitsGroupAttributes = new OperationalLimitsGroupAttributes();
                operationalLimitsGroupAttributes.setId(operationalLimitsGroupId);
                LimitsAttributes currentLimits = createLimitsAttributes(
                        resultSet.getObject(7, Double.class),
                        resultSet.getString(8)
                );
                operationalLimitsGroupAttributes.setCurrentLimits(currentLimits);

                LimitsAttributes apparentPowerLimits = createLimitsAttributes(
                        resultSet.getObject(9, Double.class),
                        resultSet.getString(10)
                );
                operationalLimitsGroupAttributes.setApparentPowerLimits(apparentPowerLimits);

                LimitsAttributes activePowerLimits = createLimitsAttributes(
                        resultSet.getObject(11, Double.class),
                        resultSet.getString(12)
                );
                operationalLimitsGroupAttributes.setActivePowerLimits(activePowerLimits);

                String propertiesData = resultSet.getString(13);
                if (!StringUtils.isEmpty(propertiesData)) {
                    Map<String, String> properties = mapper.readValue(propertiesData, new TypeReference<>() {
                    });
                    operationalLimitsGroupAttributes.setProperties(properties);
                }

                map.put(owner, operationalLimitsGroupAttributes);
            }
            return map;
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private LimitsAttributes createLimitsAttributes(Double permanentLimitData,
                                                    String temporaryLimitsData)
            throws JsonProcessingException {
        boolean hasPermanentLimit = permanentLimitData != null && !Double.isNaN(permanentLimitData);
        boolean hasTemporaryLimits = temporaryLimitsData != null && !temporaryLimitsData.equals("[]");
        if (!hasPermanentLimit && !hasTemporaryLimits) {
            return null;
        }

        double permanentLimit = hasPermanentLimit ? permanentLimitData : Double.NaN;
        TreeMap<Integer, TemporaryLimitAttributes> temporaryLimits = null;
        if (hasTemporaryLimits) {
            List<TemporaryLimitAttributes> temporaryLimitsList = mapper.readValue(temporaryLimitsData, new TypeReference<>() { });
            temporaryLimits = new TreeMap<>();
            for (TemporaryLimitAttributes temporaryLimit : temporaryLimitsList) {
                int duration = temporaryLimit.getAcceptableDuration();
                temporaryLimits.put(duration, temporaryLimit);
            }
        }

        return new LimitsAttributes(permanentLimit, temporaryLimits);
    }

    protected <T extends LimitHolder & IdentifiableAttributes> Map<OperationalLimitsGroupOwnerInfo, OperationalLimitsGroupAttributes> getOperationalLimitsGroupsFromEquipments(UUID networkUuid, List<Resource<T>> resources) {
        Map<OperationalLimitsGroupOwnerInfo, OperationalLimitsGroupAttributes> map = new HashMap<>();

        if (!resources.isEmpty()) {
            for (Resource<T> resource : resources) {
                T equipment = resource.getAttributes();

                for (Integer side : equipment.getSideList()) {
                    Map<String, OperationalLimitsGroupAttributes> operationalLimitsGroupsForSide = equipment.getOperationalLimitsGroups(side);
                    for (Map.Entry<String, OperationalLimitsGroupAttributes> groupEntry : operationalLimitsGroupsForSide.entrySet()) {
                        String operationalLimitsGroupId = groupEntry.getKey();
                        OperationalLimitsGroupAttributes attributes = groupEntry.getValue();
                        OperationalLimitsGroupOwnerInfo ownerInfo = new OperationalLimitsGroupOwnerInfo(
                                resource.getId(),
                                resource.getType(),
                                networkUuid,
                                resource.getVariantNum(),
                                operationalLimitsGroupId,
                                side
                        );
                        map.put(ownerInfo, attributes);
                    }
                }
            }
        }
        return map;
    }

    public void insertOperationalLimitsGroups(Map<OperationalLimitsGroupOwnerInfo, OperationalLimitsGroupAttributes> operationalLimitsGroups) {
        try (var connection = dataSource.getConnection()) {
            try (var preparedStmt = connection.prepareStatement(buildInsertOperationalLimitsGroupQuery())) {
                List<Object> values = new ArrayList<>(13);
                List<Map.Entry<OperationalLimitsGroupOwnerInfo, OperationalLimitsGroupAttributes>> list = new ArrayList<>(operationalLimitsGroups.entrySet());
                for (List<Map.Entry<OperationalLimitsGroupOwnerInfo, OperationalLimitsGroupAttributes>> subUnit : Lists.partition(list, BATCH_SIZE)) {
                    for (Map.Entry<OperationalLimitsGroupOwnerInfo, OperationalLimitsGroupAttributes> entry : subUnit) {
                        values.clear();
                        values.add(entry.getKey().getNetworkUuid());
                        values.add(entry.getKey().getVariantNum());
                        values.add(entry.getKey().getEquipmentType().toString());
                        values.add(entry.getKey().getEquipmentId());
                        values.add(entry.getKey().getOperationalLimitsGroupId());
                        values.add(entry.getKey().getSide());
                        OperationalLimitsGroupAttributesSqlData operationalLimitsGroupSqlData = OperationalLimitsGroupAttributesSqlData.of(entry.getValue());
                        values.add(operationalLimitsGroupSqlData.getCurrentLimitsPermanentLimit());
                        values.add(operationalLimitsGroupSqlData.getCurrentLimitsTemporaryLimits());
                        values.add(operationalLimitsGroupSqlData.getApparentPowerLimitsPermanentLimit());
                        values.add(operationalLimitsGroupSqlData.getApparentPowerLimitsTemporaryLimits());
                        values.add(operationalLimitsGroupSqlData.getActivePowerLimitsPermanentLimit());
                        values.add(operationalLimitsGroupSqlData.getActivePowerLimitsTemporaryLimits());
                        values.add(operationalLimitsGroupSqlData.getProperties());
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

    protected <T extends LimitHolder & IdentifiableAttributes> void insertOperationalLimitsGroupsInEquipments(UUID networkUuid, List<Resource<T>> equipments, Map<OwnerInfo, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> operationalLimitsGroups) {
        for (Resource<T> equipmentAttributesResource : equipments) {
            OwnerInfo owner = new OwnerInfo(
                    equipmentAttributesResource.getId(),
                    equipmentAttributesResource.getType(),
                    networkUuid,
                    equipmentAttributesResource.getVariantNum()
            );
            if (operationalLimitsGroups.containsKey(owner)) {
                T equipment = equipmentAttributesResource.getAttributes();
                for (Integer side : equipment.getSideList()) {
                    Map<String, OperationalLimitsGroupAttributes> operationalLimitsGroupAttributes = operationalLimitsGroups.get(owner).get(side);
                    if (operationalLimitsGroupAttributes != null) {
                        equipment.getOperationalLimitsGroups(side).putAll(operationalLimitsGroupAttributes);
                    }
                }
            }
        }
    }

    public void deleteOperationalLimitsGroups(UUID networkUuid, int variantNum, List<String> equipmentIds) {
        try (var connection = dataSource.getConnection()) {
            try (var preparedStmt = connection.prepareStatement(buildDeleteOperationalLimitsGroupVariantEquipmentINQuery(equipmentIds.size()))) {
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

    public void deleteOperationalLimitsGroups(UUID networkUuid, Set<OperationalLimitsGroupOwnerInfo> operationalLimitsGroupInfos) {
        Map<Integer, Set<OperationalLimitsGroupOwnerInfo>> operationalLimitGroupsToDeleteByVariant =
            operationalLimitsGroupInfos.stream()
                .collect(Collectors.groupingBy(
                    OperationalLimitsGroupOwnerInfo::getVariantNum,
                    Collectors.toSet()
                ));

        try (var connection = dataSource.getConnection()) {
            for (Map.Entry<Integer, Set<OperationalLimitsGroupOwnerInfo>> variantEntry : operationalLimitGroupsToDeleteByVariant.entrySet()) {
                Integer variantNum = variantEntry.getKey();
                Set<OperationalLimitsGroupOwnerInfo> operationalLimitGroupsToDelete = variantEntry.getValue();
                deleteOperationalLimitsGroupsForVariant(connection, networkUuid, variantNum, operationalLimitGroupsToDelete);
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private void deleteOperationalLimitsGroupsForVariant(Connection connection, UUID networkUuid, int variantNum,
                                                         Set<OperationalLimitsGroupOwnerInfo> operationalLimitsGroupsToDelete) throws SQLException {
        List<OperationalLimitsGroupOwnerInfo> operationalLimitsGroupsToDeleteList = new ArrayList<>(operationalLimitsGroupsToDelete);
        for (List<OperationalLimitsGroupOwnerInfo> subUnit : Lists.partition(operationalLimitsGroupsToDeleteList, BATCH_SIZE)) {
            try (var preparedStmt = connection.prepareStatement(buildDeleteOperationalLimitsGroupByGroupIdAndSideAndIdentifiableIdINQuery(subUnit.size()))) {
                preparedStmt.setObject(1, networkUuid);
                preparedStmt.setInt(2, variantNum);
                int paramIndex = 3;
                for (OperationalLimitsGroupOwnerInfo group : subUnit) {
                    preparedStmt.setString(paramIndex++, group.getEquipmentId());
                    preparedStmt.setString(paramIndex++, group.getOperationalLimitsGroupId());
                    preparedStmt.setInt(paramIndex++, group.getSide());
                }
                preparedStmt.executeUpdate();
            }
        }
    }

    public <T extends IdentifiableAttributes & LimitHolder> void updateOperationalLimitsGroups(UUID networkUuid, List<Resource<T>> resources) {
        Map<OperationalLimitsGroupOwnerInfo, OperationalLimitsGroupAttributes> operationalLimitsGroups = getOperationalLimitsGroupsFromEquipments(networkUuid, resources);
        deleteOperationalLimitsGroups(networkUuid, operationalLimitsGroups.keySet());
        insertOperationalLimitsGroups(operationalLimitsGroups);
    }

    public Optional<OperationalLimitsGroupAttributes> getOperationalLimitsGroupAttributes(UUID networkId, int variantNum,
                                                                                          String branchId, ResourceType type,
                                                                                          String operationalLimitsGroupId,
                                                                                          int side) {
        OwnerInfo ownerInfo = new OwnerInfo(branchId, type, networkId, variantNum);
        Map<Integer, Map<String, OperationalLimitsGroupAttributes>> operationalLimitsGroups = getOperationalLimitsGroups(networkId, variantNum, EQUIPMENT_ID_COLUMN, branchId).get(ownerInfo);
        if (operationalLimitsGroups == null) {
            return Optional.empty();
        }
        return Optional.of(operationalLimitsGroups)
                .map(sideMap -> sideMap.get(side))
                .map(groupMap -> groupMap.get(operationalLimitsGroupId));
    }

    public void deleteAndTombstoneOperationalLimitsGroups(UUID networkUuid, Set<OperationalLimitsGroupOwnerInfo> operationalLimitsGroupInfos, boolean isPartialVariant) throws SQLException {
        deleteOperationalLimitsGroups(networkUuid, operationalLimitsGroupInfos);
        if (isPartialVariant) {
            insertTombstonedOperationalLimitsGroups(operationalLimitsGroupInfos);
        }
    }

    public void insertTombstonedOperationalLimitsGroups(Set<OperationalLimitsGroupOwnerInfo> operationalLimitsGroupInfos) throws SQLException {
        try (var connection = dataSource.getConnection()) {
            try (var preparedStmt = connection.prepareStatement(QueryLimitsCatalog.buildInsertTombstonedOperationalLimitsGroupQuery())) {
                for (OperationalLimitsGroupOwnerInfo entry : operationalLimitsGroupInfos) {
                    preparedStmt.setObject(1, entry.getNetworkUuid());
                    preparedStmt.setInt(2, entry.getVariantNum());
                    preparedStmt.setString(3, entry.getEquipmentId());
                    preparedStmt.setInt(4, entry.getSide());
                    preparedStmt.setString(5, entry.getOperationalLimitsGroupId());
                    preparedStmt.addBatch();
                }
                preparedStmt.executeBatch();
            }
        }
    }

    public List<OperationalLimitsGroupAttributes> getAllOperationalLimitsGroupAttributesForBranchSide(
        UUID networkId, int variantNum, ResourceType type, String branchId, int side) {
        OwnerInfo ownerInfo = new OwnerInfo(branchId, type, networkId, variantNum);
        Map<Integer, Map<String, OperationalLimitsGroupAttributes>> operationalLimitsGroups = getOperationalLimitsGroups(networkId, variantNum, EQUIPMENT_ID_COLUMN, branchId).get(ownerInfo);
        if (operationalLimitsGroups == null) {
            return Collections.emptyList();
        }

        Map<String, OperationalLimitsGroupAttributes> sideAttributes = operationalLimitsGroups.get(side);
        if (sideAttributes == null) {
            return Collections.emptyList();
        }

        return new ArrayList<>(sideAttributes.values());
    }

    public Map<String, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> getAllSelectedOperationalLimitsGroupAttributesByResourceType(
            UUID networkId, int variantNum, ResourceType type) {
        Map<OwnerInfo, SelectedOperationalLimitsGroupIdentifiers> selectedOperationalLimitsGroupIds = getSelectedOperationalLimitsGroupIds(networkId, variantNum, type);
        Map<OwnerInfo, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> selectedOperationalLimitsGroups = getSelectedOperationalLimitsGroups(networkId, variantNum, selectedOperationalLimitsGroupIds.values().stream().toList());

        return selectedOperationalLimitsGroups.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().getEquipmentId(),
                        Map.Entry::getValue
                ));
    }

    private Map<OwnerInfo, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> getSelectedOperationalLimitsGroups(UUID networkId, int variantNum, List<SelectedOperationalLimitsGroupIdentifiers> selectedOperationalLimitsGroups) {
        if (selectedOperationalLimitsGroups.isEmpty()) {
            return Collections.emptyMap();
        }

        try (var connection = dataSource.getConnection()) {
            Map<OperationalLimitsGroupOwnerInfo, OperationalLimitsGroupAttributes> result = PartialVariantUtils.getExternalAttributes(
                    variantNum,
                    getNetworkAttributes(connection, networkId, variantNum, mappings, mapper).getFullVariantNum(),
                    () -> getTombstonedIdentifiableIds(connection, networkId, variantNum),
                    Set::of,
                    variant -> getSelectedOperationalLimitsGroupsForVariant(connection, networkId, variant, selectedOperationalLimitsGroups, variantNum),
                    OperationalLimitsGroupOwnerInfo::getEquipmentId);
            return convertOperationalLimitsGroupsMap(result);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private Map<OperationalLimitsGroupOwnerInfo, OperationalLimitsGroupAttributes> getSelectedOperationalLimitsGroupsForVariant(Connection connection, UUID networkId, int variantNum, List<SelectedOperationalLimitsGroupIdentifiers> selectedOperationalLimitsGroups, int variantNumOverride) {
        if (selectedOperationalLimitsGroups.isEmpty()) {
            return Collections.emptyMap();
        }

        int conditionCount = selectedOperationalLimitsGroups.stream()
                .mapToInt(identifiers ->
                        (identifiers.operationalLimitsGroupId1() != null ? 1 : 0) +
                        (identifiers.operationalLimitsGroupId2() != null ? 1 : 0))
                .sum();

        try (var preparedStmt = connection.prepareStatement(QueryLimitsCatalog.buildSelectedOperationalLimitsGroupINQuery(conditionCount))) {
            preparedStmt.setObject(1, networkId);
            preparedStmt.setInt(2, variantNum);

            int paramIndex = 3;
            for (SelectedOperationalLimitsGroupIdentifiers identifiers : selectedOperationalLimitsGroups) {
                if (identifiers.operationalLimitsGroupId1() != null) {
                    preparedStmt.setString(paramIndex++, identifiers.branchId());
                    preparedStmt.setString(paramIndex++, identifiers.operationalLimitsGroupId1());
                    preparedStmt.setInt(paramIndex++, 1);
                }
                if (identifiers.operationalLimitsGroupId2() != null) {
                    preparedStmt.setString(paramIndex++, identifiers.branchId());
                    preparedStmt.setString(paramIndex++, identifiers.operationalLimitsGroupId2());
                    preparedStmt.setInt(paramIndex++, 2);
                }
            }

            return innerGetOperationalLimitsGroups(preparedStmt, variantNumOverride);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private Map<OwnerInfo, SelectedOperationalLimitsGroupIdentifiers> getSelectedOperationalLimitsGroupIds(UUID networkId, int variantNum, ResourceType type) {
        try (var connection = dataSource.getConnection()) {
            return PartialVariantUtils.getExternalAttributes(
                    variantNum,
                    getNetworkAttributes(connection, networkId, variantNum, mappings, mapper).getFullVariantNum(),
                    () -> getTombstonedIdentifiableIds(connection, networkId, variantNum),
                    Set::of,
                    variant -> getSelectedOperationalLimitsGroupIdsForVariant(connection, networkId, variant, type, variantNum),
                    OwnerInfo::getEquipmentId);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private Map<OwnerInfo, SelectedOperationalLimitsGroupIdentifiers> getSelectedOperationalLimitsGroupIdsForVariant(Connection connection, UUID networkId, int variantNum, ResourceType type, int variantNumOverride) {
        try (var preparedStmt = connection.prepareStatement(
                QueryCatalog.buildGetSelectedOperationalLimitsGroupsQuery(mappings.getTableMapping(type).getTable()))) {
            preparedStmt.setObject(1, networkId);
            preparedStmt.setInt(2, variantNum);
            return getInnerSelectedOperationalLimitsGroupIds(networkId, type, preparedStmt, variantNumOverride);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private Map<OwnerInfo, SelectedOperationalLimitsGroupIdentifiers> getInnerSelectedOperationalLimitsGroupIds(UUID networkId, ResourceType type, PreparedStatement preparedStmt, int variantNumOverride) throws SQLException {
        try (ResultSet resultSet = preparedStmt.executeQuery()) {
            Map<OwnerInfo, SelectedOperationalLimitsGroupIdentifiers> resources = new HashMap<>();
            while (resultSet.next()) {
                String branchId = resultSet.getString(1);
                String operationalLimitsGroupId1 = resultSet.getString(2);
                String operationalLimitsGroupId2 = resultSet.getString(3);
                resources.put(new OwnerInfo(branchId, type, networkId, variantNumOverride), new SelectedOperationalLimitsGroupIdentifiers(branchId, operationalLimitsGroupId1, operationalLimitsGroupId2));
            }
            return resources;
        }
    }

    private record SelectedOperationalLimitsGroupIdentifiers(String branchId, String operationalLimitsGroupId1,
                                                     String operationalLimitsGroupId2) {
    }
}
