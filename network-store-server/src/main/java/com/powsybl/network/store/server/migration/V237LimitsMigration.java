/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Stopwatch;
import com.powsybl.network.store.model.OperationalLimitsGroupAttributes;
import com.powsybl.network.store.model.ResourceType;
import com.powsybl.network.store.server.ExtensionHandler;
import com.powsybl.network.store.server.LimitsHandler;
import com.powsybl.network.store.server.Mappings;
import com.powsybl.network.store.server.NetworkStoreRepository;
import com.powsybl.network.store.server.dto.OperationalLimitsGroupOwnerInfo;
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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author Etienne Lesot <etienne.lesot at rte-france.com>
 */
public class V237LimitsMigration implements CustomTaskChange {

    private static final Logger LOGGER = LoggerFactory.getLogger(V237LimitsMigration.class);
    private NetworkStoreRepository repository;

    @Override
    public String getConfirmationMessage() {
        return "V2.37 limits were successfully migrated";
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

    public void init(Database database) {
        DataSource dataSource = new SingleConnectionDataSource(((JdbcConnection) database.getConnection()).getUnderlyingConnection(), true);
        ObjectMapper mapper = new ObjectMapper();
        Mappings mappings = new Mappings();
        this.repository = new NetworkStoreRepository(dataSource, mapper, mappings, new ExtensionHandler(mapper), new LimitsHandler(dataSource, mapper, mappings));
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
                migrateV237LimitsQuietly(repository, networkId, variantNum, exceptions);
            }
        } catch (Exception e) {
            throw new CustomChangeException("V2.37 limits migration : error when getting the variants list", e);
        }
        if (!exceptions.isEmpty()) {
            throw new CustomChangeException("V2.37 limits migration failed. " + exceptions.size() + " exceptions were thrown. First exception as cause : ", exceptions.get(0));
        }
    }

    public static void migrateV237LimitsQuietly(NetworkStoreRepository repository, UUID networkId, int variantNum, List<Exception> exceptions) {
        try {
            migrateV237Limits(repository, networkId, variantNum);
        } catch (Exception e) {
            LOGGER.error("V2.37 limits migration : failure for network " + networkId + "/variantNum=" + variantNum, e);
            exceptions.add(e);
        }
    }

    public static void migrateV237Limits(NetworkStoreRepository repository, UUID networkId, int variantNum) {
        Stopwatch stopwatch = Stopwatch.createStarted();

        migrateOlgForEquipmentType(repository, networkId, variantNum, ResourceType.LINE);
        migrateOlgForEquipmentType(repository, networkId, variantNum, ResourceType.TWO_WINDINGS_TRANSFORMER);

        stopwatch.stop();
        LOGGER.info("The limits of network {}/variantNum={} were migrated in {} ms.", networkId, variantNum, stopwatch.elapsed(TimeUnit.MILLISECONDS));
        LOGGER.info("=============================================================================================================\n\n\n\n");
    }

    private static void migrateOlgForEquipmentType(NetworkStoreRepository repository, UUID networkId, int variantNum, ResourceType resourceType) {
        Map<String, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> operationalLimitsGroupsMap = repository.getAllOperationalLimitsGroupAttributesByResourceType(networkId, variantNum, resourceType);
        repository.deleteOperationalLimitsGroups(networkId, variantNum, operationalLimitsGroupsMap.keySet().stream().toList());
        repository.insertOperationalLimitsGroups(convertOlgMap(networkId, variantNum, resourceType, operationalLimitsGroupsMap));
    }

    private static Map<OperationalLimitsGroupOwnerInfo, OperationalLimitsGroupAttributes> convertOlgMap(UUID networkId, int variantNum, ResourceType resourceType, Map<String, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> operationalLimitsGroupsMap) {
        Map<OperationalLimitsGroupOwnerInfo, OperationalLimitsGroupAttributes> newMap = new HashMap<>();
        operationalLimitsGroupsMap.forEach((equipmentId, olgPerEquipmentId) ->
                olgPerEquipmentId.forEach((side, olgPerEquipmentIdPerSide) -> olgPerEquipmentIdPerSide.forEach((olgId, olg) -> {
                    newMap.put(new OperationalLimitsGroupOwnerInfo(equipmentId, resourceType, networkId, variantNum, olgId, side), olg);
                })));
        return newMap;
    }
}
