package eva.pilot;

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

public class PilotStatusRepository implements AutoCloseable {

    private final Connection connection;

    public PilotStatusRepository(Path directory) {
        this.connection = Db.open(directory, "pilot");
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS pilot_session ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, pilot_name VARCHAR(100) NOT NULL, "
                    + "chassis_id VARCHAR(50) NOT NULL, stress DOUBLE NOT NULL, "
                    + "sync_ratio DOUBLE NOT NULL, status VARCHAR(20) NOT NULL)");
        } catch (SQLException e) {
            throw new StoreException("cannot prepare pilot store", e);
        }
    }

    public PilotSession insert(PilotSession session) {
        String sql = "INSERT INTO pilot_session (pilot_name, chassis_id, stress, sync_ratio, status) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, session.pilotName());
            ps.setString(2, session.chassisId());
            ps.setDouble(3, session.stress());
            ps.setDouble(4, session.syncRatio());
            ps.setString(5, session.status().name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                session.assignId(keys.getLong(1));
            }
            return session;
        } catch (SQLException e) {
            throw new StoreException("cannot insert pilot session", e);
        }
    }

    public void updateStatus(PilotSession session) {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE pilot_session SET status = ? WHERE id = ?")) {
            ps.setString(1, session.status().name());
            ps.setLong(2, session.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("cannot update pilot session", e);
        }
    }

    public Optional<PilotSession> findById(long id) {
        return queryOne("SELECT * FROM pilot_session WHERE id = ?", id);
    }

    public Optional<PilotSession> findLockedOnChassis(String chassisId) {
        return queryOne("SELECT * FROM pilot_session WHERE chassis_id = ? AND status = 'SYNC_LOCKED'", chassisId);
    }

    public List<PilotSession> findAll() {
        List<PilotSession> result = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM pilot_session ORDER BY id")) {
            while (rs.next()) {
                result.add(map(rs));
            }
            return result;
        } catch (SQLException e) {
            throw new StoreException("cannot list pilot sessions", e);
        }
    }

    private Optional<PilotSession> queryOne(String sql, Object parameter) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, parameter);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StoreException("cannot read pilot session", e);
        }
    }

    private PilotSession map(ResultSet rs) throws SQLException {
        return new PilotSession(rs.getLong("id"), rs.getString("pilot_name"), rs.getString("chassis_id"),
                rs.getDouble("stress"), rs.getDouble("sync_ratio"),
                PilotSyncStatus.valueOf(rs.getString("status")));
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new StoreException("cannot close pilot store", e);
        }
    }
}
