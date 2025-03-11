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
import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.LimitType;
import com.powsybl.network.store.model.*;
import com.powsybl.network.store.server.dto.LimitsInfos;
import com.powsybl.network.store.server.dto.OwnerInfo;
import com.powsybl.network.store.server.dto.PermanentLimitAttributes;
import com.powsybl.network.store.server.exceptions.UncheckedSqlException;
import com.powsybl.network.store.server.json.PermanentLimitSqlData;
import com.powsybl.network.store.server.json.TemporaryLimitSqlData;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.powsybl.network.store.server.QueryCatalog.*;

/**
 * @author Etienne Lesot <etienne.lesot at rte-france.com>
 */
@Component
public class LimitsHandler {
    private final DataSource dataSource;
    private final ObjectMapper mapper;

    public LimitsHandler(DataSource dataSource, ObjectMapper mapper) {
        this.dataSource = dataSource;
        this.mapper = mapper;
    }

    public Optional<LimitsInfos> getLimitsInfos(UUID networkUuid, int variantNum, String type, String equipmentId) {
        List<TemporaryLimitAttributes> temporaryLimitAttributes = getTemporaryLimits(networkUuid, variantNum, type, equipmentId);
        List<PermanentLimitAttributes> permanentLimitAttributes = getPermanentLimits(networkUuid, variantNum, type, equipmentId);
        if (permanentLimitAttributes == null || permanentLimitAttributes.isEmpty() && temporaryLimitAttributes.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new LimitsInfos(permanentLimitAttributes, temporaryLimitAttributes));
    }

    // temporary limits
    public List<TemporaryLimitAttributes> getTemporaryLimits(UUID networkUuid, int variantNum, String type, String equipmentId) {
        try (Connection connection = dataSource.getConnection()) {
            var preparedStmt = connection.prepareStatement(QueryCatalog.buildTemporaryLimitQuery());
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            preparedStmt.setString(3, type);
            preparedStmt.setString(4, equipmentId);

            return innerGetTemporaryLimits(preparedStmt);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private List<TemporaryLimitAttributes> innerGetTemporaryLimits(PreparedStatement preparedStmt) throws SQLException {
        try (ResultSet resultSet = preparedStmt.executeQuery()) {
            if (resultSet.next()) {
                String temporaryLimitData = resultSet.getString(1);
                List<TemporaryLimitSqlData> parsedTemporaryLimitData = mapper.readValue(temporaryLimitData, new TypeReference<>() { });
                return parsedTemporaryLimitData.stream().map(TemporaryLimitSqlData::toTemporaryLimitAttributes).toList();
            }
            return Collections.emptyList();
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    // permanent limits
    public List<PermanentLimitAttributes> getPermanentLimits(UUID networkUuid, int variantNum, String type, String equipmentId) {
        try (Connection connection = dataSource.getConnection()) {
            var preparedStmt = connection.prepareStatement(QueryCatalog.buildGetPermanentLimitQuery());
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            preparedStmt.setString(3, type);
            preparedStmt.setString(4, equipmentId);

            return innerGetPermanentLimits(preparedStmt);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private List<PermanentLimitAttributes> innerGetPermanentLimits(PreparedStatement preparedStmt) throws SQLException {
        try (ResultSet resultSet = preparedStmt.executeQuery()) {
            if (resultSet.next()) {
                String permanentLimitData = resultSet.getString(1);
                List<PermanentLimitSqlData> parsedTemporaryLimitData = mapper.readValue(permanentLimitData, new TypeReference<>() { });
                return parsedTemporaryLimitData.stream().map(PermanentLimitSqlData::toPermanentLimitAttributes).toList();
            }
            return null;
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Optional<OperationalLimitsGroupAttributes> convertCurrentLimitInfosToOperationalLimitsGroup(Optional<LimitsInfos> limitsInfos,
                                                                                                        String operationalLimitsGroupName, int side) {

        if (limitsInfos.isPresent()) {
            LimitsAttributes.LimitsAttributesBuilder currentLimitsBuilder = LimitsAttributes.builder();
            List<TemporaryLimitAttributes> temporaryLimitAttributes = limitsInfos.get().getTemporaryLimits();
            TreeMap<Integer, TemporaryLimitAttributes> currentTemporaryLimitAttributes = new TreeMap<>(temporaryLimitAttributes.stream()
                .filter(tl -> tl.getLimitType().equals(LimitType.CURRENT)
                    && tl.getSide() == side
                    && tl.getOperationalLimitsGroupId().equals(operationalLimitsGroupName))
                .collect(Collectors.toMap(TemporaryLimitAttributes::getAcceptableDuration, Function.identity())));
            for (PermanentLimitAttributes permanentLimit : limitsInfos.get().getPermanentLimits()) {
                if (permanentLimit.getSide().equals(side)
                    && Objects.requireNonNull(permanentLimit.getLimitType()) == LimitType.CURRENT
                    && permanentLimit.getOperationalLimitsGroupId().equals(operationalLimitsGroupName)) {
                    currentLimitsBuilder
                        .operationalLimitsGroupId(operationalLimitsGroupName)
                        .permanentLimit(permanentLimit.getValue())
                        .temporaryLimits(currentTemporaryLimitAttributes);
                    break;
                }

            }
            return Optional.of(OperationalLimitsGroupAttributes.builder()
                .id(operationalLimitsGroupName)
                .currentLimits(currentLimitsBuilder.build())
                .build());
        } else {
            return Optional.empty();
        }

    }

    public Optional<OperationalLimitsGroupAttributes> getSelectedCurrentLimitsGroup(UUID networkId, int variantNum, String branchId, ResourceType type, String operationalLimitsGroupName, int side) {
        Optional<LimitsInfos> limitsInfos = getLimitsInfos(networkId, variantNum, type.toString(), branchId);
        return convertCurrentLimitInfosToOperationalLimitsGroup(limitsInfos, operationalLimitsGroupName, side);
    }

    private Optional<OperationalLimitsGroupAttributes> convertLimitInfosToOperationalLimitsGroup(Optional<LimitsInfos> limitsInfos,
                                                                                                 String operationalLimitsGroupName, int side) {
        if (limitsInfos.isPresent()) {
            LimitsAttributes.LimitsAttributesBuilder currentLimitsBuilder = LimitsAttributes.builder();
            LimitsAttributes.LimitsAttributesBuilder apparentPowerLimitsLimitsBuilder = LimitsAttributes.builder();
            LimitsAttributes.LimitsAttributesBuilder activePowerLimitsBuilder = LimitsAttributes.builder();
            List<TemporaryLimitAttributes> temporaryLimitAttributes = limitsInfos.get().getTemporaryLimits().stream().filter(tl -> tl.getSide() == side
                && tl.getOperationalLimitsGroupId().equals(operationalLimitsGroupName)).toList();
            TreeMap<Integer, TemporaryLimitAttributes> currentTemporaryLimitAttributes = new TreeMap<>(temporaryLimitAttributes.stream()
                .filter(tl -> tl.getLimitType().equals(LimitType.CURRENT))
                .collect(Collectors.toMap(TemporaryLimitAttributes::getAcceptableDuration, Function.identity())));
            TreeMap<Integer, TemporaryLimitAttributes> apparentPowerTemporaryLimitAttributes = new TreeMap<>(temporaryLimitAttributes.stream()
                .filter(tl -> tl.getLimitType().equals(LimitType.APPARENT_POWER))
                .collect(Collectors.toMap(TemporaryLimitAttributes::getAcceptableDuration, Function.identity())));
            TreeMap<Integer, TemporaryLimitAttributes> activePowerTemporaryLimitAttributes = new TreeMap<>(temporaryLimitAttributes.stream()
                .filter(tl -> tl.getLimitType().equals(LimitType.ACTIVE_POWER))
                .collect(Collectors.toMap(TemporaryLimitAttributes::getAcceptableDuration, Function.identity())));

            for (PermanentLimitAttributes permanentLimit : limitsInfos.get().getPermanentLimits()) {
                if (permanentLimit.getSide().equals(side) && permanentLimit.getOperationalLimitsGroupId().equals(operationalLimitsGroupName)) {
                    switch (permanentLimit.getLimitType()) {
                        case CURRENT -> currentLimitsBuilder
                            .operationalLimitsGroupId(operationalLimitsGroupName)
                            .permanentLimit(permanentLimit.getValue())
                            .temporaryLimits(currentTemporaryLimitAttributes);
                        case APPARENT_POWER -> apparentPowerLimitsLimitsBuilder
                            .operationalLimitsGroupId(operationalLimitsGroupName)
                            .permanentLimit(permanentLimit.getValue())
                            .temporaryLimits(apparentPowerTemporaryLimitAttributes);
                        case ACTIVE_POWER -> activePowerLimitsBuilder
                            .operationalLimitsGroupId(operationalLimitsGroupName)
                            .permanentLimit(permanentLimit.getValue())
                            .temporaryLimits(activePowerTemporaryLimitAttributes);
                    }
                }
            }
            return Optional.of(OperationalLimitsGroupAttributes.builder()
                .id(operationalLimitsGroupName)
                .currentLimits(currentLimitsBuilder.build())
                .apparentPowerLimits(apparentPowerLimitsLimitsBuilder.build())
                .activePowerLimits(activePowerLimitsBuilder.build())
                .build());
        } else {
            return Optional.empty();
        }

    }

    public Optional<OperationalLimitsGroupAttributes> getOperationalLimitsGroup(UUID networkId, int variantNum, String branchId, ResourceType type, String operationalLimitsGroupName, int side) {
        Optional<LimitsInfos> limitsInfos = getLimitsInfos(networkId, variantNum, type.toString(), branchId);
        return convertLimitInfosToOperationalLimitsGroup(limitsInfos, operationalLimitsGroupName, side);
    }

    public Map<OperationalLimitsGroupIdentifier, OperationalLimitsGroupAttributes> convertLimitInfosToOperationalLimitsGroup(OwnerInfo owner, LimitsInfos limitsInfos) {
        Map<OperationalLimitsGroupIdentifier, OperationalLimitsGroupAttributes> operationalLimitGroups = new HashMap<>();
        // permanent limits
        limitsInfos.getPermanentLimits().forEach(permanentLimit -> {
            OperationalLimitsGroupIdentifier identifier = new OperationalLimitsGroupIdentifier(owner.getEquipmentId(),
                permanentLimit.getOperationalLimitsGroupId(), permanentLimit.getSide());
            if (operationalLimitGroups.containsKey(identifier)) {
                setPermanentLimit(operationalLimitGroups.get(identifier), permanentLimit);
            } else {
                OperationalLimitsGroupAttributes operationalLimitsGroupAttributes = new OperationalLimitsGroupAttributes();
                operationalLimitsGroupAttributes.setId(owner.getEquipmentId());
                setPermanentLimit(operationalLimitsGroupAttributes, permanentLimit);
                operationalLimitGroups.put(identifier, operationalLimitsGroupAttributes);
            }
        });
        // temporary limits
        limitsInfos.getTemporaryLimits().forEach(temporaryLimit -> {
            OperationalLimitsGroupIdentifier identifier = new OperationalLimitsGroupIdentifier(owner.getEquipmentId(),
                temporaryLimit.getOperationalLimitsGroupId(), temporaryLimit.getSide());
            if (operationalLimitGroups.containsKey(identifier)) {
                setTemporaryLimit(operationalLimitGroups.get(identifier), temporaryLimit);
            } else {
                throw new PowsyblException("a limit groups can not have temporary limits without a permanent limit");
            }
        });
        return operationalLimitGroups;
    }

    private void setPermanentLimit(OperationalLimitsGroupAttributes operationalLimitsGroupAttributes, PermanentLimitAttributes permanentLimitAttributes) {
        LimitsAttributes limitsAttributes = new LimitsAttributes();
        limitsAttributes.setOperationalLimitsGroupId(operationalLimitsGroupAttributes.getId());
        limitsAttributes.setPermanentLimit(permanentLimitAttributes.getValue());
        switch (permanentLimitAttributes.getLimitType()) {
            case CURRENT -> operationalLimitsGroupAttributes.setCurrentLimits(limitsAttributes);
            case ACTIVE_POWER -> operationalLimitsGroupAttributes.setActivePowerLimits(limitsAttributes);
            case APPARENT_POWER -> operationalLimitsGroupAttributes.setApparentPowerLimits(limitsAttributes);
        }
    }

    private void setTemporaryLimit(OperationalLimitsGroupAttributes operationalLimitsGroupAttributes, TemporaryLimitAttributes temporaryLimitAttributes) {
        switch (temporaryLimitAttributes.getLimitType()) {
            case CURRENT -> operationalLimitsGroupAttributes.getCurrentLimits().addTemporaryLimit(temporaryLimitAttributes);
            case ACTIVE_POWER -> operationalLimitsGroupAttributes.getActivePowerLimits().addTemporaryLimit(temporaryLimitAttributes);
            case APPARENT_POWER -> operationalLimitsGroupAttributes.getApparentPowerLimits().addTemporaryLimit(temporaryLimitAttributes);
        }
    }

    public Map<String, Map<OperationalLimitsGroupIdentifier, OperationalLimitsGroupAttributes>> getAllCurrentLimitsGroupAttributesByResourceType(
        UUID networkId, int variantNum, ResourceType type, Map<OwnerInfo, LimitsInfos> limitsInfos) {
        Map<OwnerInfo, SelectedOperationalLimitsGroup> selectedOperationalLimitsGroups = getSelectedOperationalLimitsGroupIds(networkId, variantNum, type);
        Map<String, Map<OperationalLimitsGroupIdentifier, OperationalLimitsGroupAttributes>> map = new HashMap<>();
        selectedOperationalLimitsGroups.forEach((owner, selectedOperationalLimitsGroup) -> {
            LimitsInfos selectedLimitsInfos = limitsInfos.get(owner);
            if (selectedOperationalLimitsGroup.operationalLimitsGroupId1() != null) {
                OperationalLimitsGroupAttributes operationalLimitsGroupAttributes = getSelectedCurrentLimitOperationalLimitsGroupAttributes(
                    selectedOperationalLimitsGroup.operationalLimitsGroupId1(), 1, selectedLimitsInfos);
                if (operationalLimitsGroupAttributes != null) {
                    Map<OperationalLimitsGroupIdentifier, OperationalLimitsGroupAttributes> elementMap = new HashMap();
                    elementMap.put(OperationalLimitsGroupIdentifier.of(owner.getEquipmentId(), selectedOperationalLimitsGroup.operationalLimitsGroupId1(), 1),
                        operationalLimitsGroupAttributes);
                    map.put(owner.getEquipmentId(), elementMap);
                }
            }
            if (selectedOperationalLimitsGroup.operationalLimitsGroupId2() != null) {
                OperationalLimitsGroupAttributes operationalLimitsGroupAttributes = getSelectedCurrentLimitOperationalLimitsGroupAttributes(
                    selectedOperationalLimitsGroup.operationalLimitsGroupId2(), 2, selectedLimitsInfos);
                if (operationalLimitsGroupAttributes != null) {
                    if (map.containsKey(owner.getEquipmentId())) {
                        map.get(owner.getEquipmentId()).put(OperationalLimitsGroupIdentifier.of(owner.getEquipmentId(),
                                selectedOperationalLimitsGroup.operationalLimitsGroupId2(), 2),
                            getSelectedCurrentLimitOperationalLimitsGroupAttributes(
                                selectedOperationalLimitsGroup.operationalLimitsGroupId1(), 2, selectedLimitsInfos));
                    } else {
                        Map<OperationalLimitsGroupIdentifier, OperationalLimitsGroupAttributes> elementMap = new HashMap();
                        elementMap.put(OperationalLimitsGroupIdentifier.of(owner.getEquipmentId(), selectedOperationalLimitsGroup.operationalLimitsGroupId2(), 2),
                            getSelectedCurrentLimitOperationalLimitsGroupAttributes(
                                selectedOperationalLimitsGroup.operationalLimitsGroupId1(), 2, selectedLimitsInfos));
                        map.put(owner.getEquipmentId(), elementMap);
                    }
                }
            }
        });
        return map;
    }

    private OperationalLimitsGroupAttributes getSelectedCurrentLimitOperationalLimitsGroupAttributes(String selectedGroupId, Integer side, LimitsInfos limitsInfos) {
        List<PermanentLimitAttributes> permanentLimits = limitsInfos.getPermanentLimits().stream().filter(permanentLimitAttributes ->
                permanentLimitAttributes.getLimitType().equals(LimitType.CURRENT) &&
                    permanentLimitAttributes.getOperationalLimitsGroupId().equals(selectedGroupId) &&
                    permanentLimitAttributes.getSide().equals(side))
            .toList();
        if (permanentLimits.size() > 1) {
            throw new PowsyblException("found more than one permanent current limit for group : " + selectedGroupId + " and side : " + side);
        } else if (permanentLimits.isEmpty()) {
            return null;
        }
        TreeMap<Integer, TemporaryLimitAttributes> temporaryLimitsTreeMap = new TreeMap<>();
        limitsInfos.getTemporaryLimits().stream().filter(temporaryLimitAttributes ->
                temporaryLimitAttributes.getLimitType().equals(LimitType.CURRENT) &&
                    temporaryLimitAttributes.getOperationalLimitsGroupId().equals(selectedGroupId) &&
                    temporaryLimitAttributes.getSide().equals(side))
            .forEach(temporaryLimitAttributes -> temporaryLimitsTreeMap.put(temporaryLimitAttributes.getAcceptableDuration(), temporaryLimitAttributes));
        return OperationalLimitsGroupAttributes.builder()
            .id(selectedGroupId)
            .currentLimits(LimitsAttributes.builder()
                .operationalLimitsGroupId(selectedGroupId)
                .permanentLimit(permanentLimits.getFirst().getValue())
                .temporaryLimits(temporaryLimitsTreeMap)
                .build())
            .build();
    }

    private Map<OwnerInfo, SelectedOperationalLimitsGroup> getSelectedOperationalLimitsGroupIds(UUID networkId, int variantNum, ResourceType type) {
        try (var connection = dataSource.getConnection()) {
            try (var preparedStmt = connection.prepareStatement(QueryCatalog.buildGetIdentifiablesSpecificColumnsQuery(convertResourceTypeToTableName(type),
                List.of(ID_COLUMN, SELECTED_OPERATIONAL_LIMITS_GROUP_ID1, SELECTED_OPERATIONAL_LIMITS_GROUP_ID2)))) {
                preparedStmt.setObject(1, networkId);
                preparedStmt.setInt(2, variantNum);
                return getInnerSelectedOperationalLimitsGroupIds(networkId, variantNum, type, preparedStmt);
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
        // get selected operational Limits group
    }

    private Map<OwnerInfo, SelectedOperationalLimitsGroup> getInnerSelectedOperationalLimitsGroupIds(UUID networkId, int variantNum, ResourceType type, PreparedStatement preparedStmt) throws SQLException {
        try (ResultSet resultSet = preparedStmt.executeQuery()) {
            Map<OwnerInfo, SelectedOperationalLimitsGroup> resources = new HashMap<>();
            while (resultSet.next()) {
                // first is ID
                String branchId = resultSet.getString(1);
                String operationalLimitsGroupId1 = resultSet.getString(2);
                String operationalLimitsGroupId2 = resultSet.getString(3);
                resources.put(new OwnerInfo(branchId, type, networkId, variantNum), new SelectedOperationalLimitsGroup(branchId, operationalLimitsGroupId1, operationalLimitsGroupId2));
            }
            return resources;
        }
    }

    private String convertResourceTypeToTableName(ResourceType resourceType) {
        return switch (resourceType) {
            case LINE -> "line";
            case TWO_WINDINGS_TRANSFORMER -> "twowindingstranformer";
            case THREE_WINDINGS_TRANSFORMER -> "threewindingstranformer";
            default -> throw new PowsyblException("no table name for resource type " + resourceType);
        };
    }

    record SelectedOperationalLimitsGroup(String branchId, String operationalLimitsGroupId1,
                                          String operationalLimitsGroupId2) {
    }
}
