/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.tck;

import com.powsybl.iidm.network.tck.AbstractTerminalFinderTest;
import com.powsybl.network.store.server.NetworkStoreApplication;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {"spring.config.location=classpath:application.yaml"})
@ContextHierarchy({@ContextConfiguration(classes = {NetworkStoreApplication.class})})
class TerminalFinderIT extends AbstractTerminalFinderTest {
    @Disabled("order differences on getConnectedTerminals")
    @Override
    @Test
    public void testLineTerminal1() {
        // FIXME remove this when we fix order differences on getConnectedTerminals
        super.testLineTerminal1();
    }

    @Disabled("order differences on getConnectedTerminals")
    @Override
    @Test
    public void testLineTerminal2() {
        // FIXME remove this when we fix order differences on getConnectedTerminals
        super.testLineTerminal2();
    }
}
