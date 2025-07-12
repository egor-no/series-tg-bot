package data;

import java.sql.SQLException;
import java.util.List;

public class SeriesProgressService {
    private final SeriesProgressDAO dao;

    public SeriesProgressService() {
        this.dao = new SeriesProgressDAO(DataSourceProvider.getDataSource());
    }

    public void saveOrUpdate(long chatId, String name, int season, int episode) {
        try {
            dao.saveOrUpdate(chatId, name, season, episode);
        } catch (SQLException e) {
            e.printStackTrace(); // можно заменить на логгер
        }
    }

    public List<Series> getAll(long chatId) {
        try {
            return dao.getAllForUser(chatId);
        } catch (SQLException e) {
            e.printStackTrace();
            return List.of(); // пустой список
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
