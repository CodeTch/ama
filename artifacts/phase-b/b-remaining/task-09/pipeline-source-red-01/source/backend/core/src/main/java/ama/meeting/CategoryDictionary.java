package ama.meeting;

/** Local approved configuration snapshot; implementations must not perform network I/O in a transaction. */
@FunctionalInterface
public interface CategoryDictionary { void requireAvailableCategory(String categoryId); }
