import type { components } from '../../src/shared/api/generated.js';
type Known<T> = unknown extends T ? false : true;
const knownSaveInfo: Known<components["schemas"]["SaveInfo"]> = true;
const knownImportRequest: Known<components["schemas"]["ImportRequest"]> = true;
const knownSaveRequest: Known<components["schemas"]["SaveRequest"]> = true;
const knownMarksRequest: Known<components["schemas"]["MarksRequest"]> = true;
const knownConfirmRequest: Known<components["schemas"]["ConfirmRequest"]> = true;
const knownRestoreRequest: Known<components["schemas"]["RestoreRequest"]> = true;
const knownMinutesInitialCommand: Known<components["schemas"]["MinutesInitialCommand"]> = true;
const knownMinutesGenerationCommand: Known<components["schemas"]["MinutesGenerationCommand"]> = true;
const knownSavedIntentCommand: Known<components["schemas"]["SavedIntentCommand"]> = true;
const knownSavedBodyCommand: Known<components["schemas"]["SavedBodyCommand"]> = true;
const knownFinalCommand: Known<components["schemas"]["FinalCommand"]> = true;
const knownSubmitCommand: Known<components["schemas"]["SubmitCommand"]> = true;
const tokenInfo: components['schemas']['SaveInfo'] = {expectedRevisionToken: '9223372036854775806', rosterDraftIds: []};
// @ts-expect-error A revision number or token is required.
const noRevision: components['schemas']['SaveInfo'] = {rosterDraftIds: []};
// @ts-expect-error Invalid field type must not be erased by unknown.
const wrongRoster: components['schemas']['SaveInfo'] = {expectedRevisionToken: '1', rosterDraftIds: 42};
// @ts-expect-error Extra wire fields are not declared.
const unknownField: components['schemas']['SaveInfo'] = {expectedRevisionToken: '1', rosterDraftIds: [], privateField: true};

type RequiresVersion<T> = [T] extends [{expectedRevision: unknown} | {expectedRevisionToken: unknown}] ? true : false;
const requiresSaveInfo: RequiresVersion<components["schemas"]["SaveInfo"]> = true;
const requiresImportRequest: RequiresVersion<components["schemas"]["ImportRequest"]> = true;
const requiresSaveRequest: RequiresVersion<components["schemas"]["SaveRequest"]> = true;
const requiresMarksRequest: RequiresVersion<components["schemas"]["MarksRequest"]> = true;
const requiresConfirmRequest: RequiresVersion<components["schemas"]["ConfirmRequest"]> = true;
const requiresRestoreRequest: RequiresVersion<components["schemas"]["RestoreRequest"]> = true;
const requiresMinutesInitialCommand: RequiresVersion<components["schemas"]["MinutesInitialCommand"]> = true;
const requiresMinutesGenerationCommand: RequiresVersion<components["schemas"]["MinutesGenerationCommand"]> = true;
const requiresSavedIntentCommand: RequiresVersion<components["schemas"]["SavedIntentCommand"]> = true;
const requiresSavedBodyCommand: RequiresVersion<components["schemas"]["SavedBodyCommand"]> = true;
const requiresFinalCommand: RequiresVersion<components["schemas"]["FinalCommand"]> = true;
const requiresSubmitCommand: RequiresVersion<components["schemas"]["SubmitCommand"]> = true;

// @ts-expect-error Every union branch must require a version, including future schema branches.
const incompleteUnion: RequiresVersion<{expectedRevisionToken: string} | {body: string}> = true;
