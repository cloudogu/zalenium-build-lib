# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed

- waiting for the selenium grid does not fail instantly if the grid api isn't up already (#13)

## [v2.1.0](https://github.com/cloudogu/zalenium-build-lib/releases/tag/v2.1.0) - 2020-12-14

### Added

- config parameter for controlling the video recording during test execution (#11)

## [v2.0.0](https://github.com/cloudogu/zalenium-build-lib/releases/tag/v2.0.0) - 2020-02-21

Heads-up: Breaking change ahead!

This version changes a hardcoded repository name into a changeable repository name in the `withMavenSettings.mvnWithSettings()` part. It does default to `maven-central` though. But if the need arises to choose a different repository, the new signature looks like this:
```
withMavenSettings.mvnWithSettings nexusCredentials, cesFQDN, yourDifferentRepositoryName, mvnCallArgs
```

### Added
- new lib function for running E2E tests with pure `Selenium` without the help of `Zalenium`
  - This is addressable with following jenkins pipeline variable: `withSelenium`
  - Please note that `withSelenium` MUST receive a Docker network because several containers are started and which must communicate with each other.

### Changed
- signatures for `withMavenSettings` changed slightly so the maven central mirror can be configured
- helper `generateZaleniumJobName` was renamed to `generateJobName`
   - a delegate method keeps up the backward compatibility
   - Please note that the method `generateZaleniumJobName()` may be removed in future releases
- `generateJobName` now uses `-` instead of `_` because of possible problems when used as DNS name 

### Bugfix

The release v1.2.0 introduced a defect in which the default Nexus mirror repository name was not properly set. This releases removes this defect (#9)

## [v1.2.0](https://github.com/cloudogu/zalenium-build-lib/releases/tag/v1.1.0) - 2020-02-21

This release contains breaking changes and should not be used. Instead use the release v2.0.0

## [v1.1.0](https://github.com/cloudogu/zalenium-build-lib/releases/tag/v1.1.0) - 2019-09-18 

### Added

In this release several Maven settings and truststore helpers are added. These are addressable with following jenkins pipeline variables for which (if the library is added) help texts appear in Jenkins' pipeline steps help (#4, #6):
- `withMavenSettings`
- `truststore`
- `helper`

   
## [v1.0.0](https://github.com/cloudogu/zalenium-build-lib/releases/tag/v1.0.0) - 2019-08-28

This adds Docker network support (#2) which automatically removes itself once the given body ends. This is addressable with following jenkins pipeline variable:
- `withDockerNetwork`
