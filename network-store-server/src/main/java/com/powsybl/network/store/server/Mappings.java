/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server;

import com.powsybl.iidm.network.*;
import com.powsybl.network.store.model.*;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static com.powsybl.network.store.server.QueryCatalog.*;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
@Getter
public class Mappings {

    private static final Supplier<IdentifiableAttributes> THREE_WINDINGS_TRANSFORMER_ATTRIBUTES_SUPPLIER = () -> {
        ThreeWindingsTransformerAttributes attributes = new ThreeWindingsTransformerAttributes();
        attributes.setLeg1(LegAttributes.builder().legNumber(1).build());
        attributes.setLeg2(LegAttributes.builder().legNumber(2).build());
        attributes.setLeg3(LegAttributes.builder().legNumber(3).build());
        return attributes;
    };

    static final String NETWORK_TABLE = "network";
    static final String SUBSTATION_TABLE = "substation";
    static final String VOLTAGE_LEVEL_TABLE = "voltageLevel";
    static final String GENERATOR_TABLE = "generator";
    static final String BATTERY_TABLE = "battery";
    static final String SHUNT_COMPENSATOR_TABLE = "shuntCompensator";
    static final String VSC_CONVERTER_STATION_TABLE = "vscConverterStation";
    static final String LCC_CONVERTER_STATION_TABLE = "lccConverterStation";
    static final String STATIC_VAR_COMPENSATOR_TABLE = "staticVarCompensator";
    static final String BUSBAR_SECTION_TABLE = "busbarSection";
    static final String SWITCH_TABLE = "switch";
    static final String TWO_WINDINGS_TRANSFORMER_TABLE = "twoWindingsTransformer";
    static final String THREE_WINDINGS_TRANSFORMER_TABLE = "threeWindingsTransformer";
    static final String HVDC_LINE_TABLE = "hvdcLine";
    static final String DANGLING_LINE_TABLE = "danglingLine";
    static final String CONFIGURED_BUS_TABLE = "configuredBus";
    static final String LOAD_TABLE = "load";
    static final String LINE_TABLE = "line";
    static final String TIE_LINE_TABLE = "tieLine";
    static final String GROUND_TABLE = "ground";
    static final String AREA_TABLE = "area";

    static final List<String> ELEMENT_TABLES = List.of(SUBSTATION_TABLE, VOLTAGE_LEVEL_TABLE, BUSBAR_SECTION_TABLE, CONFIGURED_BUS_TABLE, SWITCH_TABLE, GENERATOR_TABLE, BATTERY_TABLE, LOAD_TABLE, SHUNT_COMPENSATOR_TABLE,
            STATIC_VAR_COMPENSATOR_TABLE, VSC_CONVERTER_STATION_TABLE, LCC_CONVERTER_STATION_TABLE, TWO_WINDINGS_TRANSFORMER_TABLE,
            THREE_WINDINGS_TRANSFORMER_TABLE, LINE_TABLE, HVDC_LINE_TABLE, DANGLING_LINE_TABLE, TIE_LINE_TABLE, GROUND_TABLE, AREA_TABLE);

    private final TableMapping lineMappings = new TableMapping(LINE_TABLE, ResourceType.LINE, Resource::lineBuilder, LineAttributes::new, Set.of(VOLTAGE_LEVEL_ID_1_COLUMN, VOLTAGE_LEVEL_ID_2_COLUMN));
    private final TableMapping loadMappings = new TableMapping(LOAD_TABLE, ResourceType.LOAD, Resource::loadBuilder, LoadAttributes::new, Set.of(VOLTAGE_LEVEL_ID_COLUMN));
    private final TableMapping generatorMappings = new TableMapping(GENERATOR_TABLE, ResourceType.GENERATOR, Resource::generatorBuilder, GeneratorAttributes::new, Set.of(VOLTAGE_LEVEL_ID_COLUMN));
    private final TableMapping switchMappings = new TableMapping(SWITCH_TABLE, ResourceType.SWITCH, Resource::switchBuilder, SwitchAttributes::new, Set.of(VOLTAGE_LEVEL_ID_COLUMN));
    private final TableMapping substationMappings = new TableMapping(SUBSTATION_TABLE, ResourceType.SUBSTATION, Resource::substationBuilder, SubstationAttributes::new, Collections.emptySet());
    private final TableMapping networkMappings = new TableMapping(NETWORK_TABLE, ResourceType.NETWORK, Resource::networkBuilder, NetworkAttributes::new, Collections.emptySet());
    private final TableMapping voltageLevelMappings = new TableMapping(VOLTAGE_LEVEL_TABLE, ResourceType.VOLTAGE_LEVEL, Resource::voltageLevelBuilder, VoltageLevelAttributes::new, Collections.emptySet());
    private final TableMapping batteryMappings = new TableMapping(BATTERY_TABLE, ResourceType.BATTERY, Resource::batteryBuilder, BatteryAttributes::new, Set.of(VOLTAGE_LEVEL_ID_COLUMN));
    private final TableMapping busbarSectionMappings = new TableMapping(BUSBAR_SECTION_TABLE, ResourceType.BUSBAR_SECTION, Resource::busbarSectionBuilder, BusbarSectionAttributes::new, Set.of(VOLTAGE_LEVEL_ID_COLUMN));
    private final TableMapping configuredBusMappings = new TableMapping(CONFIGURED_BUS_TABLE, ResourceType.CONFIGURED_BUS, Resource::configuredBusBuilder, ConfiguredBusAttributes::new, Set.of(VOLTAGE_LEVEL_ID_COLUMN));
    private final TableMapping danglingLineMappings = new TableMapping(DANGLING_LINE_TABLE, ResourceType.DANGLING_LINE, Resource::danglingLineBuilder, DanglingLineAttributes::new, Set.of(VOLTAGE_LEVEL_ID_COLUMN));
    private final TableMapping shuntCompensatorMappings = new TableMapping(SHUNT_COMPENSATOR_TABLE, ResourceType.SHUNT_COMPENSATOR, Resource::shuntCompensatorBuilder, ShuntCompensatorAttributes::new, Set.of(VOLTAGE_LEVEL_ID_COLUMN));
    private final TableMapping vscConverterStationMappings = new TableMapping(VSC_CONVERTER_STATION_TABLE, ResourceType.VSC_CONVERTER_STATION, Resource::vscConverterStationBuilder, VscConverterStationAttributes::new, Set.of(VOLTAGE_LEVEL_ID_COLUMN));
    private final TableMapping lccConverterStationMappings = new TableMapping(LCC_CONVERTER_STATION_TABLE, ResourceType.LCC_CONVERTER_STATION, Resource::lccConverterStationBuilder, LccConverterStationAttributes::new, Set.of(VOLTAGE_LEVEL_ID_COLUMN));
    private final TableMapping staticVarCompensatorMappings = new TableMapping(STATIC_VAR_COMPENSATOR_TABLE, ResourceType.STATIC_VAR_COMPENSATOR, Resource::staticVarCompensatorBuilder, StaticVarCompensatorAttributes::new, Set.of(VOLTAGE_LEVEL_ID_COLUMN));
    private final TableMapping hvdcLineMappings = new TableMapping(HVDC_LINE_TABLE, ResourceType.HVDC_LINE, Resource::hvdcLineBuilder, HvdcLineAttributes::new, Set.of(VOLTAGE_LEVEL_ID_COLUMN));
    private final TableMapping twoWindingsTransformerMappings = new TableMapping(TWO_WINDINGS_TRANSFORMER_TABLE, ResourceType.TWO_WINDINGS_TRANSFORMER, Resource::twoWindingsTransformerBuilder, TwoWindingsTransformerAttributes::new, Set.of(VOLTAGE_LEVEL_ID_1_COLUMN, VOLTAGE_LEVEL_ID_2_COLUMN));
    private final TableMapping threeWindingsTransformerMappings = new TableMapping(THREE_WINDINGS_TRANSFORMER_TABLE, ResourceType.THREE_WINDINGS_TRANSFORMER, Resource::threeWindingsTransformerBuilder, THREE_WINDINGS_TRANSFORMER_ATTRIBUTES_SUPPLIER, Set.of(VOLTAGE_LEVEL_ID_1_COLUMN, VOLTAGE_LEVEL_ID_2_COLUMN, VOLTAGE_LEVEL_ID_3_COLUMN));
    private final TableMapping tieLineMappings = new TableMapping(TIE_LINE_TABLE, ResourceType.TIE_LINE, Resource::tieLineBuilder, TieLineAttributes::new, Collections.emptySet());
    private final TableMapping groundMappings = new TableMapping(GROUND_TABLE, ResourceType.GROUND, Resource::groundBuilder, GroundAttributes::new, Set.of(VOLTAGE_LEVEL_ID_COLUMN));
    private final TableMapping areaMappings = new TableMapping(AREA_TABLE, ResourceType.AREA, Resource::areaBuilder, AreaAttributes::new, Collections.emptySet());

    private final List<TableMapping> all = List.of(lineMappings,
                                                   loadMappings,
                                                   generatorMappings,
                                                   switchMappings,
                                                   substationMappings,
                                                   networkMappings,
                                                   voltageLevelMappings,
                                                   batteryMappings,
                                                   busbarSectionMappings,
                                                   configuredBusMappings,
                                                   danglingLineMappings,
                                                   shuntCompensatorMappings,
                                                   vscConverterStationMappings,
                                                   vscConverterStationMappings,
                                                   lccConverterStationMappings,
                                                   staticVarCompensatorMappings,
                                                   hvdcLineMappings,
                                                   twoWindingsTransformerMappings,
                                                   threeWindingsTransformerMappings,
                                                   tieLineMappings,
                                                   groundMappings,
                                                   areaMappings);

    private final Map<String, TableMapping> mappingByTable = new LinkedHashMap<>();
    private final Map<ResourceType, TableMapping> mappingByType = new LinkedHashMap<>();

    private static final String VOLTAGE_LEVEL_ID = "voltageLevelId";
    private static final String VOLTAGE_LEVEL_ID_1 = "voltageLevelId1";
    private static final String VOLTAGE_LEVEL_ID_2 = "voltageLevelId2";
    private static final String CONNECTABLE_BUS = "connectableBus";
    private static final String CONNECTABLE_BUS_1 = "connectableBus1";
    private static final String CONNECTABLE_BUS_2 = "connectableBus2";
    private static final String FICTITIOUS = "fictitious";
    private static final String NODE = "node";
    private static final String NODE_1 = "node1";
    private static final String NODE_2 = "node2";
    private static final String BUS = "bus";
    private static final String R_VALUE = "r";
    private static final String X_VALUE = "x";
    private static final String G_VALUE = "g";
    private static final String B_VALUE = "b";
    private static final String RATED_U = "ratedU";
    private static final String RATED_S = "ratedS";
    private static final String PROPERTIES = "properties";
    private static final String ALIAS_BY_TYPE = "aliasByType";
    private static final String ALIASES_WITHOUT_TYPE = "aliasesWithoutType";
    private static final String POSITION = "position";
    private static final String POSITION_1 = "position1";
    private static final String POSITION_2 = "position2";
    private static final String SELECTED_OPERATIONAL_LIMITS_GROUP_ID_COLUMN = "selectedOperationalLimitsGroupId";
    private static final String SELECTED_OPERATIONAL_LIMITS_GROUP_ID1_COLUMN = "selectedOperationalLimitsGroupId1";
    private static final String SELECTED_OPERATIONAL_LIMITS_GROUP_ID2_COLUMN = "selectedOperationalLimitsGroupId2";
    private static final String MINQ = "minQ";
    private static final String MAXQ = "maxQ";
    private static final String TIE_LINE_ID = "tieLineId";

    public TableMapping getTableMapping(String table) {
        Objects.requireNonNull(table);
        TableMapping tableMapping = mappingByTable.get(table);
        if (tableMapping == null) {
            throw new IllegalArgumentException("Unknown table: " + table);
        }
        return tableMapping;
    }

    public TableMapping getTableMapping(ResourceType type) {
        Objects.requireNonNull(type);
        TableMapping tableMapping = mappingByType.get(type);
        if (tableMapping == null) {
            throw new IllegalArgumentException("Unknown type: " + type);
        }
        return tableMapping;
    }

    private void createLineMappings() {
        lineMappings.addColumnMapping("name", new ColumnMapping<>(String.class, LineAttributes::getName, LineAttributes::setName));
        lineMappings.addColumnMapping(VOLTAGE_LEVEL_ID_1, new ColumnMapping<>(String.class, LineAttributes::getVoltageLevelId1, LineAttributes::setVoltageLevelId1));
        lineMappings.addColumnMapping(VOLTAGE_LEVEL_ID_2, new ColumnMapping<>(String.class, LineAttributes::getVoltageLevelId2, LineAttributes::setVoltageLevelId2));
        lineMappings.addColumnMapping("bus1", new ColumnMapping<>(String.class, LineAttributes::getBus1, LineAttributes::setBus1));
        lineMappings.addColumnMapping("bus2", new ColumnMapping<>(String.class, LineAttributes::getBus2, LineAttributes::setBus2));
        lineMappings.addColumnMapping(CONNECTABLE_BUS_1, new ColumnMapping<>(String.class, LineAttributes::getConnectableBus1, LineAttributes::setConnectableBus1));
        lineMappings.addColumnMapping(CONNECTABLE_BUS_2, new ColumnMapping<>(String.class, LineAttributes::getConnectableBus2, LineAttributes::setConnectableBus2));
        lineMappings.addColumnMapping("r", new ColumnMapping<>(Double.class, LineAttributes::getR, LineAttributes::setR));
        lineMappings.addColumnMapping("x", new ColumnMapping<>(Double.class, LineAttributes::getX, LineAttributes::setX));
        lineMappings.addColumnMapping("g1", new ColumnMapping<>(Double.class, LineAttributes::getG1, LineAttributes::setG1));
        lineMappings.addColumnMapping("b1", new ColumnMapping<>(Double.class, LineAttributes::getB1, LineAttributes::setB1));
        lineMappings.addColumnMapping("g2", new ColumnMapping<>(Double.class, LineAttributes::getG2, LineAttributes::setG2));
        lineMappings.addColumnMapping("b2", new ColumnMapping<>(Double.class, LineAttributes::getB2, LineAttributes::setB2));
        lineMappings.addColumnMapping("p1", new ColumnMapping<>(Double.class, LineAttributes::getP1, LineAttributes::setP1));
        lineMappings.addColumnMapping("q1", new ColumnMapping<>(Double.class, LineAttributes::getQ1, LineAttributes::setQ1));
        lineMappings.addColumnMapping("p2", new ColumnMapping<>(Double.class, LineAttributes::getP2, LineAttributes::setP2));
        lineMappings.addColumnMapping("q2", new ColumnMapping<>(Double.class, LineAttributes::getQ2, LineAttributes::setQ2));
        lineMappings.addColumnMapping(FICTITIOUS, new ColumnMapping<>(Boolean.class, LineAttributes::isFictitious, LineAttributes::setFictitious));
        lineMappings.addColumnMapping(NODE_1, new ColumnMapping<>(Integer.class, LineAttributes::getNode1, LineAttributes::setNode1));
        lineMappings.addColumnMapping(NODE_2, new ColumnMapping<>(Integer.class, LineAttributes::getNode2, LineAttributes::setNode2));
        lineMappings.addColumnMapping(PROPERTIES, new ColumnMapping<>(Map.class, LineAttributes::getProperties, LineAttributes::setProperties));
        lineMappings.addColumnMapping(ALIAS_BY_TYPE, new ColumnMapping<>(Map.class, LineAttributes::getAliasByType, LineAttributes::setAliasByType));
        lineMappings.addColumnMapping(ALIASES_WITHOUT_TYPE, new ColumnMapping<>(Set.class, LineAttributes::getAliasesWithoutType, LineAttributes::setAliasesWithoutType));
        lineMappings.addColumnMapping(POSITION_1, new ColumnMapping<>(ConnectablePositionAttributes.class, LineAttributes::getPosition1, LineAttributes::setPosition1));
        lineMappings.addColumnMapping(POSITION_2, new ColumnMapping<>(ConnectablePositionAttributes.class, LineAttributes::getPosition2, LineAttributes::setPosition2));
        lineMappings.addColumnMapping("mergedXnode", new ColumnMapping<>(MergedXnodeAttributes.class, LineAttributes::getMergedXnode, LineAttributes::setMergedXnode));
        lineMappings.addColumnMapping(SELECTED_OPERATIONAL_LIMITS_GROUP_ID1_COLUMN, new ColumnMapping<>(String.class, LineAttributes::getSelectedOperationalLimitsGroupId1, LineAttributes::setSelectedOperationalLimitsGroupId1));
        lineMappings.addColumnMapping(SELECTED_OPERATIONAL_LIMITS_GROUP_ID2_COLUMN, new ColumnMapping<>(String.class, LineAttributes::getSelectedOperationalLimitsGroupId2, LineAttributes::setSelectedOperationalLimitsGroupId2));
    }

    private void createLoadMappings() {
        loadMappings.addColumnMapping("name", new ColumnMapping<>(String.class, LoadAttributes::getName, LoadAttributes::setName));
        loadMappings.addColumnMapping(VOLTAGE_LEVEL_ID, new ColumnMapping<>(String.class, LoadAttributes::getVoltageLevelId, LoadAttributes::setVoltageLevelId));
        loadMappings.addColumnMapping("bus", new ColumnMapping<>(String.class, LoadAttributes::getBus, LoadAttributes::setBus));
        loadMappings.addColumnMapping(CONNECTABLE_BUS, new ColumnMapping<>(String.class, LoadAttributes::getConnectableBus, LoadAttributes::setConnectableBus));
        loadMappings.addColumnMapping("p0", new ColumnMapping<>(Double.class, LoadAttributes::getP0, LoadAttributes::setP0));
        loadMappings.addColumnMapping("q0", new ColumnMapping<>(Double.class, LoadAttributes::getQ0, LoadAttributes::setQ0));
        loadMappings.addColumnMapping("loadType", new ColumnMapping<>(LoadType.class, LoadAttributes::getLoadType, LoadAttributes::setLoadType));
        loadMappings.addColumnMapping("p", new ColumnMapping<>(Double.class, LoadAttributes::getP, LoadAttributes::setP));
        loadMappings.addColumnMapping("q", new ColumnMapping<>(Double.class, LoadAttributes::getQ, LoadAttributes::setQ));
        loadMappings.addColumnMapping(FICTITIOUS, new ColumnMapping<>(Boolean.class, LoadAttributes::isFictitious, LoadAttributes::setFictitious));
        loadMappings.addColumnMapping("node", new ColumnMapping<>(Integer.class, LoadAttributes::getNode, LoadAttributes::setNode));
        loadMappings.addColumnMapping(PROPERTIES, new ColumnMapping<>(Map.class, LoadAttributes::getProperties, LoadAttributes::setProperties));
        loadMappings.addColumnMapping(ALIAS_BY_TYPE, new ColumnMapping<>(Map.class, LoadAttributes::getAliasByType, LoadAttributes::setAliasByType));
        loadMappings.addColumnMapping(ALIASES_WITHOUT_TYPE, new ColumnMapping<>(Set.class, LoadAttributes::getAliasesWithoutType, LoadAttributes::setAliasesWithoutType));
        loadMappings.addColumnMapping(POSITION, new ColumnMapping<>(ConnectablePositionAttributes.class, LoadAttributes::getPosition, LoadAttributes::setPosition));
        loadMappings.addColumnMapping("loadDetail", new ColumnMapping<>(LoadDetailAttributes.class, LoadAttributes::getLoadDetail, LoadAttributes::setLoadDetail));
    }

    private void createGeneratorMappings() {
        generatorMappings.addColumnMapping("name", new ColumnMapping<>(String.class, GeneratorAttributes::getName, GeneratorAttributes::setName));
        generatorMappings.addColumnMapping(VOLTAGE_LEVEL_ID, new ColumnMapping<>(String.class, GeneratorAttributes::getVoltageLevelId, GeneratorAttributes::setVoltageLevelId));
        generatorMappings.addColumnMapping("bus", new ColumnMapping<>(String.class, GeneratorAttributes::getBus, GeneratorAttributes::setBus));
        generatorMappings.addColumnMapping(CONNECTABLE_BUS, new ColumnMapping<>(String.class, GeneratorAttributes::getConnectableBus, GeneratorAttributes::setConnectableBus));
        generatorMappings.addColumnMapping("minP", new ColumnMapping<>(Double.class, GeneratorAttributes::getMinP, GeneratorAttributes::setMinP));
        generatorMappings.addColumnMapping("maxP", new ColumnMapping<>(Double.class, GeneratorAttributes::getMaxP, GeneratorAttributes::setMaxP));
        generatorMappings.addColumnMapping("energySource", new ColumnMapping<>(EnergySource.class, GeneratorAttributes::getEnergySource, GeneratorAttributes::setEnergySource));
        generatorMappings.addColumnMapping("p", new ColumnMapping<>(Double.class, GeneratorAttributes::getP, GeneratorAttributes::setP));
        generatorMappings.addColumnMapping("q", new ColumnMapping<>(Double.class, GeneratorAttributes::getQ, GeneratorAttributes::setQ));
        generatorMappings.addColumnMapping(FICTITIOUS, new ColumnMapping<>(Boolean.class, GeneratorAttributes::isFictitious, GeneratorAttributes::setFictitious));
        generatorMappings.addColumnMapping("targetP", new ColumnMapping<>(Double.class, GeneratorAttributes::getTargetP, GeneratorAttributes::setTargetP));
        generatorMappings.addColumnMapping("targetQ", new ColumnMapping<>(Double.class, GeneratorAttributes::getTargetQ, GeneratorAttributes::setTargetQ));
        generatorMappings.addColumnMapping("targetV", new ColumnMapping<>(Double.class, GeneratorAttributes::getTargetV, GeneratorAttributes::setTargetV));
        generatorMappings.addColumnMapping(RATED_S, new ColumnMapping<>(Double.class, GeneratorAttributes::getRatedS, GeneratorAttributes::setRatedS));
        generatorMappings.addColumnMapping(MINQ, new ColumnMapping<>(Double.class,
            (GeneratorAttributes attributes) -> attributes.getReactiveLimits() instanceof MinMaxReactiveLimitsAttributes ? ((MinMaxReactiveLimitsAttributes) attributes.getReactiveLimits()).getMinQ() : null,
            (GeneratorAttributes attributes, Double value) -> {
                if (attributes.getReactiveLimits() == null) {
                    attributes.setReactiveLimits(new MinMaxReactiveLimitsAttributes());
                }
                ((MinMaxReactiveLimitsAttributes) attributes.getReactiveLimits()).setMinQ(value);
            }));
        generatorMappings.addColumnMapping(MAXQ, new ColumnMapping<>(Double.class,
            (GeneratorAttributes attributes) -> attributes.getReactiveLimits() instanceof MinMaxReactiveLimitsAttributes ? ((MinMaxReactiveLimitsAttributes) attributes.getReactiveLimits()).getMaxQ() : null,
            (GeneratorAttributes attributes, Double value) -> {
                if (attributes.getReactiveLimits() == null) {
                    attributes.setReactiveLimits(new MinMaxReactiveLimitsAttributes());
                }
                ((MinMaxReactiveLimitsAttributes) attributes.getReactiveLimits()).setMaxQ(value);
            }));
        generatorMappings.addColumnMapping("coordinatedReactiveControl", new ColumnMapping<>(CoordinatedReactiveControlAttributes.class, GeneratorAttributes::getCoordinatedReactiveControl, GeneratorAttributes::setCoordinatedReactiveControl));
        generatorMappings.addColumnMapping("remoteReactivePowerControl", new ColumnMapping<>(RemoteReactivePowerControlAttributes.class, GeneratorAttributes::getRemoteReactivePowerControl, GeneratorAttributes::setRemoteReactivePowerControl));
        generatorMappings.addColumnMapping("entsoeCategory", new ColumnMapping<>(GeneratorEntsoeCategoryAttributes.class, GeneratorAttributes::getEntsoeCategoryAttributes, GeneratorAttributes::setEntsoeCategoryAttributes));
        generatorMappings.addColumnMapping("node", new ColumnMapping<>(Integer.class, GeneratorAttributes::getNode, GeneratorAttributes::setNode));
        generatorMappings.addColumnMapping(PROPERTIES, new ColumnMapping<>(Map.class, GeneratorAttributes::getProperties, GeneratorAttributes::setProperties));
        generatorMappings.addColumnMapping(ALIAS_BY_TYPE, new ColumnMapping<>(Map.class, GeneratorAttributes::getAliasByType, GeneratorAttributes::setAliasByType));
        generatorMappings.addColumnMapping(ALIASES_WITHOUT_TYPE, new ColumnMapping<>(Set.class, GeneratorAttributes::getAliasesWithoutType, GeneratorAttributes::setAliasesWithoutType));
        generatorMappings.addColumnMapping(POSITION, new ColumnMapping<>(ConnectablePositionAttributes.class, GeneratorAttributes::getPosition, GeneratorAttributes::setPosition));
        generatorMappings.addColumnMapping("generatorShortCircuit", new ColumnMapping<>(ShortCircuitAttributes.class, GeneratorAttributes::getGeneratorShortCircuitAttributes, GeneratorAttributes::setGeneratorShortCircuitAttributes));
        generatorMappings.addColumnMapping("condenser", new ColumnMapping<>(Boolean.class, GeneratorAttributes::isCondenser, GeneratorAttributes::setCondenser));
    }

    private void createSwitchMappings() {
        switchMappings.addColumnMapping("name", new ColumnMapping<>(String.class, SwitchAttributes::getName, SwitchAttributes::setName));
        switchMappings.addColumnMapping(VOLTAGE_LEVEL_ID, new ColumnMapping<>(String.class, SwitchAttributes::getVoltageLevelId, SwitchAttributes::setVoltageLevelId));
        switchMappings.addColumnMapping("bus1", new ColumnMapping<>(String.class, SwitchAttributes::getBus1, SwitchAttributes::setBus1));
        switchMappings.addColumnMapping("bus2", new ColumnMapping<>(String.class, SwitchAttributes::getBus2, SwitchAttributes::setBus2));
        switchMappings.addColumnMapping("kind", new ColumnMapping<>(SwitchKind.class, SwitchAttributes::getKind, SwitchAttributes::setKind));
        switchMappings.addColumnMapping("open", new ColumnMapping<>(Boolean.class, SwitchAttributes::isOpen, SwitchAttributes::setOpen));
        switchMappings.addColumnMapping("retained", new ColumnMapping<>(Boolean.class, SwitchAttributes::isRetained, SwitchAttributes::setRetained));
        switchMappings.addColumnMapping(FICTITIOUS, new ColumnMapping<>(Boolean.class, SwitchAttributes::isFictitious, SwitchAttributes::setFictitious));
        switchMappings.addColumnMapping(NODE_1, new ColumnMapping<>(Integer.class, SwitchAttributes::getNode1, SwitchAttributes::setNode1));
        switchMappings.addColumnMapping(NODE_2, new ColumnMapping<>(Integer.class, SwitchAttributes::getNode2, SwitchAttributes::setNode2));
        switchMappings.addColumnMapping(PROPERTIES, new ColumnMapping<>(Map.class, SwitchAttributes::getProperties, SwitchAttributes::setProperties));
        switchMappings.addColumnMapping(ALIAS_BY_TYPE, new ColumnMapping<>(Map.class, SwitchAttributes::getAliasByType, SwitchAttributes::setAliasByType));
        switchMappings.addColumnMapping(ALIASES_WITHOUT_TYPE, new ColumnMapping<>(Set.class, SwitchAttributes::getAliasesWithoutType, SwitchAttributes::setAliasesWithoutType));
    }

    private void createSubstationMappings() {
        substationMappings.addColumnMapping("name", new ColumnMapping<>(String.class, SubstationAttributes::getName, SubstationAttributes::setName));
        substationMappings.addColumnMapping(FICTITIOUS, new ColumnMapping<>(Boolean.class, SubstationAttributes::isFictitious, SubstationAttributes::setFictitious));
        substationMappings.addColumnMapping(PROPERTIES, new ColumnMapping<>(Map.class, SubstationAttributes::getProperties, SubstationAttributes::setProperties));
        substationMappings.addColumnMapping(ALIAS_BY_TYPE, new ColumnMapping<>(Map.class, SubstationAttributes::getAliasByType, SubstationAttributes::setAliasByType));
        substationMappings.addColumnMapping(ALIASES_WITHOUT_TYPE, new ColumnMapping<>(Set.class, SubstationAttributes::getAliasesWithoutType, SubstationAttributes::setAliasesWithoutType));
        substationMappings.addColumnMapping("country", new ColumnMapping<>(Country.class, SubstationAttributes::getCountry, SubstationAttributes::setCountry));
        substationMappings.addColumnMapping("tso", new ColumnMapping<>(String.class, SubstationAttributes::getTso, SubstationAttributes::setTso));
        substationMappings.addColumnMapping("geographicalTags", new ColumnMapping<>(Set.class, SubstationAttributes::getGeographicalTags, SubstationAttributes::setGeographicalTags));
        substationMappings.addColumnMapping("entsoeArea", new ColumnMapping<>(EntsoeAreaAttributes.class, SubstationAttributes::getEntsoeArea, SubstationAttributes::setEntsoeArea));
    }

    private void createNetworkMappings() {
        networkMappings.addColumnMapping("uuid", new ColumnMapping<>(UUID.class, NetworkAttributes::getUuid, NetworkAttributes::setUuid));
        networkMappings.addColumnMapping("variantId", new ColumnMapping<>(String.class, NetworkAttributes::getVariantId, NetworkAttributes::setVariantId));
        networkMappings.addColumnMapping("fullVariantNum", new ColumnMapping<>(Integer.class, NetworkAttributes::getFullVariantNum, NetworkAttributes::setFullVariantNum));
        networkMappings.addColumnMapping("name", new ColumnMapping<>(String.class, NetworkAttributes::getName, NetworkAttributes::setName));
        networkMappings.addColumnMapping(FICTITIOUS, new ColumnMapping<>(Boolean.class, NetworkAttributes::isFictitious, NetworkAttributes::setFictitious));
        networkMappings.addColumnMapping(PROPERTIES, new ColumnMapping<>(Map.class, NetworkAttributes::getProperties, NetworkAttributes::setProperties));
        networkMappings.addColumnMapping(ALIAS_BY_TYPE, new ColumnMapping<>(Map.class, NetworkAttributes::getAliasByType, NetworkAttributes::setAliasByType));
        networkMappings.addColumnMapping(ALIASES_WITHOUT_TYPE, new ColumnMapping<>(Set.class, NetworkAttributes::getAliasesWithoutType, NetworkAttributes::setAliasesWithoutType));
        networkMappings.addColumnMapping("idByAlias", new ColumnMapping<>(Map.class, NetworkAttributes::getIdByAlias, NetworkAttributes::setIdByAlias));
        networkMappings.addColumnMapping("caseDate", new ColumnMapping<>(Instant.class, (NetworkAttributes attributes) -> attributes.getCaseDate().toInstant(),
            (NetworkAttributes attributes, Instant instant) -> attributes.setCaseDate(ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()))));
        networkMappings.addColumnMapping("forecastDistance", new ColumnMapping<>(Integer.class, NetworkAttributes::getForecastDistance, NetworkAttributes::setForecastDistance));
        networkMappings.addColumnMapping("sourceFormat", new ColumnMapping<>(String.class, NetworkAttributes::getSourceFormat, NetworkAttributes::setSourceFormat));
        networkMappings.addColumnMapping("connectedComponentsValid", new ColumnMapping<>(Boolean.class, NetworkAttributes::isConnectedComponentsValid, NetworkAttributes::setConnectedComponentsValid));
        networkMappings.addColumnMapping("synchronousComponentsValid", new ColumnMapping<>(Boolean.class, NetworkAttributes::isSynchronousComponentsValid, NetworkAttributes::setSynchronousComponentsValid));
        networkMappings.addColumnMapping("cimCharacteristics", new ColumnMapping<>(CimCharacteristicsAttributes.class, NetworkAttributes::getCimCharacteristics, NetworkAttributes::setCimCharacteristics));
        networkMappings.addColumnMapping("baseVoltageMapping", new ColumnMapping<>(BaseVoltageMappingAttributes.class, NetworkAttributes::getBaseVoltageMapping, NetworkAttributes::setBaseVoltageMapping));
    }

    private void createVoltageLevelMappings() {
        voltageLevelMappings.addColumnMapping("substationId", new ColumnMapping<>(String.class, VoltageLevelAttributes::getSubstationId, VoltageLevelAttributes::setSubstationId));
        voltageLevelMappings.addColumnMapping("name", new ColumnMapping<>(String.class, VoltageLevelAttributes::getName, VoltageLevelAttributes::setName));
        voltageLevelMappings.addColumnMapping(FICTITIOUS, new ColumnMapping<>(Boolean.class, VoltageLevelAttributes::isFictitious, VoltageLevelAttributes::setFictitious));
        voltageLevelMappings.addColumnMapping(PROPERTIES, new ColumnMapping<>(Map.class, VoltageLevelAttributes::getProperties, VoltageLevelAttributes::setProperties));
        voltageLevelMappings.addColumnMapping(ALIAS_BY_TYPE, new ColumnMapping<>(Map.class, VoltageLevelAttributes::getAliasByType, VoltageLevelAttributes::setAliasByType));
        voltageLevelMappings.addColumnMapping(ALIASES_WITHOUT_TYPE, new ColumnMapping<>(Set.class, VoltageLevelAttributes::getAliasesWithoutType, VoltageLevelAttributes::setAliasesWithoutType));
        voltageLevelMappings.addColumnMapping("nominalV", new ColumnMapping<>(Double.class, VoltageLevelAttributes::getNominalV, VoltageLevelAttributes::setNominalV));
        voltageLevelMappings.addColumnMapping("lowVoltageLimit", new ColumnMapping<>(Double.class, VoltageLevelAttributes::getLowVoltageLimit, VoltageLevelAttributes::setLowVoltageLimit));
        voltageLevelMappings.addColumnMapping("highVoltageLimit", new ColumnMapping<>(Double.class, VoltageLevelAttributes::getHighVoltageLimit, VoltageLevelAttributes::setHighVoltageLimit));
        voltageLevelMappings.addColumnMapping("topologyKind", new ColumnMapping<>(TopologyKind.class, VoltageLevelAttributes::getTopologyKind, VoltageLevelAttributes::setTopologyKind));
        voltageLevelMappings.addColumnMapping("internalConnections", new ColumnMapping<>(List.class, VoltageLevelAttributes::getInternalConnections, VoltageLevelAttributes::setInternalConnections));
        voltageLevelMappings.addColumnMapping("calculatedBusesForBusView", new ColumnMapping<>(List.class, VoltageLevelAttributes::getCalculatedBusesForBusView, VoltageLevelAttributes::setCalculatedBusesForBusView));
        voltageLevelMappings.addColumnMapping("nodeToCalculatedBusForBusView", new ColumnMapping<>(null, VoltageLevelAttributes::getNodeToCalculatedBusForBusView, VoltageLevelAttributes::setNodeToCalculatedBusForBusView, Integer.class, Integer.class));
        voltageLevelMappings.addColumnMapping("busToCalculatedBusForBusView", new ColumnMapping<>(null, VoltageLevelAttributes::getBusToCalculatedBusForBusView, VoltageLevelAttributes::setBusToCalculatedBusForBusView, String.class, Integer.class));
        voltageLevelMappings.addColumnMapping("calculatedBusesForBusBreakerView", new ColumnMapping<>(List.class, VoltageLevelAttributes::getCalculatedBusesForBusBreakerView, VoltageLevelAttributes::setCalculatedBusesForBusBreakerView));
        voltageLevelMappings.addColumnMapping("nodeToCalculatedBusForBusBreakerView", new ColumnMapping<>(null, VoltageLevelAttributes::getNodeToCalculatedBusForBusBreakerView, VoltageLevelAttributes::setNodeToCalculatedBusForBusBreakerView, Integer.class, Integer.class));
        voltageLevelMappings.addColumnMapping("busToCalculatedBusForBusBreakerView", new ColumnMapping<>(null, VoltageLevelAttributes::getBusToCalculatedBusForBusBreakerView, VoltageLevelAttributes::setBusToCalculatedBusForBusBreakerView, String.class, Integer.class));
        voltageLevelMappings.addColumnMapping("slackTerminal", new ColumnMapping<>(TerminalRefAttributes.class, VoltageLevelAttributes::getSlackTerminal, VoltageLevelAttributes::setSlackTerminal));
        voltageLevelMappings.addColumnMapping("calculatedBusesValid", new ColumnMapping<>(Boolean.class, VoltageLevelAttributes::isCalculatedBusesValid, VoltageLevelAttributes::setCalculatedBusesValid));
        voltageLevelMappings.addColumnMapping("identifiableShortCircuit", new ColumnMapping<>(IdentifiableShortCircuitAttributes.class, VoltageLevelAttributes::getIdentifiableShortCircuitAttributes, VoltageLevelAttributes::setIdentifiableShortCircuitAttributes));
        voltageLevelMappings.addColumnMapping("nodeToFictitiousP0", new ColumnMapping<>(null, VoltageLevelAttributes::getNodeToFictitiousP0, VoltageLevelAttributes::setNodeToFictitiousP0, Integer.class, Double.class));
        voltageLevelMappings.addColumnMapping("nodeToFictitiousQ0", new ColumnMapping<>(null, VoltageLevelAttributes::getNodeToFictitiousQ0, VoltageLevelAttributes::setNodeToFictitiousQ0, Integer.class, Double.class));
    }

    private void createBatteryMappings() {
        batteryMappings.addColumnMapping("name", new ColumnMapping<>(String.class, BatteryAttributes::getName, BatteryAttributes::setName));
        batteryMappings.addColumnMapping(VOLTAGE_LEVEL_ID, new ColumnMapping<>(String.class, BatteryAttributes::getVoltageLevelId, BatteryAttributes::setVoltageLevelId));
        batteryMappings.addColumnMapping("bus", new ColumnMapping<>(String.class, BatteryAttributes::getBus, BatteryAttributes::setBus));
        batteryMappings.addColumnMapping(CONNECTABLE_BUS, new ColumnMapping<>(String.class, BatteryAttributes::getConnectableBus, BatteryAttributes::setConnectableBus));
        batteryMappings.addColumnMapping("minP", new ColumnMapping<>(Double.class, BatteryAttributes::getMinP, BatteryAttributes::setMinP));
        batteryMappings.addColumnMapping("maxP", new ColumnMapping<>(Double.class, BatteryAttributes::getMaxP, BatteryAttributes::setMaxP));
        batteryMappings.addColumnMapping("targetP", new ColumnMapping<>(Double.class, BatteryAttributes::getTargetP, BatteryAttributes::setTargetP));
        batteryMappings.addColumnMapping("targetQ", new ColumnMapping<>(Double.class, BatteryAttributes::getTargetQ, BatteryAttributes::setTargetQ));
        batteryMappings.addColumnMapping("p", new ColumnMapping<>(Double.class, BatteryAttributes::getP, BatteryAttributes::setP));
        batteryMappings.addColumnMapping("q", new ColumnMapping<>(Double.class, BatteryAttributes::getQ, BatteryAttributes::setQ));
        batteryMappings.addColumnMapping(FICTITIOUS, new ColumnMapping<>(Boolean.class, BatteryAttributes::isFictitious, BatteryAttributes::setFictitious));
        batteryMappings.addColumnMapping(MINQ, new ColumnMapping<>(Double.class,
            (BatteryAttributes attributes) -> attributes.getReactiveLimits() instanceof MinMaxReactiveLimitsAttributes ? ((MinMaxReactiveLimitsAttributes) attributes.getReactiveLimits()).getMinQ() : null,
            (BatteryAttributes attributes, Double value) -> {
                if (attributes.getReactiveLimits() == null) {
                    attributes.setReactiveLimits(new MinMaxReactiveLimitsAttributes());
                }
                ((MinMaxReactiveLimitsAttributes) attributes.getReactiveLimits()).setMinQ(value);
            }));
        batteryMappings.addColumnMapping(MAXQ, new ColumnMapping<>(Double.class,
            (BatteryAttributes attributes) -> attributes.getReactiveLimits() instanceof MinMaxReactiveLimitsAttributes ? ((MinMaxReactiveLimitsAttributes) attributes.getReactiveLimits()).getMaxQ() : null,
            (BatteryAttributes attributes, Double value) -> {
                if (attributes.getReactiveLimits() == null) {
                    attributes.setReactiveLimits(new MinMaxReactiveLimitsAttributes());
                }
                ((MinMaxReactiveLimitsAttributes) attributes.getReactiveLimits()).setMaxQ(value);
            }));
        batteryMappings.addColumnMapping("node", new ColumnMapping<>(Integer.class, BatteryAttributes::getNode, BatteryAttributes::setNode));
        batteryMappings.addColumnMapping(PROPERTIES, new ColumnMapping<>(Map.class, BatteryAttributes::getProperties, BatteryAttributes::setProperties));
        batteryMappings.addColumnMapping(ALIAS_BY_TYPE, new ColumnMapping<>(Map.class, BatteryAttributes::getAliasByType, BatteryAttributes::setAliasByType));
        batteryMappings.addColumnMapping(ALIASES_WITHOUT_TYPE, new ColumnMapping<>(Set.class, BatteryAttributes::getAliasesWithoutType, BatteryAttributes::setAliasesWithoutType));
        batteryMappings.addColumnMapping(POSITION, new ColumnMapping<>(ConnectablePositionAttributes.class, BatteryAttributes::getPosition, BatteryAttributes::setPosition));
        batteryMappings.addColumnMapping("batteryShortCircuit", new ColumnMapping<>(ShortCircuitAttributes.class, BatteryAttributes::getBatteryShortCircuitAttributes, BatteryAttributes::setBatteryShortCircuitAttributes));
    }

    private void createBusbarSectionMappings() {
        busbarSectionMappings.addColumnMapping("name", new ColumnMapping<>(String.class, BusbarSectionAttributes::getName, BusbarSectionAttributes::setName));
        busbarSectionMappings.addColumnMapping(VOLTAGE_LEVEL_ID, new ColumnMapping<>(String.class, BusbarSectionAttributes::getVoltageLevelId, BusbarSectionAttributes::setVoltageLevelId));
        busbarSectionMappings.addColumnMapping(FICTITIOUS, new ColumnMapping<>(Boolean.class, BusbarSectionAttributes::isFictitious, BusbarSectionAttributes::setFictitious));
        busbarSectionMappings.addColumnMapping("node", new ColumnMapping<>(Integer.class, BusbarSectionAttributes::getNode, BusbarSectionAttributes::setNode));
        busbarSectionMappings.addColumnMapping(PROPERTIES, new ColumnMapping<>(Map.class, BusbarSectionAttributes::getProperties, BusbarSectionAttributes::setProperties));
        busbarSectionMappings.addColumnMapping(ALIAS_BY_TYPE, new ColumnMapping<>(Map.class, BusbarSectionAttributes::getAliasByType, BusbarSectionAttributes::setAliasByType));
        busbarSectionMappings.addColumnMapping(ALIASES_WITHOUT_TYPE, new ColumnMapping<>(Set.class, BusbarSectionAttributes::getAliasesWithoutType, BusbarSectionAttributes::setAliasesWithoutType));
        busbarSectionMappings.addColumnMapping(POSITION, new ColumnMapping<>(BusbarSectionPositionAttributes.class, BusbarSectionAttributes::getPosition, BusbarSectionAttributes::setPosition));
    }

    private void createConfiguredBusMappings() {
        configuredBusMappings.addColumnMapping("name", new ColumnMapping<>(String.class, ConfiguredBusAttributes::getName, ConfiguredBusAttributes::setName));
        configuredBusMappings.addColumnMapping(VOLTAGE_LEVEL_ID, new ColumnMapping<>(String.class, ConfiguredBusAttributes::getVoltageLevelId, ConfiguredBusAttributes::setVoltageLevelId));
        configuredBusMappings.addColumnMapping(FICTITIOUS, new ColumnMapping<>(Boolean.class, ConfiguredBusAttributes::isFictitious, ConfiguredBusAttributes::setFictitious));
        configuredBusMappings.addColumnMapping(PROPERTIES, new ColumnMapping<>(Map.class, ConfiguredBusAttributes::getProperties, ConfiguredBusAttributes::setProperties));
        configuredBusMappings.addColumnMapping(ALIAS_BY_TYPE, new ColumnMapping<>(Map.class, ConfiguredBusAttributes::getAliasByType, ConfiguredBusAttributes::setAliasByType));
        configuredBusMappings.addColumnMapping(ALIASES_WITHOUT_TYPE, new ColumnMapping<>(Set.class, ConfiguredBusAttributes::getAliasesWithoutType, ConfiguredBusAttributes::setAliasesWithoutType));
        configuredBusMappings.addColumnMapping("v", new ColumnMapping<>(Double.class, ConfiguredBusAttributes::getV, ConfiguredBusAttributes::setV));
        configuredBusMappings.addColumnMapping("angle", new ColumnMapping<>(Double.class, ConfiguredBusAttributes::getAngle, ConfiguredBusAttributes::setAngle));
        configuredBusMappings.addColumnMapping("fictitiousp0", new ColumnMapping<>(Double.class, ConfiguredBusAttributes::getFictitiousP0, ConfiguredBusAttributes::setFictitiousP0));
        configuredBusMappings.addColumnMapping("fictitiousq0", new ColumnMapping<>(Double.class, ConfiguredBusAttributes::getFictitiousQ0, ConfiguredBusAttributes::setFictitiousQ0));
    }

    private void createDanglingLineMappings() {
        danglingLineMappings.addColumnMapping("name", new ColumnMapping<>(String.class, DanglingLineAttributes::getName, DanglingLineAttributes::setName));
        danglingLineMappings.addColumnMapping(VOLTAGE_LEVEL_ID, new ColumnMapping<>(String.class, DanglingLineAttributes::getVoltageLevelId, DanglingLineAttributes::setVoltageLevelId));
        danglingLineMappings.addColumnMapping("bus", new ColumnMapping<>(String.class, DanglingLineAttributes::getBus, DanglingLineAttributes::setBus));
        danglingLineMappings.addColumnMapping(CONNECTABLE_BUS, new ColumnMapping<>(String.class, DanglingLineAttributes::getConnectableBus, DanglingLineAttributes::setConnectableBus));
        danglingLineMappings.addColumnMapping("r", new ColumnMapping<>(Double.class, DanglingLineAttributes::getR, DanglingLineAttributes::setR));
        danglingLineMappings.addColumnMapping("x", new ColumnMapping<>(Double.class, DanglingLineAttributes::getX, DanglingLineAttributes::setX));
        danglingLineMappings.addColumnMapping("g", new ColumnMapping<>(Double.class, DanglingLineAttributes::getG, DanglingLineAttributes::setG));
        danglingLineMappings.addColumnMapping("b", new ColumnMapping<>(Double.class, DanglingLineAttributes::getB, DanglingLineAttributes::setB));
        danglingLineMappings.addColumnMapping("p0", new ColumnMapping<>(Double.class, DanglingLineAttributes::getP0, DanglingLineAttributes::setP0));
        danglingLineMappings.addColumnMapping("q0", new ColumnMapping<>(Double.class, DanglingLineAttributes::getQ0, DanglingLineAttributes::setQ0));
        danglingLineMappings.addColumnMapping("p", new ColumnMapping<>(Double.class, DanglingLineAttributes::getP, DanglingLineAttributes::setP));
        danglingLineMappings.addColumnMapping("q", new ColumnMapping<>(Double.class, DanglingLineAttributes::getQ, DanglingLineAttributes::setQ));
        danglingLineMappings.addColumnMapping(FICTITIOUS, new ColumnMapping<>(Boolean.class, DanglingLineAttributes::isFictitious, DanglingLineAttributes::setFictitious));
        danglingLineMappings.addColumnMapping("node", new ColumnMapping<>(Integer.class, DanglingLineAttributes::getNode, DanglingLineAttributes::setNode));
        danglingLineMappings.addColumnMapping(PROPERTIES, new ColumnMapping<>(Map.class, DanglingLineAttributes::getProperties, DanglingLineAttributes::setProperties));
        danglingLineMappings.addColumnMapping(ALIAS_BY_TYPE, new ColumnMapping<>(Map.class, DanglingLineAttributes::getAliasByType, DanglingLineAttributes::setAliasByType));
        danglingLineMappings.addColumnMapping(ALIASES_WITHOUT_TYPE, new ColumnMapping<>(Set.class, DanglingLineAttributes::getAliasesWithoutType, DanglingLineAttributes::setAliasesWithoutType));
        danglingLineMappings.addColumnMapping("generation", new ColumnMapping<>(DanglingLineGenerationAttributes.class, DanglingLineAttributes::getGeneration, DanglingLineAttributes::setGeneration));
        danglingLineMappings.addColumnMapping("pairingKey", new ColumnMapping<>(String.class, DanglingLineAttributes::getPairingKey, DanglingLineAttributes::setPairingKey));
        danglingLineMappings.addColumnMapping(POSITION, new ColumnMapping<>(ConnectablePositionAttributes.class, DanglingLineAttributes::getPosition, DanglingLineAttributes::setPosition));
        danglingLineMappings.addColumnMapping(SELECTED_OPERATIONAL_LIMITS_GROUP_ID_COLUMN, new ColumnMapping<>(String.class, DanglingLineAttributes::getSelectedOperationalLimitsGroupId, DanglingLineAttributes::setSelectedOperationalLimitsGroupId));
        danglingLineMappings.addColumnMapping(TIE_LINE_ID, new ColumnMapping<>(String.class, DanglingLineAttributes::getTieLineId, DanglingLineAttributes::setTieLineId));
    }

    private void createTieLineMappings() {
        tieLineMappings.addColumnMapping("name", new ColumnMapping<>(String.class, TieLineAttributes::getName, TieLineAttributes::setName));
        tieLineMappings.addColumnMapping("danglingLine1Id", new ColumnMapping<>(String.class, TieLineAttributes::getDanglingLine1Id, TieLineAttributes::setDanglingLine1Id));
        tieLineMappings.addColumnMapping("danglingLine2Id", new ColumnMapping<>(String.class, TieLineAttributes::getDanglingLine2Id, TieLineAttributes::setDanglingLine2Id));
        tieLineMappings.addColumnMapping(FICTITIOUS, new ColumnMapping<>(Boolean.class, TieLineAttributes::isFictitious, TieLineAttributes::setFictitious));
        tieLineMappings.addColumnMapping(PROPERTIES, new ColumnMapping<>(Map.class, TieLineAttributes::getProperties, TieLineAttributes::setProperties));
        tieLineMappings.addColumnMapping(ALIAS_BY_TYPE, new ColumnMapping<>(Map.class, TieLineAttributes::getAliasByType, TieLineAttributes::setAliasByType));
        tieLineMappings.addColumnMapping(ALIASES_WITHOUT_TYPE, new ColumnMapping<>(Set.class, TieLineAttributes::getAliasesWithoutType, TieLineAttributes::setAliasesWithoutType));
    }

    private void createAreaMappings() {
        areaMappings.addColumnMapping("name", new ColumnMapping<>(String.class, AreaAttributes::getName, AreaAttributes::setName));
        areaMappings.addColumnMapping("areaType", new ColumnMapping<>(String.class, AreaAttributes::getAreaType, AreaAttributes::setAreaType));
        areaMappings.addColumnMapping("voltageLevelIds", new ColumnMapping<>(Set.class, AreaAttributes::getVoltageLevelIds, AreaAttributes::setVoltageLevelIds));
        areaMappings.addColumnMapping("interchangeTarget", new ColumnMapping<>(Double.class, AreaAttributes::getInterchangeTarget, AreaAttributes::setInterchangeTarget));
        areaMappings.addColumnMapping(FICTITIOUS, new ColumnMapping<>(Boolean.class, AreaAttributes::isFictitious, AreaAttributes::setFictitious));
        areaMappings.addColumnMapping(PROPERTIES, new ColumnMapping<>(Map.class, AreaAttributes::getProperties, AreaAttributes::setProperties));
        areaMappings.addColumnMapping(ALIAS_BY_TYPE, new ColumnMapping<>(Map.class, AreaAttributes::getAliasByType, AreaAttributes::setAliasByType));
        areaMappings.addColumnMapping(ALIASES_WITHOUT_TYPE, new ColumnMapping<>(Set.class, AreaAttributes::getAliasesWithoutType, AreaAttributes::setAliasesWithoutType));
    }

    private void createGroundMappings() {
        groundMappings.addColumnMapping(NAME_COLUMN, new ColumnMapping<>(String.class, GroundAttributes::getName, GroundAttributes::setName));
        groundMappings.addColumnMapping(VOLTAGE_LEVEL_ID, new ColumnMapping<>(String.class, GroundAttributes::getVoltageLevelId, GroundAttributes::setVoltageLevelId));
        groundMappings.addColumnMapping(BUS, new ColumnMapping<>(String.class, GroundAttributes::getBus, GroundAttributes::setBus));
        groundMappings.addColumnMapping(CONNECTABLE_BUS, new ColumnMapping<>(String.class, GroundAttributes::getConnectableBus, GroundAttributes::setConnectableBus));
        groundMappings.addColumnMapping("p", new ColumnMapping<>(Double.class, GroundAttributes::getP, GroundAttributes::setP));
        groundMappings.addColumnMapping("q", new ColumnMapping<>(Double.class, GroundAttributes::getQ, GroundAttributes::setQ));
        groundMappings.addColumnMapping(FICTITIOUS, new ColumnMapping<>(Boolean.class, GroundAttributes::isFictitious, GroundAttributes::setFictitious));
        groundMappings.addColumnMapping(NODE, new ColumnMapping<>(Integer.class, GroundAttributes::getNode, GroundAttributes::setNode));
        groundMappings.addColumnMapping(PROPERTIES, new ColumnMapping<>(Map.class, GroundAttributes::getProperties, GroundAttributes::setProperties));
        groundMappings.addColumnMapping(ALIAS_BY_TYPE, new ColumnMapping<>(Map.class, GroundAttributes::getAliasByType, GroundAttributes::setAliasByType));
        groundMappings.addColumnMapping(ALIASES_WITHOUT_TYPE, new ColumnMapping<>(Set.class, GroundAttributes::getAliasesWithoutType, GroundAttributes::setAliasesWithoutType));
        groundMappings.addColumnMapping(POSITION, new ColumnMapping<>(ConnectablePositionAttributes.class, GroundAttributes::getPosition, GroundAttributes::setPosition));
    }

    private void createShuntCompensatorMappings() {
        shuntCompensatorMappings.addColumnMapping("name", new ColumnMapping<>(String.class, ShuntCompensatorAttributes::getName, ShuntCompensatorAttributes::setName));
        shuntCompensatorMappings.addColumnMapping(VOLTAGE_LEVEL_ID, new ColumnMapping<>(String.class, ShuntCompensatorAttributes::getVoltageLevelId, ShuntCompensatorAttributes::setVoltageLevelId));
        shuntCompensatorMappings.addColumnMapping("bus", new ColumnMapping<>(String.class, ShuntCompensatorAttributes::getBus, ShuntCompensatorAttributes::setBus));
        shuntCompensatorMappings.addColumnMapping(CONNECTABLE_BUS, new ColumnMapping<>(String.class, ShuntCompensatorAttributes::getConnectableBus, ShuntCompensatorAttributes::setConnectableBus));
        shuntCompensatorMappings.addColumnMapping("p", new ColumnMapping<>(Double.class, ShuntCompensatorAttributes::getP, ShuntCompensatorAttributes::setP));
        shuntCompensatorMappings.addColumnMapping("q", new ColumnMapping<>(Double.class, ShuntCompensatorAttributes::getQ, ShuntCompensatorAttributes::setQ));
        shuntCompensatorMappings.addColumnMapping(FICTITIOUS, new ColumnMapping<>(Boolean.class, ShuntCompensatorAttributes::isFictitious, ShuntCompensatorAttributes::setFictitious));
        shuntCompensatorMappings.addColumnMapping("targetV", new ColumnMapping<>(Double.class, ShuntCompensatorAttributes::getTargetV, ShuntCompensatorAttributes::setTargetV));
        shuntCompensatorMappings.addColumnMapping("targetDeadband", new ColumnMapping<>(Double.class, ShuntCompensatorAttributes::getTargetDeadband, ShuntCompensatorAttributes::setTargetDeadband));
        shuntCompensatorMappings.addColumnMapping("linearModel", new ColumnMapping<>(ShuntCompensatorModelAttributes.class, (ShuntCompensatorAttributes attributes) ->
            attributes.getModel() instanceof ShuntCompensatorLinearModelAttributes ? attributes.getModel() : null,
            (ShuntCompensatorAttributes attributes, ShuntCompensatorModelAttributes model) -> {
                if (model instanceof ShuntCompensatorLinearModelAttributes) {
                    attributes.setModel(model);
                }
            }));
        shuntCompensatorMappings.addColumnMapping("nonLinearModel", new ColumnMapping<>(ShuntCompensatorModelAttributes.class, (ShuntCompensatorAttributes attributes) ->
            attributes.getModel() instanceof ShuntCompensatorNonLinearModelAttributes ? attributes.getModel() : null,
            (ShuntCompensatorAttributes attributes, ShuntCompensatorModelAttributes model) -> {
                if (model instanceof ShuntCompensatorNonLinearModelAttributes) {
                    attributes.setModel(model);
                }
            }));
        shuntCompensatorMappings.addColumnMapping("node", new ColumnMapping<>(Integer.class, ShuntCompensatorAttributes::getNode, ShuntCompensatorAttributes::setNode));
        shuntCompensatorMappings.addColumnMapping("sectionCount", new ColumnMapping<>(Integer.class, ShuntCompensatorAttributes::getSectionCount, ShuntCompensatorAttributes::setSectionCount));
        shuntCompensatorMappings.addColumnMapping(PROPERTIES, new ColumnMapping<>(Map.class, ShuntCompensatorAttributes::getProperties, ShuntCompensatorAttributes::setProperties));
        shuntCompensatorMappings.addColumnMapping(ALIAS_BY_TYPE, new ColumnMapping<>(Map.class, ShuntCompensatorAttributes::getAliasByType, ShuntCompensatorAttributes::setAliasByType));
        shuntCompensatorMappings.addColumnMapping(ALIASES_WITHOUT_TYPE, new ColumnMapping<>(Set.class, ShuntCompensatorAttributes::getAliasesWithoutType, ShuntCompensatorAttributes::setAliasesWithoutType));
        shuntCompensatorMappings.addColumnMapping(POSITION, new ColumnMapping<>(ConnectablePositionAttributes.class, ShuntCompensatorAttributes::getPosition, ShuntCompensatorAttributes::setPosition));
    }

    private void createVscConverterStationMappings() {
        vscConverterStationMappings.addColumnMapping("name", new ColumnMapping<>(String.class, VscConverterStationAttributes::getName, VscConverterStationAttributes::setName));
        vscConverterStationMappings.addColumnMapping(VOLTAGE_LEVEL_ID, new ColumnMapping<>(String.class, VscConverterStationAttributes::getVoltageLevelId, VscConverterStationAttributes::setVoltageLevelId));
        vscConverterStationMappings.addColumnMapping("bus", new ColumnMapping<>(String.class, VscConverterStationAttributes::getBus, VscConverterStationAttributes::setBus));
        vscConverterStationMappings.addColumnMapping(CONNECTABLE_BUS, new ColumnMapping<>(String.class, VscConverterStationAttributes::getConnectableBus, VscConverterStationAttributes::setConnectableBus));
        vscConverterStationMappings.addColumnMapping("p", new ColumnMapping<>(Double.class, VscConverterStationAttributes::getP, VscConverterStationAttributes::setP));
        vscConverterStationMappings.addColumnMapping("q", new ColumnMapping<>(Double.class, VscConverterStationAttributes::getQ, VscConverterStationAttributes::setQ));
        vscConverterStationMappings.addColumnMapping("lossFactor", new ColumnMapping<>(Float.class, VscConverterStationAttributes::getLossFactor, VscConverterStationAttributes::setLossFactor));
        vscConverterStationMappings.addColumnMapping("reactivePowerSetPoint", new ColumnMapping<>(Double.class, VscConverterStationAttributes::getReactivePowerSetPoint, VscConverterStationAttributes::setReactivePowerSetPoint));
        vscConverterStationMappings.addColumnMapping("voltageSetPoint", new ColumnMapping<>(Double.class, VscConverterStationAttributes::getVoltageSetPoint, VscConverterStationAttributes::setVoltageSetPoint));
        vscConverterStationMappings.addColumnMapping(FICTITIOUS, new ColumnMapping<>(Boolean.class, VscConverterStationAttributes::isFictitious, VscConverterStationAttributes::setFictitious));
        vscConverterStationMappings.addColumnMapping(MINQ, new ColumnMapping<>(Double.class,
            (VscConverterStationAttributes attributes) -> attributes.getReactiveLimits() instanceof MinMaxReactiveLimitsAttributes ? ((MinMaxReactiveLimitsAttributes) attributes.getReactiveLimits()).getMinQ() : null,
            (VscConverterStationAttributes attributes, Double value) -> {
                if (attributes.getReactiveLimits() == null) {
                    attributes.setReactiveLimits(new MinMaxReactiveLimitsAttributes());
                }
                ((MinMaxReactiveLimitsAttributes) attributes.getReactiveLimits()).setMinQ(value);
            }));
        vscConverterStationMappings.addColumnMapping(MAXQ, new ColumnMapping<>(Double.class,
            (VscConverterStationAttributes attributes) -> attributes.getReactiveLimits() instanceof MinMaxReactiveLimitsAttributes ? ((MinMaxReactiveLimitsAttributes) attributes.getReactiveLimits()).getMaxQ() : null,
            (VscConverterStationAttributes attributes, Double value) -> {
                if (attributes.getReactiveLimits() == null) {
                    attributes.setReactiveLimits(new MinMaxReactiveLimitsAttributes());
                }
                ((MinMaxReactiveLimitsAttributes) attributes.getReactiveLimits()).setMaxQ(value);
            }));
        vscConverterStationMappings.addColumnMapping("node", new ColumnMapping<>(Integer.class, VscConverterStationAttributes::getNode, VscConverterStationAttributes::setNode));
        vscConverterStationMappings.addColumnMapping(PROPERTIES, new ColumnMapping<>(Map.class, VscConverterStationAttributes::getProperties, VscConverterStationAttributes::setProperties));
        vscConverterStationMappings.addColumnMapping(ALIAS_BY_TYPE, new ColumnMapping<>(Map.class, VscConverterStationAttributes::getAliasByType, VscConverterStationAttributes::setAliasByType));
        vscConverterStationMappings.addColumnMapping(ALIASES_WITHOUT_TYPE, new ColumnMapping<>(Set.class, VscConverterStationAttributes::getAliasesWithoutType, VscConverterStationAttributes::setAliasesWithoutType));
        vscConverterStationMappings.addColumnMapping(POSITION, new ColumnMapping<>(ConnectablePositionAttributes.class, VscConverterStationAttributes::getPosition, VscConverterStationAttributes::setPosition));
    }

    private void createLccConverterStationMappings() {
        lccConverterStationMappings.addColumnMapping("name", new ColumnMapping<>(String.class, LccConverterStationAttributes::getName, LccConverterStationAttributes::setName));
        lccConverterStationMappings.addColumnMapping(VOLTAGE_LEVEL_ID, new ColumnMapping<>(String.class, LccConverterStationAttributes::getVoltageLevelId, LccConverterStationAttributes::setVoltageLevelId));
        lccConverterStationMappings.addColumnMapping("bus", new ColumnMapping<>(String.class, LccConverterStationAttributes::getBus, LccConverterStationAttributes::setBus));
        lccConverterStationMappings.addColumnMapping(CONNECTABLE_BUS, new ColumnMapping<>(String.class, LccConverterStationAttributes::getConnectableBus, LccConverterStationAttributes::setConnectableBus));
        lccConverterStationMappings.addColumnMapping("p", new ColumnMapping<>(Double.class, LccConverterStationAttributes::getP, LccConverterStationAttributes::setP));
        lccConverterStationMappings.addColumnMapping("q", new ColumnMapping<>(Double.class, LccConverterStationAttributes::getQ, LccConverterStationAttributes::setQ));
        lccConverterStationMappings.addColumnMapping("powerFactor", new ColumnMapping<>(Float.class, LccConverterStationAttributes::getPowerFactor, LccConverterStationAttributes::setPowerFactor));
        lccConverterStationMappings.addColumnMapping("lossFactor", new ColumnMapping<>(Float.class, LccConverterStationAttributes::getLossFactor, LccConverterStationAttributes::setLossFactor));
        lccConverterStationMappings.addColumnMapping(FICTITIOUS, new ColumnMapping<>(Boolean.class, LccConverterStationAttributes::isFictitious, LccConverterStationAttributes::setFictitious));
        lccConverterStationMappings.addColumnMapping("node", new ColumnMapping<>(Integer.class, LccConverterStationAttributes::getNode, LccConverterStationAttributes::setNode));
        lccConverterStationMappings.addColumnMapping(PROPERTIES, new ColumnMapping<>(Map.class, LccConverterStationAttributes::getProperties, LccConverterStationAttributes::setProperties));
        lccConverterStationMappings.addColumnMapping(ALIAS_BY_TYPE, new ColumnMapping<>(Map.class, LccConverterStationAttributes::getAliasByType, LccConverterStationAttributes::setAliasByType));
        lccConverterStationMappings.addColumnMapping(ALIASES_WITHOUT_TYPE, new ColumnMapping<>(Set.class, LccConverterStationAttributes::getAliasesWithoutType, LccConverterStationAttributes::setAliasesWithoutType));
        lccConverterStationMappings.addColumnMapping(POSITION, new ColumnMapping<>(ConnectablePositionAttributes.class, LccConverterStationAttributes::getPosition, LccConverterStationAttributes::setPosition));
    }

    private void createStaticVarCompensatorMappings() {
        staticVarCompensatorMappings.addColumnMapping("name", new ColumnMapping<>(String.class, StaticVarCompensatorAttributes::getName, StaticVarCompensatorAttributes::setName));
        staticVarCompensatorMappings.addColumnMapping(VOLTAGE_LEVEL_ID, new ColumnMapping<>(String.class, StaticVarCompensatorAttributes::getVoltageLevelId, StaticVarCompensatorAttributes::setVoltageLevelId));
        staticVarCompensatorMappings.addColumnMapping("bus", new ColumnMapping<>(String.class, StaticVarCompensatorAttributes::getBus, StaticVarCompensatorAttributes::setBus));
        staticVarCompensatorMappings.addColumnMapping(CONNECTABLE_BUS, new ColumnMapping<>(String.class, StaticVarCompensatorAttributes::getConnectableBus, StaticVarCompensatorAttributes::setConnectableBus));
        staticVarCompensatorMappings.addColumnMapping("bmin", new ColumnMapping<>(Double.class, StaticVarCompensatorAttributes::getBmin, StaticVarCompensatorAttributes::setBmin));
        staticVarCompensatorMappings.addColumnMapping("bmax", new ColumnMapping<>(Double.class, StaticVarCompensatorAttributes::getBmax, StaticVarCompensatorAttributes::setBmax));
        staticVarCompensatorMappings.addColumnMapping("voltageSetPoint", new ColumnMapping<>(Double.class, StaticVarCompensatorAttributes::getVoltageSetPoint, StaticVarCompensatorAttributes::setVoltageSetPoint));
        staticVarCompensatorMappings.addColumnMapping("reactivePowerSetPoint", new ColumnMapping<>(Double.class, StaticVarCompensatorAttributes::getReactivePowerSetPoint, StaticVarCompensatorAttributes::setReactivePowerSetPoint));
        staticVarCompensatorMappings.addColumnMapping("p", new ColumnMapping<>(Double.class, StaticVarCompensatorAttributes::getP, StaticVarCompensatorAttributes::setP));
        staticVarCompensatorMappings.addColumnMapping("q", new ColumnMapping<>(Double.class, StaticVarCompensatorAttributes::getQ, StaticVarCompensatorAttributes::setQ));
        staticVarCompensatorMappings.addColumnMapping(FICTITIOUS, new ColumnMapping<>(Boolean.class, StaticVarCompensatorAttributes::isFictitious, StaticVarCompensatorAttributes::setFictitious));
        staticVarCompensatorMappings.addColumnMapping("node", new ColumnMapping<>(Integer.class, StaticVarCompensatorAttributes::getNode, StaticVarCompensatorAttributes::setNode));
        staticVarCompensatorMappings.addColumnMapping(PROPERTIES, new ColumnMapping<>(Map.class, StaticVarCompensatorAttributes::getProperties, StaticVarCompensatorAttributes::setProperties));
        staticVarCompensatorMappings.addColumnMapping(ALIAS_BY_TYPE, new ColumnMapping<>(Map.class, StaticVarCompensatorAttributes::getAliasByType, StaticVarCompensatorAttributes::setAliasByType));
        staticVarCompensatorMappings.addColumnMapping(ALIASES_WITHOUT_TYPE, new ColumnMapping<>(Set.class, StaticVarCompensatorAttributes::getAliasesWithoutType, StaticVarCompensatorAttributes::setAliasesWithoutType));
        staticVarCompensatorMappings.addColumnMapping(POSITION, new ColumnMapping<>(ConnectablePositionAttributes.class, StaticVarCompensatorAttributes::getPosition, StaticVarCompensatorAttributes::setPosition));
        staticVarCompensatorMappings.addColumnMapping("voltagePerReactivePowerControl", new ColumnMapping<>(VoltagePerReactivePowerControlAttributes.class, StaticVarCompensatorAttributes::getVoltagePerReactiveControl, StaticVarCompensatorAttributes::setVoltagePerReactiveControl));
        staticVarCompensatorMappings.addColumnMapping("standbyAutomaton", new ColumnMapping<>(StandbyAutomatonAttributes.class, StaticVarCompensatorAttributes::getStandbyAutomaton, StaticVarCompensatorAttributes::setStandbyAutomaton));
    }

    private void createHvdcLineMappings() {
        hvdcLineMappings.addColumnMapping("name", new ColumnMapping<>(String.class, HvdcLineAttributes::getName, HvdcLineAttributes::setName));
        hvdcLineMappings.addColumnMapping(FICTITIOUS, new ColumnMapping<>(Boolean.class, HvdcLineAttributes::isFictitious, HvdcLineAttributes::setFictitious));
        hvdcLineMappings.addColumnMapping(PROPERTIES, new ColumnMapping<>(Map.class, HvdcLineAttributes::getProperties, HvdcLineAttributes::setProperties));
        hvdcLineMappings.addColumnMapping(ALIAS_BY_TYPE, new ColumnMapping<>(Map.class, HvdcLineAttributes::getAliasByType, HvdcLineAttributes::setAliasByType));
        hvdcLineMappings.addColumnMapping(ALIASES_WITHOUT_TYPE, new ColumnMapping<>(Set.class, HvdcLineAttributes::getAliasesWithoutType, HvdcLineAttributes::setAliasesWithoutType));
        hvdcLineMappings.addColumnMapping("r", new ColumnMapping<>(Double.class, HvdcLineAttributes::getR, HvdcLineAttributes::setR));
        hvdcLineMappings.addColumnMapping("nominalV", new ColumnMapping<>(Double.class, HvdcLineAttributes::getNominalV, HvdcLineAttributes::setNominalV));
        hvdcLineMappings.addColumnMapping("activePowerSetpoint", new ColumnMapping<>(Double.class, HvdcLineAttributes::getActivePowerSetpoint, HvdcLineAttributes::setActivePowerSetpoint));
        hvdcLineMappings.addColumnMapping("maxP", new ColumnMapping<>(Double.class, HvdcLineAttributes::getMaxP, HvdcLineAttributes::setMaxP));
        hvdcLineMappings.addColumnMapping("convertersMode", new ColumnMapping<>(HvdcLine.ConvertersMode.class, HvdcLineAttributes::getConvertersMode, HvdcLineAttributes::setConvertersMode));
        hvdcLineMappings.addColumnMapping("converterStationId1", new ColumnMapping<>(String.class, HvdcLineAttributes::getConverterStationId1, HvdcLineAttributes::setConverterStationId1));
        hvdcLineMappings.addColumnMapping("converterStationId2", new ColumnMapping<>(String.class, HvdcLineAttributes::getConverterStationId2, HvdcLineAttributes::setConverterStationId2));
        hvdcLineMappings.addColumnMapping("hvdcAngleDroopActivePowerControl", new ColumnMapping<>(HvdcAngleDroopActivePowerControlAttributes.class, HvdcLineAttributes::getHvdcAngleDroopActivePowerControl, HvdcLineAttributes::setHvdcAngleDroopActivePowerControl));
        hvdcLineMappings.addColumnMapping("hvdcOperatorActivePowerRange", new ColumnMapping<>(HvdcOperatorActivePowerRangeAttributes.class, HvdcLineAttributes::getHvdcOperatorActivePowerRange, HvdcLineAttributes::setHvdcOperatorActivePowerRange));
    }

    private void createTwoWindingsTransformerMappings() {
        twoWindingsTransformerMappings.addColumnMapping("name", new ColumnMapping<>(String.class, TwoWindingsTransformerAttributes::getName, TwoWindingsTransformerAttributes::setName));
        twoWindingsTransformerMappings.addColumnMapping(VOLTAGE_LEVEL_ID_1, new ColumnMapping<>(String.class, TwoWindingsTransformerAttributes::getVoltageLevelId1, TwoWindingsTransformerAttributes::setVoltageLevelId1));
        twoWindingsTransformerMappings.addColumnMapping(VOLTAGE_LEVEL_ID_2, new ColumnMapping<>(String.class, TwoWindingsTransformerAttributes::getVoltageLevelId2, TwoWindingsTransformerAttributes::setVoltageLevelId2));
        twoWindingsTransformerMappings.addColumnMapping("bus1", new ColumnMapping<>(String.class, TwoWindingsTransformerAttributes::getBus1, TwoWindingsTransformerAttributes::setBus1));
        twoWindingsTransformerMappings.addColumnMapping("bus2", new ColumnMapping<>(String.class, TwoWindingsTransformerAttributes::getBus2, TwoWindingsTransformerAttributes::setBus2));
        twoWindingsTransformerMappings.addColumnMapping(CONNECTABLE_BUS_1, new ColumnMapping<>(String.class, TwoWindingsTransformerAttributes::getConnectableBus1, TwoWindingsTransformerAttributes::setConnectableBus1));
        twoWindingsTransformerMappings.addColumnMapping(CONNECTABLE_BUS_2, new ColumnMapping<>(String.class, TwoWindingsTransformerAttributes::getConnectableBus2, TwoWindingsTransformerAttributes::setConnectableBus2));
        twoWindingsTransformerMappings.addColumnMapping("r", new ColumnMapping<>(Double.class, TwoWindingsTransformerAttributes::getR, TwoWindingsTransformerAttributes::setR));
        twoWindingsTransformerMappings.addColumnMapping("x", new ColumnMapping<>(Double.class, TwoWindingsTransformerAttributes::getX, TwoWindingsTransformerAttributes::setX));
        twoWindingsTransformerMappings.addColumnMapping("g", new ColumnMapping<>(Double.class, TwoWindingsTransformerAttributes::getG, TwoWindingsTransformerAttributes::setG));
        twoWindingsTransformerMappings.addColumnMapping("b", new ColumnMapping<>(Double.class, TwoWindingsTransformerAttributes::getB, TwoWindingsTransformerAttributes::setB));
        twoWindingsTransformerMappings.addColumnMapping("ratedU1", new ColumnMapping<>(Double.class, TwoWindingsTransformerAttributes::getRatedU1, TwoWindingsTransformerAttributes::setRatedU1));
        twoWindingsTransformerMappings.addColumnMapping("ratedU2", new ColumnMapping<>(Double.class, TwoWindingsTransformerAttributes::getRatedU2, TwoWindingsTransformerAttributes::setRatedU2));
        twoWindingsTransformerMappings.addColumnMapping(RATED_S, new ColumnMapping<>(Double.class, TwoWindingsTransformerAttributes::getRatedS, TwoWindingsTransformerAttributes::setRatedS));
        twoWindingsTransformerMappings.addColumnMapping("p1", new ColumnMapping<>(Double.class, TwoWindingsTransformerAttributes::getP1, TwoWindingsTransformerAttributes::setP1));
        twoWindingsTransformerMappings.addColumnMapping("q1", new ColumnMapping<>(Double.class, TwoWindingsTransformerAttributes::getQ1, TwoWindingsTransformerAttributes::setQ1));
        twoWindingsTransformerMappings.addColumnMapping("p2", new ColumnMapping<>(Double.class, TwoWindingsTransformerAttributes::getP2, TwoWindingsTransformerAttributes::setP2));
        twoWindingsTransformerMappings.addColumnMapping("q2", new ColumnMapping<>(Double.class, TwoWindingsTransformerAttributes::getQ2, TwoWindingsTransformerAttributes::setQ2));
        twoWindingsTransformerMappings.addColumnMapping(FICTITIOUS, new ColumnMapping<>(Boolean.class, TwoWindingsTransformerAttributes::isFictitious, TwoWindingsTransformerAttributes::setFictitious));
        twoWindingsTransformerMappings.addColumnMapping(NODE_1, new ColumnMapping<>(Integer.class, TwoWindingsTransformerAttributes::getNode1, TwoWindingsTransformerAttributes::setNode1));
        twoWindingsTransformerMappings.addColumnMapping(NODE_2, new ColumnMapping<>(Integer.class, TwoWindingsTransformerAttributes::getNode2, TwoWindingsTransformerAttributes::setNode2));
        twoWindingsTransformerMappings.addColumnMapping(PROPERTIES, new ColumnMapping<>(Map.class, TwoWindingsTransformerAttributes::getProperties, TwoWindingsTransformerAttributes::setProperties));
        twoWindingsTransformerMappings.addColumnMapping(ALIAS_BY_TYPE, new ColumnMapping<>(Map.class, TwoWindingsTransformerAttributes::getAliasByType, TwoWindingsTransformerAttributes::setAliasByType));
        twoWindingsTransformerMappings.addColumnMapping(ALIASES_WITHOUT_TYPE, new ColumnMapping<>(Set.class, TwoWindingsTransformerAttributes::getAliasesWithoutType, TwoWindingsTransformerAttributes::setAliasesWithoutType));
        twoWindingsTransformerMappings.addColumnMapping(POSITION_1, new ColumnMapping<>(ConnectablePositionAttributes.class, TwoWindingsTransformerAttributes::getPosition1, TwoWindingsTransformerAttributes::setPosition1));
        twoWindingsTransformerMappings.addColumnMapping(POSITION_2, new ColumnMapping<>(ConnectablePositionAttributes.class, TwoWindingsTransformerAttributes::getPosition2, TwoWindingsTransformerAttributes::setPosition2));
        twoWindingsTransformerMappings.addColumnMapping(SELECTED_OPERATIONAL_LIMITS_GROUP_ID1_COLUMN, new ColumnMapping<>(String.class, TwoWindingsTransformerAttributes::getSelectedOperationalLimitsGroupId1, TwoWindingsTransformerAttributes::setSelectedOperationalLimitsGroupId1));
        twoWindingsTransformerMappings.addColumnMapping(SELECTED_OPERATIONAL_LIMITS_GROUP_ID2_COLUMN, new ColumnMapping<>(String.class, TwoWindingsTransformerAttributes::getSelectedOperationalLimitsGroupId2, TwoWindingsTransformerAttributes::setSelectedOperationalLimitsGroupId2));
        twoWindingsTransformerMappings.addColumnMapping("cgmesTapChangers", new ColumnMapping<>(List.class, TwoWindingsTransformerAttributes::getCgmesTapChangerAttributesList, TwoWindingsTransformerAttributes::setCgmesTapChangerAttributesList));
        twoWindingsTransformerMappings.addColumnMapping("phaseAngleClock", new ColumnMapping<>(TwoWindingsTransformerPhaseAngleClockAttributes.class, TwoWindingsTransformerAttributes::getPhaseAngleClockAttributes, TwoWindingsTransformerAttributes::setPhaseAngleClockAttributes));

        // phaseTapChanger
        twoWindingsTransformerMappings.addColumnMapping("phaseTapChangerLowTapPosition", new ColumnMapping<>(Integer.class,
            (TwoWindingsTransformerAttributes attributes) -> attributes.getPhaseTapChangerAttributes() != null ? attributes.getPhaseTapChangerAttributes().getLowTapPosition() : null,
            (TwoWindingsTransformerAttributes attributes, Integer value) -> {
                if (attributes.getPhaseTapChangerAttributes() == null) {
                    attributes.setPhaseTapChangerAttributes(new PhaseTapChangerAttributes());
                }
                attributes.getPhaseTapChangerAttributes().setLowTapPosition(value);
            }));
        twoWindingsTransformerMappings.addColumnMapping("phaseTapChangerTapPosition", new ColumnMapping<>(Integer.class,
            (TwoWindingsTransformerAttributes attributes) -> attributes.getPhaseTapChangerAttributes() != null ? attributes.getPhaseTapChangerAttributes().getTapPosition() : null,
            (TwoWindingsTransformerAttributes attributes, Integer value) -> {
                if (attributes.getPhaseTapChangerAttributes() == null) {
                    attributes.setPhaseTapChangerAttributes(new PhaseTapChangerAttributes());
                }
                attributes.getPhaseTapChangerAttributes().setTapPosition(value);
            }));
        twoWindingsTransformerMappings.addColumnMapping("phaseTapChangerTargetDeadband", new ColumnMapping<>(Double.class,
            (TwoWindingsTransformerAttributes attributes) -> attributes.getPhaseTapChangerAttributes() != null ? attributes.getPhaseTapChangerAttributes().getTargetDeadband() : null,
            (TwoWindingsTransformerAttributes attributes, Double value) -> {
                if (attributes.getPhaseTapChangerAttributes() == null) {
                    attributes.setPhaseTapChangerAttributes(new PhaseTapChangerAttributes());
                }
                attributes.getPhaseTapChangerAttributes().setTargetDeadband(value);
            }));
        twoWindingsTransformerMappings.addColumnMapping("phaseTapChangerRegulationValue", new ColumnMapping<>(Double.class,
            (TwoWindingsTransformerAttributes attributes) -> attributes.getPhaseTapChangerAttributes() != null ? attributes.getPhaseTapChangerAttributes().getRegulationValue() : null,
            (TwoWindingsTransformerAttributes attributes, Double value) -> {
                if (attributes.getPhaseTapChangerAttributes() == null) {
                    attributes.setPhaseTapChangerAttributes(new PhaseTapChangerAttributes());
                }
                attributes.getPhaseTapChangerAttributes().setRegulationValue(value);
            }));
        // ratioTapChanger
        twoWindingsTransformerMappings.addColumnMapping("ratioTapChangerLowTapPosition", new ColumnMapping<>(Integer.class,
            (TwoWindingsTransformerAttributes attributes) -> attributes.getRatioTapChangerAttributes() != null ? attributes.getRatioTapChangerAttributes().getLowTapPosition() : null,
            (TwoWindingsTransformerAttributes attributes, Integer value) -> {
                if (attributes.getRatioTapChangerAttributes() == null) {
                    attributes.setRatioTapChangerAttributes(new RatioTapChangerAttributes());
                }
                attributes.getRatioTapChangerAttributes().setLowTapPosition(value);
            }));
        twoWindingsTransformerMappings.addColumnMapping("ratioTapChangerTapPosition", new ColumnMapping<>(Integer.class,
            (TwoWindingsTransformerAttributes attributes) -> attributes.getRatioTapChangerAttributes() != null ? attributes.getRatioTapChangerAttributes().getTapPosition() : null,
            (TwoWindingsTransformerAttributes attributes, Integer value) -> {
                if (attributes.getRatioTapChangerAttributes() == null) {
                    attributes.setRatioTapChangerAttributes(new RatioTapChangerAttributes());
                }
                attributes.getRatioTapChangerAttributes().setTapPosition(value);
            }));
        twoWindingsTransformerMappings.addColumnMapping("ratioTapChangerTargetDeadband", new ColumnMapping<>(Double.class,
            (TwoWindingsTransformerAttributes attributes) -> attributes.getRatioTapChangerAttributes() != null ? attributes.getRatioTapChangerAttributes().getTargetDeadband() : null,
            (TwoWindingsTransformerAttributes attributes, Double value) -> {
                if (attributes.getRatioTapChangerAttributes() == null) {
                    attributes.setRatioTapChangerAttributes(new RatioTapChangerAttributes());
                }
                attributes.getRatioTapChangerAttributes().setTargetDeadband(value);
            }));
        twoWindingsTransformerMappings.addColumnMapping("ratioTapChangerLoadTapChangingCapabilities", new ColumnMapping<>(Boolean.class,
            (TwoWindingsTransformerAttributes attributes) -> attributes.getRatioTapChangerAttributes() != null ? attributes.getRatioTapChangerAttributes().isLoadTapChangingCapabilities() : null,
            (TwoWindingsTransformerAttributes attributes, Boolean value) -> {
                if (attributes.getRatioTapChangerAttributes() == null) {
                    attributes.setRatioTapChangerAttributes(new RatioTapChangerAttributes());
                }
                attributes.getRatioTapChangerAttributes().setLoadTapChangingCapabilities(value);
            }));
        twoWindingsTransformerMappings.addColumnMapping("ratioTapChangerRegulationValue", new ColumnMapping<>(Double.class,
            (TwoWindingsTransformerAttributes attributes) -> attributes.getRatioTapChangerAttributes() != null ? attributes.getRatioTapChangerAttributes().getRegulationValue() : null,
            (TwoWindingsTransformerAttributes attributes, Double value) -> {
                if (attributes.getRatioTapChangerAttributes() == null) {
                    attributes.setRatioTapChangerAttributes(new RatioTapChangerAttributes());
                }
                attributes.getRatioTapChangerAttributes().setRegulationValue(value);
            }));
    }

    private void createThreeWindingsTransformerMappings() {
        threeWindingsTransformerMappings.addColumnMapping("name", new ColumnMapping<>(String.class, ThreeWindingsTransformerAttributes::getName, ThreeWindingsTransformerAttributes::setName));
        threeWindingsTransformerMappings.addColumnMapping("p1", new ColumnMapping<>(Double.class, ThreeWindingsTransformerAttributes::getP1, ThreeWindingsTransformerAttributes::setP1));
        threeWindingsTransformerMappings.addColumnMapping("q1", new ColumnMapping<>(Double.class, ThreeWindingsTransformerAttributes::getQ1, ThreeWindingsTransformerAttributes::setQ1));
        threeWindingsTransformerMappings.addColumnMapping("p2", new ColumnMapping<>(Double.class, ThreeWindingsTransformerAttributes::getP2, ThreeWindingsTransformerAttributes::setP2));
        threeWindingsTransformerMappings.addColumnMapping("q2", new ColumnMapping<>(Double.class, ThreeWindingsTransformerAttributes::getQ2, ThreeWindingsTransformerAttributes::setQ2));
        threeWindingsTransformerMappings.addColumnMapping("p3", new ColumnMapping<>(Double.class, ThreeWindingsTransformerAttributes::getP3, ThreeWindingsTransformerAttributes::setP3));
        threeWindingsTransformerMappings.addColumnMapping("q3", new ColumnMapping<>(Double.class, ThreeWindingsTransformerAttributes::getQ3, ThreeWindingsTransformerAttributes::setQ3));
        threeWindingsTransformerMappings.addColumnMapping("ratedU0", new ColumnMapping<>(Double.class, ThreeWindingsTransformerAttributes::getRatedU0, ThreeWindingsTransformerAttributes::setRatedU0));
        threeWindingsTransformerMappings.addColumnMapping(FICTITIOUS, new ColumnMapping<>(Boolean.class, ThreeWindingsTransformerAttributes::isFictitious, ThreeWindingsTransformerAttributes::setFictitious));
        threeWindingsTransformerMappings.addColumnMapping(PROPERTIES, new ColumnMapping<>(Map.class, ThreeWindingsTransformerAttributes::getProperties, ThreeWindingsTransformerAttributes::setProperties));
        threeWindingsTransformerMappings.addColumnMapping(ALIAS_BY_TYPE, new ColumnMapping<>(Map.class, ThreeWindingsTransformerAttributes::getAliasByType, ThreeWindingsTransformerAttributes::setAliasByType));
        threeWindingsTransformerMappings.addColumnMapping(ALIASES_WITHOUT_TYPE, new ColumnMapping<>(Set.class, ThreeWindingsTransformerAttributes::getAliasesWithoutType, ThreeWindingsTransformerAttributes::setAliasesWithoutType));
        threeWindingsTransformerMappings.addColumnMapping(POSITION_1, new ColumnMapping<>(ConnectablePositionAttributes.class, ThreeWindingsTransformerAttributes::getPosition1, ThreeWindingsTransformerAttributes::setPosition1));
        threeWindingsTransformerMappings.addColumnMapping(POSITION_2, new ColumnMapping<>(ConnectablePositionAttributes.class, ThreeWindingsTransformerAttributes::getPosition2, ThreeWindingsTransformerAttributes::setPosition2));
        threeWindingsTransformerMappings.addColumnMapping("position3", new ColumnMapping<>(ConnectablePositionAttributes.class, ThreeWindingsTransformerAttributes::getPosition3, ThreeWindingsTransformerAttributes::setPosition3));
        threeWindingsTransformerMappings.addColumnMapping("cgmesTapChangers", new ColumnMapping<>(List.class, ThreeWindingsTransformerAttributes::getCgmesTapChangerAttributesList, ThreeWindingsTransformerAttributes::setCgmesTapChangerAttributesList));
        threeWindingsTransformerMappings.addColumnMapping("phaseAngleClock", new ColumnMapping<>(ThreeWindingsTransformerPhaseAngleClockAttributes.class, ThreeWindingsTransformerAttributes::getPhaseAngleClock, ThreeWindingsTransformerAttributes::setPhaseAngleClock));
        // Mapping for legs
        IntStream.of(1, 2, 3).forEach(i -> {
            threeWindingsTransformerMappings.addColumnMapping(VOLTAGE_LEVEL_ID + i, new ColumnMapping<>(String.class,
                (ThreeWindingsTransformerAttributes attributes) -> attributes.getLeg(i).getVoltageLevelId(),
                (ThreeWindingsTransformerAttributes attributes, String vId) -> attributes.getLeg(i).setVoltageLevelId(vId)));
            threeWindingsTransformerMappings.addColumnMapping(NODE + i, new ColumnMapping<>(Integer.class,
                (ThreeWindingsTransformerAttributes attributes) -> attributes.getLeg(i).getNode(),
                (ThreeWindingsTransformerAttributes attributes, Integer node) -> attributes.getLeg(i).setNode(node)));
            threeWindingsTransformerMappings.addColumnMapping(BUS + i, new ColumnMapping<>(String.class,
                (ThreeWindingsTransformerAttributes attributes) -> attributes.getLeg(i).getBus(),
                (ThreeWindingsTransformerAttributes attributes, String bus) -> attributes.getLeg(i).setBus(bus)));
            threeWindingsTransformerMappings.addColumnMapping(CONNECTABLE_BUS + i, new ColumnMapping<>(String.class,
                (ThreeWindingsTransformerAttributes attributes) -> attributes.getLeg(i).getConnectableBus(),
                (ThreeWindingsTransformerAttributes attributes, String bus) -> attributes.getLeg(i).setConnectableBus(bus)));
            threeWindingsTransformerMappings.addColumnMapping(R_VALUE + i, new ColumnMapping<>(Double.class,
                (ThreeWindingsTransformerAttributes attributes) -> attributes.getLeg(i).getR(),
                (ThreeWindingsTransformerAttributes attributes, Double r) -> attributes.getLeg(i).setR(r)));
            threeWindingsTransformerMappings.addColumnMapping(X_VALUE + i, new ColumnMapping<>(Double.class,
                (ThreeWindingsTransformerAttributes attributes) -> attributes.getLeg(i).getX(),
                (ThreeWindingsTransformerAttributes attributes, Double x) -> attributes.getLeg(i).setX(x)));
            threeWindingsTransformerMappings.addColumnMapping(G_VALUE + i, new ColumnMapping<>(Double.class,
                (ThreeWindingsTransformerAttributes attributes) -> attributes.getLeg(i).getG(),
                (ThreeWindingsTransformerAttributes attributes, Double g) -> attributes.getLeg(i).setG(g)));
            threeWindingsTransformerMappings.addColumnMapping(B_VALUE + i, new ColumnMapping<>(Double.class,
                (ThreeWindingsTransformerAttributes attributes) -> attributes.getLeg(i).getB(),
                (ThreeWindingsTransformerAttributes attributes, Double b) -> attributes.getLeg(i).setB(b)));
            threeWindingsTransformerMappings.addColumnMapping(RATED_U + i, new ColumnMapping<>(Double.class,
                (ThreeWindingsTransformerAttributes attributes) -> attributes.getLeg(i).getRatedU(),
                (ThreeWindingsTransformerAttributes attributes, Double ratedU) -> attributes.getLeg(i).setRatedU(ratedU)));
            threeWindingsTransformerMappings.addColumnMapping(RATED_S + i, new ColumnMapping<>(Double.class,
                (ThreeWindingsTransformerAttributes attributes) -> attributes.getLeg(i).getRatedS(),
                (ThreeWindingsTransformerAttributes attributes, Double ratedS) -> attributes.getLeg(i).setRatedS(ratedS)));
            threeWindingsTransformerMappings.addColumnMapping(SELECTED_OPERATIONAL_LIMITS_GROUP_ID_COLUMN + i, new ColumnMapping<>(String.class,
                (ThreeWindingsTransformerAttributes attributes) -> attributes.getLeg(i).getSelectedOperationalLimitsGroupId(),
                (ThreeWindingsTransformerAttributes attributes, String selectedOperationalLimitsGroupId) -> attributes.getLeg(i).setSelectedOperationalLimitsGroupId(selectedOperationalLimitsGroupId)));
            // PhaseTapChanger
            threeWindingsTransformerMappings.addColumnMapping("phaseTapChangerLowTapPosition" + i, new ColumnMapping<>(Integer.class,
                (ThreeWindingsTransformerAttributes attributes) -> attributes.getLeg(i).getPhaseTapChangerAttributes() != null ? attributes.getLeg(i).getPhaseTapChangerAttributes().getLowTapPosition() : null,
                (ThreeWindingsTransformerAttributes attributes, Integer value) -> {
                    if (attributes.getLeg(i).getPhaseTapChangerAttributes() == null) {
                        attributes.getLeg(i).setPhaseTapChangerAttributes(new PhaseTapChangerAttributes());
                    }
                    attributes.getLeg(i).getPhaseTapChangerAttributes().setLowTapPosition(value);
                }));
            threeWindingsTransformerMappings.addColumnMapping("phaseTapChangerTapPosition" + i, new ColumnMapping<>(Integer.class,
                (ThreeWindingsTransformerAttributes attributes) -> attributes.getLeg(i).getPhaseTapChangerAttributes() != null ? attributes.getLeg(i).getPhaseTapChangerAttributes().getTapPosition() : null,
                (ThreeWindingsTransformerAttributes attributes, Integer value) -> {
                    if (attributes.getLeg(i).getPhaseTapChangerAttributes() == null) {
                        attributes.getLeg(i).setPhaseTapChangerAttributes(new PhaseTapChangerAttributes());
                    }
                    attributes.getLeg(i).getPhaseTapChangerAttributes().setTapPosition(value);
                }));
            threeWindingsTransformerMappings.addColumnMapping("phaseTapChangerTargetDeadband" + i, new ColumnMapping<>(Double.class,
                (ThreeWindingsTransformerAttributes attributes) -> attributes.getLeg(i).getPhaseTapChangerAttributes() != null ? attributes.getLeg(i).getPhaseTapChangerAttributes().getTargetDeadband() : null,
                (ThreeWindingsTransformerAttributes attributes, Double value) -> {
                    if (attributes.getLeg(i).getPhaseTapChangerAttributes() == null) {
                        attributes.getLeg(i).setPhaseTapChangerAttributes(new PhaseTapChangerAttributes());
                    }
                    attributes.getLeg(i).getPhaseTapChangerAttributes().setTargetDeadband(value);
                }));
            threeWindingsTransformerMappings.addColumnMapping("phaseTapChangerRegulationValue" + i, new ColumnMapping<>(Double.class,
                (ThreeWindingsTransformerAttributes attributes) -> attributes.getLeg(i).getPhaseTapChangerAttributes() != null ? attributes.getLeg(i).getPhaseTapChangerAttributes().getRegulationValue() : null,
                (ThreeWindingsTransformerAttributes attributes, Double value) -> {
                    if (attributes.getLeg(i).getPhaseTapChangerAttributes() == null) {
                        attributes.getLeg(i).setPhaseTapChangerAttributes(new PhaseTapChangerAttributes());
                    }
                    attributes.getLeg(i).getPhaseTapChangerAttributes().setRegulationValue(value);
                }));
            // RatioTapChanger
            threeWindingsTransformerMappings.addColumnMapping("ratioTapChangerLowTapPosition" + i, new ColumnMapping<>(Integer.class,
                (ThreeWindingsTransformerAttributes attributes) -> attributes.getLeg(i).getRatioTapChangerAttributes() != null ? attributes.getLeg(i).getRatioTapChangerAttributes().getLowTapPosition() : null,
                (ThreeWindingsTransformerAttributes attributes, Integer value) -> {
                    if (attributes.getLeg(i).getRatioTapChangerAttributes() == null) {
                        attributes.getLeg(i).setRatioTapChangerAttributes(new RatioTapChangerAttributes());
                    }
                    attributes.getLeg(i).getRatioTapChangerAttributes().setLowTapPosition(value);
                }));
            threeWindingsTransformerMappings.addColumnMapping("ratioTapChangerTapPosition" + i, new ColumnMapping<>(Integer.class,
                (ThreeWindingsTransformerAttributes attributes) -> attributes.getLeg(i).getRatioTapChangerAttributes() != null ? attributes.getLeg(i).getRatioTapChangerAttributes().getTapPosition() : null,
                (ThreeWindingsTransformerAttributes attributes, Integer value) -> {
                    if (attributes.getLeg(i).getRatioTapChangerAttributes() == null) {
                        attributes.getLeg(i).setRatioTapChangerAttributes(new RatioTapChangerAttributes());
                    }
                    attributes.getLeg(i).getRatioTapChangerAttributes().setTapPosition(value);
                }));
            threeWindingsTransformerMappings.addColumnMapping("ratioTapChangerTargetDeadband" + i, new ColumnMapping<>(Double.class,
                (ThreeWindingsTransformerAttributes attributes) -> attributes.getLeg(i).getRatioTapChangerAttributes() != null ? attributes.getLeg(i).getRatioTapChangerAttributes().getTargetDeadband() : null,
                (ThreeWindingsTransformerAttributes attributes, Double value) -> {
                    if (attributes.getLeg(i).getRatioTapChangerAttributes() == null) {
                        attributes.getLeg(i).setRatioTapChangerAttributes(new RatioTapChangerAttributes());
                    }
                    attributes.getLeg(i).getRatioTapChangerAttributes().setTargetDeadband(value);
                }));
            threeWindingsTransformerMappings.addColumnMapping("ratioTapChangerLoadTapChangingCapabilities" + i, new ColumnMapping<>(Boolean.class,
                (ThreeWindingsTransformerAttributes attributes) -> attributes.getLeg(i).getRatioTapChangerAttributes() != null ? attributes.getLeg(i).getRatioTapChangerAttributes().isLoadTapChangingCapabilities() : null,
                (ThreeWindingsTransformerAttributes attributes, Boolean value) -> {
                    if (attributes.getLeg(i).getRatioTapChangerAttributes() == null) {
                        attributes.getLeg(i).setRatioTapChangerAttributes(new RatioTapChangerAttributes());
                    }
                    attributes.getLeg(i).getRatioTapChangerAttributes().setLoadTapChangingCapabilities(value);
                }));
            threeWindingsTransformerMappings.addColumnMapping("ratioTapChangerRegulationValue" + i, new ColumnMapping<>(Double.class,
                (ThreeWindingsTransformerAttributes attributes) -> attributes.getLeg(i).getRatioTapChangerAttributes() != null ? attributes.getLeg(i).getRatioTapChangerAttributes().getRegulationValue() : null,
                (ThreeWindingsTransformerAttributes attributes, Double value) -> {
                    if (attributes.getLeg(i).getRatioTapChangerAttributes() == null) {
                        attributes.getLeg(i).setRatioTapChangerAttributes(new RatioTapChangerAttributes());
                    }
                    attributes.getLeg(i).getRatioTapChangerAttributes().setRegulationValue(value);
                }));
        });
    }

    public Mappings() {
        createLineMappings();
        createLoadMappings();
        createGeneratorMappings();
        createSwitchMappings();
        createSubstationMappings();
        createNetworkMappings();
        createBatteryMappings();
        createVoltageLevelMappings();
        createBusbarSectionMappings();
        createConfiguredBusMappings();
        createDanglingLineMappings();
        createShuntCompensatorMappings();
        createVscConverterStationMappings();
        createLccConverterStationMappings();
        createStaticVarCompensatorMappings();
        createHvdcLineMappings();
        createTwoWindingsTransformerMappings();
        createThreeWindingsTransformerMappings();
        createTieLineMappings();
        createGroundMappings();
        createAreaMappings();
        for (TableMapping tableMapping : all) {
            mappingByTable.put(tableMapping.getTable().toLowerCase(), tableMapping);
            mappingByType.put(tableMapping.getResourceType(), tableMapping);
        }
    }
}
