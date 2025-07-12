import data.DataSourceProvider;
import data.SeriesProgressService;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import javax.sql.DataSource;

public class Main {

    public static void main(String[] args) {
        try {
            SeriesProgressService service = new SeriesProgressService();
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new SeriesProgressBot(service));
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            DataSource ds = DataSourceProvider.getDataSource();
            ds.getConnection().close();
            System.out.println("✅ Connection to Railway DB successful!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}


