import com.sun.net.httpserver.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Full-stack app: REST API + static frontend. Run: java -cp out Main [port] */
public class Main {
    static Model model;
    static final Path HIST = Paths.get("history.csv");

    public static void main(String[] args) throws Exception {
        Data.loadPlaces();
        model = new Model(Data.load());
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        HttpServer s = HttpServer.create(new InetSocketAddress(port), 0);
        s.createContext("/api/locations", ex -> json(ex, 200, Data.locationsJson()));
        s.createContext("/api/model", ex -> json(ex, 200, String.format(
                "{\"trainingRows\":%d,\"r2\":%.4f,\"rmse\":%.2f}", model.n, model.r2, model.rmse)));
        s.createContext("/api/history", ex -> json(ex, 200, historyJson()));
        s.createContext("/api/predict", Main::predict);
        s.createContext("/", Main::staticFile);
        s.start();
        System.out.printf("R2=%.3f RMSE=%.2f lakhs%nOpen http://localhost:%d%n", model.r2, model.rmse, port);
    }

    static void predict(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { json(ex, 405, "{\"error\":\"Use POST\"}"); return; }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        try {
            double sqft = num(body, "sqft", 200, 20000), bed = num(body, "bedrooms", 1, 10),
                   bath = num(body, "bathrooms", 1, 10), age = num(body, "age", 0, 100);
            String st = clean(str(body, "state")), city = clean(str(body, "city")), area = clean(str(body, "area"));
            if (!Data.TREE.containsKey(st)) throw new IllegalArgumentException("Unknown state");
            int[] r = Data.resolve(st, city, area);
            double p = Math.max(5, model.predict(Data.features(sqft, bed, bath, age, r[0])));
            String place = String.join(", ", Arrays.stream(new String[]{area, city.startsWith("Other") ? "" : city, st})
                    .filter(x -> !x.isEmpty()).toArray(String[]::new));
            addHistory(place, sqft, p);
            json(ex, 200, String.format("{\"place\":\"%s\",\"price\":%.2f,\"low\":%.2f,\"high\":%.2f,\"perSqft\":%.0f,\"known\":%b}",
                    place, p, Math.max(0, p - model.rmse), p + model.rmse, p * 100000 / sqft, r[1] == 1));
        } catch (Exception e) {
            json(ex, 400, "{\"error\":\"" + clean(String.valueOf(e.getMessage())) + "\"}");
        }
    }

    static String clean(String s) { return s.replaceAll("[\"\\\\\\r\\n,\\t]", " ").trim(); }

    static String str(String body, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
        if (!m.find()) throw new IllegalArgumentException("Missing field: " + key);
        return m.group(1);
    }

    static double num(String body, String key, double min, double max) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"?(-?[0-9.]+)\"?").matcher(body);
        if (!m.find()) throw new IllegalArgumentException("Missing field: " + key);
        double v = Double.parseDouble(m.group(1));
        if (v < min || v > max) throw new IllegalArgumentException(key + " must be between " + (int) min + " and " + (int) max);
        return v;
    }

    // ---- saved estimate history (history.csv, tab separated: time, place, sqft, price) ----
    static synchronized void addHistory(String place, double sqft, double price) throws IOException {
        Files.writeString(HIST, System.currentTimeMillis() / 1000 + "\t" + place + "\t" + (int) sqft + "\t"
                + String.format("%.2f", price) + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    static synchronized String historyJson() throws IOException {
        List<String> lines = Files.exists(HIST) ? Files.readAllLines(HIST) : new ArrayList<>();
        StringBuilder b = new StringBuilder("[");
        for (int i = lines.size() - 1, n = 0; i >= 0 && n < 8; i--) {
            String[] s = lines.get(i).split("\t");
            if (s.length < 4) continue;
            if (n++ > 0) b.append(',');
            b.append("{\"place\":\"").append(clean(s[1])).append("\",\"sqft\":").append(s[2]).append(",\"price\":").append(s[3]).append('}');
        }
        return b.append(']').toString();
    }

    static void json(HttpExchange ex, int code, String body) throws IOException {
        send(ex, code, "application/json; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    static void staticFile(HttpExchange ex) throws IOException {
        Path root = Paths.get("public").toAbsolutePath().normalize();
        String p = ex.getRequestURI().getPath();
        if (p.equals("/")) p = "/index.html";
        Path f = root.resolve(p.substring(1)).normalize();
        if (!f.startsWith(root) || !Files.isRegularFile(f)) { send(ex, 404, "text/plain", "Not found".getBytes()); return; }
        String n = f.getFileName().toString();
        String type = n.endsWith(".html") ? "text/html; charset=utf-8" : n.endsWith(".css") ? "text/css"
                : n.endsWith(".js") ? "application/javascript" : "application/octet-stream";
        send(ex, 200, type, Files.readAllBytes(f));
    }

    static void send(HttpExchange ex, int code, String type, byte[] data) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(code, data.length);
        try (var os = ex.getResponseBody()) { os.write(data); }
    }
}
