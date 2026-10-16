import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/*
 * RequestHandler.java
 * -------------------
 * This class handles ONE client request. The main server starts a new
 * RequestHandler thread for every connection, exactly like the lecturer's
 * "HandleGet extends Thread" example. Here we extend it to:
 *   - read the request line (method + path), headers and body,
 *   - work out which operation the client wants,
 *   - ask the TransactionStore to do the work,
 *   - and write back a proper HTTP response with a JSON body.
 *
 * It is written step-by-step and heavily commented for a beginner.
 */
public class RequestHandler extends Thread {

    private final Socket socket;            // the connection to this one client
    private final TransactionStore store;   // shared data (thread-safe)
    private final String root;              // our URI root, e.g. "/2356784"

    public RequestHandler(Socket socket, TransactionStore store, String root) {
        this.socket = socket;
        this.store = store;
        this.root = root;
    }

    /*
     * run() is what the thread actually does. We wrap everything in try/catch
     * so one broken request can never crash the whole server.
     */
    @Override
    public void run() {
        try {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(socket.getOutputStream());

            // ---- 1. Read the FIRST line, e.g. "GET /2356784/transactions HTTP/1.1" ----
            String requestLine = in.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                socket.close();
                return;
            }
            System.out.println("Request: " + requestLine);

            String[] parts = requestLine.split(" ");
            String method = parts[0];          // GET / POST / PUT / DELETE
            String fullPath = parts.length > 1 ? parts[1] : "/";

            // ---- 2. Read the HEADERS (up to the blank line) ----
            // We mainly want Content-Length so we know how big the body is.
            int contentLength = 0;
            String headerLine;
            while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
                String lower = headerLine.toLowerCase();
                if (lower.startsWith("content-length:")) {
                    contentLength = Integer.parseInt(headerLine.substring(15).trim());
                }
            }

            // ---- 3. Read the BODY, if there is one (used by POST and PUT) ----
            String body = "";
            if (contentLength > 0) {
                char[] buffer = new char[contentLength];
                int read = in.read(buffer, 0, contentLength);
                body = new String(buffer, 0, Math.max(read, 0));
            }

            // ---- 4. Split the path into the part before "?" and the query string ----
            String path = fullPath;
            String query = "";
            int qmark = fullPath.indexOf('?');
            if (qmark >= 0) {
                path = fullPath.substring(0, qmark);
                query = fullPath.substring(qmark + 1);
            }
            Map<String, String> params = parseQuery(query);

            // ---- 5. Route the request to the right operation ----
            route(method, path, params, body, out);

            out.flush();
            socket.close();
        } catch (IOException e) {
            System.out.println("Problem handling request: " + e.getMessage());
        } catch (Exception e) {
            // Any other unexpected problem: we simply log it. The connection
            // may already be closed, so we do not try to write again here.
            System.out.println("Unexpected error: " + e.getMessage());
        }
    }

    /*
     * route(): decide what to do based on the HTTP method and the path.
     * The path still contains our root (e.g. "/2356784/transactions"), so we
     * first strip the root off to get the "resource" part ("/transactions").
     */
    private void route(String method, String path, Map<String, String> params,
                       String body, PrintWriter out) {

        // Remove the root. If the path does not start with our root, it is wrong.
        if (!path.startsWith(root)) {
            sendError(out, 404, "Not Found", "Unknown path. Try " + root + "/transactions");
            return;
        }
        String resource = path.substring(root.length()); // e.g. "/transactions/TXN-100013"
        if (resource.isEmpty()) {
            resource = "/";
        }

        // Break the resource into pieces, ignoring empty pieces.
        // "/transactions/TXN-100013" -> ["transactions", "TXN-100013"]
        String[] seg = splitPath(resource);

        // ---------- Collection: /transactions ----------
        if (seg.length == 1 && seg[0].equals("transactions")) {
            if (method.equals("GET"))  { listTransactions(params, out); return; }
            if (method.equals("POST")) { createTransaction(body, out);  return; }
            sendError(out, 405, "Method Not Allowed", "Use GET or POST on /transactions");
            return;
        }

        // ---------- Single item: /transactions/{id} ----------
        if (seg.length == 2 && seg[0].equals("transactions")) {
            String id = seg[1];
            if (method.equals("GET"))    { getOneTransaction(id, out);        return; }
            if (method.equals("PUT"))    { replaceTransaction(id, body, out); return; }
            if (method.equals("DELETE")) { deleteTransaction(id, out);        return; }
            sendError(out, 405, "Method Not Allowed", "Use GET, PUT or DELETE on /transactions/{id}");
            return;
        }

        // ---------- Status sub-resource: /transactions/{id}/status ----------
        if (seg.length == 3 && seg[0].equals("transactions") && seg[2].equals("status")) {
            if (method.equals("PUT")) { updateStatus(seg[1], body, out); return; }
            sendError(out, 405, "Method Not Allowed", "Use PUT on /transactions/{id}/status");
            return;
        }

        // ---------- Customer's transactions: /customers/{id}/transactions ----------
        if (seg.length == 3 && seg[0].equals("customers") && seg[2].equals("transactions")) {
            if (method.equals("GET")) { listCustomerTransactions(seg[1], out); return; }
            sendError(out, 405, "Method Not Allowed", "Use GET here");
            return;
        }

        // ---------- Customer cost total: /customers/{id}/transactions/cost ----------
        if (seg.length == 4 && seg[0].equals("customers")
                && seg[2].equals("transactions") && seg[3].equals("cost")) {
            if (method.equals("GET")) { customerCost(seg[1], params, out); return; }
            sendError(out, 405, "Method Not Allowed", "Use GET here");
            return;
        }

        // ---------- Stats: /stats/top-customers ----------
        if (seg.length == 2 && seg[0].equals("stats") && seg[1].equals("top-customers")) {
            if (method.equals("GET")) { topCustomers(params, out); return; }
            sendError(out, 405, "Method Not Allowed", "Use GET here");
            return;
        }

        // ---------- Stats: /stats/channels ----------
        if (seg.length == 2 && seg[0].equals("stats") && seg[1].equals("channels")) {
            if (method.equals("GET")) { channelStats(params, out); return; }
            sendError(out, 405, "Method Not Allowed", "Use GET here");
            return;
        }

        // Nothing matched.
        sendError(out, 404, "Not Found", "No endpoint matches " + path);
    }

    // =====================================================================
    //  One method per operation
    // =====================================================================

    // Operation 1: GET /transactions  (list + optional filters)
    private void listTransactions(Map<String, String> params, PrintWriter out) {
        ArrayList<Transaction> found = store.find(
                params.get("channel"), params.get("type"),
                params.get("status"), params.get("from"), params.get("to"));
        sendJson(out, 200, "OK", listToJson(found));
    }

    // Operation 2: GET /transactions/{id}
    private void getOneTransaction(String id, PrintWriter out) {
        Transaction t = store.getById(id);
        if (t == null) {
            sendError(out, 404, "Not Found", "No transaction with id " + id);
            return;
        }
        sendJson(out, 200, "OK", t.toJson());
    }

    // Operation 3: POST /transactions
    private void createTransaction(String body, PrintWriter out) {
        Transaction incoming = transactionFromJson(body);
        if (incoming == null) {
            sendError(out, 400, "Bad Request", "Body must be valid JSON with the required fields");
            return;
        }
        Transaction created = store.create(incoming);
        sendJson(out, 201, "Created", created.toJson());
    }

    // Operation 4: PUT /transactions/{id}  (full replace)
    private void replaceTransaction(String id, String body, PrintWriter out) {
        Transaction incoming = transactionFromJson(body);
        if (incoming == null) {
            sendError(out, 400, "Bad Request", "Body must be valid JSON with the required fields");
            return;
        }
        Transaction updated = store.update(id, incoming);
        if (updated == null) {
            sendError(out, 404, "Not Found", "No transaction with id " + id);
            return;
        }
        sendJson(out, 200, "OK", updated.toJson());
    }

    // Operation 5: PUT /transactions/{id}/status
    private void updateStatus(String id, String body, PrintWriter out) {
        String status = readJsonString(body, "status");
        if (status == null) {
            sendError(out, 400, "Bad Request", "Body must look like {\"status\":\"refunded\"}");
            return;
        }
        Transaction updated = store.updateStatus(id, status);
        if (updated == null) {
            sendError(out, 404, "Not Found", "No transaction with id " + id);
            return;
        }
        String json = "{" + Json.pair("transactionId", id) + "," + Json.pair("status", status) + "}";
        sendJson(out, 200, "OK", json);
    }

    // Operation 6: DELETE /transactions/{id}
    private void deleteTransaction(String id, PrintWriter out) {
        boolean removed = store.delete(id);
        if (!removed) {
            sendError(out, 404, "Not Found", "No transaction with id " + id);
            return;
        }
        // 204 means success with no body to return.
        sendNoContent(out);
    }

    // Operation 7: GET /customers/{id}/transactions/cost
    private void customerCost(String customerId, Map<String, String> params, PrintWriter out) {
        if (!store.customerExists(customerId)) {
            sendError(out, 404, "Not Found", "No customer with id " + customerId);
            return;
        }
        String from = params.get("from");
        String to = params.get("to");
        TransactionStore.CostResult r = store.costForCustomer(customerId, from, to);
        StringBuilder sb = new StringBuilder("{");
        sb.append(Json.pair("customerId", customerId)).append(",");
        sb.append(Json.pair("from", from == null ? "" : from)).append(",");
        sb.append(Json.pair("to", to == null ? "" : to)).append(",");
        sb.append(Json.pair("currency", "GBP")).append(",");
        sb.append(Json.pairRaw("transactionCount", String.valueOf(r.count))).append(",");
        sb.append(Json.pairNum("totalAmount", r.total));
        sb.append("}");
        sendJson(out, 200, "OK", sb.toString());
    }

    // Operation 8: GET /stats/top-customers
    private void topCustomers(Map<String, String> params, PrintWriter out) {
        int limit = 5;
        if (params.get("limit") != null) {
            try {
                limit = Integer.parseInt(params.get("limit"));
            } catch (NumberFormatException e) {
                sendError(out, 400, "Bad Request", "limit must be a whole number");
                return;
            }
        }
        if (limit <= 0) {
            sendError(out, 400, "Bad Request", "limit must be greater than 0");
            return;
        }
        String from = params.get("from");
        String to = params.get("to");
        LinkedHashMap<String, Double> top = store.topCustomersBySpend(limit, from, to);

        StringBuilder results = new StringBuilder("[");
        int rank = 1;
        boolean firstOne = true;
        for (Map.Entry<String, Double> e : top.entrySet()) {
            if (!firstOne) results.append(",");
            firstOne = false;
            results.append("{")
                   .append(Json.pairRaw("rank", String.valueOf(rank))).append(",")
                   .append(Json.pair("customerId", e.getKey())).append(",")
                   .append(Json.pairNum("totalAmount", e.getValue()))
                   .append("}");
            rank++;
        }
        results.append("]");

        StringBuilder sb = new StringBuilder("{");
        sb.append(Json.pair("metric", "spend")).append(",");
        sb.append(Json.pair("currency", "GBP")).append(",");
        sb.append(Json.pairRaw("results", results.toString()));
        sb.append("}");
        sendJson(out, 200, "OK", sb.toString());
    }

    // Operation 9: GET /stats/channels
    private void channelStats(Map<String, String> params, PrintWriter out) {
        String from = params.get("from");
        String to = params.get("to");
        LinkedHashMap<String, TransactionStore.CostResult> breakdown = store.channelBreakdown(from, to);

        StringBuilder arr = new StringBuilder("[");
        boolean firstOne = true;
        for (Map.Entry<String, TransactionStore.CostResult> e : breakdown.entrySet()) {
            if (!firstOne) arr.append(",");
            firstOne = false;
            arr.append("{")
               .append(Json.pair("channel", e.getKey())).append(",")
               .append(Json.pairRaw("transactionCount", String.valueOf(e.getValue().count))).append(",")
               .append(Json.pairNum("totalAmount", e.getValue().total))
               .append("}");
        }
        arr.append("]");

        String json = "{" + Json.pairRaw("channels", arr.toString()) + "}";
        sendJson(out, 200, "OK", json);
    }

    // Operation 10: GET /customers/{id}/transactions
    private void listCustomerTransactions(String customerId, PrintWriter out) {
        if (!store.customerExists(customerId)) {
            sendError(out, 404, "Not Found", "No customer with id " + customerId);
            return;
        }
        ArrayList<Transaction> list = store.findByCustomer(customerId);
        sendJson(out, 200, "OK", listToJson(list));
    }

    // =====================================================================
    //  Small helpers: building JSON, parsing input, writing HTTP responses
    // =====================================================================

    /* Wrap a list of transactions in a simple JSON envelope with a count. */
    private String listToJson(ArrayList<Transaction> list) {
        StringBuilder items = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) items.append(",");
            items.append(list.get(i).toJson());
        }
        items.append("]");
        return "{" + Json.pairRaw("totalItems", String.valueOf(list.size())) + ","
                   + Json.pairRaw("items", items.toString()) + "}";
    }

    /*
     * parseQuery(): turn "channel=web&from=2026-01-01" into a Map:
     *   channel -> web ,  from -> 2026-01-01
     * Values are URL-decoded so %20 becomes a space, etc.
     */
    private Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return map;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String key = urlDecode(pair.substring(0, eq));
                String value = urlDecode(pair.substring(eq + 1));
                map.put(key, value);
            }
        }
        return map;
    }

    private String urlDecode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    /* Split "/transactions/TXN-1/status" into {"transactions","TXN-1","status"}. */
    private String[] splitPath(String resource) {
        ArrayList<String> pieces = new ArrayList<>();
        for (String p : resource.split("/")) {
            if (!p.isEmpty()) {
                pieces.add(p);
            }
        }
        return pieces.toArray(new String[0]);
    }

    /*
     * A VERY small JSON reader. It is NOT a full parser; it just pulls out the
     * values we need from a flat JSON object like the client sends. This keeps
     * the project beginner-friendly and dependency-free. It assumes the body
     * contains simple "key":"value" or "key":number pairs with no nesting.
     */
    private String readJsonString(String json, String key) {
        if (json == null) return null;
        String needle = "\"" + key + "\"";
        int k = json.indexOf(needle);
        if (k < 0) return null;
        int colon = json.indexOf(':', k + needle.length());
        if (colon < 0) return null;
        int i = colon + 1;
        // skip spaces
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        if (i >= json.length()) return null;
        if (json.charAt(i) == '"') {           // a text value in quotes
            int end = json.indexOf('"', i + 1);
            if (end < 0) return null;
            return json.substring(i + 1, end);
        } else {                                // a number (read until , or })
            int end = i;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
            return json.substring(i, end).trim();
        }
    }

    /*
     * Build a Transaction from a client's JSON body (for POST and PUT).
     * Returns null if the essential fields are missing.
     */
    private Transaction transactionFromJson(String body) {
        String customerId = readJsonString(body, "customerId");
        String channel = readJsonString(body, "channel");
        String type = readJsonString(body, "transactionType");
        String vehicleType = readJsonString(body, "vehicleType");
        String vehicleReg = readJsonString(body, "vehicleReg");
        String date = readJsonString(body, "transactionDate");
        String amountText = readJsonString(body, "amount");
        String currency = readJsonString(body, "currency");
        String status = readJsonString(body, "status");

        // The bare minimum we insist on for a valid transaction.
        if (customerId == null || channel == null || type == null) {
            return null;
        }
        double amount = 0.0;
        if (amountText != null) {
            try {
                amount = Double.parseDouble(amountText);
            } catch (NumberFormatException e) {
                return null;   // amount was present but not a number -> invalid
            }
        }
        if (currency == null) currency = "GBP";
        if (status == null) status = "pending";

        // transactionId is deliberately ignored for create; the store sets it.
        return new Transaction("", customerId, channel, type, vehicleType,
                vehicleReg, date, amount, currency, status);
    }

    /* Send a success/data response: status line, headers, blank line, JSON body. */
    private void sendJson(PrintWriter out, int code, String reason, String json) {
        byte[] bodyBytes = json.getBytes(StandardCharsets.UTF_8);
        out.write("HTTP/1.1 " + code + " " + reason + "\r\n");
        out.write("Content-Type: application/json\r\n");
        out.write("Content-Length: " + bodyBytes.length + "\r\n");
        out.write("\r\n");
        out.write(json);
    }

    /* Send a 204 No Content response (used by DELETE). No body. */
    private void sendNoContent(PrintWriter out) {
        out.write("HTTP/1.1 204 No Content\r\n");
        out.write("Content-Length: 0\r\n");
        out.write("\r\n");
    }

    /* Send an error response with a small JSON body describing the problem. */
    private void sendError(PrintWriter out, int code, String reason, String message) {
        String json = "{" + Json.pairRaw("status", String.valueOf(code)) + ","
                          + Json.pair("error", reason) + ","
                          + Json.pair("message", message) + "}";
        sendJson(out, code, reason, json);
    }
}
