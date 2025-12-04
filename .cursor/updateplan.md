# Dependency Update Plan

## Overview
This document outlines the systematic plan to update all third-party dependencies and libraries in the abuhint project while maintaining functionality.

## Current State Analysis

### Spring Boot Version
- **Current**: 3.4.4
- **Status**: Latest stable version ✅
- **Action**: No update needed

### Java Version
- **Current**: 21
- **Status**: Latest LTS ✅
- **Action**: No update needed

---

## Phase 1: Critical Version Mismatches and Outdated Core Libraries

### Task 1.1: Fix Kotlin Version Mismatch
- **Issue**: `kotlin-stdlib` is pinned to `1.9.10` while `kotlin.version` property is `1.9.25`
- **Current**: kotlin-stdlib: 1.9.10, kotlin.version: 1.9.25
- **Target**: Align kotlin-stdlib with kotlin.version (remove explicit version to use parent)
- **Priority**: HIGH
- **Risk**: Medium - Version mismatch can cause runtime issues
- **Files Affected**: `pom.xml`
- **Action Steps**:
  1. Remove explicit version from kotlin-stdlib dependency
  2. Let Spring Boot parent manage Kotlin version
  3. Verify compilation succeeds
  4. Run tests to ensure compatibility

### Task 1.2: Update Kotlin Version
- **Current**: 1.9.25
- **Target**: Latest stable (check for 2.0.x or latest 1.9.x)
- **Priority**: HIGH
- **Risk**: Medium - Major version updates may have breaking changes
- **Files Affected**: `pom.xml`
- **Action Steps**:
  1. Research latest stable Kotlin version compatible with Spring Boot 3.4.4
  2. Update `kotlin.version` property
  3. Update kotlin-maven-allopen plugin version
  4. Test compilation and runtime

### Task 1.3: Update Jackson Module Kotlin
- **Current**: 2.18.0
- **Target**: Latest compatible with Spring Boot 3.4.4 (likely 2.17.x or 2.18.x)
- **Priority**: MEDIUM
- **Risk**: Low - Usually backward compatible within minor versions
- **Files Affected**: `pom.xml`, potentially JSON serialization code
- **Action Steps**:
  1. Check Spring Boot 3.4.4 managed version
  2. Remove explicit version if managed by parent
  3. Or update to latest compatible version
  4. Test JSON serialization/deserialization

---

## Phase 2: LangChain4j Ecosystem Updates

### Task 2.1: Update LangChain4j Core Libraries
- **Current**: 0.36.1
- **Target**: Latest stable version (check for 0.40.x+ or latest)
- **Priority**: HIGH
- **Risk**: HIGH - LangChain4j is core to the application, breaking changes likely
- **Files Affected**: 
  - `pom.xml`
  - `src/main/kotlin/no/josefus/abuhint/repository/LangChain4jAssistant.kt`
  - `src/main/kotlin/no/josefus/abuhint/repository/PineconeChatMemoryStore.kt`
  - `src/main/kotlin/no/josefus/abuhint/configuration/LangChain4jConfiguration.kt`
  - All service classes using LangChain4j
- **Action Steps**:
  1. Research latest LangChain4j version and breaking changes
  2. Review migration guide for version jump
  3. Update `langchain.version` property
  4. Test all AI service integrations
  5. Verify Pinecone integration still works
  6. Check OpenAI and Gemini integrations

### Task 2.2: Update Pinecone Client
- **Current**: 1.2.2
- **Target**: Latest stable version
- **Priority**: MEDIUM
- **Risk**: Medium - Pinecone API changes could affect embedding storage
- **Files Affected**: 
  - `pom.xml`
  - `src/main/kotlin/no/josefus/abuhint/repository/PineconeChatMemoryStore.kt`
  - `src/main/kotlin/no/josefus/abuhint/configuration/LangChain4jConfiguration.kt`
- **Action Steps**:
  1. Check latest Pinecone Java client version
  2. Verify compatibility with updated LangChain4j
  3. Test embedding storage and retrieval
  4. Verify namespace handling

---

## Phase 3: Apache POI Updates

### Task 3.1: Update Apache POI Libraries
- **Current**: 5.2.4
- **Target**: Latest stable version (check for 5.3.x or 6.x)
- **Priority**: MEDIUM
- **Risk**: Medium - PowerPoint generation is critical feature
- **Files Affected**: 
  - `pom.xml`
  - `src/main/kotlin/no/josefus/abuhint/tools/PowerPointGeneratorTool.kt`
- **Action Steps**:
  1. Research latest POI version
  2. Check for breaking API changes
  3. Update poi-ooxml and poi-scratchpad versions
  4. Test PowerPoint generation functionality
  5. Verify all slide types work correctly

---

## Phase 4: Kotlinx Libraries Updates

### Task 4.1: Update Kotlinx Coroutines
- **Current**: 1.8.1
- **Target**: Latest stable version (likely 1.9.x or 2.x)
- **Priority**: MEDIUM
- **Risk**: Medium - Coroutines API changes can affect async code
- **Files Affected**: 
  - `pom.xml`
  - Any code using coroutines (check for suspend functions)
- **Action Steps**:
  1. Research latest kotlinx-coroutines version
  2. Check compatibility with updated Kotlin version
  3. Review breaking changes in changelog
  4. Update dependency version
  5. Test async operations

### Task 4.2: Update Kotlinx Serialization JSON
- **Current**: 1.6.0
- **Target**: Latest stable version (likely 1.7.x or 2.x)
- **Priority**: MEDIUM
- **Risk**: Medium - Serialization format changes could break data
- **Files Affected**: 
  - `pom.xml`
  - `src/main/kotlin/no/josefus/abuhint/tools/PowerPointGeneratorTool.kt`
  - Any classes using `@Serializable`
- **Action Steps**:
  1. Research latest kotlinx-serialization-json version
  2. Check compatibility with updated Kotlin version
  3. Review breaking changes
  4. Test JSON serialization/deserialization
  5. Verify PowerPoint tool JSON parsing

---

## Phase 5: Third-Party API Clients

### Task 5.1: Update GitHub API Client
- **Current**: 1.326
- **Target**: Latest stable version
- **Priority**: MEDIUM
- **Risk**: Medium - GitHub API changes could affect repository operations
- **Files Affected**: 
  - `pom.xml`
  - `src/main/kotlin/no/josefus/abuhint/tools/GithubService.kt`
- **Action Steps**:
  1. Check latest kohsuke github-api version
  2. Review GitHub API changes
  3. Test pull request creation
  4. Test branch operations
  5. Verify file content operations

### Task 5.2: Update Resend Java Client
- **Current**: 3.1.0
- **Target**: Latest stable version
- **Priority**: LOW
- **Risk**: Low - Email service is straightforward
- **Files Affected**: 
  - `pom.xml`
  - `src/main/kotlin/no/josefus/abuhint/tools/EmailService.kt`
- **Action Steps**:
  1. Check latest resend-java version
  2. Review API changes
  3. Test email sending functionality

---

## Phase 6: Reactor Core Update

### Task 6.1: Update Reactor Core
- **Current**: 3.7.4 (explicitly pinned)
- **Target**: Use Spring Boot managed version (likely 3.7.x or newer)
- **Priority**: LOW
- **Risk**: Low - Should be managed by Spring Boot parent
- **Files Affected**: `pom.xml`
- **Action Steps**:
  1. Remove explicit version
  2. Let Spring Boot parent manage version
  3. Verify reactive streams still work

---

## Phase 7: Testing and Validation

### Task 7.1: Comprehensive Testing
- **Priority**: CRITICAL
- **Action Steps**:
  1. Run all existing unit tests
  2. Run integration tests
  3. Test all API endpoints
  4. Test AI chat functionality
  5. Test PowerPoint generation
  6. Test GitHub operations
  7. Test email sending
  8. Test Pinecone memory storage
  9. Performance testing
  10. Load testing

### Task 7.2: Update Test Dependencies
- **Priority**: MEDIUM
- **Action Steps**:
  1. Ensure kotlin-test-junit5 is compatible with updated Kotlin
  2. Update Spring Boot Test if needed
  3. Verify all tests pass

---

## Phase 8: Documentation and Cleanup

### Task 8.1: Update Documentation
- **Priority**: LOW
- **Action Steps**:
  1. Update README.md with new dependency versions
  2. Document any breaking changes
  3. Update API documentation if needed

### Task 8.2: Dependency Cleanup
- **Priority**: LOW
- **Action Steps**:
  1. Remove unused dependencies if any
  2. Consolidate duplicate dependencies
  3. Verify no conflicting versions

---

## Execution Strategy

### Recommended Order
1. **Phase 1** (Critical fixes) - Do first to resolve version mismatches
2. **Phase 7** (Testing) - Run tests after Phase 1
3. **Phase 2** (LangChain4j) - Core functionality, high risk
4. **Phase 7** (Testing) - Comprehensive testing after LangChain4j update
5. **Phase 3-6** (Other libraries) - Can be done in parallel or sequentially
6. **Phase 7** (Final testing) - Full regression testing
7. **Phase 8** (Documentation) - Final cleanup

### Risk Mitigation
- Update one major library at a time
- Create feature branch for each phase
- Run full test suite after each update
- Keep rollback plan ready
- Document all breaking changes encountered

### Success Criteria
- ✅ All tests pass
- ✅ Application starts successfully
- ✅ All API endpoints work
- ✅ AI chat functionality works
- ✅ PowerPoint generation works
- ✅ GitHub operations work
- ✅ Email sending works
- ✅ No deprecation warnings in build logs
- ✅ No security vulnerabilities in dependency scan

---

## Notes
- Always check compatibility between libraries (e.g., Kotlin version with kotlinx libraries)
- Spring Boot 3.4.4 manages many dependency versions - prefer using managed versions
- Test thoroughly after each major update
- Keep this document updated as updates progress

---

## ✅ UPDATE COMPLETION STATUS

**Date Completed**: December 4, 2025  
**Status**: ✅ **ALL PHASES COMPLETED**

### Phase Completion Summary
- ✅ **Phase 1**: Critical fixes (Kotlin, Jackson) - COMPLETED
- ✅ **Phase 2**: LangChain4j ecosystem update - COMPLETED
- ✅ **Phase 3**: Apache POI update - COMPLETED
- ✅ **Phase 4**: Kotlinx libraries update - COMPLETED
- ✅ **Phase 5**: API clients update - COMPLETED
- ✅ **Phase 6**: Reactor Core verification - COMPLETED
- ✅ **Phase 7**: Comprehensive testing - COMPLETED
- ✅ **Phase 8**: Documentation and cleanup - COMPLETED

### Final Test Results
- **Total Tests**: 10
- **Passed**: 10 ✅
- **Failed**: 0
- **Errors**: 0
- **Build Status**: ✅ SUCCESS

### See Also
- `.cursor/UPDATE_SUMMARY.md` - Comprehensive update summary
- `.cursor/dependenciesissues.md` - Detailed issue tracking and resolutions

