import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

public class SeriesProgressBot extends TelegramLongPollingBot {

    @Override
    public String getBotUsername() {
        return "SeriesProgressBot";
    }

    @Override
    public String getBotToken() {
        String token = System.getenv("BOT_TOKEN");
        System.out.println("TOKEN: " + token); // временно!
        return token;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            SendMessage response = new SendMessage();
            response.setChatId(String.valueOf(chatId));
            response.setText("Ты написал: " + text);

            try {
                execute(response);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}