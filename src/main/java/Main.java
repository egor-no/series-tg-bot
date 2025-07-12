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
        System.out.println("ENV VARS:");
        for (String key : System.getenv().keySet()) {
            if (key.toLowerCase().contains("sql")) {
                System.out.println(key + " = " + System.getenv(key));
            }
        }
        System.out.println("MYSQLHOST: " + System.getenv("MYSQLHOST"));
        System.out.println("MYSQLPORT: " + System.getenv("MYSQLPORT"));
        System.out.println("MYSQLDATABASE: " + System.getenv("MYSQLDATABASE"));
        System.out.println("MYSQLUSER: " + System.getenv("MYSQLUSER"));
        System.out.println("MYSQLPASSWORD: " + System.getenv("MYSQLPASSWORD"));

//        try {
//            DataSource ds = DataSourceProvider.getDataSource();
//            ds.getConnection().close();
//            System.out.println("✅ Connection to Railway DB successful!");
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }

}


