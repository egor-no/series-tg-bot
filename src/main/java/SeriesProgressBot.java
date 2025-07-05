import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class SeriesProgressBot extends TelegramLongPollingBot {

    private final Map<Long, Map<String, int[]>> userSeries = new HashMap<>();

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

    private void sendReply(long chatId, String text) {
        SendMessage response = new SendMessage();
        response.setChatId(String.valueOf(chatId));
        response.setText(text);
        try {
            execute(response);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isNumeric(String str) {
        return str.matches("\\d+");
    }

    private ParsedTitle parseTitleSeasonEpisode(String input) {
        String[] words = input.trim().split(" ");
        int season = 0, episode = 0;
        String title;

        if (words.length >= 3 &&
                isNumeric(words[words.length - 1]) &&
                isNumeric(words[words.length - 2])) {

            episode = Integer.parseInt(words[words.length - 1]);
            season = Integer.parseInt(words[words.length - 2]);
            title = String.join(" ", Arrays.copyOfRange(words, 0, words.length - 2));
        } else {
            title = input.trim();
            season = -1;
            episode = -1;
        }

        return new ParsedTitle(title, season, episode);
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();
            String reply;

            if (text.startsWith("/add")) {
                ParsedTitle parsed = parseTitleSeasonEpisode(text.substring(4).trim());
                if (parsed.title().isEmpty()) {
                    reply = "Формат: /add <название> [<сезон> <эпизод>]";
                } else {
                    int season = parsed.season() >= 0 ? parsed.season() : 0;
                    int episode = parsed.episode() >= 0 ? parsed.episode() : 0;

                    userSeries.computeIfAbsent(chatId, k -> new HashMap<>())
                            .put(parsed.title(), new int[]{season, episode});

                    reply = "Сериал \"" + parsed.title() + "\" добавлен: Сезон " + season + ", Эпизод " + episode;
                }
            } else if (text.startsWith("/set")) {
                ParsedTitle parsed = parseTitleSeasonEpisode(text.substring(4).trim());
                if (parsed.season() < 0 || parsed.episode() < 0) {
                    reply = "Формат: /set <название> <сезон> <эпизод>";
                } else {
                    Map<String, int[]> series = userSeries.computeIfAbsent(chatId, k -> new HashMap<>());
                    if (!series.containsKey(parsed.title())) {
                        reply = "Сначала добавь сериал командой /add";
                    } else {
                        series.put(parsed.title(), new int[]{parsed.season(), parsed.episode()});
                        reply = "Установлено: " + parsed.title() + " — Сезон " + parsed.season() + ", Эпизод " + parsed.episode();
                    }
                }
            } else if (text.equals("/status")) {
                Map<String, int[]> series = userSeries.get(chatId);
                if (series == null || series.isEmpty()) {
                    reply = "Ты пока не добавил ни одного сериала.";
                } else {
                    StringBuilder sb = new StringBuilder("Твои сериалы:\n");
                    for (Map.Entry<String, int[]> entry : series.entrySet()) {
                        int[] progress = entry.getValue();
                        sb.append("• ").append(entry.getKey())
                                .append(" — Сезон ").append(progress[0])
                                .append(", Эпизод ").append(progress[1])
                                .append("\n");
                    }
                    reply = sb.toString();
                }

            } else {
                reply = "Команды: /add, /set, /status";
            }

            sendReply(chatId, reply);
        }
    }
}

record ParsedTitle(String title, Integer season, Integer episode) {}
