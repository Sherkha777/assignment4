package eva.facade;

import eva.store.Db;
import eva.store.StoreException;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LaunchOrderRepository implements AutoCloseable {

    private final Connection connection;

    public LaunchOrderRepository(Path directory) {
        this.connection = Db.open(directory, "orders");
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS launch_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, pilot_name VARCHAR(100) NOT NULL, "
                    + "chassis_id VARCHAR(50) NOT NULL, stress DOUBLE NOT NULL, shaft_id VARCHAR(50) NOT NULL, "
                    + "status VARCHAR(20) NOT NULL, pilot_session_id BIGINT, power_line_id BIGINT, "
                    + "catapult_run_id BIGINT, failure_reason VARCHAR(400), trail VARCHAR(400))");
        } catch (SQLException e) {
            throw new StoreException("cannot prepare order store", e);
        }
    }

    public LaunchOrder create(LaunchRequest request) {
        LaunchOrder order = new LaunchOrder(0, request.pilotName(), request.chassisId(), request.stress(),
                request.shaftId(), ChainStatus.STARTED, null, null, null, "", "");
        String sql = "INSERT INTO launch_order (pilot_name, chassis_id, stress, shaft_id, status, failure_reason, trail) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, order.pilotName());
            ps.setString(2, order.chassisId());
            ps.setDouble(3, order.stress());
            ps.setString(4, order.shaftId());
            ps.setString(5, order.status().name());
            ps.setString(6, order.failureReason());
            ps.setString(7, order.trail());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                order.assignId(keys.getLong(1));
            }
            return order;
        } catch (SQLException e) {
            throw new StoreException("cannot insert launch order", e);
        }
    }

    public void update(LaunchOrder order) {
        String sql = "UPDATE launch_order SET status = ?, pilot_session_id = ?, power_line_id = ?, "
                + "catapult_run_id = ?, failure_reason = ?, trail = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, order.status().name());
            setNullableLong(ps, 2, order.pilotSessionId());
            setNullableLong(ps, 3, order.powerLineId());
            setNullableLong(ps, 4, order.catapultRunId());
            ps.setString(5, order.failureReason());
            ps.setString(6, order.trail());
            ps.setLong(7, order.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("cannot update launch order", e);
        }
    }

    public Optional<LaunchOrder> find(long id) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM launch_order WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StoreException("cannot read launch order", e);
        }
    }

    public List<LaunchOrder> findAll() {
        return query("SELECT * FROM launch_order ORDER BY id");
    }

    public List<LaunchOrder> findNonTerminal() {
        return query("SELECT * FROM launch_order WHERE status NOT IN ('COMPLETED', 'COMPENSATED', 'REJECTED') "
                + "ORDER BY id");
    }

    private List<LaunchOrder> query(String sql) {
        List<LaunchOrder> result = new ArrayList<>();
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                result.add(map(rs));
            }
            return result;
        } catch (SQLException e) {
            throw new StoreException("cannot list launch orders", e);
        }
    }

    private void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BIGINT);
        } else {
            ps.setLong(index, value);
        }
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private LaunchOrder map(ResultSet rs) throws SQLException {
        return new LaunchOrder(rs.getLong("id"), rs.getString("pilot_name"), rs.getString("chassis_id"),
                rs.getDouble("stress"), rs.getString("shaft_id"), ChainStatus.valueOf(rs.getString("status")),
                nullableLong(rs, "pilot_session_id"), nullableLong(rs, "power_line_id"),
                nullableLong(rs, "catapult_run_id"), rs.getString("failure_reason"), rs.getString("trail"));
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new StoreException("cannot close order store", e);
        }
    }
}
