import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/*
 * TransactionStore.java
 * ---------------------
 * This class is the "memory" of our web service. It:
 *   1. Loads the CSV file once when the server starts.
 *   2. Keeps all the transactions in a Map (id -> Transaction).
 *   3. Offers simple methods to Create, Read, Update and Delete (CRUD).
 *   4. Offers a few "aggregate" methods (totals, top customers, channel stats).
 *
 * WHY "synchronized"?  (this is the Concurrency part of the coursework)
 * Our web server starts a NEW thread for every incoming request. That means
 * several threads could touch this shared data AT THE SAME TIME. If one thread
 * is adding a transaction while another is reading them all, we could get a
 * "race condition" (muddled or half-updated data).
 *
 * To keep things SIMPLE and SAFE for a beginner, every method that touches the
 * shared Map is marked "synchronized". That means Java lets only ONE thread run
 * any of these methods at a time, so the data can never be left half-changed.
 * (A more advanced solution would allow many readers at once, but we keep it
 * simple here and explain the trade-off in the design document.)
 */
public class TransactionStore {

    // The shared data. LinkedHashMap keeps rows in the order we inserted them.
    // Key = transactionId (e.g. "TXN-100013"), Value = the Transaction object.
    private final Map<String, Transaction> transactions = new LinkedHashMap<>();

    // Used to invent a new unique id when a client POSTs a new transaction.
    private int nextIdNumber = 100000;

    /*
     * Constructor: load the CSV file straight away when the store is created.
     */
    public TransactionStore(String csvFilePath) {
        loadFromCsv(csvFilePath);
    }

    /*
     * loadFromCsv(): read the file line by line and build Transaction objects.
     * The first line is the header row, so we skip it.
     */
    private synchronized void loadFromCsv(String csvFilePath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(csvFilePath))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) {        // skip the header row
                    firstLine = false;
                    continue;
                }
                if (line.trim().isEmpty()) {
                    continue;           // skip any blank lines
                }

                // Split the line on commas. Our data has no commas inside values,
                // so this simple split is enough for a beginner project.
                String[] c = line.split(",");

                // Turn the "amount" text into a number. If it is broken, use 0.
                double amount;
                try {
                    amount = Double.parseDouble(c[7]);
                } catch (NumberFormatException e) {
                    amount = 0.0;
                }

                Transaction t = new Transaction(
                        c[0], c[1], c[2], c[3], c[4], c[5], c[6], amount, c[8], c[9]);
                transactions.put(t.getTransactionId(), t);

                // Keep nextIdNumber ahead of any id we have already seen,
                // so new ids never clash with existing ones.
                try {
                    int num = Integer.parseInt(c[0].replace("TXN-", ""));
                    if (num >= nextIdNumber) {
                        nextIdNumber = num + 1;
                    }
                } catch (NumberFormatException ignored) {
                    // id was not in the TXN-number form; just ignore it
                }
            }
            System.out.println("Loaded " + transactions.size() + " transactions from " + csvFilePath);
        } catch (IOException e) {
            System.out.println("Could not read CSV file: " + e.getMessage());
        }
    }

    // =====================================================================
    //  READ operations
    // =====================================================================

    /* Get one transaction by its id. Returns null if it does not exist. */
    public synchronized Transaction getById(String id) {
        return transactions.get(id);
    }

    /*
     * Find transactions matching optional filters. Pass null for a filter you
     * do not want to use. "from" and "to" are date strings (ISO 8601); because
     * ISO dates sort the same as text, we can compare them with simple string
     * comparison.
     */
    public synchronized ArrayList<Transaction> find(String channel, String type,
                                                     String status, String from, String to) {
        ArrayList<Transaction> results = new ArrayList<>();
        for (Transaction t : transactions.values()) {
            if (channel != null && !channel.equals(t.getChannel()))      continue;
            if (type    != null && !type.equals(t.getTransactionType())) continue;
            if (status  != null && !status.equals(t.getStatus()))        continue;
            if (from    != null && t.getTransactionDate().compareTo(from) < 0) continue;
            if (to      != null && t.getTransactionDate().compareTo(to)  > 0) continue;
            results.add(t);
        }
        return results;
    }

    /* All transactions for one customer. */
    public synchronized ArrayList<Transaction> findByCustomer(String customerId) {
        ArrayList<Transaction> results = new ArrayList<>();
        for (Transaction t : transactions.values()) {
            if (t.getCustomerId().equals(customerId)) {
                results.add(t);
            }
        }
        return results;
    }

    /* Does this customer appear anywhere in the data? */
    public synchronized boolean customerExists(String customerId) {
        for (Transaction t : transactions.values()) {
            if (t.getCustomerId().equals(customerId)) {
                return true;
            }
        }
        return false;
    }

    // =====================================================================
    //  CREATE / UPDATE / DELETE operations
    // =====================================================================

    /*
     * Add a brand new transaction. The SERVER invents the id (not the client),
     * which matches using POST for creation. Returns the created Transaction.
     */
    public synchronized Transaction create(Transaction t) {
        String newId = "TXN-" + nextIdNumber;
        nextIdNumber++;
        Transaction created = new Transaction(
                newId, t.getCustomerId(), t.getChannel(), t.getTransactionType(),
                t.getVehicleType(), t.getVehicleReg(), t.getTransactionDate(),
                t.getAmount(), t.getCurrency(), t.getStatus());
        transactions.put(newId, created);
        return created;
    }

    /*
     * Replace an existing transaction's details (PUT). Returns the updated
     * object, or null if the id does not exist.
     */
    public synchronized Transaction update(String id, Transaction newData) {
        Transaction existing = transactions.get(id);
        if (existing == null) {
            return null;
        }
        existing.setCustomerId(newData.getCustomerId());
        existing.setChannel(newData.getChannel());
        existing.setTransactionType(newData.getTransactionType());
        existing.setVehicleType(newData.getVehicleType());
        existing.setVehicleReg(newData.getVehicleReg());
        existing.setTransactionDate(newData.getTransactionDate());
        existing.setAmount(newData.getAmount());
        existing.setCurrency(newData.getCurrency());
        existing.setStatus(newData.getStatus());
        return existing;
    }

    /* Update only the status field. Returns the object, or null if not found. */
    public synchronized Transaction updateStatus(String id, String newStatus) {
        Transaction existing = transactions.get(id);
        if (existing == null) {
            return null;
        }
        existing.setStatus(newStatus);
        return existing;
    }

    /* Delete a transaction. Returns true if something was removed. */
    public synchronized boolean delete(String id) {
        return transactions.remove(id) != null;
    }

    // =====================================================================
    //  AGGREGATE operations (the "complex returns")
    // =====================================================================

    /*
     * Total amount a single customer spent between two dates (inclusive).
     * Returns a small result object holding the count and the total.
     */
    public synchronized CostResult costForCustomer(String customerId, String from, String to) {
        double total = 0.0;
        int count = 0;
        for (Transaction t : transactions.values()) {
            if (!t.getCustomerId().equals(customerId)) continue;
            if (from != null && t.getTransactionDate().compareTo(from) < 0) continue;
            if (to   != null && t.getTransactionDate().compareTo(to)  > 0) continue;
            total += t.getAmount();
            count++;
        }
        return new CostResult(count, total);
    }

    /*
     * Top N customers by total spend over a date range.
     * Returns an ordered map: customerId -> total, highest first.
     */
    public synchronized LinkedHashMap<String, Double> topCustomersBySpend(int limit, String from, String to) {
        // Step 1: add up each customer's spend.
        Map<String, Double> totals = new LinkedHashMap<>();
        for (Transaction t : transactions.values()) {
            if (from != null && t.getTransactionDate().compareTo(from) < 0) continue;
            if (to   != null && t.getTransactionDate().compareTo(to)  > 0) continue;
            String cust = t.getCustomerId();
            double current = totals.getOrDefault(cust, 0.0);
            totals.put(cust, current + t.getAmount());
        }

        // Step 2: simple selection sort to pick the top "limit" customers.
        LinkedHashMap<String, Double> top = new LinkedHashMap<>();
        for (int picked = 0; picked < limit && !totals.isEmpty(); picked++) {
            String bestCust = null;
            double bestVal = -1.0;
            for (Map.Entry<String, Double> e : totals.entrySet()) {
                if (e.getValue() > bestVal) {
                    bestVal = e.getValue();
                    bestCust = e.getKey();
                }
            }
            top.put(bestCust, bestVal);
            totals.remove(bestCust);
        }
        return top;
    }

    /*
     * Count and total amount grouped by channel over a date range.
     * Returns channel -> {count, total}.
     */
    public synchronized LinkedHashMap<String, CostResult> channelBreakdown(String from, String to) {
        LinkedHashMap<String, CostResult> result = new LinkedHashMap<>();
        for (Transaction t : transactions.values()) {
            if (from != null && t.getTransactionDate().compareTo(from) < 0) continue;
            if (to   != null && t.getTransactionDate().compareTo(to)  > 0) continue;
            String ch = t.getChannel();
            CostResult current = result.getOrDefault(ch, new CostResult(0, 0.0));
            result.put(ch, new CostResult(current.count + 1, current.total + t.getAmount()));
        }
        return result;
    }

    /*
     * A tiny helper class to carry a count and a total back together.
     * (Nested inside TransactionStore to keep the project small.)
     */
    public static class CostResult {
        public final int count;
        public final double total;
        public CostResult(int count, double total) {
            this.count = count;
            this.total = total;
        }
    }
}
