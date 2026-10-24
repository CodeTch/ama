package ama.storage;

import ama.contract.FileContracts.*;
import ama.contract.IdentityContracts.Actor;

import java.io.InputStream;

/** Independent short database transactions around durable storage I/O. */
public interface FileUploads {
    Accepted begin(Actor actor, String meeting, String key, Begin request, String trace);

    void part(
            Actor actor,
            String upload,
            int part,
            InputStream body,
            long size,
            String hash,
            String trace);

    Completed complete(Actor actor, String upload, Completion request, String trace);

    void abort(Actor actor, String upload, String trace);
}
