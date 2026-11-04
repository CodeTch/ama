import { ref } from 'vue';
import { validImportDraftCommand } from '../../shared/api/import-drafts';
import type { CommandSnapshot } from '../../shared/composables/useCommand';
import type { TabJournalContext } from '../../shared/composables/tab-journal';

type PendingImport = { command: CommandSnapshot; newImport: boolean; denied: boolean };

export function createPendingImportJournal(context: TabJournalContext) {
 const key = 'ama.pending-imports.v1:' + context.tab;
 const revision = ref(0);
 const error = ref(false);

 function read(): PendingImport[] {
  revision.value;
  if (!context.storage) throw Error('Import recovery storage unavailable');
  const raw = context.storage.getItem(key);
  if (raw === null) return [];
  if (raw.length > 2 * 1024 * 1024) throw Error('Import recovery exceeds limit');
  const entries: PendingImport[] = JSON.parse(raw);
  if (!Array.isArray(entries) || entries.length > 16 || !entries.every(entry =>
   entry && typeof entry.newImport === 'boolean' && typeof entry.denied === 'boolean' &&
   typeof entry.command?.actor === 'string' && validImportDraftCommand(entry.command, entry.command.actor, entry.command.resource)
  )) throw Error('Invalid import recovery record');
  return entries;
 }

 function change(update: (entries: PendingImport[]) => PendingImport[]) {
  try {
   const entries = update(read());
   const encoded = JSON.stringify(entries);
   if (entries.length > 16 || encoded.length > 2 * 1024 * 1024) return false;
   if (entries.length) context.storage!.setItem(key, encoded);
   else context.storage!.removeItem(key);
   error.value = false;
   revision.value++;
   return true;
  } catch {
   error.value = true;
   return false;
  }
 }

 return {
  error,
  list(actor: string) {
   try { return read().filter(entry => entry.command.actor === actor); }
   catch { return []; }
  },
  get(actor: string, meeting: string) {
   return read().find(entry => entry.command.actor === actor && entry.command.resource === meeting);
  },
  put(entry: PendingImport) {
   return change(entries => {
    const { actor, resource, key } = entry.command;
    const existing = entries.find(item => item.command.actor === actor && item.command.resource === resource);
    if (existing && existing.command.key !== key) throw Error('Another import request is pending');
    return [...entries.filter(item => item !== existing), entry];
   });
  },
  remove(command: CommandSnapshot) {
   return change(entries => entries.filter(({command: item}) =>
    item.actor !== command.actor || item.resource !== command.resource || item.key !== command.key));
  },
  clearActor(actor: string) {
   return change(entries => entries.filter(entry => entry.command.actor !== actor));
  },
 };
}
