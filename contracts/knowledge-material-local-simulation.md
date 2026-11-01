# Local knowledge material simulation

The harness is a parallel development simulator. Its signatures prove an execution of the explicitly configured local protocol, not a real enterprise authorization.

The authenticated material catalog, exact-version download and batch authorization use the same native harness records plus retained controlled publication records. Every native read requires an explicit actor/library grant and an active CAS actor. Retained controlled documents use their original client, folder grants and governance state. A batch proof checks every exact (libraryId, documentId, version) and binds the ordered items digest, actor, five configured identity pins and original five-second validity period.

Content versions are SHA-256 of the agreed canonical payload: UTF-8, keys ordered by UTF-8, no insignificant whitespace, all U+0000–U+001F characters escaped as lowercase `\u00xx`, quote/backslash escaped without changing literal backslash sequences. Lists, downloads, sizes and authority use the same bytes. A payload's old incompatible digest is never accepted as a correct new digest.

AMA material errors retain private-safe `error.code` and `traceId`; upstream exception text and document contents are not rendered. `MATERIAL_KNOWLEDGE_CONTENT_INVALID` (503) means integrity validation failed; `MATERIAL_KNOWLEDGE_VERSION_CONFLICT` (409) alone does not authorize dropping a pending operation.

For batch preflight version conflicts, AMA serializes a terminal rejection with the meeting lock and the existing material_batch_receipt journal. A prior successful outcome wins. A rejected key cannot subsequently commit, and changed bytes under that key conflict. Only `MATERIAL_BATCH_VERSION_REJECTED` (409) permits the browser to remove that pending operation and explicitly select new document versions. Network failures and unknown outcomes retain the original key/body across refresh.

Harness HTTP8792/SMTP2525 remain fixed. Explicit native material identities, readers and the other controlled bridge settings are retained in the sibling's ignored .env. AMA retains only allowlisted local integration choices in .tools/local-app/local-settings.json (0600); explicit shell variables take precedence.
