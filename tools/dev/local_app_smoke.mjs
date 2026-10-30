// The local app entry point is authenticated by the same CAS flow as production.
// Keep the historical command name as a compatibility wrapper for the focused
// CAS browser smoke test.
await import('./local_login_smoke.mjs');
