import data.DataSourceProvider;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import javax.sql.DataSource;

public class Main {

    public static void main(String[] args) {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new SeriesProgressBot());
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


