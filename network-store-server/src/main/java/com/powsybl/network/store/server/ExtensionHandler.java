package com.powsybl.network.store.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.powsybl.network.store.model.ExtensionAttributes;
import com.powsybl.network.store.model.IdentifiableAttributes;
import com.powsybl.network.store.model.Resource;
import com.powsybl.network.store.model.ResourceType;
import com.powsybl.network.store.server.exceptions.UncheckedSqlException;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static com.powsybl.network.store.server.NetworkStoreRepository.BATCH_SIZE;
import static com.powsybl.network.store.server.Utils.bindValues;

@Component
public class ExtensionHandler {
    private final DataSource dataSource;
    private final ObjectMapper mapper;

    public ExtensionHandler(DataSource dataSource, ObjectMapper mapper) {
        this.dataSource = dataSource;
        this.mapper = mapper;
    }

    public void insertExtensions(Map<OwnerInfo, Map<String, ExtensionAttributes>> extensions) {
        try (var connection = dataSource.getConnection()) {
            try (var preparedStmt = connection.prepareStatement(QueryExtensionCatalog.buildInsertExtensionsQuery())) {
                List<Object> values = new ArrayList<>(6);
                List<Map.Entry<OwnerInfo, Map<String, ExtensionAttributes>>> list = new ArrayList<>(extensions.entrySet());
                for (List<Map.Entry<OwnerInfo, Map<String, ExtensionAttributes>>> subExtensions : Lists.partition(list, BATCH_SIZE)) {
                    for (Map.Entry<OwnerInfo, Map<String, ExtensionAttributes>> entry : subExtensions) {
                        for (Map.Entry<String, ExtensionAttributes> extension : entry.getValue().entrySet()) {
                            values.clear();
                            // In order, from the QueryCatalog.buildInsertExtensionsQuery SQL query :
                            // equipmentId, equipmentType, networkUuid, variantNum, name, value_
                            values.add(entry.getKey().getEquipmentId());
                            values.add(entry.getKey().getEquipmentType().toString());
                            values.add(entry.getKey().getNetworkUuid());
                            values.add(entry.getKey().getVariantNum());
                            values.add(extension.getKey());
                            values.add(extension.getValue().toJson());
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

    //TODO remove?
    public void updateExtensions(Map<OwnerInfo, Map<String, ExtensionAttributes>> extensions) {
        try (var connection = dataSource.getConnection()) {
            try (var preparedStmt = connection.prepareStatement(QueryExtensionCatalog.buildUpdateExtensionsQuery())) {
                List<Object> values = new ArrayList<>(6);
                List<Map.Entry<OwnerInfo, Map<String, ExtensionAttributes>>> list = new ArrayList<>(extensions.entrySet());
                for (List<Map.Entry<OwnerInfo, Map<String, ExtensionAttributes>>> subExtensions : Lists.partition(list, BATCH_SIZE)) {
                    for (Map.Entry<OwnerInfo, Map<String, ExtensionAttributes>> entry : subExtensions) {
                        for (Map.Entry<String, ExtensionAttributes> extension : entry.getValue().entrySet()) {
                            values.clear();
                            values.add(extension.getValue().toJson());
                            values.add(extension.getKey());
                            values.add(entry.getKey().getEquipmentId());
                            values.add(entry.getKey().getEquipmentType().toString());
                            values.add(entry.getKey().getNetworkUuid());
                            values.add(entry.getKey().getVariantNum());
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

    public Map<OwnerInfo, Map<String, ExtensionAttributes>> getExtensionsWithInClause(UUID networkUuid, int variantNum, String columnNameForWhereClause, List<String> valuesForInClause) {
        if (valuesForInClause.isEmpty()) {
            return Collections.emptyMap();
        }
        try (var connection = dataSource.getConnection()) {
            var preparedStmt = connection.prepareStatement(QueryExtensionCatalog.buildExtensionsWithInClauseQuery(columnNameForWhereClause, valuesForInClause.size()));
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            for (int i = 0; i < valuesForInClause.size(); i++) {
                preparedStmt.setString(3 + i, valuesForInClause.get(i));
            }

            return innerGetExtensions(preparedStmt);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public Map<OwnerInfo, Map<String, ExtensionAttributes>> getExtensions(UUID networkUuid, int variantNum, String columnNameForWhereClause, String valueForWhereClause) {
        try (var connection = dataSource.getConnection()) {
            var preparedStmt = connection.prepareStatement(QueryExtensionCatalog.buildExtensionsQuery(columnNameForWhereClause));
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            preparedStmt.setString(3, valueForWhereClause);

            return innerGetExtensions(preparedStmt);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private Map<OwnerInfo, Map<String, ExtensionAttributes>> innerGetExtensions(PreparedStatement preparedStmt) throws SQLException {
        try (ResultSet resultSet = preparedStmt.executeQuery()) {
            Map<OwnerInfo, Map<String, ExtensionAttributes>> map = new HashMap<>();
            while (resultSet.next()) {

                OwnerInfo owner = new OwnerInfo();
                // In order, from the QueryCatalog.buildInsertExtensionsQuery SQL query :
                // equipmentId, equipmentType, networkUuid, variantNum, name, value_
                owner.setEquipmentId(resultSet.getString(1));
                owner.setEquipmentType(ResourceType.valueOf(resultSet.getString(2)));
                owner.setNetworkUuid(UUID.fromString(resultSet.getString(3)));
                owner.setVariantNum(resultSet.getInt(4));

                String extensionName = resultSet.getString(5);
                ExtensionAttributes extensionValue = mapper.readValue(resultSet.getString(6), ExtensionAttributes.class); // TODO can do better than object mapper? Use method fromJson?

                map.computeIfAbsent(owner, k -> new HashMap<>());
                map.get(owner).put(extensionName, extensionValue);
            }
            return map;
        } catch (JsonMappingException e) {
            throw new RuntimeException(e);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    protected <T extends IdentifiableAttributes> Map<OwnerInfo, Map<String, ExtensionAttributes>> getExtensionsFromEquipments(UUID networkUuid, List<Resource<T>> resources) {
        Map<OwnerInfo, Map<String, ExtensionAttributes>> map = new HashMap<>();

        if (!resources.isEmpty()) {
            for (Resource<T> resource : resources) {
                Map<String, ExtensionAttributes> extensions = resource.getAttributes().getExtensionAttributes();
                if (extensions != null && !extensions.isEmpty()) {
                    OwnerInfo info = new OwnerInfo(
                            resource.getId(),
                            resource.getType(),
                            networkUuid,
                            resource.getVariantNum()
                    );
                    map.put(info, extensions);
                }
            }
        }
        return map;
    }

    protected <T extends IdentifiableAttributes> void insertExtensionsInEquipments(UUID networkUuid, List<Resource<T>> equipments, Map<OwnerInfo, Map<String, ExtensionAttributes>> extensions) {
        if (!extensions.isEmpty() && !equipments.isEmpty()) {
            for (Resource<T> equipmentAttributesResource : equipments) {
                OwnerInfo owner = new OwnerInfo(
                        equipmentAttributesResource.getId(),
                        equipmentAttributesResource.getType(),
                        networkUuid,
                        equipmentAttributesResource.getVariantNum()
                );
                if (extensions.containsKey(owner)) {
                    T attributes = equipmentAttributesResource.getAttributes();
                    extensions.get(owner).forEach((key, value) -> {
                        if (attributes != null && attributes.getExtensionAttributes() != null) {
                            attributes.getExtensionAttributes().put(key, value);
                        }
                    });
                }
            }
        }
    }

    protected void deleteExtensions(UUID networkUuid, int variantNum, String equipmentId) {
        deleteExtensions(networkUuid, variantNum, List.of(equipmentId));
    }

    protected void deleteExtensions(UUID networkUuid, int variantNum, List<String> equipmentIds) {
        try (var connection = dataSource.getConnection()) {
            try (var preparedStmt = connection.prepareStatement(QueryExtensionCatalog.buildDeleteExtensionsVariantEquipmentINQuery(equipmentIds.size()))) {
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

    protected <T extends IdentifiableAttributes> void deleteExtensions(UUID networkUuid, List<Resource<T>> resources) {
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
        resourceIdsByVariant.forEach((k, v) -> deleteExtensions(networkUuid, k, v));
    }

    //TODO tests
    public <T extends IdentifiableAttributes> void updateExtensions(UUID networkUuid, List<Resource<T>> resources) {
        deleteExtensions(networkUuid, resources);
        insertExtensions(getExtensionsFromEquipments(networkUuid, resources));
    }
}
