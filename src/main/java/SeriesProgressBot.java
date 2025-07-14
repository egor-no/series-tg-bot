import data.Series;
import data.SeriesProgressService;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.*;
import java.util.stream.Collectors;

public class SeriesProgressBot extends TelegramLongPollingBot {

    private final SeriesProgressService seriesService;
    private final Map<Long, UserSession> sessions = new HashMap<>();
    private final InlineKeyboardMarkup mainMenu;
    private final InlineKeyboardMarkup emptyMenu;
    private final InlineKeyboardMarkup cancelMenu;

    public SeriesProgressBot(SeriesProgressService seriesService) {
        this.seriesService = seriesService;
        this.mainMenu = buildMainMenu();
        this.emptyMenu = buildEmptyMenu();
        this.cancelMenu = buildCancelMenu();
    }

    @Override
    public String getBotUsername() {
        return "SeriesProgressBot";
    }

    @Override
    public String getBotToken() {
        return System.getenv("BOT_TOKEN");
    }

    private InlineKeyboardMarkup buildMainMenu() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(InlineKeyboardButton.builder()
                .text("➕ Добавить сериал")
                .callbackData("add")
                .build());
        row.add(InlineKeyboardButton.builder()
                .text("❌ Удалить сериал")
                .callbackData("delete")
                .build());
        rows.add(row);
        row = new ArrayList<>();
        row.add(InlineKeyboardButton.builder()
                .text("✏\uFE0F Переименовать сериал")
                .callbackData("rename")
                .build());
        row.add(InlineKeyboardButton.builder()
                .text("⚙ Обновить прогресс")
                .callbackData("set")
                .build());
        rows.add(row);
        row = new ArrayList<>();
        row.add(InlineKeyboardButton.builder()
                .text("📋 Список моих сериалов")
                .callbackData("status")
                .build());
        rows.add(row);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup buildEmptyMenu() {
        InlineKeyboardButton addButton = InlineKeyboardButton.builder()
                .text("➕ Добавить сериал")
                .callbackData("add")
                .build();
        List<List<InlineKeyboardButton>> rows = List.of(List.of(addButton));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private InlineKeyboardMarkup buildCancelMenu() {
        InlineKeyboardButton cancelButton = InlineKeyboardButton.builder()
                .text("🔙 Отменить")
                .callbackData("cancel")
                .build();
        List<List<InlineKeyboardButton>> rows = List.of(List.of(cancelButton));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private InlineKeyboardMarkup getMenu(long chatId) {
        List<Series> list = seriesService.getAll(chatId);
        if (list.isEmpty()) {
            return emptyMenu;
        } else {
            return mainMenu;
        }
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

    private void deleteMessage(long chatId, int messageId) {
        try {
            DeleteMessage deleteMessage = new DeleteMessage();
            deleteMessage.setChatId(String.valueOf(chatId));
            deleteMessage.setMessageId(messageId);
            execute(deleteMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String handleStatus(long chatId) {
        List<Series> seriesList = seriesService.getAll(chatId);
        if (seriesList.isEmpty()) {
            return "Ты пока не добавил ни одного сериала.";
        }
        StringBuilder sb = new StringBuilder("Твои сериалы:\n");
        for (Series s : seriesList) {
            String statusMark = "finished".equalsIgnoreCase(s.getStatus()) ? "🏁 " : "";
            sb.append("• ").append(statusMark)
                    .append(s.getName())
                    .append(" — Сезон ").append(s.getSeason())
                    .append(", Эпизод ").append(s.getEpisode())
                    .append("\n");
        }
        return sb.toString();
    }

    private InlineKeyboardMarkup buildSeriesChoiceMenu(long chatId, String callbackPrefix, String icon) {
        List<Series> seriesList = seriesService.getAll(chatId);

        List<List<InlineKeyboardButton>> rows = seriesList.stream()
                .map(s -> {
                    boolean isFinished = "finished".equalsIgnoreCase(s.getStatus());
                    String prefix = isFinished ? "🏁 " : icon + " ";
                    return List.of(InlineKeyboardButton.builder()
                            .text(prefix + s.getName())
                            .callbackData(callbackPrefix + s.getName())
                            .build());
                })
                .collect(Collectors.toList());

        rows.add(List.of(
                InlineKeyboardButton.builder()
                        .text("🔙 Назад")
                        .callbackData("cancel")
                        .build()
        ));

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
                        sendReply(chatId, "Теперь эпизод?", cancelMenu);
                    } catch (NumberFormatException e) {
                        sendReply(chatId, "Сезон должен быть числом", cancelMenu);
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
                        sendReply(chatId, "Эпизод должен быть числом", cancelMenu);
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
                        sendReply(chatId,
                                """
                                        Привет! 👋 Я бот для отслеживания прогресса по сериалам.\s
                                        Теперь ты не забудешь, на какой серии остановился, даже если не смотрел сериал уже год.\s
                                       \s
                                        📌 Что ты можешь сделать:
                                        • Добавлять сериалы и устанавливать их прогресс
                                        • Помечать, если завершил смотреть сериал
                                        • Смотреть список всех своих сериалов
                                       \s
                                        Бот ещё в бета-версии, поэтому не обижайтесь на ошибки.\s
                                       \s
                                        Выбирай действие из меню ниже 👇""", getMenu(chatId));
                    } else {
                        sendReply(chatId, "Неизвестная команда. Выбери из меню", getMenu(chatId));
                    }
                }
            }
        }

        //КНОПКИ
        if (update.hasCallbackQuery()) {
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
                rows.add(List.of(
                        InlineKeyboardButton.builder().text("🔙 Назад").callbackData("cancel").build()
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

                int messageId = update.getCallbackQuery().getMessage().getMessageId();
                deleteMessage(chatId, messageId);

                sendReply(chatId, "Как хотите обновить ваш прогресс с \"" + title + "\"?", markup);
                return;
            }

            if (session.state == State.AWAITING_SET_ACTION) {
                String title = session.selectedTitle;
                Series s = seriesService.getByName(chatId, title);

                if (s != null) {
                    int messageId = update.getCallbackQuery().getMessage().getMessageId();
                    deleteMessage(chatId, messageId);
                    switch (data) {
                        case "set_manual" -> {
                            session.state = State.AWAITING_SET_SEASON;
                            sendReply(chatId, "Укажи сезон для \"" + title + "\":", cancelMenu);
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

                Series updated = seriesService.getByName(chatId, title);
                if (updated != null) {
                    sendReply(chatId, "Обновлено: " + title + " — Сезон " + updated.getSeason() + ", Эпизод " + updated.getEpisode(), mainMenu);
                } else {
                    sendReply(chatId, "Обновление завершено, но сериал не найден.", mainMenu);
                }
                return;
            }

            if (session.state == State.AWAITING_DELETE_CHOICE && data.startsWith("delete_")) {
                String title = data.substring("delete_".length());
                seriesService.delete(chatId, title);
                int messageId = update.getCallbackQuery().getMessage().getMessageId();
                deleteMessage(chatId, messageId);

                InlineKeyboardMarkup menu = getMenu(chatId);
                if (menu == emptyMenu) {
                    sendReply(chatId, "Сериал \"" + title + "\" удалён. Вы удалили все сериалы из своего списка. ", menu);
                } else {
                    sendReply(chatId, "Сериал \"" + title + "\" удалён.", menu);
                }
                session.state = State.IDLE;
                return;
            }

            if (session.state == State.AWAITING_RENAME_CHOICE && data.startsWith("rename_")) {
                String oldTitle = data.substring(7);
                session.selectedTitle = oldTitle;
                session.state = State.AWAITING_RENAME_INPUT;
                int messageId = update.getCallbackQuery().getMessage().getMessageId();
                deleteMessage(chatId, messageId);
                sendReply(chatId, "Введи новое название для \"" + oldTitle + "\":", null);
                return;
            }

            int messageId = update.getCallbackQuery().getMessage().getMessageId();
            deleteMessage(chatId, messageId);
            switch (data) {
                case "add" -> {
                    sendReply(chatId, "Введи название сериала (можно сразу с сезоном и эпизодом — задай два числа через пробел, без запятых):", cancelMenu);
                }
                case "set" -> {
                    session.state = State.AWAITING_SET_CHOICE;
                    sendReply(chatId, "Выбери сериал для обновления:",
                            buildSeriesChoiceMenu(chatId, "set_", "🎬"));
                }
                case "rename" -> {
                    session.state = State.AWAITING_RENAME_CHOICE;
                    sendReply(chatId, "Выбери сериал для переименования:",
                            buildSeriesChoiceMenu(chatId, "rename_", "✏\uFE0F"));
                }
                case "delete" -> {
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
                case "cancel" -> {
                    switch (session.state) {
                        case AWAITING_ADD ->  sendReply(chatId, "❌ Добавление отменено.", null);
                        case AWAITING_SET_CHOICE, AWAITING_SET_ACTION, AWAITING_SET_SEASON, AWAITING_SET_EPISODE -> sendReply(chatId, "❌ Обновление прогресса отменено.", null);
                        default -> {}
                    };

                    session.state = State.IDLE;
                    session.selectedTitle = null;
                    session.manualSeason = null;

                    sendReply(chatId, "Окей, возвращаемся в главное меню.", mainMenu);
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

