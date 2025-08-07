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
import com.powsybl.network.store.server.json.LimitsGroupAttributesSqlData;
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

import static com.powsybl.network.store.server.QueryCatalog.*;
import static com.powsybl.network.store.server.Utils.*;

/**
 * @author Etienne Lesot <etienne.lesot at rte-france.com>
 */
//FIXME: how to deal with existing DB data [] and 0.0 ?
// fix tests
// Temporary limits treemap => to list...
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

    public Map<OwnerInfo, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> getOperationalLimitsGroup(UUID networkUuid, int variantNum, String columnNameForWhereClause, String valueForWhereClause) {
        try (var connection = dataSource.getConnection()) {
            return PartialVariantUtils.getOperationalLimitsGroup(
                variantNum,
                getNetworkAttributes(connection, networkUuid, variantNum, mappings, mapper).getFullVariantNum(),
                () -> getTombstonedIdentifiableIds(connection, networkUuid, variantNum),
                variant -> getOperationalLimitsGroupForVariant(connection, networkUuid, variant, columnNameForWhereClause, valueForWhereClause, variantNum));
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public Map<OwnerInfo, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> getOperationalLimitsGroupWithInClause(UUID networkUuid, int variantNum, String columnNameForWhereClause, List<String> valuesForInClause) {
        try (var connection = dataSource.getConnection()) {
            return PartialVariantUtils.getOperationalLimitsGroup(
                variantNum,
                getNetworkAttributes(connection, networkUuid, variantNum, mappings, mapper).getFullVariantNum(),
                () -> getTombstonedIdentifiableIds(connection, networkUuid, variantNum),
                variant -> getOperationalLimitsGroupWithInClauseForVariant(connection, networkUuid, variant, columnNameForWhereClause, valuesForInClause, variantNum));
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public Map<OwnerInfo, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> getOperationalLimitsGroupForVariant(Connection connection, UUID networkUuid, int variantNum, String columnNameForWhereClause, String valueForWhereClause, int variantNumOverride) {
        try (var preparedStmt = connection.prepareStatement(buildOperationalLimitsGroupQuery(columnNameForWhereClause))) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            preparedStmt.setString(3, valueForWhereClause);

            return innerGetOperationalLimitsGroup(preparedStmt, variantNumOverride);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public Map<OwnerInfo, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> getOperationalLimitsGroupWithInClauseForVariant(Connection connection, UUID networkUuid, int variantNum, String columnNameForWhereClause, List<String> valuesForInClause, int variantNumOverride) {
        if (valuesForInClause.isEmpty()) {
            return Collections.emptyMap();
        }
        try (var preparedStmt = connection.prepareStatement(buildOperationalLimitsGroupWithInClauseQuery(columnNameForWhereClause, valuesForInClause.size()))) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            for (int i = 0; i < valuesForInClause.size(); i++) {
                preparedStmt.setString(3 + i, valuesForInClause.get(i));
            }

            return innerGetOperationalLimitsGroup(preparedStmt, variantNumOverride);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private Map<OwnerInfo, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> innerGetOperationalLimitsGroup(PreparedStatement preparedStmt, int variantNumOverride) throws SQLException {
        try (ResultSet resultSet = preparedStmt.executeQuery()) {
            Map<OwnerInfo, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> map = new HashMap<>();
            while (resultSet.next()) {
                OwnerInfo owner = new OwnerInfo();
                // In order, from the QueryCatalog.buildOperationalLimitsGroupQuery SQL query :
                // equipmentId, equipmentType, networkUuid, variantNum, side, operationallimitgroupid,
                // current_limits_permanent_limit, current_limits_temporary_limits,
                // apparent_power_limits_permanent_limit, apparent_power_limits_temporary_limits,
                // active_power_limits_permanent_limit, active_power_limits_temporary_limits, properties
                owner.setEquipmentId(resultSet.getString(1));
                owner.setEquipmentType(ResourceType.valueOf(resultSet.getString(2)));
                owner.setNetworkUuid(UUID.fromString(resultSet.getString(3)));
                owner.setVariantNum(variantNumOverride);

                OperationalLimitsGroupAttributes operationalLimitsGroupAttributes = new OperationalLimitsGroupAttributes();

                Integer side = resultSet.getInt(5);
                String operationalLimitsGroupId = resultSet.getString(6);
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

                Map<String, OperationalLimitsGroupAttributes> innerMap = map.computeIfAbsent(owner, k -> new HashMap<>())
                        .computeIfAbsent(side, k -> new HashMap<>());
                innerMap.put(operationalLimitsGroupId, operationalLimitsGroupAttributes);
            }
            return map;
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    //FIXME: write it better
    private LimitsAttributes createLimitsAttributes(Double permanentLimitData,
                                                    String temporaryLimitData)
            throws JsonProcessingException {
        if (permanentLimitData == null && temporaryLimitData == null) {
            return null;
        }

        double permanentLimit = permanentLimitData == null ? Double.NaN : permanentLimitData;
        TreeMap<Integer, TemporaryLimitAttributes> temporaryLimit = new TreeMap<>();
        if (!StringUtils.isEmpty(temporaryLimitData)) {
            List<TemporaryLimitAttributes> tempLimitList = mapper.readValue(temporaryLimitData, new TypeReference<>() { });
            if (tempLimitList != null) {
                for (TemporaryLimitAttributes limitInfo : tempLimitList) {
                    int duration = limitInfo.getAcceptableDuration();
                    temporaryLimit.put(duration, limitInfo);
                }
            }
        }

        return new LimitsAttributes(permanentLimit, temporaryLimit);
    }

    protected <T extends LimitHolder & IdentifiableAttributes> Map<OwnerInfo, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> getOperationalLimitsGroupFromEquipments(UUID networkUuid, List<Resource<T>> resources) {
        Map<OwnerInfo, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> map = new HashMap<>();

        if (!resources.isEmpty()) {
            for (Resource<T> resource : resources) {
                OwnerInfo info = new OwnerInfo(
                    resource.getId(),
                    resource.getType(),
                    networkUuid,
                    resource.getVariantNum()
                );
                T equipment = resource.getAttributes();
                map.put(info, getAllOperationalLimitsGroup(equipment));
            }
        }
        return map;
    }

    public void insertOperationalLimitsGroupAttributes(Map<OwnerInfo, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> operationalLimitsGroups) {
        try (var connection = dataSource.getConnection()) {
            try (var preparedStmt = connection.prepareStatement(buildInsertOperationalLimitsGroupQuery())) {
                List<LimitsGroupAttributesSqlData> operationalLimitsGroup = LimitsGroupAttributesSqlData.of(operationalLimitsGroups);
                List<Object> values = new ArrayList<>(13);
                for (List<LimitsGroupAttributesSqlData> subUnit : Lists.partition(operationalLimitsGroup, BATCH_SIZE)) {
                    for (LimitsGroupAttributesSqlData entry : subUnit) {
                        values.clear();
                        values.add(entry.getNetworkUuid());
                        values.add(entry.getVariantNum());
                        values.add(entry.getEquipmentType());
                        values.add(entry.getEquipmentId());
                        values.add(entry.getOperationalLimitsGroupId());
                        values.add(entry.getSide());
                        values.add(entry.getCurrentLimitsPermanentLimit());
                        values.add(entry.getCurrentLimitsTemporaryLimits());
                        values.add(entry.getApparentPowerLimitsPermanentLimit());
                        values.add(entry.getApparentPowerLimitsTemporaryLimits());
                        values.add(entry.getActivePowerLimitsPermanentLimit());
                        values.add(entry.getActivePowerLimitsTemporaryLimits());
                        values.add(entry.getProperties());
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

    protected <T extends LimitHolder & IdentifiableAttributes> void insertOperationalLimitsGroupInEquipments(UUID networkUuid, List<Resource<T>> equipments, Map<OwnerInfo, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> operationalLimitsGroups) {
        if (!operationalLimitsGroups.isEmpty() && !equipments.isEmpty()) {
            for (Resource<T> equipmentAttributesResource : equipments) {
                OwnerInfo owner = new OwnerInfo(
                    equipmentAttributesResource.getId(),
                    equipmentAttributesResource.getType(),
                    networkUuid,
                    equipmentAttributesResource.getVariantNum()
                );
                if (operationalLimitsGroups.containsKey(owner)) {
                    T equipment = equipmentAttributesResource.getAttributes();
                    processOperationalLimitsGroupsForEquipment(equipment, operationalLimitsGroups.get(owner));
                }
            }
        }
    }

    private <T extends LimitHolder> void processOperationalLimitsGroupsForEquipment(T equipment, Map<Integer, Map<String, OperationalLimitsGroupAttributes>> operationalLimitsGroups) {
        for (Integer side : equipment.getSideList()) {
            Map<String, OperationalLimitsGroupAttributes> operationalLimitsGroupAttributes = operationalLimitsGroups.get(side);
            if (operationalLimitsGroupAttributes != null) {
                equipment.getOperationalLimitsGroups(side).putAll(operationalLimitsGroupAttributes);
            }
        }
    }

    public void deleteOperationalLimitsGroup(UUID networkUuid, int variantNum, List<String> equipmentIds) {
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

    public void deleteOperationalLimitsGroup(UUID networkUuid, Map<OwnerInfo, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> operationalLimitsGroup) {
        Map<Integer, Set<OperationalLimitsGroupOwnerInfo>> operationalLimitGroupsToDeleteByVariant = getOperationalLimitGroupsByVariant(operationalLimitsGroup);

        try (var connection = dataSource.getConnection()) {
            for (Map.Entry<Integer, Set<OperationalLimitsGroupOwnerInfo>> variantEntry : operationalLimitGroupsToDeleteByVariant.entrySet()) {
                Integer variantNum = variantEntry.getKey();
                Set<OperationalLimitsGroupOwnerInfo> operationalLimitGroupsToDelete = variantEntry.getValue();
                deleteOperationalLimitsGroupForVariant(connection, networkUuid, variantNum, operationalLimitGroupsToDelete);
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private static Map<Integer, Set<OperationalLimitsGroupOwnerInfo>> getOperationalLimitGroupsByVariant(Map<OwnerInfo, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> operationalLimitsGroups) {
        Map<Integer, Set<OperationalLimitsGroupOwnerInfo>> operationalLimitGroupsByVariant = new HashMap<>();

        for (Map.Entry<OwnerInfo, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> entry : operationalLimitsGroups.entrySet()) {
            OwnerInfo ownerInfo = entry.getKey();
            Map<Integer, Map<String, OperationalLimitsGroupAttributes>> sideToGroupsMap = entry.getValue();

            for (Map.Entry<Integer, Map<String, OperationalLimitsGroupAttributes>> sideEntry : sideToGroupsMap.entrySet()) {
                Integer side = sideEntry.getKey();
                Map<String, OperationalLimitsGroupAttributes> groupsMap = sideEntry.getValue();

                for (String operationalLimitsGroupId : groupsMap.keySet()) {
                    OperationalLimitsGroupOwnerInfo groupOwnerInfo = new OperationalLimitsGroupOwnerInfo(
                            ownerInfo.getEquipmentId(),
                            ownerInfo.getEquipmentType(),
                            ownerInfo.getNetworkUuid(),
                            ownerInfo.getVariantNum(),
                            operationalLimitsGroupId,
                            side
                    );

                    operationalLimitGroupsByVariant
                            .computeIfAbsent(ownerInfo.getVariantNum(), k -> new HashSet<>())
                            .add(groupOwnerInfo);
                }
            }
        }
        return operationalLimitGroupsByVariant;
    }

    private void deleteOperationalLimitsGroupForVariant(Connection connection, UUID networkUuid, int variantNum,
                                                        Set<OperationalLimitsGroupOwnerInfo> operationalLimitsGroupsToDelete) throws SQLException {
        try (var preparedStmt = connection.prepareStatement(buildDeleteOperationalLimitsGroupByGroupIdAndSideAndIdentifiableIdINQuery(operationalLimitsGroupsToDelete.size()))) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            int paramIndex = 3;
            for (OperationalLimitsGroupOwnerInfo group : operationalLimitsGroupsToDelete) {
                preparedStmt.setString(paramIndex++, group.getEquipmentId());
                preparedStmt.setString(paramIndex++, group.getOperationalLimitsGroupId());
                preparedStmt.setInt(paramIndex++, group.getSide());
            }
            preparedStmt.executeUpdate();
        }
    }

    private Map<Integer, Map<String, OperationalLimitsGroupAttributes>> getAllOperationalLimitsGroup(LimitHolder equipment) {
        Map<Integer, Map<String, OperationalLimitsGroupAttributes>> result = new HashMap<>();
        for (Integer side : equipment.getSideList()) {
            result.computeIfAbsent(side, k -> new HashMap<>()).putAll(equipment.getOperationalLimitsGroups(side));
        }
        return result;
    }

    public <T extends IdentifiableAttributes & LimitHolder> void updateOperationalLimitsGroup(UUID networkUuid, List<Resource<T>> resources) {
        Map<OwnerInfo, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> operationalLimitsGroups = getOperationalLimitsGroupFromEquipments(networkUuid, resources);
        deleteOperationalLimitsGroup(networkUuid, operationalLimitsGroups);
        insertOperationalLimitsGroupAttributes(operationalLimitsGroups);
    }

    public Optional<OperationalLimitsGroupAttributes> getOperationalLimitsGroup(UUID networkId, int variantNum,
                                                                                String branchId, ResourceType type,
                                                                                String operationalLimitsGroupId,
                                                                                int side) {
        OwnerInfo ownerInfo = new OwnerInfo(branchId, type, networkId, variantNum);
        Map<Integer, Map<String, OperationalLimitsGroupAttributes>> operationalLimitsGroups = getOperationalLimitsGroup(networkId, variantNum, EQUIPMENT_ID_COLUMN, branchId).get(ownerInfo);
        if (operationalLimitsGroups == null) {
            return Optional.empty();
        }
        return Optional.of(operationalLimitsGroups)
                .map(sideMap -> sideMap.get(side))
                .map(groupMap -> groupMap.get(operationalLimitsGroupId));
    }

    public List<OperationalLimitsGroupAttributes> getAllOperationalLimitsGroupAttributesForBranchSide(
        UUID networkId, int variantNum, ResourceType type, String branchId, int side) {
        OwnerInfo ownerInfo = new OwnerInfo(branchId, type, networkId, variantNum);
        Map<Integer, Map<String, OperationalLimitsGroupAttributes>> limitsInfos = getOperationalLimitsGroup(networkId, variantNum, EQUIPMENT_ID_COLUMN, branchId).get(ownerInfo);
        if (limitsInfos == null) {
            return Collections.emptyList();
        }

        Map<String, OperationalLimitsGroupAttributes> sideAttributes = limitsInfos.get(side);
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
            return PartialVariantUtils.getOperationalLimitsGroup(
                    variantNum,
                    getNetworkAttributes(connection, networkId, variantNum, mappings, mapper).getFullVariantNum(),
                    () -> getTombstonedIdentifiableIds(connection, networkId, variantNum),
                    variant -> getSelectedOperationalLimitsGroupsForVariant(connection, networkId, variant, selectedOperationalLimitsGroups, variantNum));
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private Map<OwnerInfo, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> getSelectedOperationalLimitsGroupsForVariant(Connection connection, UUID networkId, int variantNum, List<SelectedOperationalLimitsGroupIdentifiers> selectedOperationalLimitsGroups, int variantNumOverride) {
        if (selectedOperationalLimitsGroups.isEmpty()) {
            return Collections.emptyMap();
        }

        int conditionCount = selectedOperationalLimitsGroups.stream()
                .mapToInt(identifiers ->
                        (identifiers.operationalLimitsGroupId1() != null ? 1 : 0) +
                        (identifiers.operationalLimitsGroupId2() != null ? 1 : 0))
                .sum();

        try (var preparedStmt = connection.prepareStatement(QueryCatalog.buildSelectedOperationalLimitsGroupQuery(conditionCount))) {
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

            return innerGetOperationalLimitsGroup(preparedStmt, variantNumOverride);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private Map<OwnerInfo, SelectedOperationalLimitsGroupIdentifiers> getSelectedOperationalLimitsGroupIds(UUID networkId, int variantNum, ResourceType type) {
        try (var connection = dataSource.getConnection()) {
            int fullVariantNum = getNetworkAttributes(connection, networkId, variantNum, mappings, mapper).getFullVariantNum();

            if (NetworkAttributes.isFullVariant(fullVariantNum)) {
                // If the variant is full, retrieve selected groups directly
                return getSelectedOperationalLimitsGroupIdsForVariant(connection, networkId, variantNum, type, variantNum);
            }

            // Retrieve selected groups from the full variant first
            Map<OwnerInfo, SelectedOperationalLimitsGroupIdentifiers> selectedGroupIds = getSelectedOperationalLimitsGroupIdsForVariant(connection, networkId, fullVariantNum, type, variantNum);

            // Remove selected groups of tombstoned identifiables
            Set<String> tombstonedIdentifiables = getTombstonedIdentifiableIds(connection, networkId, variantNum);
            selectedGroupIds.entrySet().removeIf(entry -> tombstonedIdentifiables.contains(entry.getKey().getEquipmentId()));

            // Retrieve selected groups from partial variant
            Map<OwnerInfo, SelectedOperationalLimitsGroupIdentifiers> partialVariantSelectedGroupIds = getSelectedOperationalLimitsGroupIdsForVariant(connection, networkId, variantNum, type, variantNum);

            // Combine selected groups from full and partial variant
            selectedGroupIds.putAll(partialVariantSelectedGroupIds);

            return selectedGroupIds;
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private Map<OwnerInfo, SelectedOperationalLimitsGroupIdentifiers> getSelectedOperationalLimitsGroupIdsForVariant(Connection connection, UUID networkId, int variantNum, ResourceType type, int variantNumOverride) throws SQLException {
        try (var preparedStmt = connection.prepareStatement(
                QueryCatalog.buildGetSelectedOperationalLimitsGroupsQuery(mappings.getTableMapping(type).getTable()))) {
            preparedStmt.setObject(1, networkId);
            preparedStmt.setInt(2, variantNum);
            return getInnerSelectedOperationalLimitsGroupIds(networkId, type, preparedStmt, variantNumOverride);
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
