package com.desc.calc.web.service.save;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** A versioned local archive, replaced atomically so a failed write preserves the previous file. */
@Repository
public class FlightPlanFileStore {
    private static final int VERSION = 1;
    private final ObjectMapper json;
    private final Path file;

    public FlightPlanFileStore(ObjectMapper json,
                              @Value("${flight-plans.file:data/flight-plans.json}") String file) {
        this.json = json;
        this.file = Path.of(file).toAbsolutePath().normalize();
    }

    public List<SavedFlightPlan> read() {
        if (Files.notExists(file)) return List.of();
        try {
            Archive archive = json.readValue(file.toFile(), Archive.class);
            if (archive.version() != VERSION || archive.plans() == null || archive.plans().size() > 10) {
                throw new IOException("Unsupported archive");
            }
            return archive.plans();
        } catch (IOException | RuntimeException exception) {
            throw new StorageException("Couldn't read saved plans. Your saved file has been kept.", exception);
        }
    }

    public void write(List<SavedFlightPlan> plans) {
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), "flight-plans-", ".tmp");
            json.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), new Archive(VERSION, plans));
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | JacksonException exception) {
            throw new StorageException("Couldn't save this plan. Please try again.", exception);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { /* Not an archive file. */ }
            }
        }
    }

    public record Archive(int version, List<SavedFlightPlan> plans) {}

    public static class StorageException extends RuntimeException {
        public StorageException(String message, Throwable cause) { super(message, cause); }
    }
}
