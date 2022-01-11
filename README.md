[![Apache Sling](https://sling.apache.org/res/logos/sling.png)](https://sling.apache.org)

&#32;[![Build Status](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-jcr-repoinit/job/master/badge/icon)](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-jcr-repoinit/job/master/)&#32;[![Test Status](https://img.shields.io/jenkins/tests.svg?jobUrl=https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-jcr-repoinit/job/master/)](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-jcr-repoinit/job/master/test/?width=800&height=600)&#32;[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=apache_sling-org-apache-sling-jcr-repoinit&metric=coverage)](https://sonarcloud.io/dashboard?id=apache_sling-org-apache-sling-jcr-repoinit)&#32;[![Sonarcloud Status](https://sonarcloud.io/api/project_badges/measure?project=apache_sling-org-apache-sling-jcr-repoinit&metric=alert_status)](https://sonarcloud.io/dashboard?id=apache_sling-org-apache-sling-jcr-repoinit)&#32;[![JavaDoc](https://www.javadoc.io/badge/org.apache.sling/org.apache.sling.jcr.repoinit.svg)](https://www.javadoc.io/doc/org.apache.sling/org.apache.sling.jcr.repoinit)&#32;[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.apache.sling/org.apache.sling.jcr.repoinit/badge.svg)](https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22org.apache.sling%22%20a%3A%22org.apache.sling.jcr.repoinit%22)&#32;[![jcr](https://sling.apache.org/badges/group-jcr.svg)](https://github.com/apache/sling-aggregator/blob/master/docs/groups/jcr.md) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

# Apache Sling JCR RepoInit module

This module is part of the [Apache Sling](https://sling.apache.org) project.

This module implements [Repository Initalization](https://sling.apache.org/documentation/bundles/repository-initialization.html) operations for the Sling _repoinit_ modules, operations that can initialize content, service users and privileges in a JCR content repository. 

The user documentation for the Sling Repoinit modules is at https://sling.apache.org/documentation/bundles/repository-initialization.html

## Updating to a newer version of Jackrabbit Oak

The Oak version (and the associated Jackrabbit version) must be maintained in two locations:

- pom.xml for build and unit testing
- src/test/features for integration testing

Since the integration tests rely on the Sling Starter to provide the the base instance values, it
happens that the Oak version used by repoinit is more recent than the one provided in the starter.

We should not depend on SNAPSHOT starter versions, since this bundle is released more often than
the Starter. Therefore the recommended approach is to override the needed bundles in the following
manner:

* in src/test/provisioning/sling.txt - all bundles from the Sling Starter part of the 'sling.txt' feature
* in src/test/provisioning/oak.txt - all bundles from the Sling Starter part of the 'oak.txt' feature

For a more concrete example, see [SLING-8627 - Update sling-jcr-repoinit to Oak 1.16.0 and Jackrabbit 2.18](https://issues.apache.org/jira/browse/SLING-8627)
and associated commits.
