# Dependencies Issues and Refactoring Tasks

This document tracks all breaking changes, deprecated APIs, and required refactoring tasks discovered during dependency updates.

---

## Kotlin Version Mismatch Issues

### Issue: kotlin-stdlib Version Mismatch
- **Library**: kotlin-stdlib
- **Current Version**: 1.9.10 (explicitly pinned)
- **Expected Version**: Should match kotlin.version (1.9.25)
- **Severity**: HIGH
- **Impact**: Version mismatch can cause runtime classpath issues and unexpected behavior
- **Refactoring Tasks**:
  - [ ] Remove explicit version from kotlin-stdlib dependency in `pom.xml`
  - [ ] Let Spring Boot parent manage Kotlin standard library version
  - [ ] Verify no compilation errors
  - [ ] Test runtime behavior
- **Files Affected**: `pom.xml`
- **Breaking Changes**: None expected - just version alignment

---

## LangChain4j Update Issues

### Issue: Major API Changes in LangChain4j 1.9.1
- **Library**: langchain4j (all modules)
- **Current Version**: 0.36.1
- **Target Version**: 1.9.1 (core) / 1.9.1-beta17 (Spring Boot starters)
- **Severity**: CRITICAL
- **Impact**: Core AI functionality depends on LangChain4j - MAJOR BREAKING CHANGES DETECTED
- **Status**: IN PROGRESS - Compilation failures detected
- **Discovered Breaking Changes**:
  - ✅ **Tokenizer class moved/renamed**: `dev.langchain4j.model.Tokenizer` is unresolved
  - ✅ **ChatMessage.text() deprecated**: Method `text()` is deprecated/unavailable - need to use new API
  - ✅ **estimateTokenCountInText removed**: Method no longer exists on Tokenizer - need replacement
  - ✅ **EmbeddingStore.findRelevant() signature changed**: Old signature `findRelevant(Embedding, Int, Double)` deprecated
  - ✅ **TextSegment.text() deprecated**: Need to use new method to get text content
  - ⚠️ **ChatLanguageModel import issue**: Unresolved reference in GeminiModelConfiguration
  - ⚠️ **GithubConfiguration okhttp3 import**: May need dependency update
- **Refactoring Tasks**:
  - [ ] Review LangChain4j migration guide for target version
  - [ ] Update `@AiService` annotation usage in `LangChain4jAssistant.kt`
  - [ ] Verify `ChatMemoryStore` interface compatibility in `PineconeChatMemoryStore.kt`
  - [ ] Check `EmbeddingStore` API in `LangChain4jConfiguration.kt`
  - [ ] Update tool annotations (`@Tool`) in:
    - `GithubService.kt`
    - `EmailService.kt`
    - `PowerPointGeneratorTool.kt`
  - [ ] Verify `MessageWindowChatMemory` builder API
  - [ ] Check `PineconeEmbeddingStore` builder API
  - [ ] Test all AI service integrations
- **Files Affected**:
  - `pom.xml`
  - `src/main/kotlin/no/josefus/abuhint/repository/LangChain4jAssistant.kt`
  - `src/main/kotlin/no/josefus/abuhint/repository/PineconeChatMemoryStore.kt`
  - `src/main/kotlin/no/josefus/abuhint/repository/ConcretePineconeChatMemoryStore.kt`
  - `src/main/kotlin/no/josefus/abuhint/configuration/LangChain4jConfiguration.kt`
  - `src/main/kotlin/no/josefus/abuhint/tools/GithubService.kt`
  - `src/main/kotlin/no/josefus/abuhint/tools/EmailService.kt`
  - `src/main/kotlin/no/josefus/abuhint/tools/PowerPointGeneratorTool.kt`
  - All service classes using LangChain4j
- **Testing Requirements**:
  - [ ] Test chat functionality
  - [ ] Test streaming responses
  - [ ] Test tool invocations
  - [ ] Test memory storage and retrieval
  - [ ] Test embedding generation and storage

### Issue: Pinecone Client Compatibility
- **Library**: pinecone-client
- **Current Version**: 1.2.2
- **Target Version**: Latest (TBD)
- **Severity**: MEDIUM
- **Impact**: Embedding storage functionality
- **Potential Breaking Changes**:
  - API client initialization changes
  - Namespace handling changes
  - Index configuration changes
- **Refactoring Tasks**:
  - [ ] Review Pinecone Java client changelog
  - [ ] Update client initialization if needed
  - [ ] Verify namespace handling in `LangChain4jConfiguration.kt`
  - [ ] Test embedding storage operations
- **Files Affected**:
  - `pom.xml`
  - `src/main/kotlin/no/josefus/abuhint/configuration/LangChain4jConfiguration.kt`
  - `src/main/kotlin/no/josefus/abuhint/repository/PineconeChatMemoryStore.kt`

---

## Apache POI Update Issues

### Issue: POI API Changes
- **Library**: poi-ooxml, poi-scratchpad
- **Current Version**: 5.2.4
- **Target Version**: 5.5.1 ✅ **COMPLETED**
- **Severity**: MEDIUM
- **Impact**: PowerPoint generation functionality
- **Status**: ✅ **RESOLVED** - No breaking changes detected in XSLF (PowerPoint) API
- **Breaking Changes**: None - XSLF API is backward compatible
- **Refactoring Tasks**:
  - [x] Review Apache POI changelog for breaking changes
  - [x] Update `XMLSlideShow` usage in `PowerPointGeneratorTool.kt` - No changes needed
  - [x] Verify `TextParagraph` API compatibility - Compatible
  - [x] Check `createTextBox()` method signature - Compatible
  - [x] Verify `TextRun` API (`setText`, `fontSize`, `isBold`, `setFontColor`) - Compatible
  - [x] Test all slide types:
    - Title slides - ✅ Working
    - Title and content slides - ✅ Working
    - Bullet point slides - ✅ Working
    - Content-only slides - ✅ Working
  - [x] Verify file output format compatibility - ✅ Compatible
- **Files Affected**:
  - `pom.xml` - ✅ Updated to 5.5.1
  - `src/main/kotlin/no/josefus/abuhint/tools/PowerPointGeneratorTool.kt` - ✅ No changes needed
- **Testing Requirements**:
  - [x] Test presentation generation - ✅ All tests pass
  - [x] Test all slide types - ✅ Verified
  - [x] Verify PowerPoint files open correctly - ✅ Compatible
  - [x] Test with various content lengths - ✅ Working
- **Notes**: XSLF (PowerPoint) API remained stable. Breaking changes mentioned in migration guide apply to HSSF/XSSF (Excel) APIs, not XSLF.

---

## Kotlinx Libraries Update Issues

### Issue: Kotlinx Coroutines API Changes
- **Library**: kotlinx-coroutines-core
- **Current Version**: 1.8.1
- **Target Version**: 1.10.2 ✅ **COMPLETED**
- **Severity**: MEDIUM
- **Impact**: Any async/reactive code using coroutines
- **Status**: ✅ **RESOLVED** - No breaking changes detected, no direct coroutine usage found
- **Breaking Changes**: None detected - backward compatible
- **Refactoring Tasks**:
  - [x] Search codebase for coroutine usage (`suspend`, `launch`, `async`, `Flow`) - No direct usage found
  - [x] Review kotlinx-coroutines migration guide - No migration needed
  - [x] Update any deprecated coroutine APIs - N/A
  - [x] Test async operations - ✅ All tests pass
- **Files Affected**: None - No direct coroutine usage in codebase (using Reactor/Mono instead)
- **Note**: Project uses Spring WebFlux with Reactor for async operations, not direct coroutines

### Issue: Kotlinx Serialization JSON Changes
- **Library**: kotlinx-serialization-json
- **Current Version**: 1.6.0
- **Target Version**: 1.9.0 ✅ **COMPLETED**
- **Severity**: MEDIUM
- **Impact**: JSON serialization/deserialization
- **Status**: ✅ **RESOLVED** - No breaking changes detected
- **Breaking Changes**: None - `Json` builder API and `decodeFromString` remain compatible
- **Refactoring Tasks**:
  - [x] Review kotlinx-serialization changelog - No breaking changes
  - [x] Update `Json` builder usage in `PowerPointGeneratorTool.kt` - No changes needed
  - [x] Verify `@Serializable` annotation compatibility - ✅ Compatible
  - [x] Test JSON parsing for slide content - ✅ Working
  - [x] Verify `SlideContent`, `SlideType`, `PresentationRequest` serialization - ✅ Working
- **Files Affected**:
  - `pom.xml` - ✅ Updated to 1.9.0
  - `src/main/kotlin/no/josefus/abuhint/tools/PowerPointGeneratorTool.kt` - ✅ No changes needed
- **Testing Requirements**:
  - [x] Test JSON parsing in `parseSlideContent()` method - ✅ Working
  - [x] Verify serialization of data classes - ✅ Working

---

## GitHub API Client Update Issues

### Issue: GitHub API Client Changes
- **Library**: github-api (kohsuke)
- **Current Version**: 1.326
- **Target Version**: Latest (TBD)
- **Severity**: MEDIUM
- **Impact**: GitHub repository operations
- **Potential Breaking Changes**:
  - `GitHub` client initialization might change
  - Repository API methods might be updated
  - Pull request creation API might change
  - Branch operations API might change
  - File content operations might be updated
- **Refactoring Tasks**:
  - [ ] Review kohsuke github-api changelog
  - [ ] Verify `GitHub` client initialization
  - [ ] Check `getRepository()` method compatibility
  - [ ] Verify `createPullRequest()` method signature
  - [ ] Check `createRef()` method for branch creation
  - [ ] Verify `getFileContent()` method
  - [ ] Check `createContent()` builder API
  - [ ] Test all GitHub operations:
    - Pull request creation
    - Branch creation
    - File content updates
    - Branch retrieval
- **Files Affected**:
  - `pom.xml`
  - `src/main/kotlin/no/josefus/abuhint/tools/GithubService.kt`
  - `src/main/kotlin/no/josefus/abuhint/configuration/GithubConfiguration.kt`
- **Testing Requirements**:
  - [ ] Test `createPullRequest()` tool
  - [ ] Test `createBranchAndCommit()` tool
  - [ ] Test `getBranch()` tool
  - [ ] Test `pushToMain()` tool

---

## Resend Java Client Update Issues

### Issue: Resend API Client Changes
- **Library**: resend-java
- **Current Version**: 3.1.0
- **Target Version**: Latest (TBD)
- **Severity**: LOW
- **Impact**: Email sending functionality
- **Potential Breaking Changes**:
  - Client initialization might change
  - Email sending API might be updated
- **Refactoring Tasks**:
  - [ ] Review resend-java changelog
  - [ ] Verify `Resend` client initialization in `EmailService.kt`
  - [ ] Check `emails().send()` method signature
  - [ ] Verify email request builder API
  - [ ] Test email sending functionality
- **Files Affected**:
  - `pom.xml`
  - `src/main/kotlin/no/josefus/abuhint/tools/EmailService.kt`
- **Testing Requirements**:
  - [ ] Test email sending via `sendEmail()` tool
  - [ ] Verify email delivery

---

## Reactor Core Update Issues

### Issue: Reactor Core Version Management
- **Library**: reactor-core
- **Current Version**: 3.7.4 (explicitly pinned)
- **Target Version**: Spring Boot managed (likely 3.7.x or newer)
- **Severity**: LOW
- **Impact**: Reactive streams (if used)
- **Potential Breaking Changes**: None expected - should be managed by Spring Boot
- **Refactoring Tasks**:
  - [ ] Remove explicit version from `pom.xml`
  - [ ] Let Spring Boot parent manage version
  - [ ] Verify no compilation errors
- **Files Affected**: `pom.xml`
- **Note**: Check if reactive streams are actually used in the codebase

---

## Jackson Module Kotlin Update Issues

### Issue: Jackson Kotlin Module Compatibility
- **Library**: jackson-module-kotlin
- **Current Version**: 2.18.0
- **Target Version**: Spring Boot managed or latest compatible
- **Severity**: LOW
- **Impact**: JSON serialization of Kotlin data classes
- **Potential Breaking Changes**:
  - Module registration might change
  - Serialization behavior might be updated
- **Refactoring Tasks**:
  - [ ] Check Spring Boot 3.4.4 managed version
  - [ ] Remove explicit version if managed by parent
  - [ ] Or update to latest compatible version
  - [ ] Test JSON serialization of Kotlin data classes
- **Files Affected**:
  - `pom.xml`
  - Potentially `src/main/kotlin/no/josefus/abuhint/configuration/JsonConfiguration.kt`
- **Testing Requirements**:
  - [ ] Test JSON serialization in controllers
  - [ ] Verify DTO serialization

---

## General Refactoring Considerations

### Code Quality Improvements
- [ ] Review and update deprecated method calls found during updates
- [ ] Add null-safety checks where new APIs require them
- [ ] Update error handling for new exception types
- [ ] Improve logging for debugging update-related issues

### Testing Enhancements
- [ ] Add integration tests for critical paths after updates
- [ ] Update existing tests to work with new APIs
- [ ] Add tests for edge cases introduced by new versions
- [ ] Performance testing after major updates

### Documentation Updates
- [ ] Document any API changes in code comments
- [ ] Update README with new dependency versions
- [ ] Document workarounds for any compatibility issues
- [ ] Create migration notes for future reference

---

## Known Issues and Workarounds

### Issue Tracking Template
For each discovered issue, document:
- **Library**: [Library name]
- **Version**: [Current → Target]
- **Issue**: [Description]
- **Workaround**: [Temporary solution if any]
- **Permanent Fix**: [Planned solution]
- **Status**: [Open/In Progress/Resolved]

---

## Testing Checklist After Updates

### Unit Tests
- [ ] All existing unit tests pass
- [ ] New tests added for updated functionality
- [ ] Edge cases covered

### Integration Tests
- [ ] LangChain4j integration works
- [ ] Pinecone integration works
- [ ] GitHub API integration works
- [ ] Email service integration works
- [ ] PowerPoint generation works

### End-to-End Tests
- [ ] Chat API endpoints work
- [ ] AI assistant responds correctly
- [ ] Tools are invoked correctly
- [ ] Memory storage works
- [ ] All controllers function properly

### Performance Tests
- [ ] No significant performance degradation
- [ ] Memory usage is acceptable
- [ ] Response times are within acceptable range

---

## Rollback Plan

If critical issues are discovered:
1. Revert dependency versions in `pom.xml`
2. Restore code changes from git history
3. Run full test suite to verify rollback
4. Document issues encountered
5. Plan alternative update strategy

---

## Notes
- This document should be updated as issues are discovered during the update process
- Each issue should be tracked until resolved
- Test thoroughly after addressing each issue
- Keep communication open about breaking changes

