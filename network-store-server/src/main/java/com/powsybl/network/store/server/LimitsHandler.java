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
import com.powsybl.iidm.network.LimitType;
import com.powsybl.network.store.model.*;
import com.powsybl.network.store.server.dto.OwnerInfo;
import com.powsybl.network.store.server.exceptions.UncheckedSqlException;
import com.powsybl.network.store.server.json.LimitsGroupAttributesSqlData;
import com.powsybl.network.store.server.json.TemporaryLimitInfosSqlData;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static com.powsybl.network.store.server.QueryCatalog.*;
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

    public Map<OwnerInfo, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> getOperationalLimitsGroup(UUID networkUuid, int variantNum, String columnNameForWhereClause, String valueForWhereClause) {
        try (var connection = dataSource.getConnection()) {
            return PartialVariantUtils.getExternalAttributes(
                variantNum,
                getNetworkAttributes(connection, networkUuid, variantNum, mappings, mapper).getFullVariantNum(),
                () -> getTombstonedOperationalLimitsGroupIds(connection, networkUuid, variantNum),
                () -> getTombstonedIdentifiableIds(connection, networkUuid, variantNum),
                variant -> getOperationalLimitsGroupForVariant(connection, networkUuid, variant, columnNameForWhereClause, valueForWhereClause, variantNum),
                OwnerInfo::getEquipmentId);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public Map<OwnerInfo, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> getOperationalLimitsGroupWithInClause(UUID networkUuid, int variantNum, String columnNameForWhereClause, List<String> valuesForInClause) {
        try (var connection = dataSource.getConnection()) {
            return PartialVariantUtils.getExternalAttributes(
                variantNum,
                getNetworkAttributes(connection, networkUuid, variantNum, mappings, mapper).getFullVariantNum(),
                () -> getTombstonedOperationalLimitsGroupIds(connection, networkUuid, variantNum),
                () -> getTombstonedIdentifiableIds(connection, networkUuid, variantNum),
                variant -> getOperationalLimitsGroupWithInClauseForVariant(connection, networkUuid, variant, columnNameForWhereClause, valuesForInClause, variantNum),
                OwnerInfo::getEquipmentId);
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
                LimitsAttributes currentLimits = createLimitsAttributes(
                        operationalLimitsGroupId,
                        resultSet.getDouble(7),
                        resultSet.getString(8),
                        LimitType.CURRENT,
                        side
                );
                operationalLimitsGroupAttributes.setCurrentLimits(currentLimits);

                LimitsAttributes apparentPowerLimits = createLimitsAttributes(
                        operationalLimitsGroupId,
                        resultSet.getDouble(9),
                        resultSet.getString(10),
                        LimitType.APPARENT_POWER,
                        side
                );
                operationalLimitsGroupAttributes.setApparentPowerLimits(apparentPowerLimits);

                LimitsAttributes activePowerLimits = createLimitsAttributes(
                        operationalLimitsGroupId,
                        resultSet.getDouble(11),
                        resultSet.getString(12),
                        LimitType.ACTIVE_POWER,
                        side
                );
                operationalLimitsGroupAttributes.setActivePowerLimits(activePowerLimits);

                Map<String, String> properties = mapper.readValue(resultSet.getString(13), new TypeReference<>() {
                });
                operationalLimitsGroupAttributes.setProperties(properties);

                Map<String, OperationalLimitsGroupAttributes> innerMap = map.computeIfAbsent(owner, k -> new HashMap<>())
                        .computeIfAbsent(side, k -> new HashMap<>());
                innerMap.put(operationalLimitsGroupAttributes.getId(), operationalLimitsGroupAttributes);
            }
            return map;
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private LimitsAttributes createLimitsAttributes(String operationalLimitsGroupId, double permanentLimit,
                                                    String temporaryLimitData, LimitType limitType, Integer side)
            throws JsonProcessingException {
        LimitsAttributes limits = new LimitsAttributes(operationalLimitsGroupId, permanentLimit, new TreeMap<>());

        if (temporaryLimitData != null && !temporaryLimitData.trim().isEmpty()) {
            List<TemporaryLimitInfosSqlData> temporaryLimitInfos = mapper.readValue(temporaryLimitData, new TypeReference<>() { });
            for (TemporaryLimitInfosSqlData sqlData : temporaryLimitInfos) {
                TemporaryLimitAttributes temporaryLimit = TemporaryLimitAttributes.builder()
                        .operationalLimitsGroupId(operationalLimitsGroupId)
                        .limitType(limitType)
                        .side(side)
                        .name(sqlData.getName())
                        .value(sqlData.getValue())
                        .acceptableDuration(sqlData.getAcceptableDuration())
                        .fictitious(sqlData.isFictitious())
                        .build();

                limits.addTemporaryLimit(temporaryLimit);
            }
        }

        return limits;
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

    // Will be removed
    public void insertTemporaryLimitsAttributes(Map<OwnerInfo, List<TemporaryLimitAttributes>> temporaryLimits) {
        throw new AssertionError("This method should not be called anymore"); // obsolete code called in V211LimitsMigration
    }

    public void insertOperationalLimitsGroupAttributes(Map<OwnerInfo, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> operationalLimitsGroups) {
        try (var connection = dataSource.getConnection()) {
            try (var preparedStmt = connection.prepareStatement(buildInsertOperationalLimitsGroupQuery())) {
                List<LimitsGroupAttributesSqlData> operationalLimitsGroup = LimitsGroupAttributesSqlData.of(operationalLimitsGroups);
                List<Object> values = new ArrayList<>(12);
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

    // Will be removed
//    public void insertPermanentLimitsAttributes(Map<OwnerInfo, List<PermanentLimitAttributes>> permanentLimits) {
//        throw new AssertionError("This method should not be called anymore"); // obsolete code called in V211LimitsMigration
//    }

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
                    //if not null
                    Map<String, OperationalLimitsGroupAttributes> operationalLimitsGroupAttributes1 = operationalLimitsGroups.get(owner).get(1);
                    equipment.getOperationalLimitsGroups(1).putAll(operationalLimitsGroupAttributes1);
                    Map<String, OperationalLimitsGroupAttributes> operationalLimitsGroupAttributes2 = operationalLimitsGroups.get(owner).get(2);
                    equipment.getOperationalLimitsGroups(2).putAll(operationalLimitsGroupAttributes2);
                    Map<String, OperationalLimitsGroupAttributes> operationalLimitsGroupAttributes3 = operationalLimitsGroups.get(owner).get(3);
                    equipment.getOperationalLimitsGroups(3).putAll(operationalLimitsGroupAttributes3);
                }
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

    public <T extends IdentifiableAttributes> void deleteOperationalLimitsGroup(UUID networkUuid, List<Resource<T>> resources) {
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
        resourceIdsByVariant.forEach((k, v) -> deleteOperationalLimitsGroup(networkUuid, k, v));
    }

    //FIXME: to fix
    private Set<String> getTombstonedOperationalLimitsGroupIds(Connection connection, UUID networkUuid, int variantNum) {
        Set<String> identifiableIds = new HashSet<>();
        try (var preparedStmt = connection.prepareStatement(buildGetTombstonedExternalAttributesIdsQuery())) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            preparedStmt.setString(3, ExternalAttributesType.OPERATIONAL_LIMIT_GROUP.toString());
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

    private Map<Integer, Map<String, OperationalLimitsGroupAttributes>> getAllOperationalLimitsGroup(LimitHolder equipment) {
        Map<Integer, Map<String, OperationalLimitsGroupAttributes>> result = new HashMap<>();
        for (Integer side : equipment.getSideList()) {
            result.computeIfAbsent(side, k -> new HashMap<>()).putAll(equipment.getOperationalLimitsGroups(side));
        }
        return result;
    }

    public <T extends IdentifiableAttributes> void updateOperationalLimitsGroup(UUID networkUuid, List<Resource<T>> resources, Map<OwnerInfo, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> limitsInfos) {
        deleteOperationalLimitsGroup(networkUuid, resources);
        insertOperationalLimitsGroupAttributes(limitsInfos);
    }

    //FIXME: use the remove method => this should not be used like this!
    private <T extends IdentifiableAttributes> void insertTombstonedOperationalLimitsGroup(Map<OwnerInfo, List<String>> limitsInfos) {
        try (var connection = dataSource.getConnection()) {
            try (var preparedStmt = connection.prepareStatement(buildInsertTombstonedExternalAttributesQuery())) {
                for (Map.Entry<OwnerInfo, List<String>> ownerInfo : limitsInfos.entrySet()) {
                    preparedStmt.setObject(1, ownerInfo.getKey().getNetworkUuid());
                    preparedStmt.setInt(2, ownerInfo.getKey().getVariantNum());
                    preparedStmt.setString(3, ownerInfo.getKey().getEquipmentId());
                    preparedStmt.setString(4, ExternalAttributesType.OPERATIONAL_LIMIT_GROUP.toString());
                    preparedStmt.addBatch();
                }
                preparedStmt.executeBatch();
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private boolean containsOperationalLimitsGroup(Map<String, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> map, String branchId, Integer side, String groupId) {
        return map.containsKey(branchId) && map.get(branchId).containsKey(side) && map.get(branchId).get(side).containsKey(groupId);
    }

    private void addElementToOperationalLimitsGroupMap(Map<String, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> map, String branchId, Integer side, String groupId, OperationalLimitsGroupAttributes operationalLimitsGroupAttributes) {
        map.computeIfAbsent(branchId, k -> new HashMap<>())
            .computeIfAbsent(side, k -> new HashMap<>())
            .put(groupId, operationalLimitsGroupAttributes);
    }

    private Optional<OperationalLimitsGroupAttributes> getElementFromOperationalLimitsGroupMap(Map<String, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> operationalLimitsGroupMap, String branchId, Integer side, String groupId) {
        return Optional.ofNullable(operationalLimitsGroupMap.get(branchId))
            .map(sideMap -> sideMap.get(side))
            .map(groupMap -> groupMap.get(groupId));
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

    //FIXME: retrieve only selected groups
    public Map<String, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> getAllSelectedOperationalLimitsGroupAttributesByResourceType(
            UUID networkId, int variantNum, ResourceType type, int fullVariantNum, Set<String> tombstonedElements) {
        // get selected operational limits ids for each element of type indicated
        Map<OwnerInfo, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> limitsInfos = getOperationalLimitsGroup(networkId, variantNum, EQUIPMENT_TYPE_COLUMN, type.toString());
        Map<OwnerInfo, SelectedOperationalLimitsGroupIdentifiers> selectedOperationalLimitsGroups =
            getSelectedOperationalLimitsGroupIdsForVariant(networkId, variantNum, fullVariantNum, type, tombstonedElements);
        Map<String, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> selectedOperationalLimitsGroupAttributes = new HashMap<>();
        // get operational limits group associated
        /*
        selectedOperationalLimitsGroups.forEach((owner, selectedOperationalLimitsGroupIdentifiers) -> {
            String selectedOperationalLimitsGroupId1 = selectedOperationalLimitsGroupIdentifiers.operationalLimitsGroupId1();
            String selectedOperationalLimitsGroupId2 = selectedOperationalLimitsGroupIdentifiers.operationalLimitsGroupId2();
            if (selectedOperationalLimitsGroupId1 != null || selectedOperationalLimitsGroupId2 != null) {
                String equipmentId = owner.getEquipmentId();
                Map<String, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> operationalLimitsGroupAttributesMap = convertLimitInfosToOperationalLimitsGroupMap(
                    equipmentId, limitsInfos.get(owner));
                addSelectedOperationalLimitsGroupOnSide(selectedOperationalLimitsGroupId1, operationalLimitsGroupAttributesMap, 1, equipmentId, selectedOperationalLimitsGroupAttributes);
                addSelectedOperationalLimitsGroupOnSide(selectedOperationalLimitsGroupId2, operationalLimitsGroupAttributesMap, 2, equipmentId, selectedOperationalLimitsGroupAttributes);
            }
        });*/
        return selectedOperationalLimitsGroupAttributes;
    }

    private void addSelectedOperationalLimitsGroupOnSide(String selectedOperationalLimitsGroupId,
                                                         Map<String, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> operationalLimitsGroupAttributesMap,
                                                         int side, String equipmentId,
                                                         Map<String, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> selectedOperationalLimitsGroupAttributes) {
        if (selectedOperationalLimitsGroupId != null) {
            Optional<OperationalLimitsGroupAttributes> operationalLimitsGroupAttributes = getElementFromOperationalLimitsGroupMap(operationalLimitsGroupAttributesMap, equipmentId, side, selectedOperationalLimitsGroupId);
            operationalLimitsGroupAttributes.ifPresent(attributes ->
                addElementToOperationalLimitsGroupMap(selectedOperationalLimitsGroupAttributes, equipmentId, side, selectedOperationalLimitsGroupId, attributes));
        }
    }

    private Map<OwnerInfo, SelectedOperationalLimitsGroupIdentifiers> getSelectedOperationalLimitsGroupIdsForVariant(UUID networkId, int variantNum, int fullVariantNum, ResourceType type, Set<String> tombstonedElements) {
        Map<OwnerInfo, SelectedOperationalLimitsGroupIdentifiers> selectedGroupIds = getSelectedOperationalLimitsGroupIds(networkId, variantNum, type, tombstonedElements, variantNum);
        if (NetworkAttributes.isFullVariant(fullVariantNum)) {
            return selectedGroupIds;
        }
        Map<OwnerInfo, SelectedOperationalLimitsGroupIdentifiers> fullVariantSelectedGroupIds = getSelectedOperationalLimitsGroupIds(networkId, fullVariantNum, type, tombstonedElements, variantNum);
        fullVariantSelectedGroupIds.putAll(selectedGroupIds);
        return fullVariantSelectedGroupIds;
    }

    private Map<OwnerInfo, SelectedOperationalLimitsGroupIdentifiers> getSelectedOperationalLimitsGroupIds(UUID networkId, int variantNum, ResourceType type, Set<String> tombstonedElements, int refVariantNum) {

        try (var connection = dataSource.getConnection()) {
            try (var preparedStmt = connection.prepareStatement(
                    QueryCatalog.buildGetSelectedOperationalLimitsGroupsQuery(mappings.getTableMapping(type).getTable()))) {
                preparedStmt.setObject(1, networkId);
                preparedStmt.setInt(2, variantNum);
                return getInnerSelectedOperationalLimitsGroupIds(networkId, type, preparedStmt, tombstonedElements, refVariantNum);
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private Map<OwnerInfo, SelectedOperationalLimitsGroupIdentifiers> getInnerSelectedOperationalLimitsGroupIds(UUID networkId, ResourceType type, PreparedStatement preparedStmt, Set<String> tombstonedElements, int refVariantNum) throws SQLException {
        try (ResultSet resultSet = preparedStmt.executeQuery()) {
            Map<OwnerInfo, SelectedOperationalLimitsGroupIdentifiers> resources = new HashMap<>();
            while (resultSet.next()) {
                String branchId = resultSet.getString(1);
                String operationalLimitsGroupId1 = resultSet.getString(2);
                String operationalLimitsGroupId2 = resultSet.getString(3);
                if (!tombstonedElements.contains(branchId)) {
                    resources.put(new OwnerInfo(branchId, type, networkId, refVariantNum), new SelectedOperationalLimitsGroupIdentifiers(branchId, operationalLimitsGroupId1, operationalLimitsGroupId2));
                }
            }
            return resources;
        }
    }

    record SelectedOperationalLimitsGroupIdentifiers(String branchId, String operationalLimitsGroupId1,
                                                     String operationalLimitsGroupId2) {
    }
}
