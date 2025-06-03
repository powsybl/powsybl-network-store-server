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
import static com.powsybl.network.store.server.Utils.convertResourceTypeToTableName;

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

    // for one equipment
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
                        case VOLTAGE, VOLTAGE_ANGLE ->
                            throw new PowsyblException("Voltage and Voltage angle not supported for operational limits");
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
            case VOLTAGE, VOLTAGE_ANGLE -> throw new IllegalArgumentException("VOLTAGE and VOLTAGE_ANGLE does not support temporary limits");
        }
    }

    public Map<OperationalLimitsGroupIdentifier, OperationalLimitsGroupAttributes> getAllSelectedOperationalLimitsGroupAttributesByResourceType(
        UUID networkId, int variantNum, ResourceType type, Map<OwnerInfo, LimitsInfos> limitsInfos, int fullVariantNum, Set<String> tombstonedElements) {
        // get selected operational limits ids for each element of type indicated
        Map<OwnerInfo, SelectedOperationalLimitsGroupIdentifiers> selectedOperationalLimitsGroups =
            getPartialVariantSelectedOperationalLimitsGroupIds(networkId, variantNum, fullVariantNum, type, tombstonedElements);

        Map<OperationalLimitsGroupIdentifier, OperationalLimitsGroupAttributes> selectedOperationalLimitsGroupAttributes = new HashMap<>();
        // get operational limits group associated
        selectedOperationalLimitsGroups.forEach((owner, selectedOperationalLimitsGroupIdentifiers) -> {
            Optional<LimitsInfos> selectedLimitsInfos = Optional.ofNullable(limitsInfos.get(owner));

            // side 1
            if (selectedOperationalLimitsGroupIdentifiers.operationalLimitsGroupId1() != null) {
                Optional<OperationalLimitsGroupAttributes> operationalLimitsGroupAttributes = convertLimitInfosToOperationalLimitsGroup(
                    selectedLimitsInfos, selectedOperationalLimitsGroupIdentifiers.operationalLimitsGroupId1(), 1);
                operationalLimitsGroupAttributes.ifPresent(limitsGroupAttributes ->
                    selectedOperationalLimitsGroupAttributes.put(
                        OperationalLimitsGroupIdentifier.of(owner.getEquipmentId(), selectedOperationalLimitsGroupIdentifiers.operationalLimitsGroupId1(), 1),
                        limitsGroupAttributes));
            }
            // side 2
            if (selectedOperationalLimitsGroupIdentifiers.operationalLimitsGroupId2() != null) {
                Optional<OperationalLimitsGroupAttributes> operationalLimitsGroupAttributes = convertLimitInfosToOperationalLimitsGroup(
                    selectedLimitsInfos, selectedOperationalLimitsGroupIdentifiers.operationalLimitsGroupId2(), 2);
                operationalLimitsGroupAttributes.ifPresent(limitsGroupAttributes ->
                    selectedOperationalLimitsGroupAttributes.put(
                        OperationalLimitsGroupIdentifier.of(owner.getEquipmentId(), selectedOperationalLimitsGroupIdentifiers.operationalLimitsGroupId2(), 2),
                        limitsGroupAttributes));
            }
        });
        return selectedOperationalLimitsGroupAttributes;
    }

    private Map<OwnerInfo, SelectedOperationalLimitsGroupIdentifiers> getPartialVariantSelectedOperationalLimitsGroupIds(UUID networkId, int variantNum, int fullVariantNum, ResourceType type, Set<String> tombstonedElements) {
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
            try (var preparedStmt = connection.prepareStatement(QueryCatalog.buildGetIdentifiablesSpecificColumnsQuery(convertResourceTypeToTableName(type),
                List.of(ID_COLUMN, SELECTED_OPERATIONAL_LIMITS_GROUP_ID1, SELECTED_OPERATIONAL_LIMITS_GROUP_ID2)))) {
                preparedStmt.setObject(1, networkId);
                preparedStmt.setInt(2, variantNum);
                return getInnerSelectedOperationalLimitsGroupIds(networkId, type, preparedStmt, tombstonedElements, refVariantNum);
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
        // get selected operational Limits group
    }

    private Map<OwnerInfo, SelectedOperationalLimitsGroupIdentifiers> getInnerSelectedOperationalLimitsGroupIds(UUID networkId, ResourceType type, PreparedStatement preparedStmt, Set<String> tombstonedElements, int refVariantNum) throws SQLException {
        try (ResultSet resultSet = preparedStmt.executeQuery()) {
            Map<OwnerInfo, SelectedOperationalLimitsGroupIdentifiers> resources = new HashMap<>();
            while (resultSet.next()) {
                // first is ID
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
