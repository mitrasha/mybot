package com.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import static org.telegram.telegrambots.bots.DefaultAbsSender.log;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class BinlangBot extends TelegramLongPollingBot {
    private static final Logger logger = LoggerFactory.getLogger(BinlangBot.class);
    private final String botUsername = "Statisticonator_3000_bot";
    private final String botToken = "8157772628:AAEyvqXUOd4QNisrMawq0_kF3D8f7x3Mq-s";
    private final Map<Long, ChannelStats> channels = new ConcurrentHashMap<>();
    private final Set<Long> admins = Set.of(7063149377L); // ADMIN_ID

    static class AuthorStats {
        private String username;
        private int postsCount;
        private int totalReactions;
        private Date lastActivity;

        public void addPost(int reactions) {
            postsCount++;
            totalReactions += reactions;
            lastActivity = new Date();
        }

        public String getUsername() { return username; }
        public int getPostsCount() { return postsCount; }
        public int getTotalReactions() { return totalReactions; }
        public Date getLastActivity() { return lastActivity; }
        public double getAverageReactions() { 
            return postsCount == 0 ? 0 : (double) totalReactions / postsCount; 
        }
        public void setUsername(String username) { this.username = username; }
    }

    static class ChannelStats {
        private final Map<Long, AuthorStats> authors = new ConcurrentHashMap<>();
        private final List<Post> posts = new ArrayList<>();

        public void addPost(Long authorId, String username, long messageId, Date date, int reactions) {
            authors.putIfAbsent(authorId, new AuthorStats());
            AuthorStats author = authors.get(authorId);
            author.setUsername(username);
            author.addPost(reactions);
            posts.add(new Post(authorId, messageId, date, reactions));
        }

        public Map<Long, AuthorStats> getAuthors() { return authors; }
        public List<Post> getPosts() { return posts; }
        public int getTotalReactions() { 
            return posts.stream().mapToInt(Post::getReactions).sum(); 
        }
        public double getAverageReactions() { 
            return posts.isEmpty() ? 0 : (double) getTotalReactions() / posts.size(); 
        }
        public List<Post> getTopPosts(int n) {
            return posts.stream()
                .sorted(Comparator.comparingInt(Post::getReactions).reversed())
                .limit(n)
                .collect(Collectors.toList());
        }
    }

    static class Post {
        private final long authorId;
        private final long messageId;
        private final Date postDate;
        private final int reactions;

        public Post(long authorId, long messageId, Date postDate, int reactions) {
            this.authorId = authorId;
            this.messageId = messageId;
            this.postDate = postDate;
            this.reactions = reactions;
        }

        public long getAuthorId() { return authorId; }
        public long getMessageId() { return messageId; }
        public Date getPostDate() { return postDate; }
        public int getReactions() { return reactions; }
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasChannelPost()) {
                processChannelPost(update.getChannelPost());
            } else if (update.hasMessage()) {
                processUserMessage(update.getMessage());
            }
        } catch (Exception e) {
            logger.error("Ошибка обработки обновления: {}", e.getMessage());
        }
    }

    private void processChannelPost(Message post) {
        long channelId = post.getChatId();
        User author = post.getFrom();
        
        channels.putIfAbsent(channelId, new ChannelStats());
        ChannelStats stats = channels.get(channelId);
        
        int reactions = post.getReplyMarkup() != null ? post.getReplyMarkup().getKeyboard().size() : 0;
        stats.addPost(author.getId(), author.getUserName(), post.getMessageId(), new Date(), reactions);
        log.info("Новый пост в канале {} от {}", channelId, author.getUserName());
    }

    private void processUserMessage(Message message) {
        if (!message.hasText()) return;
        
        long userId = message.getFrom().getId();
        String text = message.getText().toLowerCase();
        
        if (!admins.contains(userId)) {
            sendMessage(message.getChatId(), "⛔ Доступ запрещен");
            return;
        }

        try {
            if (text.startsWith("/stats")) handleStatsCommand(message);
            else if (text.startsWith("/topposts")) handleTopPostsCommand(message);
            else if (text.startsWith("/activity")) handleActivityCommand(message);
            else if (text.startsWith("/compare")) handleCompareCommand(message);
            else if (text.startsWith("/weekly")) handleWeeklyCommand(message);
            else if (text.startsWith("/author")) handleAuthorCommand(message);
            else if (text.equals("/help")) sendHelp(message.getChatId());
        } catch (Exception e) {
            log.error("Ошибка обработки команды: {}", e.getMessage());
            sendMessage(message.getChatId(), "❌ Ошибка выполнения команды");
        }
    }

    private void handleStatsCommand(Message message) {
        String[] parts = message.getText().split(" ");
        if (parts.length != 2) {
            sendMessage(message.getChatId(), "ℹ Использование: /stats [ID_канала]");
            return;
        }

        try {
            long channelId = Long.parseLong(parts[1]);
            ChannelStats stats = channels.get(channelId);
            
            if (stats == null) {
                sendMessage(message.getChatId(), "📭 Канал не найден");
                return;
            }

            StringBuilder report = new StringBuilder()
                .append("📊 Статистика канала ").append(channelId).append("\n\n")
                .append("👥 Топ авторов:\n");

            stats.getAuthors().values().stream()
                .sorted(Comparator.comparingInt(AuthorStats::getPostsCount).reversed())
                .limit(10)
                .forEach(author -> report.append(String.format(
                    "▫ %s: %d постов, %d реакций\n",
                    author.getUsername(),
                    author.getPostsCount(),
                    author.getTotalReactions()
                )));

            report.append("\n📌 Всего постов: ").append(stats.getPosts().size())
                  .append("\n👍 Всего реакций: ").append(stats.getTotalReactions())
                  .append("\n🌟 Среднее реакций: ").append(String.format("%.2f", stats.getAverageReactions()));

            sendMessage(message.getChatId(), report.toString());
        } catch (Exception e) {
            sendMessage(message.getChatId(), "❌ Ошибка формата");
        }
    }

    private void handleTopPostsCommand(Message message) {
        String[] parts = message.getText().split(" ");
        if (parts.length < 2) {
            sendMessage(message.getChatId(), "ℹ Использование: /topposts [ID_канала] [лимит=5]");
            return;
        }

        try {
            long channelId = Long.parseLong(parts[1]);
            int limit = parts.length > 2 ? Integer.parseInt(parts[2]) : 5;

            ChannelStats stats = channels.get(channelId);
            if (stats == null) {
                sendMessage(message.getChatId(), "📭 Канал не найден");
                return;
            }

            StringBuilder response = new StringBuilder();
            response.append("🔥 Топ-").append(limit).append(" постов:\n\n");
            
            stats.getTopPosts(limit).forEach(post -> {
                AuthorStats author = stats.getAuthors().get(post.getAuthorId());
                response.append(String.format("📌 Пост #%d\n", post.getMessageId()))
                       .append(String.format("👤 Автор: %s\n", author.getUsername()))
                       .append(String.format("🕒 %s\n", formatDate(post.getPostDate())))
                       .append(String.format("💡 Реакций: %d\n\n", post.getReactions()));
            });

            sendMessage(message.getChatId(), response.toString());
        } catch (Exception e) {
            sendMessage(message.getChatId(), "❌ Ошибка параметров");
        }
    }

    private void handleActivityCommand(Message message) {
        String[] parts = message.getText().split(" ");
        if (parts.length < 2) {
            sendMessage(message.getChatId(), "ℹ Использование: /activity [ID_канала] [часы=24]");
            return;
        }

        try {
            long channelId = Long.parseLong(parts[1]);
            int hours = parts.length > 2 ? Integer.parseInt(parts[2]) : 24;

            ChannelStats stats = channels.get(channelId);
            if (stats == null) {
                sendMessage(message.getChatId(), "📭 Канал не найден");
                return;
            }

            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.HOUR_OF_DAY, -hours);
            Date threshold = calendar.getTime();

            Map<Long, Integer> activity = new HashMap<>();
            stats.getPosts().stream()
                .filter(post -> post.getPostDate().after(threshold))
                .forEach(post -> activity.merge(post.getAuthorId(), 1, Integer::sum));

            StringBuilder response = new StringBuilder();
            response.append("⏳ Активность за последние ").append(hours).append(" ч:\n\n");
            
            if (activity.isEmpty()) {
                response.append("Нет активности");
            } else {
                activity.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                    .forEach(entry -> {
                        AuthorStats author = stats.getAuthors().get(entry.getKey());
                        response.append(String.format("▫ %s: %d постов\n", author.getUsername(), entry.getValue()));
                    });
            }

            sendMessage(message.getChatId(), response.toString());
        } catch (Exception e) {
            sendMessage(message.getChatId(), "❌ Ошибка параметров");
        }
    }

    private void handleCompareCommand(Message message) {
        String[] parts = message.getText().split(" ");
        if (parts.length != 3) {
            sendMessage(message.getChatId(), "ℹ Использование: /compare [ID1] [ID2]");
            return;
        }

        try {
            long channelId1 = Long.parseLong(parts[1]);
            long channelId2 = Long.parseLong(parts[2]);

            ChannelStats stats1 = channels.get(channelId1);
            ChannelStats stats2 = channels.get(channelId2);

            if (stats1 == null || stats2 == null) {
                sendMessage(message.getChatId(), "📭 Один из каналов не найден");
                return;
            }

            StringBuilder response = new StringBuilder();
            response.append("📊 Сравнение каналов:\n\n")
                    .append("📌 Посты: ")
                    .append(stats1.getPosts().size()).append(" vs ").append(stats2.getPosts().size()).append("\n")
                    .append("👍 Реакции: ")
                    .append(stats1.getTotalReactions()).append(" vs ").append(stats2.getTotalReactions()).append("\n")
                    .append("👥 Авторы: ")
                    .append(stats1.getAuthors().size()).append(" vs ").append(stats2.getAuthors().size());

            sendMessage(message.getChatId(), response.toString());
        } catch (Exception e) {
            sendMessage(message.getChatId(), "❌ Ошибка параметров");
        }
    }

    private void handleWeeklyCommand(Message message) {
        String[] parts = message.getText().split(" ");
        if (parts.length != 2) {
            sendMessage(message.getChatId(), "ℹ Использование: /weekly [ID_канала]");
            return;
        }

        try {
            long channelId = Long.parseLong(parts[1]);
            ChannelStats stats = channels.get(channelId);

            if (stats == null) {
                sendMessage(message.getChatId(), "📭 Канал не найден");
                return;
            }

            Map<Integer, Integer> hourlyActivity = new TreeMap<>();
            stats.getPosts().forEach(post -> {
                Calendar cal = Calendar.getInstance();
                cal.setTime(post.getPostDate());
                int hour = cal.get(Calendar.HOUR_OF_DAY);
                hourlyActivity.put(hour, hourlyActivity.getOrDefault(hour, 0) + 1);
            });

            StringBuilder response = new StringBuilder();
            response.append("📅 Активность по часам:\n\n");
            hourlyActivity.forEach((hour, count) -> 
                response.append(String.format("%02d:00 - %d постов\n", hour, count)));

            sendMessage(message.getChatId(), response.toString());
        } catch (Exception e) {
            sendMessage(message.getChatId(), "❌ Ошибка параметров");
        }
    }

    private void handleAuthorCommand(Message message) {
        String[] parts = message.getText().split(" ");
        if (parts.length != 3) {
            sendMessage(message.getChatId(), "ℹ Использование: /author [ID_канала] [username]");
            return;
        }

        try {
            long channelId = Long.parseLong(parts[1]);
            String username = parts[2];

            ChannelStats stats = channels.get(channelId);
            if (stats == null) {
                sendMessage(message.getChatId(), "📭 Канал не найден");
                return;
            }

            Optional<AuthorStats> author = stats.getAuthors().values().stream()
                .filter(a -> a.getUsername().equalsIgnoreCase(username))
                .findFirst();

            if (author.isEmpty()) {
                sendMessage(message.getChatId(), "👤 Автор не найден");
                return;
            }

            AuthorStats a = author.get();
            StringBuilder response = new StringBuilder();
            response.append("📈 Статистика автора ").append(username).append(":\n\n")
                    .append("📌 Постов: ").append(a.getPostsCount()).append("\n")
                    .append("👍 Реакций: ").append(a.getTotalReactions()).append("\n")
                    .append("🌟 Среднее: ").append(String.format("%.1f", a.getAverageReactions())).append("\n")
                    .append("⏱ Последний пост: ").append(formatDate(a.getLastActivity()));

            sendMessage(message.getChatId(), response.toString());
        } catch (Exception e) {
            sendMessage(message.getChatId(), "❌ Ошибка параметров");
        }
    }

    private String formatDate(Date date) {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm").format(date);
    }

    private void sendHelp(Long chatId) {
        String helpText = """
            📚 Доступные команды:
            /stats [ID] - Основная статистика
            /topposts [ID] [N] - Топ постов
            /activity [ID] [часы] - Активность авторов
            /compare [ID1] [ID2] - Сравнение каналов
            /weekly [ID] - Активность по часам
            /author [ID] [ник] - Статистика автора
            /help - Справка
            """;
        sendMessage(chatId, helpText);
    }

    private void sendMessage(Long chatId, String text) {
        try {
            execute(SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .build());
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки: {}", e.getMessage());
        }
    }

    
    public static void main(String[] args) {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new BinlangBot());
            logger.info("Бот успешно запущен!");
        } catch (TelegramApiException e) {
            logger.error("Ошибка запуска: {}", e.getMessage());
        }
    }
}