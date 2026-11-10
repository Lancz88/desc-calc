package com.desc.calc.web.in;

import com.desc.calc.msfs.MSFSException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;

/** Panel protocol errors remain JSON responses, independently of the web page's error view. */
@ControllerAdvice(assignableTypes = MSFSController.class)
public class MSFSControllerAdvice {

    @ExceptionHandler(MSFSException.class)
    public ResponseEntity<Map<String, Object>> msfsError(MSFSException exception) {

        return ResponseEntity.status(exception.getStatus())
                .header("Cache-Control", "no-store")
                .body(Map.of("ok", false, "error", exception.getCode(), "message", exception.getMessage()));
    }
}
