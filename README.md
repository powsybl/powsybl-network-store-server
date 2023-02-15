# PowSyBl Network Store

[![Actions Status](https://github.com/powsybl/powsybl-network-store/workflows/CI/badge.svg)](https://github.com/powsybl/powsybl-network-store/actions)
[![Coverage Status](https://sonarcloud.io/api/project_badges/measure?project=com.powsybl%3Apowsybl-network-store&metric=coverage)](https://sonarcloud.io/component_measures?id=com.powsybl%3Apowsybl-network-store&metric=coverage)
[![MPL-2.0 License](https://img.shields.io/badge/license-MPL_2.0-blue.svg)](https://www.mozilla.org/en-US/MPL/2.0/)
[![Join the community on Spectrum](https://withspectrum.github.io/badge/badge.svg)](https://spectrum.chat/powsybl)
[![Slack](https://img.shields.io/badge/slack-powsybl-blueviolet.svg?logo=slack)](https://join.slack.com/t/powsybl/shared_invite/zt-rzvbuzjk-nxi0boim1RKPS5PjieI0rA)

PowSyBl (**Pow**er **Sy**stem **Bl**ocks) is an open source framework written in Java, that makes it easy to write complex
software for power systems’ simulations and analysis. Its modular approach allows developers to extend or customize its
features.

PowSyBl is part of the LF Energy Foundation, a project of The Linux Foundation that supports open source innovation projects
within the energy and electricity sectors.

<p align="center">
<img src="https://raw.githubusercontent.com/powsybl/powsybl-gse/main/gse-spi/src/main/resources/images/logo_lfe_powsybl.svg?sanitize=true" alt="PowSyBl Logo" width="50%"/>
</p>

Read more at https://www.powsybl.org !

This project and everyone participating in it is governed by the [PowSyBl Code of Conduct](https://github.com/powsybl/.github/blob/main/CODE_OF_CONDUCT.md).
By participating, you are expected to uphold this code. Please report unacceptable behavior to [powsybl-tsc@lists.lfenergy.org](mailto:powsybl-tsc@lists.lfenergy.org).

## PowSyBl vs PowSyBl Network Store

PowSyBl Network Store is an alternative implementation of PowSyBl Core Network API that persists
in a [PostgreSQL database](https://www.postgresql.org/).

## Getting started

### Build

```bash
cd powsybl-network-store-server
mvn clean install
```

### Postgresql install

Install postgresql with your system package manager or with a dedicated docker container (or any other method) and connect to the sql shell and create a database:
```sql
CREATE DATABASE iidm;
```

Copy paste network-store-server/src/main/resources/schema.sql in the sql shell to create the iidm tables.


### Start network store server

In an other shell: 

```bash
cd powsybl-network-store/network-store-server/target/
java -jar powsybl-network-store-server-1.0.0-SNAPSHOT-exec.jar
```

Spring boot server should start and connect to the postgresql database (localhost hardcoded...)

### Run integration tests

You can run the integration tests:
```bash
$ mvn verify
```
