import java.nio.file.*;
import java.util.*;

/** Places (places.csv) + training data. Tier: 0 premium, 1 mid-range, 2 affordable. */
final class Data {
    static final Map<String, Map<String, Map<String, Integer>>> TREE = new LinkedHashMap<>(); // state -> city -> area -> tier
    static final Map<String, Integer> CITY = new HashMap<>();   // "state|city" -> tier
    static final Map<String, Integer> NAMES = new HashMap<>();  // lower-case place name -> tier

    static void loadPlaces() throws Exception {
        List<String> lines = Files.readAllLines(Paths.get("places.csv"));
        for (int i = 1; i < lines.size(); i++) {
            String[] s = lines.get(i).split(",", -1);
            if (s.length < 4) continue;
            String st = s[0], c = s[1], a = s[2];
            int t = Integer.parseInt(s[3].trim());
            if (!st.isEmpty()) {
                Map<String, Integer> areas = TREE.computeIfAbsent(st, k -> new LinkedHashMap<>())
                        .computeIfAbsent(c, k -> new LinkedHashMap<>());
                if (a.isEmpty()) CITY.put(st + "|" + c, t); else areas.put(a, t);
            }
            String n = (a.isEmpty() ? c : a).toLowerCase();
            if (!n.isEmpty()) NAMES.putIfAbsent(n, t);
        }
    }

    /** Returns {tier, known(1/0)}. */
    static int[] resolve(String st, String city, String area) {
        area = area == null ? "" : area.trim();
        if (!area.isEmpty()) {
            Map<String, Integer> m = TREE.getOrDefault(st, new HashMap<>()).getOrDefault(city, new HashMap<>());
            if (m.containsKey(area)) return new int[]{m.get(area), 1};
            String q = area.toLowerCase();
            Integer t = NAMES.get(q);
            if (t == null) {
                String best = null;
                for (String k : NAMES.keySet()) if (q.contains(k) && (best == null || k.length() > best.length())) best = k;
                if (best != null) t = NAMES.get(best);
            }
            if (t != null) return new int[]{t, 1};
        }
        if (!city.startsWith("Other")) return new int[]{CITY.getOrDefault(st + "|" + city, 2), 1};
        return new int[]{2, 0};
    }

    static String locationsJson() {
        StringBuilder b = new StringBuilder("{\"states\":[");
        boolean fs = true;
        for (var se : TREE.entrySet()) {
            if (!fs) b.append(','); fs = false;
            b.append("{\"name\":\"").append(se.getKey()).append("\",\"cities\":[");
            boolean fc = true;
            for (var ce : se.getValue().entrySet()) {
                if (!fc) b.append(','); fc = false;
                b.append("{\"name\":\"").append(ce.getKey()).append("\",\"areas\":[");
                boolean fa = true;
                for (var ae : ce.getValue().entrySet()) {
                    if (!fa) b.append(','); fa = false;
                    b.append("{\"n\":\"").append(ae.getKey()).append("\",\"t\":").append(ae.getValue()).append('}');
                }
                b.append("]}");
            }
            b.append("]}");
        }
        return b.append("]}").toString();
    }

    /** Feature vector: sqft, bedrooms, bathrooms, age, isMidTier, isAffordableTier */
    static double[] features(double sqft, double bed, double bath, double age, int tier) {
        return new double[]{sqft, bed, bath, age, tier == 1 ? 1 : 0, tier == 2 ? 1 : 0};
    }

    /** Optional data.csv header: sqft,bedrooms,bathrooms,age,tier,price (price in lakhs). Else synthetic sample data. */
    static List<double[]> load() throws Exception {
        List<double[]> rows = new ArrayList<>();
        Path p = Paths.get("data.csv");
        if (Files.exists(p)) {
            List<String> lines = Files.readAllLines(p);
            for (int i = 1; i < lines.size(); i++) {
                String[] s = lines.get(i).split(",");
                if (s.length < 6) continue;
                rows.add(append(features(Double.parseDouble(s[0]), Double.parseDouble(s[1]), Double.parseDouble(s[2]),
                        Double.parseDouble(s[3]), Integer.parseInt(s[4].trim())), Double.parseDouble(s[5])));
            }
            System.out.println("Loaded " + rows.size() + " rows from data.csv");
        } else {
            Random r = new Random(42);
            double[] shift = {45, 15, 0};
            for (int i = 0; i < 600; i++) {
                double a = 500 + r.nextInt(3000), b = 1 + r.nextInt(5), c = 1 + r.nextInt(4), d = r.nextInt(31);
                int t = r.nextInt(3);
                rows.add(append(features(a, b, c, d, t), 10 + shift[t] + 0.05 * a + 4 * b + 3 * c - 0.5 * d + r.nextGaussian() * 6));
            }
            System.out.println("No data.csv found - using synthetic sample data.");
        }
        return rows;
    }

    private static double[] append(double[] f, double y) {
        double[] r = Arrays.copyOf(f, f.length + 1);
        r[f.length] = y;
        return r;
    }
}
