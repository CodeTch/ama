# Task 09 final mechanical custody — incremental committed pass

Fixed 10 new `result.json` windows at `2026-10-24T10:53:01.862334+00:00`, plus the already completed `formal-runtime-start-01/formal-runtime-result.json` window. The initial 165-window index and 20,557-path protected check are reused by hash; their raw files were not rescanned. No test, build, SQL, Docker, service, migration, network, or Git mutation was run.

## New window accounting

9 test windows and 1 OpenAPI evidence window; 72 copied XML and 335 selected-case observations over 318 distinct keys. Combined with the initial matrix, there are 391 distinct selected keys. Mechanical issue windows: 0; source-snapshot issue windows: 0. Full command/result/source/report hashes, interval/before records, and each case observation are in [task-09-mechanical-custody-final-20261024.json](task-09-mechanical-custody-final-20261024.json).

The first inherited/migration run remains **12 tests / 2 FAIL**. The later run is **12 PASS**. The canonical unit run remains **293 / 0 FAIL / 4 ERROR**: its other 289 XML cases passed. The separate exact `WorkerShutdownTest` run is **4 PASS** after a test-only reflection-signature source difference; the canonical 4 ERROR are retained, and no single all-green 293 run is claimed.

## Final production source closure

The `candidate-pages-final-02` snapshot has 274 main-source paths. Current bytes match all 274: 0 differences. The 154 own-path manifest matches current bytes with 0 differences and equals the 154-entry closure list: True. The final production path map is identical in every new test window except `inherited-migration-final-01`, whose `JdbcExternalOperations.java` differs; `final-02` and later qualification windows carry the final map. Older closure comparisons remain source-version records and were not reopened. Canonical-to-exact-shutdown snapshot differences: backend/worker/src/test/java/ama/worker/WorkerShutdownTest.java.

## Final migration history

`full-fifteen-history.json` has 15 before and 15 after rows, each with the exact ten Flyway fields: True. Raw before, after, and the 15-row original derived baseline are equal: True; reported migration count: 0. The sealed final test uses SQL `DATE_FORMAT(installed_on, ...)`, and its sealed controlled datasource sets UTC session time. This qualifies the final02 rerun, not a retroactive UTC claim for bootstrap-01.

## Formal installed runtime

Controller receipt lists 9 raw files; hash differences: 0. Runtime exit0: True; process group closed with none remaining: True; 64 MiB/2 CPU: True; guarded before/after equality: True; retained rows 18 tasks/10 operations. Provider exit 143 and gone=True; native child exit 137 and alive=false=True. Bundle manifest matches command hash, names source window `candidate-pages-final-02`, and all 150 listed noncredential bundle files matched: 0 issues. This is installed import/reconciliation start-close scope with new-generation dispatch=false.

## Raw scope, private scan receipt, and commit

Raw manifest has 96140 distinct declared files and covers the current raw root exactly after its three excluded final metadata files: True. Path coverage issues 0, size issues 0. The scanner's 96142 raw files include the raw and staged manifests and exclude only its own receipt. The pre-receipt staged list contains 96297 exact paths: all raw-manifest entries, raw/stage metadata, 154 own source paths, and the W report; set equality True. No private `.tools/` path is in that list. The private-scan receipt is the sole additional path. Commit `0e22b812000f2efa00c2ef8ed66e06d8254beab5` has 96298 changed paths, exactly matching the staged list plus receipt: True; parent matches base: True; index empty: True. The committed receipt blob matches its working bytes: True. This pass checks raw manifest path and size coverage plus the new window hashes; it does not rehash every raw payload or repeat the private-value scan.

The author's scanner source hash matches its receipt: True. Static inspection shows Task09 owner provider-secret discovery, all raw files except the receipt, exact staged names and deduplicated staged blobs, a private-key PEM pattern, and an in-memory check of the receipt itself. The receipt reports known-original 6, actual provider files 2, unique values 8, exact-value hits 0, private-PEM hits 0, raw files 96142, staged paths 96297, and unique staged blobs 3099. Those scope counts agree with path metadata and the final commit tree. This review did not read any private credential value or rerun the scan.

The author's final validation records `git diff --check` exit 2 for the single retained `Task09NativeFixture.java:33` blank-line trailing whitespace finding. It is retained in the validation JSON and is not reported as clean.

## New window ledger

| Window | Exit | T/F/E/S | XML | Main diff vs final | Qualification |
| --- | ---: | ---: | ---: | ---: | --- |
| `openapi-final-01` | 0 | –/–/–/– | 0 | – | non-test-exit0 |
| `inherited-migration-final-01` | 1 | 12/2/0/0 | 3 | 1 | assertion-failure |
| `inherited-migration-final-02` | 0 | 12/0/0/0 | 3 | 0 | qualified-pass |
| `candidate-pages-final-01` | 1 | 1/1/0/0 | 1 | 0 | assertion-failure |
| `candidate-pages-final-02` | 0 | 1/0/0/0 | 1 | 0 | qualified-pass |
| `canonical-units-final-01` | 1 | 293/0/4/0 | 51 | 0 | test-error |
| `worker-shutdown-final-01` | 0 | 4/0/0/0 | 1 | 0 | qualified-pass |
| `minutes-http-final-01` | 0 | 4/0/0/0 | 4 | 0 | qualified-pass |
| `terminal-replay-final-01` | 0 | 5/0/0/0 | 5 | 0 | qualified-pass |
| `disposition-replay-final-01` | 0 | 3/0/0/0 | 3 | 0 | qualified-pass |

## Combined selected-case matrix

Each row shows the last qualifying whole-window PASS. The JSON retains every historical observation and source-manifest SHA. Canonical unit PASS cases in an ERROR window remain observed PASS but are not silently promoted to a whole-window qualification.

| Class#method | Observations | Last qualified | Latest observation |
| --- | ---: | --- | --- |
| `ama.api.DeploymentConfigTest#allFourDatabaseStorageCombinationsAreIndependent` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.api.DeploymentConfigTest#healthIsNotExposedOnPublicListener` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.api.DeploymentConfigTest#invalidConfigurationRejectedWithoutEchoingSecrets` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.api.DeploymentConfigTest#localStartsWithoutS3Credentials` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.api.DeploymentConfigTest#readinessFailsWhenDatabaseUnavailable` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.api.DeploymentConfigTest#readinessRecoversFromRealDatabaseOutage` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.api.DeploymentConfigTest#s3MissingEndpointRejected` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.api.DeploymentConfigTest#s3RejectsEachMissingRequiredFieldAndMalformedEndpoint` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.api.DeploymentConfigTest#untrustedNetworkCannotReadHealthAndForwardedHeadersAreIgnored` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.api.FileConfigurationTest#productionAssemblyRejectsHttpEvenWithValidManagedIdentity` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.api.HttpRevisionTokensTest#tokenOnlyRosterCommandPreservesEveryLongBit` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.api.IdentityContractsTest#actorCannotCarryMutablePrivileges` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.api.IdentityContractsTest#invalidActorCannotEnterAuthorizationBoundary` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.api.IdentityContractsTest#sessionTimestampsTruncateInsteadOfRound` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.api.IdentitySecurityTest#cookieUsesHostBoundaryAndNeverExposesCsrf` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.api.IdentitySecurityTest#databaseFailureProducesSafeUnavailableBusinessError` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.api.IdentitySecurityTest#opaqueOrNonOriginConfigurationIsRejected` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.api.IdentitySecurityTest#productionIssuerCannotIssueEvenWithValidLocalIdentity` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.api.IdentitySecurityTest#sessionDiagnosticsRedactSecretsAndTruncateIssuedExpiry` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.api.IdentitySecurityTest#testModeConfigurationCannotStartProductionAssembly` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.api.TranscriptWireTest#duplicateUnknownTrailingScalarAndInvalidTokenRejected` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.api.TranscriptWireTest#escapedResponseAcceptsExactBytesAndRejectsOneLess` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.api.TranscriptWireTest#exactUnicodeAndLongTokenWithNormalizedMarks` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.api.TranscriptWireTest#unknownLengthStopsAtSingleOverflowSentinelAndDeclaredExcessDoesNotRead` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.canonical.CanonicalValueCodecTest#cancellationDuringScanSortAndHashNeverReturnsSuccess` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.canonical.CanonicalValueCodecTest#everyCanonicalBudgetBoundaryAndPlusOne` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.canonical.CanonicalValueCodecTest#frozenEntriesRejectDuplicateBeforeConversionAndRejectLoneSurrogates` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.canonical.CanonicalValueCodecTest#literalPrimitiveVectors` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.canonical.CanonicalValueCodecTest#unsignedUtf8SortPreservesListOrderAndExactControls` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.file.ControlledArchiveFix1Test#namedPipePublishingLockFailsUnavailableInBoundedOwnedChild` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.file.ControlledArchiveFix1Test#publicationReservesEntireIndexChainAndReopensAtExactBoundary` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.file.ControlledArchiveFix1Test#publisherImpossibleReleaseBudgetPrecedesCallerPayloadInterpretation` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.file.ControlledArchiveFix1Test#startupReleaseCapacityRefusesBeforeAnyObjectAcquisition` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.file.ControlledConfigArchiveSourceTest#boundedStartupSharesTokenScalarAndByteBudgets` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.file.ControlledConfigArchiveSourceTest#cancellationAfterPartialStagingLeavesPreviousReleaseIntact` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.file.ControlledConfigArchiveSourceTest#cancellationAndInterruptFailClosedWithoutPayloadDiagnostics` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.file.ControlledConfigArchiveSourceTest#cancellationAtDirectoryAcquisitionBoundariesClosesEveryHandle` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.file.ControlledConfigArchiveSourceTest#closureBudgetsReserveManifestBeforeAnyTransportAllocation` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.file.ControlledConfigArchiveSourceTest#controlledLiteralClosureRemainsAcceptableToUnchangedS1` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.file.ControlledConfigArchiveSourceTest#everyLoadRechecksLengthEofHashAndMissingObjects` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.file.ControlledConfigArchiveSourceTest#heldPublishingLockAndLockSymlinkRejectSecondPublisher` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.file.ControlledConfigArchiveSourceTest#historyRemapDropAndGenerationGapFailClosed` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.file.ControlledConfigArchiveSourceTest#indexEntryOrderUniquenessAndSizeAreStrict` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.file.ControlledConfigArchiveSourceTest#initialPublicationCannotResetAnExistingReleaseHistory` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.file.ControlledConfigArchiveSourceTest#manifestDiscoveryReturnsHistoricalRefsAndNeverRepairsMalformedBytes` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.file.ControlledConfigArchiveSourceTest#missingDependencyCycleAndWrongHeaderAreNeverPublishable` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.file.ControlledConfigArchiveSourceTest#noLatestOrMissingHistoryFallback` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.file.ControlledConfigArchiveSourceTest#nonregularFilesAndUnsupportedSecureFilesystemFailUnavailable` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.file.ControlledConfigArchiveSourceTest#pinnedHistoryLoadsExactOldClosureWithFreshTransportArrays` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.file.ControlledConfigArchiveSourceTest#publisherCreatesLiteralReleasesAndPreservesPriorBytes` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.file.ControlledConfigArchiveSourceTest#publisherRejectsRemapDuplicateMissingClosureAndCancellationWithoutChangingOldRelease` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.file.ControlledConfigArchiveSourceTest#publisherWritesSameIsolatedBytesEvenWhenCallerMutatesTransportAfterValidation` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.file.ControlledConfigArchiveSourceTest#releaseObjectsCannotContainUnindexedFiles` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.file.ControlledConfigArchiveSourceTest#startupValidatesAllObjectsIncludingUnselectedHistory` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.file.ControlledConfigArchiveSourceTest#strictIndexRejectsUnknownDuplicateNoncanonicalAndInvalidUtf8` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.file.ControlledConfigArchiveSourceTest#symlinkRootAncestorObjectDirectoryAndReplacementAreRejected` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.file.ControlledConfigArchiveSourceTest#versionsUseUnsignedUtf8OrderingRatherThanUtf16Ordering` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.ArchiveBudgetTest#eachCumulativeBoundRejectsOneMoreThanItsLimit()[10]` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.ArchiveBudgetTest#eachCumulativeBoundRejectsOneMoreThanItsLimit()[11]` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.ArchiveBudgetTest#eachCumulativeBoundRejectsOneMoreThanItsLimit()[12]` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.ArchiveBudgetTest#eachCumulativeBoundRejectsOneMoreThanItsLimit()[13]` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.ArchiveBudgetTest#eachCumulativeBoundRejectsOneMoreThanItsLimit()[14]` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.ArchiveBudgetTest#eachCumulativeBoundRejectsOneMoreThanItsLimit()[1]` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.ArchiveBudgetTest#eachCumulativeBoundRejectsOneMoreThanItsLimit()[2]` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.ArchiveBudgetTest#eachCumulativeBoundRejectsOneMoreThanItsLimit()[3]` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.ArchiveBudgetTest#eachCumulativeBoundRejectsOneMoreThanItsLimit()[4]` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.ArchiveBudgetTest#eachCumulativeBoundRejectsOneMoreThanItsLimit()[5]` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.ArchiveBudgetTest#eachCumulativeBoundRejectsOneMoreThanItsLimit()[6]` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.ArchiveBudgetTest#eachCumulativeBoundRejectsOneMoreThanItsLimit()[7]` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.ArchiveBudgetTest#eachCumulativeBoundRejectsOneMoreThanItsLimit()[8]` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.ArchiveBudgetTest#eachCumulativeBoundRejectsOneMoreThanItsLimit()[9]` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.ArchiveBudgetTest#independentExactCumulativeBudgetAcceptsAllFiveRawDocuments` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.ConfigBindingsTest#draftComputeNeedsNoOwnStoredFingerprintTaskOrOutputRevision` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.ConfigBindingsTest#fileMeetingSizeHashAndSavedTaskFencesAreRequiredFacts` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.ConfigBindingsTest#injectedParsedDtoCannotAttestRawArchiveAndCancellationNeverMintsSuccess` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.ConfigBindingsTest#invalidRawOrIncompleteArchiveClosureCannotMintBinding` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.ConfigBindingsTest#matchingArchiveIdentityCannotSubstituteDifferentRawDefinitionOrExtraNodes` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.ConfigBindingsTest#originalParseBudgetSurvivesCompleteClosure` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.ConfigBindingsTest#storedVerifyRequiresActualDigestTaskAndProducerChain` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.ConfigWireReaderTest#adoptedRawVectorsReadTheirExactIdentitiesAndHashes` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.ConfigWireReaderTest#canonicalControlEscapesAndSupplementaryConfigTextArePreserved` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.ConfigWireReaderTest#rawArraysAreIsolatedAfterBoundCheckAndCumulativeBytesCannotReset` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.ConfigWireReaderTest#recognizedFutureProcessingAndPinnedMaterialsNeverFallBackToImport` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.ConfigWireReaderTest#transportRejectsDuplicateUnknownNoncanonicalAndBadUnicode` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.ConfigWireReaderTest#unknownProcessingKindIsNotARecognizedFutureCapability` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.DraftConfirmationHashTest#completeMarksAndPermutationMatchIndependentManualGolden` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.DraftConfirmationHashTest#missingOrCorruptOriginalAndCancellationNeverProduceDraftHash` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.DraftConfirmationHashTest#persistedWorkingWithoutConfirmationUsesOriginalArchiveGolden` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.RawHashChainTest#actualBodyActionDetectionBackgroundAndIntrinsicMatrixMatchesFullIndependentFinal` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.RawHashChainTest#completeStoredGenerationAndFinalReloadHashesUseRawOriginalArchives` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.RawHashChainTest#confirmationRawChainReloadRetainsOriginalProfileAndActualContent` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.RawHashChainTest#exactReviewArchiveClosureAndIntrinsicConfirmationProduceIndependentHashes` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.RawHashChainTest#fix1ContextArtifactsReuseTheSameIsolatedBoundedOriginal` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.RawHashChainTest#fix1DeepRawBackedOriginsMeetExactDepthAndRejectOneMore` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.RawHashChainTest#fix1DirectStoredVerifyRejectsCorruptedNestedIntrinsicConfirmation` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.RawHashChainTest#fix1FinalRejectsCorruptedNestedIntrinsicConfirmation` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.RawHashChainTest#fix1OriginNodeBudgetIsUniqueAndCumulativeAcrossSharedBranches` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.RawHashChainTest#fix1OuterDraftRejectsCorruptedNestedIntrinsicConfirmation` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.RawHashChainTest#fix1RawBackedCycleAndCancellationCannotReturnBusinessDigest` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.RawHashChainTest#fix1ReusedNonleafDiamondDepthUsesCachedSubtreeHeight` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.RawHashChainTest#fix1ReviewRejectsCorruptedNestedIntrinsicConfirmation` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.RawHashChainTest#fix1SharedHistoricalArchiveAcquisitionUsesOneCumulativeByteBudget` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.RawHashChainTest#fix1SharedLeafDepthUsesEveryPathInEitherOriginOrdering` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.RawHashChainTest#fix1ValidNestedIntrinsicChainSucceedsAcrossAllBusinessEntryPoints` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.RawHashChainTest#newExclusionRevisionComputesIndependentlyAndAppliedOriginRequiresExactBodyInput` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.RawHashChainTest#pinnedCanonicalBudgetAlsoProtectsTemplateRowsetBeforeItsDigestIsHidden` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.RawHashChainTest#pinnedSetResolvesAllActualHistoricalOriginsAndMatchesIndependentBytes` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.RawHashChainTest#presentContradictorySuggestionNeverFallsBackToIntrinsicTranscript` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.RawHashChainTest#rawFullGenerationComputesBeforeOwnRowTaskAndOutputExist` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.RawHashChainTest#savedManualChildUsesExactBodyMarksAndInheritedOriginalProcessingProfile` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.RawHashChainTest#selectedReviewUsesSavedScopeAndExactFrozenMemberships` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.RawHashChainTest#storedTaskIdentityFactsCanSupplyCompleteHistoricalGenerationFence` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.RawHashChainTest#unrelatedCurrentConfigFactsAndQueryOrderCannotChangeHistoricalFinalHash` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.SavedTranscriptAdmissionTest#originalCanonicalMinimaConstrainLedgerBeforeTransfer` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.SavedTranscriptAdmissionTest#smallerRuntimeStillRestrictsOriginal` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.config.json.Task09CandidatePlanTest#completeCandidatePlanRemapsEveryTableAndGrantsNoAcceptedActionOrReview` | 4 | `candidate-plan-green-02` | `canonical-units-final-01` PASS |
| `ama.config.json.Task09CandidatePlanTest#independentPlansUseDifferentServerIdsAndOnlyHistorySlotsAreBoundLater` | 4 | `candidate-plan-green-02` | `canonical-units-final-01` PASS |
| `ama.config.json.Task09ControlledArchivesTest#actualTemplateIdentityChangesBothRootBindingsWithoutChangingSharedCodec` | 3 | `controlled-archives-green-01` | `canonical-units-final-01` PASS |
| `ama.config.json.Task09ControlledArchivesTest#completeFifteenProfilesAndBothRootsUseStrictCanonicalOriginalBytes` | 3 | `controlled-archives-green-01` | `canonical-units-final-01` PASS |
| `ama.config.json.Task09ControlledClientTest#lookupOfNeverSentExecutionIsUnknownAndDoesNotCreateProviderReservation` | 6 | `client-zero-proof-green-01` | `canonical-units-final-01` PASS |
| `ama.config.json.Task09ControlledClientTest#postRejectionWithoutPersistedLookupProofCannotIssueZeroEffectReceipt` | 4 | `client-zero-proof-green-01` | `canonical-units-final-01` PASS |
| `ama.config.json.Task09ControlledClientTest#realAuthenticatedSuccessBindsMetadataAndExactOutputToPrivateRequest` | 6 | `client-zero-proof-green-01` | `canonical-units-final-01` PASS |
| `ama.config.json.Task09ControlledEnvelopeStoreTest#changedEnvelopeWithOldHashesNeverChangesExistingMapping` | 3 | `envelope-store-green-01` | `canonical-units-final-01` PASS |
| `ama.config.json.Task09ControlledEnvelopeStoreTest#corruptEnvelopeCannotBeRepairedByDuplicatePost` | 3 | `envelope-store-green-01` | `canonical-units-final-01` PASS |
| `ama.config.json.Task09ControlledEnvelopeStoreTest#exactCanonicalEnvelopeAndPayloadAreValidatedAfterReopen` | 3 | `envelope-store-green-01` | `canonical-units-final-01` PASS |
| `ama.config.json.Task09ControlledEnvelopeStoreTest#lookupLeavesReservedExecutionUnknownAcrossReopen` | 3 | `envelope-store-green-01` | `canonical-units-final-01` PASS |
| `ama.config.json.Task09ControlledHttpTest#actualHttpDuplicateRetainsOneDurableResult` | 4 | `provider-http-boundaries-01` | `canonical-units-final-01` PASS |
| `ama.config.json.Task09ControlledHttpTest#aggregateAndPerFieldLimitsRejectBeforeUnboundedParse` | 4 | `provider-http-boundaries-01` | `canonical-units-final-01` PASS |
| `ama.config.json.Task09ControlledHttpTest#exactAggregateHeaderMaximumSucceedsAndOneMoreByteFails` | 2 | `provider-http-boundaries-01` | `canonical-units-final-01` PASS |
| `ama.config.json.Task09ControlledHttpTest#freshProviderProcessPreservesExactEnvelopeAndResultIdentity` | 4 | `provider-http-boundaries-01` | `canonical-units-final-01` PASS |
| `ama.config.json.Task09ControlledHttpTest#rawRepeatedCaseDuplicateCombinedAndFoldedEnvelopeRejectedBeforeReservation` | 4 | `provider-http-boundaries-01` | `canonical-units-final-01` PASS |
| `ama.config.json.Task09ControlledHttpTest#unauthenticatedRequestCannotReserve` | 4 | `provider-http-boundaries-01` | `canonical-units-final-01` PASS |
| `ama.config.json.Task09ControlledProtocolTest#recomputesRealV2InputAndLogicalEnvelopeInsteadOfComparingSuppliedHashes` | 3 | `protocol-green-02` | `canonical-units-final-01` PASS |
| `ama.config.json.Task09ControlledProtocolTest#rejectsMissingCombinedPaddingNoncanonicalAndOversizeHeaders` | 3 | `protocol-green-02` | `canonical-units-final-01` PASS |
| `ama.config.json.Task09ControlledProtocolTest#rejectsNoncanonicalBodyDuplicateNumbersAndWrongExecution` | 3 | `protocol-green-02` | `canonical-units-final-01` PASS |
| `ama.config.json.Task09ControlledProtocolTest#rejectsWrongInputFingerprintAndDuplicateUnsortedRoots` | 3 | `protocol-green-02` | `canonical-units-final-01` PASS |
| `ama.config.json.Task09GenerationBindingTest#prospectiveBindingRetainsExactCatalogAndVerifiedDefinitionsWithoutOwnRow` | 3 | `bindings-green-01` | `canonical-units-final-01` PASS |
| `ama.config.json.Task09GenerationBindingTest#storedBindingRetainsSameDefinitionsAndCatalogAfterClosureFinish` | 3 | `bindings-green-01` | `canonical-units-final-01` PASS |
| `ama.config.json.Task09OutputAdmissionTest#lookupOfNeverSentExecutionIsUnknownAndDoesNotCreateProviderReservation` | 3 | `output-admission-green-01` | `canonical-units-final-01` PASS |
| `ama.config.json.Task09OutputAdmissionTest#originalWireBudgetRejectsDurableSuccessBeforeResultGet` | 3 | `output-admission-green-01` | `canonical-units-final-01` PASS |
| `ama.config.json.Task09OutputAdmissionTest#postRejectionWithoutPersistedLookupProofCannotIssueZeroEffectReceipt` | 3 | `output-admission-green-01` | `canonical-units-final-01` PASS |
| `ama.config.json.Task09OutputAdmissionTest#realAuthenticatedSuccessBindsMetadataAndExactOutputToPrivateRequest` | 3 | `output-admission-green-01` | `canonical-units-final-01` PASS |
| `ama.config.json.Task09ProducerTest#completeFrozenProducerEnvelopeIncludesEveryFenceAndHasExactProfile` | 3 | `generation-producer-green-01` | `canonical-units-final-01` PASS |
| `ama.contract.MinutesFactBudgetTest#artifactCaptureRefusesSmallMaximumAndRemainingBeforeOwnedBytesExist` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.contract.MinutesFactBudgetTest#artifactReusesBoundedIsolatedCaptureWithoutExposingMutableArray` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.contract.MinutesFactBudgetTest#catalogSnapshotAlsoChecksItsBoundBeforeDefensiveCopy` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.contract.MinutesFactBudgetTest#factBagChecksCallerLimitBeforeAnyLargeDefensiveCopy` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.Task09BootstrapIT#bootstrapOnlyAcknowledgedEmptySchemaThenNormalRerunZero` | 1 | `bootstrap-01` | `bootstrap-01` PASS |
| `ama.minutes.Task09ControlledAdminIT#explicitCommandInstallsRealTemplateReleaseAndCapacityWithPrivateLaunchFiles` | 2 | `controlled-admin-green-01` | `controlled-admin-green-01` PASS |
| `ama.minutes.Task09FinalMigrationIT#normalRerunZeroPreservesAllFifteenCompleteOriginalHistoryRows` | 2 | `inherited-migration-final-02` | `inherited-migration-final-02` PASS |
| `ama.minutes.Task09FixtureIsolationIT#retireCapturedReadyFixturesWithoutChangingAnyImmutableSpec` | 2 | `fixture-isolation-retirement-02` | `fixture-isolation-retirement-02` PASS |
| `ama.minutes.Task09GraphCaptureIT#authenticSameIssuerHolderCapturesProtectedBodyAndExactEmptySets` | 6 | `graph-current-read-green-01` | `graph-current-read-green-01` PASS |
| `ama.minutes.Task09GraphCaptureIT#childSetChangedAfterDiscoveryIsRejected` | 6 | `graph-current-read-green-01` | `graph-current-read-green-01` PASS |
| `ama.minutes.Task09GraphCaptureIT#foreignAndExpiredScopesNeverBecomeAmbientTransactionAuthority` | 6 | `graph-current-read-green-01` | `graph-current-read-green-01` PASS |
| `ama.minutes.Task09GraphCaptureIT#oversizedLongtextIsRejectedBeforeAnyBodySelect` | 6 | `graph-current-read-green-01` | `graph-current-read-green-01` PASS |
| `ama.minutes.Task09GraphCaptureIT#priorRepeatableReadSnapshotCannotHideNewChildMembership` | 2 | `graph-current-read-green-01` | `graph-current-read-green-01` PASS |
| `ama.minutes.Task09GraphCaptureIT#protectedContentTransferUsesCurrentReadAfterOldSnapshot` | 2 | `graph-current-read-green-01` | `graph-current-read-green-01` PASS |
| `ama.minutes.Task09GraphDiscoveryIT#currentControlRootsIncludeExactRosterMembershipAndNeverTransferDisplayText` | 3 | `graph-controls-green-02` | `graph-controls-green-02` PASS |
| `ama.minutes.Task09GraphDiscoveryIT#currentSettingsCaptureExactZeroExclusionMembers` | 5 | `graph-controls-green-02` | `graph-controls-green-02` PASS |
| `ama.minutes.Task09GraphDiscoveryIT#followsEveryBodyParentAndRetainsExplicitEmptyEvidenceSetsWithoutTextTransfer` | 5 | `graph-controls-green-02` | `graph-controls-green-02` PASS |
| `ama.minutes.Task09GraphDiscoveryIT#foreignCurrentControlPointerFailsWholeDiscovery` | 3 | `graph-controls-green-02` | `graph-controls-green-02` PASS |
| `ama.minutes.Task09GraphDiscoveryIT#overflowRejectsWholeGraphAndForeignMeetingIsNeverAdopted` | 5 | `graph-controls-green-02` | `graph-controls-green-02` PASS |
| `ama.minutes.Task09GraphDiscoveryIT#setEnumeratesEveryCategoryActionAndEmptyDirectoryMembership` | 5 | `graph-controls-green-02` | `graph-controls-green-02` PASS |
| `ama.minutes.Task09LimitsConnectionIT#deployedFactoryEnforcesUtcUtf8AndFiniteSessionWaits` | 2 | `remaining-limits-green-01` | `remaining-limits-green-01` PASS |
| `ama.minutes.Task09SourceGraphIT#currentControlRootsIncludeExactRosterMembershipAndNeverTransferDisplayText` | 3 | `graph-sources-green-01` | `graph-sources-green-01` PASS |
| `ama.minutes.Task09SourceGraphIT#currentSettingsCaptureExactZeroExclusionMembers` | 3 | `graph-sources-green-01` | `graph-sources-green-01` PASS |
| `ama.minutes.Task09SourceGraphIT#discoversWholeExternalHistoryWithoutLongtextBeforeAnyCapture` | 3 | `graph-sources-green-01` | `graph-sources-green-01` PASS |
| `ama.minutes.Task09SourceGraphIT#enumeratesOriginalSessionFileReferenceAndEveryTypedInputTaskAttempt` | 3 | `graph-sources-green-01` | `graph-sources-green-01` PASS |
| `ama.minutes.Task09SourceGraphIT#followsEveryBodyParentAndRetainsExplicitEmptyEvidenceSetsWithoutTextTransfer` | 3 | `graph-sources-green-01` | `graph-sources-green-01` PASS |
| `ama.minutes.Task09SourceGraphIT#foreignCurrentControlPointerFailsWholeDiscovery` | 3 | `graph-sources-green-01` | `graph-sources-green-01` PASS |
| `ama.minutes.Task09SourceGraphIT#overflowRejectsWholeGraphAndForeignMeetingIsNeverAdopted` | 3 | `graph-sources-green-01` | `graph-sources-green-01` PASS |
| `ama.minutes.Task09SourceGraphIT#setEnumeratesEveryCategoryActionAndEmptyDirectoryMembership` | 3 | `graph-sources-green-01` | `graph-sources-green-01` PASS |
| `ama.minutes.Task09TemplateIT#conflictingAssignedIdentityCannotOverwriteOrLeavePartialRevision` | 2 | `template-green-01` | `template-green-01` PASS |
| `ama.minutes.Task09TemplateIT#explicitInstallCreatesActualParentRevisionAndExactSevenThemes` | 2 | `template-green-01` | `template-green-01` PASS |
| `ama.minutes.Task09TemplateIT#unselectedModeCannotConstructAnAdministrativeInstaller` | 2 | `template-green-01` | `template-green-01` PASS |
| `ama.minutes.contract.FinalAndCoverageTest#independentFullFinalProjectionAndFrozenMembershipIgnoresLaterReview` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.contract.FinalAndCoverageTest#latestContraryExactScopeWinsAndPartialReviewsNeverGiveWholeCredit` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.contract.FinalAndCoverageTest#newTargetOrHashInvalidatesReviewAndMissingFinalInputFails` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.contract.GenerationFingerprintTest#completeIndependentEmptyGenerationVector` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.contract.GenerationFingerprintTest#computeCannotPretendUninterpretedArchiveFactsAreValidated` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.contract.GenerationFingerprintTest#fixedFenceChangesDigestAndRowBaseMustMatch` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.contract.GenerationFingerprintTest#taskVerificationRejectsCrossInputAndMissingOrConflictingHistoricalFences` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.contract.HashProfilesTest#completeIndependentConfirmationVectorAndFourMarkSetOrder` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.contract.HashProfilesTest#completeIndependentReviewTargetAndSourcesVectors` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.contract.HashProfilesTest#confirmationCannotClaimRawProfileChainWithoutConfiguredArchiveReader` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.contract.HashProfilesTest#eachSavedMarkAdditionAndRemovalChangesWholeConfirmationDomain` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.contract.HashProfilesTest#materialReviewCannotBypassOwningRawArchiveChain` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.contract.HashProfilesTest#reviewSourcesRejectsSupportiveSubsetAndFullContentChangesTargetHash` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.contract.SourceAndSupportTest#aliasesAndFullTargetsValidatedWithoutInferringTruth` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.contract.SourceAndSupportTest#backgroundOnlyActionsStayUnsupportedAndSuggestionsNeverAcceptIdentity` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.contract.SourceAndSupportTest#danglingDuplicateRangeAndPartialScopeNeverSucceed` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.contract.SourceAndSupportTest#deterministicAliasesTranscriptFirstAndExactDedup` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.contract.SourceAndSupportTest#scalarAnchorHashesWholeTextAndFormatsCanonicalAscii` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.contract.SourceAndSupportTest#syntheticAudioVersionHasNullMaterialAndParserFields` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.contract.SourceAndSupportTest#unitMembershipUsesSavedPhysicalParagraphGapsAndVersion` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.contract.StoredSourceGraphTest#ambiguousPinnedParserSelectionRejectsInsteadOfChoosingOrder` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.contract.StoredSourceGraphTest#artifactFactBudgetCountsUniqueAcquisitionAndRefusesWholeSnapshotExcess` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.contract.StoredSourceGraphTest#completeIndependentOccurrenceGraphPreservesBothLabelsAndEitherParserHash` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.contract.StoredSourceGraphTest#cycleBudgetAndCancellationApplyAcrossAllOrigins` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.contract.StoredSourceGraphTest#onePhysicalLocatorKeepsBothActualNoteInputsAndParserContexts` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.contract.StoredSourceGraphTest#ordinaryCopiedLocalElementNeverAcceptsAttractiveHistoricalInput` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.contract.StoredSourceGraphTest#orphanStoredMembershipCannotBeIgnoredAsAnUnrelatedUsage` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.contract.StoredSourceGraphTest#suppliedMembershipMustBeExactBijection` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.json.OutputBudgetBoundaryTest#eachBoundaryPlusOneFailsWithoutPartialResult()[1]` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.json.OutputBudgetBoundaryTest#eachBoundaryPlusOneFailsWithoutPartialResult()[2]` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.json.OutputBudgetBoundaryTest#eachBoundaryPlusOneFailsWithoutPartialResult()[3]` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.json.OutputBudgetBoundaryTest#eachBoundaryPlusOneFailsWithoutPartialResult()[4]` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.json.OutputBudgetBoundaryTest#eachBoundaryPlusOneFailsWithoutPartialResult()[5]` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.json.OutputBudgetBoundaryTest#eachBoundaryPlusOneFailsWithoutPartialResult()[6]` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.json.OutputBudgetBoundaryTest#eachBoundaryPlusOneFailsWithoutPartialResult()[7]` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.json.OutputBudgetBoundaryTest#eachBoundaryPlusOneFailsWithoutPartialResult()[8]` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.json.OutputBudgetBoundaryTest#eachContainerLimitIsCumulativeAndFailsAtOneOver()[1]` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.json.OutputBudgetBoundaryTest#eachContainerLimitIsCumulativeAndFailsAtOneOver()[2]` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.json.OutputBudgetBoundaryTest#eachContainerLimitIsCumulativeAndFailsAtOneOver()[3]` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.json.OutputBudgetBoundaryTest#eachContainerLimitIsCumulativeAndFailsAtOneOver()[4]` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.json.OutputBudgetBoundaryTest#eachContainerLimitIsCumulativeAndFailsAtOneOver()[5]` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.json.OutputBudgetBoundaryTest#eachContainerLimitIsCumulativeAndFailsAtOneOver()[6]` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.json.OutputBudgetBoundaryTest#exactAllDirectoryAndMembershipCountsAccept` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.json.OutputBudgetBoundaryTest#exactEveryScalarTokenNameDepthAndWireBoundaryAccepts` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.json.StrictMinutesJsonReaderTest#allSevenKindsEmptyDirectoriesTitleAndNonBmpBody` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.json.StrictMinutesJsonReaderTest#cancellationReadFailuresAndInterruptedIoAreRedactedAndPreserveOwnership` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.json.StrictMinutesJsonReaderTest#exactWireBoundaryPlusOneSentinelAndNoCloseOnEveryOutcome` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.json.StrictMinutesJsonReaderTest#perFactoryTokenNameStringDepthAndMembershipLimits` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.json.StrictMinutesJsonReaderTest#rawActionSuggestionsStrictNumbersDatesAndScalarOffsets` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.json.StrictMinutesJsonReaderTest#strictSchemaUnknownEscapedDuplicateMissingAndEof` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.json.StrictMinutesJsonReaderTest#utf8DecoderRejectsMalformedSuffixBomOtherEncodingsAndEscapedSurrogates` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.minutes.json.Task09StoredAnchorTest#rejectsOversizeAndCancellation` | 3 | `anchors-green-01` | `canonical-units-final-01` PASS |
| `ama.minutes.json.Task09StoredAnchorTest#rejectsReorderedWhitespaceDuplicateUnknownAndTrailingStoredForms` | 3 | `anchors-green-01` | `canonical-units-final-01` PASS |
| `ama.minutes.json.Task09StoredAnchorTest#retainsExactScalarAnchorAndCanonicalStoredText` | 3 | `anchors-green-01` | `canonical-units-final-01` PASS |
| `ama.model.Task09ControlledStoreTest#corruptAcknowledgedResultFailsClosedOnReopen` | 3 | `provider-store-green-01` | `canonical-units-final-01` PASS |
| `ama.model.Task09ControlledStoreTest#definitiveRejectionIsDurableAndPermanentlyProhibitsComputation` | 3 | `provider-store-green-01` | `canonical-units-final-01` PASS |
| `ama.model.Task09ControlledStoreTest#exclusiveStoreLockRejectsSecondProcessOwner` | 3 | `provider-store-green-01` | `canonical-units-final-01` PASS |
| `ama.model.Task09ControlledStoreTest#immutableLogicalAndExecutionTuplesRejectChangedBytes` | 3 | `provider-store-green-01` | `canonical-units-final-01` PASS |
| `ama.model.Task09ControlledStoreTest#incompleteUnacknowledgedJournalTailPreservesCompletePrefix` | 3 | `provider-store-green-01` | `canonical-units-final-01` PASS |
| `ama.model.Task09ControlledStoreTest#rejectedModeAndOversizeNeverReserve` | 3 | `provider-store-green-01` | `canonical-units-final-01` PASS |
| `ama.model.Task09ControlledStoreTest#reserveAndSuccessSurviveRestartAndDuplicateReturnsExactIdentity` | 3 | `provider-store-green-01` | `canonical-units-final-01` PASS |
| `ama.model.Task09ControlledStoreTest#reservedComputationSurvivesRestartAndLookupDoesNotCompute` | 3 | `provider-store-green-01` | `canonical-units-final-01` PASS |
| `ama.model.Task09TransportAbortTest#cancellationClosesActualSocketDuringBodyReadAndCloseIsIdempotent` | 3 | `transport-abort-green-01` | `canonical-units-final-01` PASS |
| `ama.model.Task09TransportAbortTest#cancellationClosesActualSocketWhileWaitingForHeaders` | 3 | `transport-abort-green-01` | `canonical-units-final-01` PASS |
| `ama.model.Task09TransportAbortTest#readIdleDeadlineFiresWithoutAnyReceivedByte` | 3 | `transport-abort-green-01` | `canonical-units-final-01` PASS |
| `ama.storage.DeadlineInputStreamTest#normalEofCloseAndWatchdogReleaseExactlyOnce` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.storage.FileBackendDefinitionsTest#allBackendsAreAttemptedWithBoundedSuppressionAndInterruptedFailurePreserved` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.storage.FileBackendDefinitionsTest#errorFromFirstBackendCannotSkipSecondBackend` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.storage.ManagedBackendIdentityTest#ambiguousOriginsAndMalformedUnicodeFailClosed` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.storage.ManagedBackendIdentityTest#credentialAndTimeoutRotationDoNotChangePhysicalId` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.storage.ManagedBackendIdentityTest#fieldCountAndUtf8LengthsMatchIndependentPythonBytes` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.storage.ManagedBackendIdentityTest#physicalChangesCannotReuseManagedIdentity` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.storage.SourceReadLifetimeTest#alreadyLostScopeImmediatelyAbortsRacingNewRequestExactlyOnce` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.storage.SourceReadLifetimeTest#cancellationWinningImmediatelyAfterActualAcquireReturnsExactlyThatPermit` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.storage.SourceReadLifetimeTest#completedTransportDetachesAndClosingScopeDoesNotCancelOriginalCarrierSignal` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.storage.SourceReadLifetimeTest#deadlineAbortsRealRegisteredWorkWithoutCancellingTheOriginalSignal` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.storage.SourceReadLifetimeTest#failedAbortStillReleasesOtherOwnedRequestsAndRepeatedCloseIsSafe` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.storage.SourceReadLifetimeTest#knownLossWakesActualAdmissionWithoutInterruptingReusableThread` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.storage.SourceReadLifetimeTest#saturatedAdmissionDeadlineCannotReleaseAnUnownedPermit` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.storage.StorageRouterTest#blobPageCannotBeChangedByCaller` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.storage.StorageRouterTest#callerCannotReplaceBackendDefinitionAfterConstruction` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.storage.StorageRouterTest#defaultChangePreservesHistoricalKeysAndActiveUploads` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.storage.StorageRouterTest#everyOperationUsesExplicitBackendAndPreservesArguments` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.storage.StorageRouterTest#metadataRejectsFalseHashesAndNegativeSizes` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.storage.StorageRouterTest#missingHistoricalBackendFailsClosedWithoutFallback` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.storage.StorageRouterTest#unsafeLogicalKeysAreRejectedBeforeRouting` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.storage.StorageRouterTest#uploadAndMetadataCannotCarryMissingIdentityOrReceipt` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.storage.Task09CandidatePaginationIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 2 | `candidate-pages-final-02` | `candidate-pages-final-02` PASS |
| `ama.storage.Task09CompositeRecoveryIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 2 | `composite-recovery-green-01` | `composite-recovery-green-01` PASS |
| `ama.storage.Task09DeletedLookupRecoveryIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 1 | `deleted-lookup-green-01` | `deleted-lookup-green-01` PASS |
| `ama.storage.Task09DeniedFactPolicyIT#disabledOwnerAllowsOnlyBoundFactRootCaptureWithoutBusinessOrSourceAuthority` | 2 | `denied-fact-policy-green-01` | `denied-fact-policy-green-01` PASS |
| `ama.storage.Task09DeniedLookupRecoveryIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 4 | `denied-lookup-green-04` | `denied-lookup-green-04` PASS |
| `ama.storage.Task09DeniedOriginalRecoveryIT#recoverOnlyOriginalFact` | 2 | `denied-original-recovery-02` | `denied-original-recovery-02` PASS |
| `ama.storage.Task09DeniedSettlementRecoveryIT#settleExistingTerminalFact` | 1 | `denied-child-settlement-01` | `denied-child-settlement-01` PASS |
| `ama.storage.Task09ExecutionManifestBudgetIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 2 | `execution-manifest-bound-green-01` | `execution-manifest-bound-green-01` PASS |
| `ama.storage.Task09FinalInheritedSavedGraphIT#noninstalledCompleteRawInspectionPreservesActualBodyAndExplicitEmptyMarks` | 2 | `inherited-migration-final-02` | `inherited-migration-final-02` PASS |
| `ama.storage.Task09GenerationApplicationRestartIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 2 | `application-provider-restart-03` | `application-provider-restart-03` PASS |
| `ama.storage.Task09GenerationArchiveAppendIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 3 | `archive-append-green-01` | `archive-append-green-01` PASS |
| `ama.storage.Task09GenerationAuditRollbackIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 1 | `generation-audit-rollback-01` | `generation-audit-rollback-01` PASS |
| `ama.storage.Task09GenerationCandidateInsertRollbackIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 1 | `generation-insert-rollback-01` | `generation-insert-rollback-01` PASS |
| `ama.storage.Task09GenerationCandidateReadbackRollbackIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 1 | `generation-readback-rollback-01` | `generation-readback-rollback-01` PASS |
| `ama.storage.Task09GenerationDeletedIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 1 | `generation-deleted-consumer-01` | `generation-deleted-consumer-01` PASS |
| `ama.storage.Task09GenerationExternalIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 5 | `candidate-reader-green-01` | `candidate-reader-green-01` PASS |
| `ama.storage.Task09GenerationFinalReaderIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 2 | `generation-final-reader-green-01` | `generation-final-reader-green-01` PASS |
| `ama.storage.Task09GenerationInvalidReplayIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 2 | `terminal-replay-final-01` | `terminal-replay-final-01` PASS |
| `ama.storage.Task09GenerationOutboxRollbackIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 1 | `generation-outbox-rollback-01` | `generation-outbox-rollback-01` PASS |
| `ama.storage.Task09GenerationPhysicalCancelIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 1 | `generation-physical-cancel-01` | `generation-physical-cancel-01` PASS |
| `ama.storage.Task09GenerationPreparationIT#realConfirmedAndInitializedFactsProduceProspectiveExactModelRequestWithoutInputRow` | 2 | `generation-prepare-green-01` | `generation-prepare-green-01` PASS |
| `ama.storage.Task09GenerationPreparationIdentitiesIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 2 | `preparation-identity-union-green-01` | `preparation-identity-union-green-01` PASS |
| `ama.storage.Task09GenerationQueueIT#acceptedQueueInputIsAtomicReplayFirstAndPreservesCurrentRoot` | 7 | `generation-worker-prepare-green-01` | `generation-worker-prepare-green-01` PASS |
| `ama.storage.Task09GenerationReplayIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 3 | `terminal-replay-final-01` | `terminal-replay-final-01` PASS |
| `ama.storage.Task09GenerationRestartIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 1 | `generation-provider-restart-01` | `generation-provider-restart-01` PASS |
| `ama.storage.Task09GenerationReviewIdentitiesIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 2 | `generation-review-identities-green-01` | `generation-review-identities-green-01` PASS |
| `ama.storage.Task09GenerationReviewReaderIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 2 | `generation-review-reader-green-01` | `generation-review-reader-green-01` PASS |
| `ama.storage.Task09GenerationRevokedIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 1 | `generation-revoked-consumer-01` | `generation-revoked-consumer-01` PASS |
| `ama.storage.Task09GenerationRichIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 3 | `generation-rich-green-02` | `generation-rich-green-02` PASS |
| `ama.storage.Task09GenerationStaleIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 2 | `generation-business-stale-green-01` | `generation-business-stale-green-01` PASS |
| `ama.storage.Task09GenerationStaleReplayIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 3 | `terminal-replay-final-01` | `terminal-replay-final-01` PASS |
| `ama.storage.Task09GenerationUnknownIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 1 | `generation-unknown-consumer-01` | `generation-unknown-consumer-01` PASS |
| `ama.storage.Task09GenerationZeroEffectReplayIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 3 | `terminal-replay-final-01` | `terminal-replay-final-01` PASS |
| `ama.storage.Task09InitialSettingsIT#auditFailureRollsBackEverySettingsEffect` | 1 | `initial-settings-boundaries-01` | `initial-settings-boundaries-01` PASS |
| `ama.storage.Task09InitialSettingsIT#committedReplayPrecedesNowUnavailableArchivesAndCurrentCas` | 3 | `initial-settings-boundaries-01` | `initial-settings-boundaries-01` PASS |
| `ama.storage.Task09InitialSettingsIT#concurrentSameKeyHasOneCompleteWinnerAndOneReplay` | 1 | `initial-settings-boundaries-01` | `initial-settings-boundaries-01` PASS |
| `ama.storage.Task09InitialSettingsIT#differentKeyAndDetachedHistoryCannotReinitialize` | 1 | `initial-settings-boundaries-01` | `initial-settings-boundaries-01` PASS |
| `ama.storage.Task09InitialSettingsIT#firstSettingsPersistRealTemplateEmptyExclusionAndAdvanceExactlyOnce` | 3 | `initial-settings-boundaries-01` | `initial-settings-boundaries-01` PASS |
| `ama.storage.Task09InitialSettingsIT#partialSettingsAreNotRepaired` | 1 | `initial-settings-boundaries-01` | `initial-settings-boundaries-01` PASS |
| `ama.storage.Task09LimitsDiscoveryIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 3 | `remaining-limits-green-01` | `remaining-limits-green-01` PASS |
| `ama.storage.Task09LimitsOutputIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 2 | `remaining-limits-green-01` | `remaining-limits-green-01` PASS |
| `ama.storage.Task09LookupAuthorityIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 1 | `lookup-authority-green-01` | `lookup-authority-green-01` PASS |
| `ama.storage.Task09MinutesHttpBoundaryIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 3 | `minutes-http-final-01` | `minutes-http-final-01` PASS |
| `ama.storage.Task09MinutesHttpIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 3 | `minutes-http-final-01` | `minutes-http-final-01` PASS |
| `ama.storage.Task09MinutesPaginationIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 3 | `minutes-http-final-01` | `minutes-http-final-01` PASS |
| `ama.storage.Task09MinutesResponseBudgetIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 5 | `minutes-http-final-01` | `minutes-http-final-01` PASS |
| `ama.storage.Task09ObservationCommitAckIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 2 | `observation-commit-ack-green-01` | `observation-commit-ack-green-01` PASS |
| `ama.storage.Task09OriginalExternalRecoveryIT#lookupSameOriginalFactThroughGenuineObserver` | 4 | `original-external-recovery-05` | `original-external-recovery-05` PASS |
| `ama.storage.Task09PinnedRenderIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 4 | `pinned-render-green-04` | `pinned-render-green-04` PASS |
| `ama.storage.Task09PipelineGraphIT#alteredConfirmedBodyCannotPassByMatchingStoredFingerprintAndTask` | 1 | `pipeline-source-integrity-01` | `pipeline-source-integrity-01` PASS |
| `ama.storage.Task09PipelineGraphIT#freshFinalAuthorizationTakesPrecedenceOverDeferredArchiveFailure` | 1 | `pipeline-source-integrity-01` | `pipeline-source-integrity-01` PASS |
| `ama.storage.Task09PipelineGraphIT#realImportSavedConfirmationPassesOriginalMinutesSourcePreparation` | 4 | `pipeline-source-integrity-01` | `pipeline-source-integrity-01` PASS |
| `ama.storage.Task09RemainingAdoptedIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 4 | `disposition-replay-final-01` | `disposition-replay-final-01` PASS |
| `ama.storage.Task09RemainingDisposedStaleIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 4 | `disposition-replay-final-01` | `disposition-replay-final-01` PASS |
| `ama.storage.Task09RemainingInitialReplayIT#replayLocksActualParentBeforeRevisionAndProvesEmptyExclusion` | 5 | `terminal-replay-final-01` | `terminal-replay-final-01` PASS |
| `ama.storage.Task09RemainingRejectedIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 4 | `disposition-replay-final-01` | `disposition-replay-final-01` PASS |
| `ama.storage.Task09RemainingTaskBudgetIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 4 | `remaining-disposition-green-01` | `remaining-disposition-green-01` PASS |
| `ama.storage.s3.S3LayoutV3Test#canonicalDecodingRejectsAlternateChunkingsAndEscapeSpellings` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.storage.s3.S3LayoutV3Test#credentialAndTimeoutRotationRetainsFullPhysicalScopeIdentity` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.storage.s3.S3LayoutV3Test#escapesAndShallowSegmentationPreserveExactOrderAndPrefix` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.storage.s3.S3LayoutV3Test#everyControlTemplateIsShallowAndInvalidPhysicalNamesFailBeforeTransport` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.storage.s3.S3LayoutV3Test#ordinary512AndExactBudgetHaveBoundedPhysicalSegments` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.storage.s3.S3ProtocolTest#admissionOneIsRejectedBeforeClientConstruction` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.storage.s3.S3ProtocolTest#canonicalRecordsRejectTrailingTruncatedUnknownSchemaAndNoncanonicalNumbers` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.storage.s3.S3ProtocolTest#keyEncodingPreservesCodeUnitsPrefixesAndOrderWithoutNormalizing` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.storage.s3.S3ProtocolTest#nativePlannerUsesOverflowSafeCeilingAndNeverLogicalPartNumbers` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.task.Task09FinalInheritedExternalExecutionIT#commitAcknowledgementUnknownReturnsNoAuthority` | 2 | `inherited-migration-final-02` | `inherited-migration-final-02` PASS |
| `ama.task.Task09FinalInheritedExternalExecutionIT#concurrentSameProducerRetryAcceptsOnlyOneExecution` | 2 | `inherited-migration-final-02` | `inherited-migration-final-02` PASS |
| `ama.task.Task09FinalInheritedExternalExecutionIT#markerActuallyCommitsBeforeAuthorityAndAmbientIsRejected` | 2 | `inherited-migration-final-02` | `inherited-migration-final-02` PASS |
| `ama.task.Task09FinalInheritedExternalExecutionIT#oldTaskExpiryDoesNotDiscardAuthoritativeSuccess` | 2 | `inherited-migration-final-02` | `inherited-migration-final-02` PASS |
| `ama.task.Task09FinalInheritedExternalExecutionIT#preparedCrashCapPreservesExecutionHistory` | 2 | `inherited-migration-final-02` | `inherited-migration-final-02` PASS |
| `ama.task.Task09FinalInheritedExternalExecutionIT#processKilledAfterCommittedMarkerNeverMakesTaskResendable` | 2 | `inherited-migration-final-02` | `inherited-migration-final-02` PASS |
| `ama.task.Task09FinalInheritedExternalExecutionIT#reconcilerTakeoverFencesOldOwnerAndPreservesExternalFact` | 2 | `inherited-migration-final-02` | `inherited-migration-final-02` PASS |
| `ama.task.Task09FinalInheritedExternalExecutionIT#safeRetryReplayReturnsSameAcceptedTask` | 2 | `inherited-migration-final-02` | `inherited-migration-final-02` PASS |
| `ama.task.Task09FinalInheritedExternalExecutionIT#unknownExecutionNeverReentersOrdinaryQueue` | 2 | `inherited-migration-final-02` | `inherited-migration-final-02` PASS |
| `ama.task.Task09FinalInheritedExternalExecutionIT#zeroEffectRetryAppendsExecutionWithoutResettingEpoch` | 2 | `inherited-migration-final-02` | `inherited-migration-final-02` PASS |
| `ama.task.TaskMetadataTest#unsafeProtocolCodesAreRejectedBeforePersistence` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.task.TaskMetadataTest#vendorIdentifiersKeepTheirBinaryIdentity` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.transcript.BatchFingerprintTest#canonicalBudgetsAndCancellationNeverReturnSuccess` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.transcript.BatchFingerprintTest#invalidOrIncompleteMembersFailClosed` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.transcript.BatchFingerprintTest#literalVectorMatchesIndependentBytes` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.transcript.BatchFingerprintTest#orderAndEveryIdentityFieldAffectDigest` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.transcript.BatchFingerprintTest#verifyComparesActualBatchFingerprintAndReturnsComputedValue` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.transcript.BatchFingerprintTest#verifyRejectsMissingExtraDuplicateForeignAndContradictoryMetadata` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.transcript.MeasuredTextTranscriptParserTest#reportsAllDecodedUnitsAndBytesWithoutChangingLegacyResult` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.transcript.SavedTranscriptBudgetTest#canonicalEncodedNodesAndContainersRejectBeforeRowOrTextAllocation` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.transcript.SavedTranscriptBudgetTest#copyAndEscapedResponseReservationsPrecedeAllocation` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.transcript.SavedTranscriptBudgetTest#explicitDisabledDeclarationIsNotAnInstalledZeroPendingReader` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.transcript.SavedTranscriptBudgetTest#futureDomainsCannotBecomeInstalledThroughAnEmptyNoOp` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.transcript.SavedTranscriptBudgetTest#supplementaryUtf16AccumulatesAcrossAcquisitionsBeforeTransfer` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.transcript.TextTranscriptParserTest#byteBudgetReadsAtMostOneOverflowSentinel` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.transcript.TextTranscriptParserTest#cancellationAfterReadStopsBeforeAnotherReadAndNeverReturnsPartial` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.transcript.TextTranscriptParserTest#cancellationBeforeReadingDoesNotTouchCallerStream` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.transcript.TextTranscriptParserTest#delimitersAcrossBufferBoundariesDoNotRenumberBlankLines` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.transcript.TextTranscriptParserTest#emptyAndJavaWhitespaceOnlyAreNoText` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.transcript.TextTranscriptParserTest#explicitBudgetsAcceptExactBoundaryAndFailOneOverWithoutTruncation` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.transcript.TextTranscriptParserTest#interruptedReadRestoresInterruptFlagWithoutExposingSourceDetails` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.transcript.TextTranscriptParserTest#invalidArgumentsFailBeforeAnyIo` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.transcript.TextTranscriptParserTest#linesBeyondOneReadBufferRetainExactUnicodeWithoutSourceBuffering` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.transcript.TextTranscriptParserTest#matchingBytesWithoutConfirmedEofStillFailOnReadError` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.transcript.TextTranscriptParserTest#nulFailsExplicitlyWithoutDroppingIt` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.transcript.TextTranscriptParserTest#preservesUnicodeSpacesAndPhysicalLinesWithoutInferringPeopleOrTimes` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.transcript.TextTranscriptParserTest#readFailuresAreTypedSafeAndDoNotCloseCallerStream` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.transcript.TextTranscriptParserTest#rejectsMalformedTruncatedAndLegacyUtf8WithoutPartialSuccess` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.transcript.TextTranscriptParserTest#rejectsWrongHashIncludingChangedBomOrLineSeparators` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.transcript.TextTranscriptParserTest#splitMultibyteCharactersAndMixedDelimitersWorkWithSingleByteReads` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.transcript.TextTranscriptParserTest#stableIdsRepeatButChangeWithFileOrExactSourceBytes` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.transcript.TextTranscriptParserTest#streamRemainsCallerOwnedAfterSuccessAndHashFailure` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.transcript.TextTranscriptParserTest#threadInterruptionIsPreservedAndPreventsRead` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.transcript.TranscriptImportProducerTest#independentProducerGoldenPreservesLongBitsAndEverySemanticField` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.worker.PreparedWorkerLoopTest#closeWhileCompletionWaitsSuppressesCallback` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.worker.PreparedWorkerLoopTest#lostHeartbeatSuppressesPreparedCallback` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.worker.PreparedWorkerLoopTest#nullPreparedCallbackHasOneQuarantineCompletion` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.worker.PreparedWorkerLoopTest#ordinaryCompletionFailureNeverStartsSecondCompletion` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.worker.PreparedWorkerLoopTest#otherQueueImplementationsRejectAtomicEffectsWithoutRunningThem` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.worker.PreparedWorkerLoopTest#preparationFailureHasOneQuarantineCompletion` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.worker.PreparedWorkerLoopTest#preparationFinishesBeforeSingleQueueOwnedCallback` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.worker.PreparedWorkerLoopTest#preparedCompletionFailureNeverStartsSecondCompletion` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.worker.PreparedWorkerLoopTest#selectedRenewalDrainsBeforeCompletionEntry` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.worker.Task09SessionAbortTest#closePhysicallyAbortsHeldHeadersAndBodyWithoutSettlingUnknownPermit` | 2 | `session-abort-green-01` | `session-abort-green-01` PASS |
| `ama.worker.Task09SessionAbortTest#renewalLossPhysicallyAbortsHeldHeadersAndBodyWithoutRegainingSend` | 2 | `session-abort-green-01` | `session-abort-green-01` PASS |
| `ama.worker.Task09SessionAbortTest#singleRegistrationLateCancellationAndAbortRunOutsideMonitor` | 2 | `session-abort-green-01` | `session-abort-green-01` PASS |
| `ama.worker.WorkerLoopTest#blockedCpuHandlerDoesNotConsumeIoCapacity` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.worker.WorkerLoopTest#boundedPoolStartsSecondHandlerOnlyAfterFirstReturns` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.worker.WorkerLoopTest#independentHeartbeatRunsDuringBlockedHandler` | 1 | `none` | `canonical-units-final-01` PASS |
| `ama.worker.WorkerShutdownTest#failedAbortStillCancelsAllExecutionsAndClosesBackendOnce` | 2 | `worker-shutdown-final-01` | `worker-shutdown-final-01` PASS |
| `ama.worker.WorkerShutdownTest#firstBackendFailureStillClosesSecondBackendOnce` | 2 | `worker-shutdown-final-01` | `worker-shutdown-final-01` PASS |
| `ama.worker.WorkerShutdownTest#interruptedCloseStillCleansUpAndPreservesInterruption` | 2 | `worker-shutdown-final-01` | `worker-shutdown-final-01` PASS |
| `ama.worker.WorkerShutdownTest#interruptedFirstBackendFailureStillClosesSecondBackendOnce` | 2 | `worker-shutdown-final-01` | `worker-shutdown-final-01` PASS |
