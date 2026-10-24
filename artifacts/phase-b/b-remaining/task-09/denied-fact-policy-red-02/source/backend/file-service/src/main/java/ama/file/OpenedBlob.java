package ama.file;

import ama.contract.StorageContracts.ErrorCode;
import ama.contract.StorageContracts.StorageException;

import java.io.InputStream;

/** Owns one opened inode until closed; a selected range stream shares that ownership. */
final class OpenedBlob implements AutoCloseable {
    private final LinuxFiles fs;
    private final LinuxFiles.Fd data;
    private final long size;
    private final Runnable guard;
    private boolean selected;

    OpenedBlob(LinuxFiles fs, LinuxFiles.Fd data, long size, Runnable guard) {
        this.fs = fs;
        this.data = data;
        this.size = size;
        this.guard = guard;
    }

    long size() {
        return size;
    }

    InputStream range(long offset, long length) {
        if (selected) throw new IllegalStateException("range already selected");
        if (offset < 0 || length < 0 || offset > size || length > size - offset)
            throw new StorageException(ErrorCode.INVALID_RANGE);
        guard.run();
        fs.seek(data, offset);
        selected = true;
        return fs.input(data, length, guard);
    }

    @Override
    public void close() {
        data.close();
    }
}
