import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import javax.mail.*;
import javax.mail.internet.*;
import javax.activation.*;
import java.io.IOException;
import yahoofinance.Stock;
import yahoofinance.YahooFinance;

public class StockSignalBot {

    // ======= CONFIGURATION =======
    private static final String[] TICKERS = {
            "HDFCBANK.NS", "^NSEI", "^NSEBANK", "ASIANPAINT.NS", "RELIANCE.NS"
    };

    // Database config
    private static final String DB_URL = "jdbc:mysql://localhost:3306/stockdb";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "root";

    // Email config
    private static final String EMAIL_SENDER = "nuggagamer2020@gmail.com";
    private static final String EMAIL_RECEIVER = "adityawghmare1903@gmail.com";
    private static final String EMAIL_PASSWORD = "fbze oopu bdao defv";
    private static final String SMTP_SERVER = "smtp.gmail.com";
    private static final int SMTP_PORT = 587;
    // ==============================


    public static void main(String[] args) {
        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                runBotForAllTickers();
            }
        };
        // Run immediately and repeat every 10 minutes
        timer.schedule(task, 0, 10 * 60 * 1000);
    }

    private static void runBotForAllTickers() {
        List<Map<String, Object>> allSignals = new ArrayList<>();

        for (String ticker : TICKERS) {
            try {
                Map<String, Object> stockData = getStockData(ticker);
                double[] emaValues = calculateEMA((List<Double>) stockData.get("closes"));
                String signal = generateTripleEMASignal(emaValues);
                double lastPrice = (double) stockData.get("lastPrice");

                System.out.println(ticker + "  Signal: " + signal);

                logSignalToDatabase(ticker, signal, lastPrice);
                Map<String, Object> map = new HashMap<>();
                map.put("ticker", ticker);
                map.put("signal", signal);
                map.put("price", lastPrice);
                allSignals.add(map);

            } catch (Exception e) {
                System.out.println("Error processing " + ticker + ": " + e.getMessage());
            }
        }

        sendEmailAlert(allSignals);
    }

    private static Map<String, Object> getStockData(String ticker) throws IOException {
        Stock stock = YahooFinance.get(ticker);
        if (stock == null || stock.getHistory() == null)
            throw new IOException("No data for " + ticker);

        List<Double> closes = new ArrayList<>();
        stock.getHistory().forEach(hist -> closes.add(hist.getClose().doubleValue()));
        double lastPrice = closes.get(closes.size() - 1);

        Map<String, Object> data = new HashMap<>();
        data.put("closes", closes);
        data.put("lastPrice", lastPrice);
        return data;
    }

    private static double[] calculateEMA(List<Double> prices) {
        double ema9 = ema(prices, 9);
        double ema21 = ema(prices, 21);
        double ema55 = ema(prices, 55);
        return new double[]{ema9, ema21, ema55};
    }

    private static double ema(List<Double> prices, int period) {
        double multiplier = 2.0 / (period + 1);
        double ema = prices.get(0);
        for (int i = 1; i < prices.size(); i++) {
            ema = ((prices.get(i) - ema) * multiplier) + ema;
        }
        return ema;
    }

    private static String generateTripleEMASignal(double[] ema) {
        double ema9 = ema[0], ema21 = ema[1], ema55 = ema[2];
        if (ema9 > ema21 && ema9 > ema55)
            return "BUY STOCK";
        else if (ema9 < ema21 && ema9 < ema55)
            return "SELL STOCK";
        else
            return "HOLD";
    }

    private static void logSignalToDatabase(String ticker, String signal, double price) {
        String query = "INSERT INTO stock_signals_log (timestamp, ticker, signal, price) VALUES (?, ?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement stmt = conn.prepareStatement(query)) {

            String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            stmt.setString(1, now);
            stmt.setString(2, ticker);
            stmt.setString(3, signal);
            stmt.setDouble(4, price);

            stmt.executeUpdate();
            System.out.println("Log saved to database: " + ticker);

        } catch (SQLException e) {
            System.out.println("Database log error: " + e.getMessage());
        }
    }

    private static void sendEmailAlert(List<Map<String, Object>> allSignals) {
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String subject = "[Stock Alert] EMA Signals Summary - " + now;

        StringBuilder body = new StringBuilder("EMA Signal Summary - " + now + "\n\n");
        for (Map<String, Object> stock : allSignals) {
            body.append(stock.get("ticker"))
                .append(" ------→ ")
                .append(stock.get("signal"))
                .append(" ====> ₹")
                .append(String.format("%.2f", stock.get("price")))
                .append("\n");
        }

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", SMTP_SERVER);
        props.put("mail.smtp.port", String.valueOf(SMTP_PORT));

        Session session = Session.getInstance(props, new javax.mail.Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(EMAIL_SENDER, EMAIL_PASSWORD);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(EMAIL_SENDER));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(EMAIL_RECEIVER));
            message.setSubject(subject);
            message.setText(body.toString());

            Transport.send(message);
            System.out.println("Summary email sent Brother!!.");

        } catch (MessagingException e) {
            System.out.println("Failed to send email: " + e.getMessage());
        }
    }
}
