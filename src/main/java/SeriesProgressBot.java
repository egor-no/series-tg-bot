import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.*;

public class SeriesProgressBot extends TelegramLongPollingBot {

    private final Map<Long, Map<String, int[]>> userSeries = new HashMap<>();
    private final Map<Long, String> userStates = new HashMap<>();
    private final InlineKeyboardMarkup mainMenu;

    public SeriesProgressBot() {
        this.mainMenu = buildMainMenu();
    }

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

    private void sendReply(long chatId, String text, InlineKeyboardMarkup markup) {
        SendMessage response = new SendMessage();
        response.setChatId(String.valueOf(chatId));
        response.setText(text);
        if (markup != null) response.setReplyMarkup(markup);
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

    private String handleStatus(long chatId) {
        Map<String, int[]> series = userSeries.get(chatId);
        if (series == null || series.isEmpty()) {
            return "Ты пока не добавил ни одного сериала.";
        }
        StringBuilder sb = new StringBuilder("Твои сериалы:\n");
        for (Map.Entry<String, int[]> entry : series.entrySet()) {
            int[] p = entry.getValue();
            sb.append("• ").append(entry.getKey())
                    .append(" — Сезон ").append(p[0])
                    .append(", Эпизод ").append(p[1])
                    .append("\n");
        }
        return sb.toString();
    }

    private InlineKeyboardMarkup buildMainMenu() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(InlineKeyboardButton.builder()
                .text("➕ Добавить сериал")
                .callbackData("add")
                .build());
        row.add(InlineKeyboardButton.builder()
                .text("📋 Статус")
                .callbackData("status")
                .build());
        rows.add(row);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        return markup;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            if ("awaiting_add".equals(userStates.get(chatId))) {
                userStates.remove(chatId);
                ParsedTitle parsed = parseTitleSeasonEpisode(text);
                int season = parsed.season() >= 0 ? parsed.season() : 0;
                int episode = parsed.episode() >= 0 ? parsed.episode() : 0;

                userSeries.computeIfAbsent(chatId, k -> new HashMap<>())
                        .put(parsed.title(), new int[]{season, episode});

                sendReply(chatId, "Добавлено: " + parsed.title() + " — Сезон " + season + ", Эпизод " + episode
                        + ". Что ещё хочешь сделать?", mainMenu);
                return;
            } else if (text.startsWith("/start")) {
                sendReply(chatId, "Что хочешь сделать?", mainMenu);
            } else {
                sendReply(chatId, "Неизвестная команда. Используй /start", null);
            }
        }

        if (update.hasCallbackQuery()) {
            String data = update.getCallbackQuery().getData();
            long chatId = update.getCallbackQuery().getMessage().getChatId();

            switch (data) {
                case "add" -> {
                    userStates.put(chatId, "awaiting_add");
                    sendReply(chatId, "Введи сериал (можно сразу с сезоном и эпизодом):", null);
                }
                case "status" -> sendReply(chatId, handleStatus(chatId), mainMenu);
                default -> sendReply(chatId, "Неизвестная кнопка.", mainMenu);
            }
        }
    }

//    @Override
//    public void onUpdateReceived(Update update) {
//        if (update.hasMessage() && update.getMessage().hasText()) {
//            String text = update.getMessage().getText();
//            long chatId = update.getMessage().getChatId();
//            String reply;
//
//            if (text.startsWith("/add")) {
//                ParsedTitle parsed = parseTitleSeasonEpisode(text.substring(4).trim());
//                if (parsed.title().isEmpty()) {
//                    reply = "Формат: /add <название> [<сезон> <эпизод>]";
//                } else {
//                    int season = parsed.season() >= 0 ? parsed.season() : 0;
//                    int episode = parsed.episode() >= 0 ? parsed.episode() : 0;
//
//                    userSeries.computeIfAbsent(chatId, k -> new HashMap<>())
//                            .put(parsed.title(), new int[]{season, episode});
//
//                    reply = "Сериал \"" + parsed.title() + "\" добавлен: Сезон " + season + ", Эпизод " + episode;
//                }
//            } else if (text.startsWith("/set")) {
//                ParsedTitle parsed = parseTitleSeasonEpisode(text.substring(4).trim());
//                if (parsed.season() < 0 || parsed.episode() < 0) {
//                    reply = "Формат: /set <название> <сезон> <эпизод>";
//                } else {
//                    Map<String, int[]> series = userSeries.computeIfAbsent(chatId, k -> new HashMap<>());
//                    if (!series.containsKey(parsed.title())) {
//                        reply = "Сначала добавь сериал командой /add";
//                    } else {
//                        series.put(parsed.title(), new int[]{parsed.season(), parsed.episode()});
//                        reply = "Установлено: " + parsed.title() + " — Сезон " + parsed.season() + ", Эпизод " + parsed.episode();
//                    }
//                }
//            } else if (text.equals("/status")) {
//                Map<String, int[]> series = userSeries.get(chatId);
//                if (series == null || series.isEmpty()) {
//                    reply = "Ты пока не добавил ни одного сериала.";
//                } else {
//                    StringBuilder sb = new StringBuilder("Твои сериалы:\n");
//                    for (Map.Entry<String, int[]> entry : series.entrySet()) {
//                        int[] progress = entry.getValue();
//                        sb.append("• ").append(entry.getKey())
//                                .append(" — Сезон ").append(progress[0])
//                                .append(", Эпизод ").append(progress[1])
//                                .append("\n");
//                    }
//                    reply = sb.toString();
//                }
//
//            } else {
//                reply = "Команды: /add, /set, /status";
//            }
//
//            sendReply(chatId, reply);
//        }
//    }
}

record ParsedTitle(String title, Integer season, Integer episode) {}
