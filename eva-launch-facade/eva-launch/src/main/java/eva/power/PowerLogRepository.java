package eva.power;

import eva.store.Db;
import eva.store.StoreException;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PowerLogRepository implements AutoCloseable {

    public static final double DEFAULT_BAY_SUPPLY = 12.0;

    private final Connection connection;

    public PowerLogRepository(Path directory) {
        this.connection = Db.open(directory, "power");
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS power_line ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, chassis_id VARCHAR(50) NOT NULL, "
                    + "megawatts DOUBLE NOT NULL, status VARCHAR(20) NOT NULL, note VARCHAR(300))");
            st.execute("CREATE TABLE IF NOT EXISTS bay_supply ("
                    + "chassis_id VARCHAR(50) PRIMARY KEY, available_mw DOUBLE NOT NULL)");
        } catch (SQLException e) {
            throw new StoreException("cannot prepare power store", e);
        }
    }

    public PowerLine insert(PowerLine line) {
        String sql = "INSERT INTO power_line (chassis_id, megawatts, status, note) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, line.chassisId());
            ps.setDouble(2, line.megawatts());
            ps.setString(3, line.status().name());
            ps.setString(4, line.note());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                line.assignId(keys.getLong(1));
            }
            return line;
        } catch (SQLException e) {
            throw new StoreException("cannot insert power line", e);
        }
    }

    public void update(PowerLine line) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE power_line SET status = ?, note = ? WHERE id = ?")) {
            ps.setString(1, line.status().name());
            ps.setString(2, line.note());
            ps.setLong(3, line.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("cannot update power line", e);
        }
    }

    public Optional<PowerLine> findById(long id) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM power_line WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StoreException("cannot read power line", e);
        }
    }

    public List<PowerLine> findAll() {
        List<PowerLine> result = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM power_line ORDER BY id")) {
            while (rs.next()) {
                result.add(map(rs));
            }
            return result;
        } catch (SQLException e) {
            throw new StoreException("cannot list power lines", e);
        }
    }

    public double bayAvailableMegawatts(String chassisId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT available_mw FROM bay_supply WHERE chassis_id = ?")) {
            ps.setString(1, chassisId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : DEFAULT_BAY_SUPPLY;
            }
        } catch (SQLException e) {
            throw new StoreException("cannot read bay supply", e);
        }
    }

    public void setBayAvailableMegawatts(String chassisId, double megawatts) {
        try (PreparedStatement ps = connection.prepareStatement(
                "MERGE INTO bay_supply (chassis_id, available_mw) KEY (chassis_id) VALUES (?, ?)")) {
            ps.setString(1, chassisId);
            ps.setDouble(2, megawatts);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("cannot write bay supply", e);
        }
    }

    private PowerLine map(ResultSet rs) throws SQLException {
        return new PowerLine(rs.getLong("id"), rs.getString("chassis_id"), rs.getDouble("megawatts"),
                PowerStatus.valueOf(rs.getString("status")), rs.getString("note"));
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new StoreException("cannot close power store", e);
        }
    }
}
