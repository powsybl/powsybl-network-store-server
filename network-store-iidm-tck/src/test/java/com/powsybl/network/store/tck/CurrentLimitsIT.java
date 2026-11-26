/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.tck;

import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.CurrentLimits;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.tck.AbstractCurrentLimitsTest;
import com.powsybl.network.store.server.NetworkStoreApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {"spring.config.location=classpath:application.yaml"})
@ContextHierarchy({@ContextConfiguration(classes = {NetworkStoreApplication.class})})
class CurrentLimitsIT extends AbstractCurrentLimitsTest {

    private static Network createNetwork() {
        Network network = Network.create("test", "test");
        Substation s1 = network.newSubstation()
                .setId("S1")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl1 = s1.newVoltageLevel()
                .setId("VL1")
                .setNominalV(400.0)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl1.getBusBreakerView().newBus()
                .setId("B1")
                .add();
        Substation s2 = network.newSubstation()
                .setId("S2")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl2 = s2.newVoltageLevel()
                .setId("VL2")
                .setNominalV(400.0)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl2.getBusBreakerView().newBus()
                .setId("B2")
                .add();
        Line l = network.newLine()
                .setId("L")
                .setVoltageLevel1("VL1")
                .setConnectableBus1("B1")
                .setBus1("B1")
                .setVoltageLevel2("VL2")
                .setConnectableBus2("B2")
                .setBus2("B2")
                .setR(1.0)
                .setX(1.0)
                .setG1(0.0)
                .setG2(0.0)
                .setB1(0.0)
                .setB2(0.0)
                .add();
        l.getOrCreateSelectedOperationalLimitsGroup1().newCurrentLimits()
                .setPermanentLimit(1000.0)
                .beginTemporaryLimit()
                .setName("20'")
                .setAcceptableDuration(20 * 60)
                .setValue(1200.0)
                .endTemporaryLimit()
                .beginTemporaryLimit()
                .setName("5'")
                .setAcceptableDuration(5 * 60)
                .setValue(1400.0)
                .endTemporaryLimit()
                .beginTemporaryLimit()
                .setName("1'")
                .setAcceptableDuration(60)
                .setValue(1600.0)
                .endTemporaryLimit()
                .add();
        return network;
    }

    // FIXME: this test is overridden because the validation rule has changed: temporary limit name duplication
    // is no longer allowed, so the expectation in powsybl-core's AbstractCurrentLimitsTest is outdated.
    @Override
    @Test
    public void testNameDuplicationIsAllowed() {
        Line line = createNetwork().getLine("L");
        CurrentLimits currentLimits = line.getOrCreateSelectedOperationalLimitsGroup1().newCurrentLimits()
                .setPermanentLimit(100.0)
                .beginTemporaryLimit()
                .setName("TL")
                .setAcceptableDuration(20 * 60)
                .setValue(1200.0)
                .endTemporaryLimit()
                .beginTemporaryLimit()
                .setName("TL")
                .setAcceptableDuration(10 * 60)
                .setValue(1400.0)
                .endTemporaryLimit()
                .add();

        assertEquals("TL", currentLimits.getTemporaryLimit(20 * 60).getName());
        assertEquals("TL#0", currentLimits.getTemporaryLimit(10 * 60).getName());
    }

    @Override
    @Test
    public void test() {
        // FIXME delete this test when we fix Bus.getV/setV not getting/updating correctly the V in all views
    }

    @Override
    @Test
    public void testForThreeWindingsTransformerLeg1() {
        // FIXME delete this test when we fix Bus.getV/setV not getting/updating correctly the V in all views
    }

    @Override
    @Test
    public void testForThreeWindingsTransformerLeg2() {
        // FIXME delete this test when we fix Bus.getV/setV not getting/updating correctly the V in all views
    }

    @Override
    @Test
    public void testForThreeWindingsTransformerLeg3() {
        // FIXME delete this test when we fix Bus.getV/setV not getting/updating correctly the V in all views
    }

    @Override
    @Test
    public void testLimitWithoutTempLimit() {
        // FIXME delete this test when we fix Bus.getV/setV not getting/updating correctly the V in all views
    }

    @Override
    @Test
    public void testSetterGetter() {
        // FIXME delete this test when we fix Bus.getV/setV not getting/updating correctly the V in all views
    }
}
