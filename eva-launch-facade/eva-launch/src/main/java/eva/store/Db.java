package eva.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class Db {

    private Db() {
    }

    public static Connection open(Path directory, String name) {
        try {
            Files.createDirectories(directory);
            String file = directory.resolve(name).toAbsolutePath().toString().replace('\\', '/');
            return DriverManager.getConnection("jdbc:h2:file:" + file + ";WRITE_DELAY=0");
        } catch (SQLException | IOException e) {
            throw new StoreException("cannot open store " + name, e);
        }
    }
}
