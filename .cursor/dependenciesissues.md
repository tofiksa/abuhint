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
- **Target Version**: Latest (TBD - check for 5.3.x or 6.x)
- **Severity**: MEDIUM
- **Impact**: PowerPoint generation functionality
- **Potential Breaking Changes**:
  - `XMLSlideShow` constructor or methods might change
  - `TextParagraph` API might be updated
  - Shape creation methods might change
  - Color handling might be updated
- **Refactoring Tasks**:
  - [ ] Review Apache POI changelog for breaking changes
  - [ ] Update `XMLSlideShow` usage in `PowerPointGeneratorTool.kt`
  - [ ] Verify `TextParagraph` API compatibility
  - [ ] Check `createTextBox()` method signature
  - [ ] Verify `TextRun` API (`setText`, `fontSize`, `isBold`, `setFontColor`)
  - [ ] Test all slide types:
    - Title slides
    - Title and content slides
    - Bullet point slides
    - Content-only slides
  - [ ] Verify file output format compatibility
- **Files Affected**:
  - `pom.xml`
  - `src/main/kotlin/no/josefus/abuhint/tools/PowerPointGeneratorTool.kt`
- **Testing Requirements**:
  - [ ] Test presentation generation
  - [ ] Test all slide types
  - [ ] Verify PowerPoint files open correctly
  - [ ] Test with various content lengths

---

## Kotlinx Libraries Update Issues

### Issue: Kotlinx Coroutines API Changes
- **Library**: kotlinx-coroutines-core
- **Current Version**: 1.8.1
- **Target Version**: Latest (TBD - likely 1.9.x or 2.x)
- **Severity**: MEDIUM
- **Impact**: Any async/reactive code using coroutines
- **Potential Breaking Changes**:
  - Coroutine builder API changes
  - Flow API changes
  - Exception handling changes
- **Refactoring Tasks**:
  - [ ] Search codebase for coroutine usage (`suspend`, `launch`, `async`, `Flow`)
  - [ ] Review kotlinx-coroutines migration guide
  - [ ] Update any deprecated coroutine APIs
  - [ ] Test async operations
- **Files Affected**: All files using coroutines (TBD - need to search)
- **Note**: May not be directly used - check if Spring WebFlux handles async operations

### Issue: Kotlinx Serialization JSON Changes
- **Library**: kotlinx-serialization-json
- **Current Version**: 1.6.0
- **Target Version**: Latest (TBD - likely 1.7.x or 2.x)
- **Severity**: MEDIUM
- **Impact**: JSON serialization/deserialization
- **Potential Breaking Changes**:
  - `Json` builder API changes
  - `decodeFromString`/`encodeToString` method changes
  - Serialization format changes
- **Refactoring Tasks**:
  - [ ] Review kotlinx-serialization changelog
  - [ ] Update `Json` builder usage in `PowerPointGeneratorTool.kt`
  - [ ] Verify `@Serializable` annotation compatibility
  - [ ] Test JSON parsing for slide content
  - [ ] Verify `SlideContent`, `SlideType`, `PresentationRequest` serialization
- **Files Affected**:
  - `pom.xml`
  - `src/main/kotlin/no/josefus/abuhint/tools/PowerPointGeneratorTool.kt`
- **Testing Requirements**:
  - [ ] Test JSON parsing in `parseSlideContent()` method
  - [ ] Verify serialization of data classes

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

