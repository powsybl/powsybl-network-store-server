/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server;

import static com.powsybl.network.store.server.QueryCatalog.*;

/**
 * @author Antoine Bouhours <antoine.bouhours at rte-france.com>
 */
public final class QueryExtensionCatalog {
    static final String EXTENSION_TABLE = "extension";
    static final String EXTENSION_NAME_COLUMN = "name";
    static final String EXTENSION_VALUE_COLUMN = "value_";
    static final String EXTENSION_RESOURCE_TYPE_COLUMN = "equipmenttype";
    static final String TOMBSTONED_EXTENSION_TABLE = "tombstonedextension";

    private QueryExtensionCatalog() {
    }

    public static String buildCloneExtensionsQuery() {
        return "insert into " + EXTENSION_TABLE + "(" + EQUIPMENT_ID_COLUMN + ", " + EQUIPMENT_TYPE_COLUMN +
                ", " + NETWORK_UUID_COLUMN + ", " + VARIANT_NUM_COLUMN + ", " + EXTENSION_NAME_COLUMN + ", " + EXTENSION_VALUE_COLUMN + ") select " +
                EQUIPMENT_ID_COLUMN + ", " + EQUIPMENT_TYPE_COLUMN +
                ", ?, ?, name, " + EXTENSION_VALUE_COLUMN + " from " + EXTENSION_TABLE + " where " + NETWORK_UUID_COLUMN +
                " = ? and " + VARIANT_NUM_COLUMN + " = ?";
    }

    public static String buildGetExtensionsQuery() {
        return "select " + EXTENSION_VALUE_COLUMN +
                " from " + EXTENSION_TABLE + " where " +
                NETWORK_UUID_COLUMN + " = ? and " +
                VARIANT_NUM_COLUMN + " = ? and " +
                EQUIPMENT_ID_COLUMN + " = ? and " +
                EXTENSION_NAME_COLUMN + " = ?";
    }

    public static String buildGetAllExtensionsAttributesByIdentifiableId() {
        return "select " + EXTENSION_NAME_COLUMN + ", " +
                EXTENSION_VALUE_COLUMN +
                " from " + EXTENSION_TABLE + " where " +
                NETWORK_UUID_COLUMN + " = ? and " +
                VARIANT_NUM_COLUMN + " = ? and " +
                EQUIPMENT_ID_COLUMN + " = ?";
    }

    public static String buildGetAllExtensionsAttributesByResourceType() {
        return "select " + EQUIPMENT_ID_COLUMN + ", " +
                EXTENSION_NAME_COLUMN + ", " +
                EXTENSION_VALUE_COLUMN +
                " from " + EXTENSION_TABLE + " where " +
                NETWORK_UUID_COLUMN + " = ? and " +
                VARIANT_NUM_COLUMN + " = ? and " +
                EQUIPMENT_TYPE_COLUMN + " = ?";
    }

    public static String buildGetAllExtensionsAttributesByResourceTypeAndExtensionName() {
        return "select " + EQUIPMENT_ID_COLUMN + ", " +
                EXTENSION_VALUE_COLUMN +
                " from " + EXTENSION_TABLE + " where " +
                NETWORK_UUID_COLUMN + " = ? and " +
                VARIANT_NUM_COLUMN + " = ? and " +
                EXTENSION_RESOURCE_TYPE_COLUMN + " = ? and " +
                EXTENSION_NAME_COLUMN + " = ?";
    }

    public static String buildInsertExtensionsQuery() {
        return "insert into " + EXTENSION_TABLE + "(" +
                EQUIPMENT_ID_COLUMN + ", " + EQUIPMENT_TYPE_COLUMN + ", " +
                NETWORK_UUID_COLUMN + " ," +
                VARIANT_NUM_COLUMN + " ," +
                EXTENSION_NAME_COLUMN + " ," +
                EXTENSION_VALUE_COLUMN + ")" +
                " values (?, ?, ?, ?, ?, ?)";
    }

    public static String buildDeleteExtensionsVariantEquipmentINQuery(int numberOfValues) {
        if (numberOfValues < 1) {
            throw new IllegalArgumentException(MINIMAL_VALUE_REQUIREMENT_ERROR);
        }
        return "delete from " + EXTENSION_TABLE + " where " +
                NETWORK_UUID_COLUMN + " = ? and " +
                VARIANT_NUM_COLUMN + " = ? and " +
                EQUIPMENT_ID_COLUMN + " in (" +
                "?, ".repeat(numberOfValues - 1) + "?)";
    }

    public static String buildDeleteExtensionsVariantQuery() {
        return "delete from " + EXTENSION_TABLE + " where " +
                NETWORK_UUID_COLUMN + " = ? and " +
                VARIANT_NUM_COLUMN + " = ?";
    }

    public static String buildDeleteExtensionsQuery() {
        return "delete from " + EXTENSION_TABLE + " where " +
                NETWORK_UUID_COLUMN + " = ?";
    }

    public static String buildDeleteExtensionsVariantByExtensionsNameAndIdentifiableIdsINQuery(int numberOfIds) {
        if (numberOfIds < 1) {
            throw new IllegalArgumentException(MINIMAL_VALUE_REQUIREMENT_ERROR);
        }

        return "delete from " + EXTENSION_TABLE +
                " where " + NETWORK_UUID_COLUMN + " = ? " +
                "and " + VARIANT_NUM_COLUMN + " = ? " +
                "and name = ? " +
                "and " + EQUIPMENT_ID_COLUMN + " in (" +
                "?, ".repeat(numberOfIds - 1) + "?)";
    }

    // Tombstoned extensions
    public static String buildInsertTombstonedExtensionsQuery() {
        return "insert into " + TOMBSTONED_EXTENSION_TABLE + " (" + NETWORK_UUID_COLUMN + ", " + VARIANT_NUM_COLUMN + ", " + EQUIPMENT_ID_COLUMN + ", " + EXTENSION_NAME_COLUMN + ") " +
                "values (?, ?, ?, ?)";
    }

    public static String buildGetTombstonedExtensionsQuery() {
        return "select " + EQUIPMENT_ID_COLUMN + ", " + EXTENSION_NAME_COLUMN + " FROM " + TOMBSTONED_EXTENSION_TABLE + " WHERE " + NETWORK_UUID_COLUMN + " = ? AND " + VARIANT_NUM_COLUMN + " = ?";
    }

    public static String buildIsTombstonedExtensionQuery() {
        return "select 1 from " + TOMBSTONED_EXTENSION_TABLE +
                " where " + NETWORK_UUID_COLUMN + " = ? and " + VARIANT_NUM_COLUMN + " = ? and " + EQUIPMENT_ID_COLUMN + " = ? and " + EXTENSION_NAME_COLUMN + " = ? limit 1";
    }

    public static String buildDeleteTombstonedExtensionsQuery() {
        return "delete from " + TOMBSTONED_EXTENSION_TABLE +
                " where " +
                NETWORK_UUID_COLUMN + " = ?";
    }

    public static String buildDeleteTombstonedExtensionsVariantQuery() {
        return "delete from " + TOMBSTONED_EXTENSION_TABLE +
                " where " +
                NETWORK_UUID_COLUMN + " = ?" + " and " +
                VARIANT_NUM_COLUMN + " = ?";
    }

    public static String buildCloneTombstonedExtensionsQuery() {
        return "insert into " + TOMBSTONED_EXTENSION_TABLE + " (" +
                NETWORK_UUID_COLUMN + ", " +
                VARIANT_NUM_COLUMN + ", " +
                EQUIPMENT_ID_COLUMN + ", " +
                EXTENSION_NAME_COLUMN + ") " +
                "select " +
                "?" + "," +
                "?" + "," +
                EQUIPMENT_ID_COLUMN + "," +
                EXTENSION_NAME_COLUMN +
                " from " + TOMBSTONED_EXTENSION_TABLE + " " +
                "where " +
                NETWORK_UUID_COLUMN + " = ?" + " and " +
                VARIANT_NUM_COLUMN + " = ? ";
    }
}
