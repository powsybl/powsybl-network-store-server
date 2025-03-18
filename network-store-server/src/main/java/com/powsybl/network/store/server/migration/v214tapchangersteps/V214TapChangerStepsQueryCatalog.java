/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server.migration.v214tapchangersteps;

import static com.powsybl.network.store.server.QueryCatalog.*;

/**
 * @author Etienne Lesot <etienne.lesot at rte-france.com>
 */
public final class V214TapChangerStepsQueryCatalog {

    static final String V214_TAP_CHANGER_STEP_TABLE = "v214tapChangerStep";

    private V214TapChangerStepsQueryCatalog() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static String buildCloneV214TapChangerStepQuery() {
        return "insert into " + V214_TAP_CHANGER_STEP_TABLE + "(" +
            EQUIPMENT_ID_COLUMN + ", " +
            EQUIPMENT_TYPE_COLUMN + ", " +
            NETWORK_UUID_COLUMN + "," +
            VARIANT_NUM_COLUMN + "," +
            INDEX_COLUMN + ", " +
            SIDE_COLUMN + ", " +
            TAPCHANGER_TYPE_COLUMN + ", " +
            "rho" + ", " +
            "r" + ", " +
            "x" + ", " +
            "g" + ", " +
            "b" + ", " +
            ALPHA_COLUMN + ") " +
            "select " +
            EQUIPMENT_ID_COLUMN + ", " +
            EQUIPMENT_TYPE_COLUMN + ", " +
            "?" + "," +
            "?" + "," +
            INDEX_COLUMN + ", " +
            SIDE_COLUMN + ", " +
            TAPCHANGER_TYPE_COLUMN + ", " +
            "rho" + ", " +
            "r" + ", " +
            "x" + ", " +
            "g" + ", " +
            "b" + ", " +
            ALPHA_COLUMN +
            " from " + V214_TAP_CHANGER_STEP_TABLE + " " +
            "where " +
            NETWORK_UUID_COLUMN + " = ?" + " and " +
            VARIANT_NUM_COLUMN + " = ? ";
    }

    public static String buildGetV214TapChangerStepQuery(String columnNameForWhereClause) {
        return "select " +
            EQUIPMENT_ID_COLUMN + ", " +
            EQUIPMENT_TYPE_COLUMN + ", " +
            NETWORK_UUID_COLUMN + "," +
            VARIANT_NUM_COLUMN + "," +
            INDEX_COLUMN + ", " +
            SIDE_COLUMN + ", " +
            TAPCHANGER_TYPE_COLUMN + ", " +
            "rho" + ", " +
            "r" + ", " +
            "x" + ", " +
            "g" + ", " +
            "b" + ", " +
            ALPHA_COLUMN +
            " from " + V214_TAP_CHANGER_STEP_TABLE + " " +
            "where " +
            NETWORK_UUID_COLUMN + " = ?" + " and " +
            VARIANT_NUM_COLUMN + " = ? and " +
            columnNameForWhereClause + " = ?" + "order by " + INDEX_COLUMN;
    }

    public static String buildV214TapChangerStepWithInClauseQuery(String columnNameForInClause, int numberOfValues) {
        if (numberOfValues < 1) {
            throw new IllegalArgumentException(MINIMAL_VALUE_REQUIREMENT_ERROR);
        }
        return "select " +
            EQUIPMENT_ID_COLUMN + ", " +
            EQUIPMENT_TYPE_COLUMN + ", " +
            NETWORK_UUID_COLUMN + "," +
            VARIANT_NUM_COLUMN + "," +
            INDEX_COLUMN + ", " +
            SIDE_COLUMN + ", " +
            TAPCHANGER_TYPE_COLUMN + ", " +
            "rho" + ", " +
            "r" + ", " +
            "x" + ", " +
            "g" + ", " +
            "b" + ", " +
            ALPHA_COLUMN +
            " from " + V214_TAP_CHANGER_STEP_TABLE + " " +
            "where " +
            NETWORK_UUID_COLUMN + " = ?" + " and " +
            VARIANT_NUM_COLUMN + " = ? and " +
            columnNameForInClause + " in (" +
            "?, ".repeat(numberOfValues - 1) + "?)";
    }

    public static String buildDeleteV214TapChangerStepQuery() {
        return "delete from " + V214_TAP_CHANGER_STEP_TABLE +
            " where " +
            NETWORK_UUID_COLUMN + " = ?";
    }

    public static String buildDeleteV214TapChangerStepVariantQuery() {
        return "delete from " + V214_TAP_CHANGER_STEP_TABLE +
            " where " +
            NETWORK_UUID_COLUMN + " = ?" + " and " +
            VARIANT_NUM_COLUMN + " = ?";
    }

    public static String buildDeleteV214TapChangerStepVariantEquipmentINQuery(int numberOfValues) {
        if (numberOfValues < 1) {
            throw new IllegalArgumentException(MINIMAL_VALUE_REQUIREMENT_ERROR);
        }
        return "delete from " + V214_TAP_CHANGER_STEP_TABLE +
            " where " +
            NETWORK_UUID_COLUMN + " = ? and " +
            VARIANT_NUM_COLUMN + " = ? and " +
            EQUIPMENT_ID_COLUMN + " in (" +
            "?, ".repeat(numberOfValues - 1) + "?)";
    }
}
