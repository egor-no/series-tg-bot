package data;

import java.sql.SQLException;
import java.util.List;

public class SeriesProgressService {
    private final SeriesProgressDAO dao;

    public SeriesProgressService() {
        this.dao = new SeriesProgressDAO(DataSourceProvider.getDataSource());
    }

    public List<Series> getAll(long chatId) {
        try {
            return dao.getAllForUser(chatId);
        } catch (SQLException e) {
            e.printStackTrace();
            return List.of();
        }
    }

    public Series getByName(long chatId, String name) {
        try {
            return dao.getByName(chatId, name);
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void setStatus(long chatId, String name, String status) {
        try {
            dao.setStatus(chatId, name, status);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void saveOrUpdate(long chatId, String name, int season, int episode) {
        try {
            dao.saveOrUpdate(chatId, name, season, episode);
            dao.clearStatus(chatId, name);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void delete(long chatId, String name) {
        try {
            dao.delete(chatId, name);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
