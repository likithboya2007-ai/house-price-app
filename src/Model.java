import java.util.*;

/** Multiple linear regression solved with the normal equation. */
final class Model {
    final double[] w;
    final double r2, rmse;
    final int n;

    Model(List<double[]> rows) {
        n = rows.size();
        int k = rows.get(0).length; // k-1 features + price; weights = bias + (k-1)
        double[][] A = new double[k][k + 1];
        for (double[] r : rows) {
            double[] x = new double[k];
            x[0] = 1;
            System.arraycopy(r, 0, x, 1, k - 1);
            for (int i = 0; i < k; i++) {
                for (int j = 0; j < k; j++) A[i][j] += x[i] * x[j];
                A[i][k] += x[i] * r[k - 1];
            }
        }
        for (int i = 1; i < k; i++) A[i][i] += 1e-6;
        for (int c = 0; c < k; c++) {
            int p = c;
            for (int r = c + 1; r < k; r++) if (Math.abs(A[r][c]) > Math.abs(A[p][c])) p = r;
            double[] t = A[c]; A[c] = A[p]; A[p] = t;
            for (int r = 0; r < k; r++) {
                if (r == c) continue;
                double f = A[r][c] / A[c][c];
                for (int j = c; j <= k; j++) A[r][j] -= f * A[c][j];
            }
        }
        w = new double[k];
        for (int i = 0; i < k; i++) w[i] = A[i][k] / A[i][i];

        double mean = 0, sse = 0, sst = 0;
        for (double[] r : rows) mean += r[k - 1] / n;
        for (double[] r : rows) {
            double e = r[k - 1] - predict(r);
            sse += e * e;
            sst += (r[k - 1] - mean) * (r[k - 1] - mean);
        }
        rmse = Math.sqrt(sse / n);
        r2 = 1 - sse / sst;
    }

    double predict(double[] f) {
        double s = w[0];
        for (int i = 0; i < w.length - 1; i++) s += w[i + 1] * f[i];
        return s;
    }
}
