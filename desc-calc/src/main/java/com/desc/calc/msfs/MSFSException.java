package com.desc.calc.msfs;

import lombok.Getter;

/** A simulator protocol/import failure with an HTTP status and a client-facing error code. */
@Getter
public class MSFSException extends RuntimeException {

    private final int status;
    private final String code;

    public MSFSException(int status, String code, String message) {

        super(message);
        this.status = status;
        this.code = code;
    }
}
