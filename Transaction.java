/*
 * Transaction.java
 * ----------------
 * A very simple "data class". One Transaction object = one row from our CSV file.
 *
 * There is no clever logic in here on purpose. It just holds the ten pieces of
 * information that describe a single DVLA transaction, plus "getters" (methods
 * that give the value back) so other classes can read them.
 *
 * Beginner note: in Java it is normal to keep the fields "private" (hidden) and
 * expose them through public getX() methods. This is called "encapsulation".
 */
public class Transaction {

    // --- The ten fields, matching the ten columns in the CSV ---
    private String transactionId;    // e.g. "TXN-100013"  (unique id / primary key)
    private String customerId;       // e.g. "CUST-4414"
    private String channel;          // web / phone / agent / post_office
    private String transactionType;  // vehicle_tax / sorn / change_of_keeper / ...
    private String vehicleType;      // car / motorcycle / hgv / bus / van
    private String vehicleReg;       // e.g. "UD73 BQO"
    private String transactionDate;  // ISO 8601 string, e.g. "2026-02-28T15:26:00Z"
    private double amount;           // fee paid, e.g. 531.95   (0.00 for free services)
    private String currency;         // always "GBP" in our data
    private String status;           // completed / pending / refunded / failed

    /*
     * Constructor: this runs when we write "new Transaction(...)".
     * It copies the ten values into the object's fields.
     */
    public Transaction(String transactionId, String customerId, String channel,
                       String transactionType, String vehicleType, String vehicleReg,
                       String transactionDate, double amount, String currency, String status) {
        this.transactionId = transactionId;
        this.customerId = customerId;
        this.channel = channel;
        this.transactionType = transactionType;
        this.vehicleType = vehicleType;
        this.vehicleReg = vehicleReg;
        this.transactionDate = transactionDate;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
    }

    // --- Getters: let other classes READ each value ---
    public String getTransactionId()   { return transactionId; }
    public String getCustomerId()      { return customerId; }
    public String getChannel()         { return channel; }
    public String getTransactionType() { return transactionType; }
    public String getVehicleType()     { return vehicleType; }
    public String getVehicleReg()      { return vehicleReg; }
    public String getTransactionDate() { return transactionDate; }
    public double getAmount()          { return amount; }
    public String getCurrency()        { return currency; }
    public String getStatus()          { return status; }

    // --- A few setters: let us CHANGE values when a client does an update (PUT) ---
    public void setCustomerId(String customerId)           { this.customerId = customerId; }
    public void setChannel(String channel)                 { this.channel = channel; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }
    public void setVehicleType(String vehicleType)         { this.vehicleType = vehicleType; }
    public void setVehicleReg(String vehicleReg)           { this.vehicleReg = vehicleReg; }
    public void setTransactionDate(String transactionDate) { this.transactionDate = transactionDate; }
    public void setAmount(double amount)                   { this.amount = amount; }
    public void setCurrency(String currency)               { this.currency = currency; }
    public void setStatus(String status)                   { this.status = status; }

    /*
     * toJson(): turn THIS transaction into a JSON object string, e.g.
     * {"transactionId":"TXN-100013", ... ,"amount":531.95, ...}
     *
     * We build the string by hand using the small Json helper class. This keeps
     * the project dependency-free (no external libraries), which is friendlier
     * for a beginner and matches the plain-Java style of the module examples.
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append(Json.pair("transactionId", transactionId)).append(",");
        sb.append(Json.pair("customerId", customerId)).append(",");
        sb.append(Json.pair("channel", channel)).append(",");
        sb.append(Json.pair("transactionType", transactionType)).append(",");
        sb.append(Json.pair("vehicleType", vehicleType)).append(",");
        sb.append(Json.pair("vehicleReg", vehicleReg)).append(",");
        sb.append(Json.pair("transactionDate", transactionDate)).append(",");
        sb.append(Json.pairNum("amount", amount)).append(",");
        sb.append(Json.pair("currency", currency)).append(",");
        sb.append(Json.pair("status", status));
        sb.append("}");
        return sb.toString();
    }
}
