from pathlib import Path
p=Path('backend/adapters/src/main/java/ama/storage/FileBackendCatalog.java');s=p.read_text();old='''    public Backend require(String id, boolean newWrite) {
        if (id == null) throw new Unavailable();''';new='''    public Backend require(String id, boolean newWrite) {
        var backend = configured(id, newWrite);
        ManagedBackendId.require(id, backend.definition());
        return requireStoredMetadata(backend);
    }

    /**
     * Locked historical-source check. Backend construction already derives and validates its ID
     * from the sealed immutable definition. This path performs only the actual DB metadata check;
     * it neither hashes nor contacts storage. Post-commit open still uses require(...).
     */
    Backend requireHistoricalMetadata(String id) {
        return requireStoredMetadata(configured(id, false));
    }

    private Backend configured(String id, boolean newWrite) {
        if (id == null) throw new Unavailable();'''
assert s.count(old)==1;s=s.replace(old,new)
old='''        ManagedBackendId.require(id, b.definition());
        var row =''';new='''        return b;
    }

    private Backend requireStoredMetadata(Backend b) {
        var row ='''
assert s.count(old)==1;s=s.replace(old,new);s=s.replace('''                        id);
        if (row.size()''','''                        b.id());
        if (row.size()''');p.write_text(s)
p=Path('backend/adapters/src/main/java/ama/storage/JdbcFileRegistry.java');s=p.read_text();old='var backend=backends.require(r.getString(3),false);';assert s.count(old)==1;p.write_text(s.replace(old,'var backend=backends.requireHistoricalMetadata(r.getString(3));'))
