/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.iidm.impl;

import com.powsybl.iidm.network.*;
import com.powsybl.math.graph.TraverseResult;
import com.powsybl.network.store.model.InjectionAttributes;
import com.powsybl.network.store.model.Resource;
import com.powsybl.network.store.model.VoltageLevelAttributes;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class TerminalBusViewImpl<U extends InjectionAttributes> implements Terminal.BusView {

    private final NetworkObjectIndex index;

    private final U attributes;
    private final Connectable connectable;

    TerminalBusViewImpl(NetworkObjectIndex index, U attributes, Connectable connectable) {
        this.index = Objects.requireNonNull(index);
        this.attributes = attributes;
        this.connectable = connectable;
    }

    private boolean isNodeBeakerTopologyKind() {
        return getVoltageLevelResource().getAttributes().getTopologyKind() == TopologyKind.NODE_BREAKER;
    }

    private boolean isBusBeakerTopologyKind() {
        return getVoltageLevelResource().getAttributes().getTopologyKind() == TopologyKind.BUS_BREAKER;
    }

    private Resource<VoltageLevelAttributes> getVoltageLevelResource() {
        return index.getVoltageLevel(attributes.getVoltageLevelId()).orElseThrow(IllegalStateException::new).checkResource();
    }

    private Bus calculateBus() {
        return isNodeBeakerTopologyKind() ?
                NodeBreakerTopology.INSTANCE.calculateBus(index, getVoltageLevelResource(), attributes.getNode(), true) :
                BusBreakerTopology.INSTANCE.calculateBus(index, getVoltageLevelResource(), attributes.getBus(), true);
    }

    @Override
    public Bus getBus() {
        return calculateBus();
    }

    @Override
    public Bus getConnectableBus() {
        if (((AbstractIdentifiableImpl) connectable).optResource().isEmpty()) {
            return null;
        }

        VoltageLevelImpl voltageLevel = index.getVoltageLevel(attributes.getVoltageLevelId()).orElseThrow(IllegalStateException::new);
        if (isBusBeakerTopologyKind()) { // Merged bus
            return voltageLevel.getBusView().getMergedBus(attributes.getConnectableBus());
        } else { // Calculated bus
            return findConnectableBus();
        }
    }

    private Bus findConnectableBus() {
        VoltageLevelImpl voltageLevel = index.getVoltageLevel(attributes.getVoltageLevelId()).orElseThrow(IllegalStateException::new);

        final Bus[] foundBus = {getBus()};

        if (foundBus[0] != null) { // connected ?
            return foundBus[0];
        }

        Terminal.TopologyTraverser topologyTraverser = new Terminal.TopologyTraverser() {
            @Override
            public TraverseResult traverse(Terminal terminal, boolean connected) {
                if (!terminal.getVoltageLevel().getId().equals(voltageLevel.getId())) {
                    return TraverseResult.TERMINATE_PATH;
                }
                if (foundBus[0] != null) {
                    return TraverseResult.TERMINATE_PATH;
                }
                foundBus[0] = terminal.getBusView().getBus();
                return foundBus[0] == null ? TraverseResult.CONTINUE : TraverseResult.TERMINATE_PATH;
            }

            @Override
            public TraverseResult traverse(Switch aSwitch) {
                return foundBus[0] == null ? TraverseResult.CONTINUE : TraverseResult.TERMINATE_PATH;
            }
        };

        voltageLevel.getNodeBreakerView().getTerminal(attributes.getNode()).traverse(topologyTraverser);
        if (foundBus[0] != null) {
            return foundBus[0];
        }

        List<Bus> buses = voltageLevel.getBusView().getBusStream().collect(Collectors.toList());
        return buses.isEmpty() ? null : buses.get(0);
    }
}
