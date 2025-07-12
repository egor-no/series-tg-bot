import data.Series;
import data.SeriesProgressService;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.*;
import java.util.stream.Collectors;

public class SeriesProgressBot extends TelegramLongPollingBot {

    private final SeriesProgressService seriesService;
    private final Map<Long, UserSession> sessions = new HashMap<>();
    private final InlineKeyboardMarkup mainMenu;

    public SeriesProgressBot(SeriesProgressService seriesService) {
        this.seriesService = seriesService;
        this.mainMenu = buildMainMenu();
    }

    @Override
    public String getBotUsername() {
        return "SeriesProgressBot";
    }

    @Override
    public String getBotToken() {
        return System.getenv("BOT_TOKEN");
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
        List<Series> seriesList = seriesService.getAll(chatId);
        if (seriesList.isEmpty()) {
            return "Ты пока не добавил ни одного сериала.";
        }
        StringBuilder sb = new StringBuilder("Твои сериалы:\n");
        for (Series s : seriesList) {
            String statusMark = s.getStatus().equals("finished") ? "🏁 " : "";
            sb.append("• ").append(statusMark)
                    .append(s.getName())
                    .append(" — Сезон ").append(s.getSeason())
                    .append(", Эпизод ").append(s.getEpisode())
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
                .text("✏\uFE0F Переименовать сериал")
                .callbackData("rename")
                .build());
        row.add(InlineKeyboardButton.builder()
                .text("❌ Удалить сериал")
                .callbackData("delete")
                .build());
        rows.add(row);
        row = new ArrayList<>();
        row.add(InlineKeyboardButton.builder()
                .text("📋 Статус")
                .callbackData("status")
                .build());
        row.add(InlineKeyboardButton.builder()
                .text("⚙ Установить прогресс")
                .callbackData("set")
                .build());
        rows.add(row);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup buildSeriesChoiceMenu(long chatId, String callbackPrefix, String icon) {
        List<Series> seriesList = seriesService.getAll(chatId);

        List<List<InlineKeyboardButton>> rows = seriesList.stream()
                .map(s -> {
                    boolean isFinished = "finished".equals(s.getStatus());
                    String prefix = isFinished ? "🏁 " : icon + " ";
                    return List.of(InlineKeyboardButton.builder()
                            .text(prefix + s.getName())
                            .callbackData(callbackPrefix + s.getName())
                            .build());
                })
                .collect(Collectors.toList());

        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();
            UserSession session = sessions.computeIfAbsent(chatId, id -> new UserSession());

            switch (session.state) {
                case AWAITING_ADD -> {
                    session.state = State.IDLE;
                    ParsedTitle parsed = parseTitleSeasonEpisode(text);
                    int season = parsed.season() >= 0 ? parsed.season() : 1;
                    int episode = parsed.episode() >= 0 ? parsed.episode() : 1;
                    seriesService.saveOrUpdate(chatId, parsed.title(), season, episode);
                    sendReply(chatId, "Добавлено: " + parsed.title() + " — Сезон " + season + ", Эпизод " + episode
                            + ". Что ещё хочешь сделать?", mainMenu);
                }
                case AWAITING_SET_SEASON -> {
                    try {
                        int season = Integer.parseInt(text);
                        session.manualSeason = season;
                        session.state = State.AWAITING_SET_EPISODE;
                        sendReply(chatId, "Теперь эпизод?", null);
                    } catch (NumberFormatException e) {
                        sendReply(chatId, "Сезон должен быть числом", null);
                    }
                }
                case AWAITING_SET_EPISODE -> {
                    try {
                        int episode = Integer.parseInt(text);
                        String title = session.selectedTitle;
                        int season = session.manualSeason;
                        seriesService.saveOrUpdate(chatId, title, season, episode);
                        session.state = State.IDLE;
                        session.selectedTitle = null;
                        session.manualSeason = null;
                        sendReply(chatId, "Установлено: " + title + " — Сезон " + season + ", Эпизод " + episode, mainMenu);
                    } catch (NumberFormatException e) {
                        sendReply(chatId, "Эпизод должен быть числом", null);
                    }
                }
                case AWAITING_RENAME_INPUT -> {
                    String oldTitle = session.selectedTitle;
                    String newTitle = text.trim();

                    if (newTitle.isEmpty()) {
                        sendReply(chatId, "Новое название не может быть пустым.", null);
                        return;
                    }

                    Series s = seriesService.getByName(chatId, oldTitle);
                    if (s != null) {
                        seriesService.delete(chatId, oldTitle);
                        seriesService.saveOrUpdate(chatId, newTitle, s.getSeason(), s.getEpisode());
                    }

                    session.state = State.IDLE;
                    session.selectedTitle = null;

                    sendReply(chatId, "Переименовано: \"" + oldTitle + "\" → \"" + newTitle + "\"", mainMenu);
                }
                default -> {
                    if (text.equals("/start")) {
                        sendReply(chatId, "Что хочешь сделать?", mainMenu);
                    } else {
                        sendReply(chatId, "Неизвестная команда. Выбери из меню", mainMenu);
                    }
                }
            }
        }

        //КНОПКИ
        if (update.hasCallbackQuery()) {
            System.out.println("CallbackQuery received: " + update.getCallbackQuery().getData());

            String data = update.getCallbackQuery().getData();
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            UserSession session = sessions.computeIfAbsent(chatId, id -> new UserSession());

            if (session.state == State.AWAITING_SET_CHOICE && data.startsWith("set_")) {
                String title = data.substring(4);
                session.selectedTitle = title;
                session.state = State.AWAITING_SET_ACTION;

                Series s = seriesService.getByName(chatId, title);

                if (s == null) {
                    sendReply(chatId, "Сериал не найден.", mainMenu);
                    session.state = State.IDLE;
                    return;
                }

                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                rows.add(List.of(
                                InlineKeyboardButton.builder().text("🎯 Вручную").callbackData("set_manual").build(),
                                InlineKeyboardButton.builder().text("🏁 Сериал закончен").callbackData("set_finish").build()
                         ));
                rows.add(List.of(
                                InlineKeyboardButton.builder().text("⏭ Следующий эпизод").callbackData("set_next_ep").build(),
                                InlineKeyboardButton.builder().text("📅 Следующий сезон").callbackData("set_next_season").build()
                        ));

                if ("finished".equalsIgnoreCase(s.getStatus())) {
                    rows.add(List.of(
                            InlineKeyboardButton.builder()
                                    .text("🎬 Начать заново")
                                    .callbackData("set_restart")
                                    .build()
                    ));
                }

                markup.setKeyboard(rows);

                sendReply(chatId, "Как хотите обновить ваш прогресс с \"" + title + "\"?", markup);
                return;
            }

            if (session.state == State.AWAITING_SET_ACTION) {
                String title = session.selectedTitle;
                Series s = seriesService.getByName(chatId, title);

                if (s != null) {
                    switch (data) {
                        case "set_manual" -> {
                            session.state = State.AWAITING_SET_SEASON;
                            sendReply(chatId, "Укажи сезон для \"" + title + "\":", null);
                            return;
                        }
                        case "set_next_ep" -> {
                            seriesService.saveOrUpdate(chatId, title, s.getSeason(), s.getEpisode() + 1);
                        }
                        case "set_next_season" -> {
                            seriesService.saveOrUpdate(chatId, title, s.getSeason() + 1, 1);
                        }
                        case "set_finish" -> {
                            seriesService.setStatus(chatId, title, "finished");
                            session.state = State.IDLE;
                            session.selectedTitle = null;
                            sendReply(chatId, "Вы закончили смотреть сериал \"" + title + "\" 🎉. Теперь он отмечен как завершённый. ", mainMenu);
                            return;
                        }
                        case "set_restart" -> {
                            seriesService.setStatus(chatId, title, "");
                            seriesService.saveOrUpdate(chatId, title, 1, 1);
                            session.state = State.IDLE;
                            session.selectedTitle = null;
                            sendReply(chatId, "Сериал \"" + title + "\" сброшен: снова Сезон 1, Эпизод 1.", mainMenu);
                            return;
                        }

                    }
                }

                session.state = State.IDLE;
                session.selectedTitle = null;
                sendReply(chatId, "Обновлено: " + title, mainMenu);
                return;
            }

            if (session.state == State.AWAITING_DELETE_CHOICE && data.startsWith("delete_")) {
                String title = data.substring("delete_".length());
                seriesService.delete(chatId, title);
                sendReply(chatId, "Сериал \"" + title + "\" удалён.", mainMenu);
                session.state = State.IDLE;
                return;
            }

            if (session.state == State.AWAITING_RENAME_CHOICE && data.startsWith("rename_")) {
                String oldTitle = data.substring(7);
                session.selectedTitle = oldTitle;
                session.state = State.AWAITING_RENAME_INPUT;
                sendReply(chatId, "Введи новое название для \"" + oldTitle + "\":", null);
                return;
            }

            System.out.println("Callback data: " + data);
            switch (data) {
                case "add" -> {
                    session.state = State.AWAITING_ADD;
                    sendReply(chatId, "Введи сериал (можно сразу с сезоном и эпизодом):", null);
                }
                case "set" -> {
                    List<Series> list = seriesService.getAll(chatId);
                    if (list.isEmpty()) {
                        sendReply(chatId, "У тебя пока нет сериалов.", mainMenu);
                        return;
                    }
                    session.state = State.AWAITING_SET_CHOICE;
                    sendReply(chatId, "Выбери сериал для обновления:",
                            buildSeriesChoiceMenu(chatId, "set_", "🎬"));
                }
                case "rename" -> {
                    List<Series> list = seriesService.getAll(chatId);
                    if (list.isEmpty()) {
                        sendReply(chatId, "У тебя пока нет сериалов.", mainMenu);
                        return;
                    }
                    session.state = State.AWAITING_RENAME_CHOICE;
                    sendReply(chatId, "Выбери сериал для переименования:",
                            buildSeriesChoiceMenu(chatId, "rename_", "✏\uFE0F"));
                }
                case "delete" -> {
                    List<Series> list = seriesService.getAll(chatId);
                    if (list.isEmpty()) {
                        sendReply(chatId, "У тебя пока нет сериалов.", mainMenu);
                        return;
                    }
                    session.state = State.AWAITING_DELETE_CHOICE;
                    sendReply(chatId, "Выбери сериал для удаления:",
                            buildSeriesChoiceMenu(chatId, "delete_", "❌"));
                }
                case "status" -> {
                    try {
                        sendReply(chatId, handleStatus(chatId), mainMenu);
                    } catch (Exception e) {
                        e.printStackTrace();
                        sendReply(chatId, "Произошла ошибка при выводе статуса.", mainMenu);
                    }
                }
                default -> {
                    System.out.println("Unhandled callback: " + data);
                    sendReply(chatId, "Неизвестная кнопка.", mainMenu);
                }
            }
        }
    }
}

record ParsedTitle(String title, Integer season, Integer episode) {}

