package eva.catapult;

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

public class LaunchTelemetryRepository implements AutoCloseable {

    private final Connection connection;

    public LaunchTelemetryRepository(Path directory) {
        this.connection = Db.open(directory, "catapult");
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS catapult_run ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, shaft_id VARCHAR(50) NOT NULL, "
                    + "chassis_id VARCHAR(50) NOT NULL, status VARCHAR(20) NOT NULL, note VARCHAR(300))");
            st.execute("CREATE TABLE IF NOT EXISTS shaft_condition ("
                    + "shaft_id VARCHAR(50) PRIMARY KEY, door_open BOOLEAN NOT NULL, debris BOOLEAN NOT NULL)");
        } catch (SQLException e) {
            throw new StoreException("cannot prepare catapult store", e);
        }
    }

    public CatapultRun insert(CatapultRun run) {
        String sql = "INSERT INTO catapult_run (shaft_id, chassis_id, status, note) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, run.shaftId());
            ps.setString(2, run.chassisId());
            ps.setString(3, run.status().name());
            ps.setString(4, run.note());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                run.assignId(keys.getLong(1));
            }
            return run;
        } catch (SQLException e) {
            throw new StoreException("cannot insert catapult run", e);
        }
    }

    public void update(CatapultRun run) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE catapult_run SET status = ?, note = ? WHERE id = ?")) {
            ps.setString(1, run.status().name());
            ps.setString(2, run.note());
            ps.setLong(3, run.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("cannot update catapult run", e);
        }
    }

    public Optional<CatapultRun> findById(long id) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM catapult_run WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StoreException("cannot read catapult run", e);
        }
    }

    public List<CatapultRun> findAll() {
        List<CatapultRun> result = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM catapult_run ORDER BY id")) {
            while (rs.next()) {
                result.add(map(rs));
            }
            return result;
        } catch (SQLException e) {
            throw new StoreException("cannot list catapult runs", e);
        }
    }

    public ShaftCondition shaftCondition(String shaftId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT door_open, debris FROM shaft_condition WHERE shaft_id = ?")) {
            ps.setString(1, shaftId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new ShaftCondition(rs.getBoolean(1), rs.getBoolean(2)) : ShaftCondition.clear();
            }
        } catch (SQLException e) {
            throw new StoreException("cannot read shaft condition", e);
        }
    }

    public void setShaftCondition(String shaftId, ShaftCondition condition) {
        try (PreparedStatement ps = connection.prepareStatement(
                "MERGE INTO shaft_condition (shaft_id, door_open, debris) KEY (shaft_id) VALUES (?, ?, ?)")) {
            ps.setString(1, shaftId);
            ps.setBoolean(2, condition.doorOpen());
            ps.setBoolean(3, condition.debris());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("cannot write shaft condition", e);
        }
    }

    private CatapultRun map(ResultSet rs) throws SQLException {
        return new CatapultRun(rs.getLong("id"), rs.getString("shaft_id"), rs.getString("chassis_id"),
                CatapultStatus.valueOf(rs.getString("status")), rs.getString("note"));
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new StoreException("cannot close catapult store", e);
        }
    }
}
