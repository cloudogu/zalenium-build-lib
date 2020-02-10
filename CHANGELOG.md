# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- new lib function for running E2E tests with pure `Selenium` without the help of `Zalenium`

### Changed
- signatures for `withMavenSettings` changed slightly so the maven central mirror can be configured
- helper `generateZaleniumJobName` was renamed to `generateJobName`