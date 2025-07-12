package data;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class SeriesProgressDAO {
    private final DataSource dataSource;

    public SeriesProgressDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void saveOrUpdate(long chatId, String name, int season, int episode) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            PreparedStatement stmt = conn.prepareStatement("""
                INSERT INTO series_progress (chat_id, series_name, season, episode)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE season = VALUES(season), episode = VALUES(episode)
            """);
            stmt.setLong(1, chatId);
            stmt.setString(2, name);
            stmt.setInt(3, season);
            stmt.setInt(4, episode);
            stmt.executeUpdate();
        }
    }

    public List<Series> getAllForUser(long chatId) throws SQLException {
        List<Series> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            PreparedStatement stmt = conn.prepareStatement("""
                    SELECT series_name, season, episode, status
                    FROM series_progress
                    WHERE chat_id = ?
                    ORDER BY
                        CASE WHEN status IS NULL OR status = '' THEN 0 ELSE 1 END,
                        series_name COLLATE utf8mb4_unicode_ci
                """);
            stmt.setLong(1, chatId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                result.add(new Series(
                        rs.getString("series_name"),
                        rs.getInt("season"),
                        rs.getInt("episode")
                ));
            }
        }
        return result;
    }

    public void setStatus(long chatId, String name, String status) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            PreparedStatement stmt = conn.prepareStatement("""
            UPDATE series_progress
            SET status = ?
            WHERE chat_id = ? AND series_name = ?
        """);
            stmt.setString(1, status);
            stmt.setLong(2, chatId);
            stmt.setString(3, name);
            stmt.executeUpdate();
        }
    }

    public void delete(long chatId, String name) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            PreparedStatement stmt = conn.prepareStatement("DELETE FROM series_progress WHERE chat_id = ? AND series_name = ?");
            stmt.setLong(1, chatId);
            stmt.setString(2, name);
            stmt.executeUpdate();
        }
    }
}