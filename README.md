# DVLA Transactions Web Service (Plain Java)

CSF2753 Web Service Development — implementation for student **2356784**.

This is a small RESTful web service written in **plain Java** using only the
standard library (`java.net.ServerSocket` + one thread per request). There is
**no Spring Boot, no Maven and no external libraries**, so it runs with nothing
but a JDK installed. It deliberately follows the style of the module's
`HttpServer` / `HandleGet` examples and is heavily commented for a beginner.

The service loads a CSV of DVLA-style vehicle transactions into memory and
exposes ten REST operations over HTTP, returning JSON.

---

## Files

| File | What it does |
|------|--------------|
| `WebServer.java` | Main class. Opens a `ServerSocket` on port **8181** and starts a new thread per connection (the accept loop). |
| `RequestHandler.java` | `extends Thread`. Handles one request: parses the HTTP method/path/body, routes to the right operation, writes the JSON response. |
| `TransactionStore.java` | Loads the CSV and holds all transactions. Thread-safe (`synchronized`) CRUD plus the aggregate methods (cost, top customers, channel stats). |
| `Transaction.java` | Simple data class for one transaction row, with a `toJson()` method. |
| `Json.java` | Tiny helper for building JSON strings by hand (no library needed). |
| `dvla_transactions.csv` | The dataset (25 sample rows). Loaded at start-up. |

---

## Requirements

- A **JDK** (Java Development Kit), version 8 or newer. Developed and tested on JDK 25.
- Check it is installed:
  ```
  java -version
  javac -version
  ```

---

## Build

From inside the `Implementation` folder:

```
javac *.java
```

This produces the `.class` files. There should be no errors or warnings.

---

## Run

Still inside the `Implementation` folder (so it can find the CSV):

```
java WebServer
```

You should see:

```
Loaded 25 transactions from dvla_transactions.csv
DVLA transactions service running.
Try: http://localhost:8181/2356784/transactions
```

Leave this window open — the server runs until you stop it with **Ctrl + C**.

---

## The endpoints

Root for everything: `http://localhost:8181/2356784`

| # | Operation | Method | Path |
|---|-----------|--------|------|
| 1 | List / filter transactions | `GET` | `/transactions?channel=web&type=vehicle_tax&status=pending&from=2026-01-01&to=2026-03-31` |
| 2 | Get one transaction | `GET` | `/transactions/{id}` |
| 3 | Create a transaction | `POST` | `/transactions` |
| 4 | Replace a transaction | `PUT` | `/transactions/{id}` |
| 5 | Update status only | `PUT` | `/transactions/{id}/status` |
| 6 | Delete a transaction | `DELETE` | `/transactions/{id}` |
| 7 | Customer cost over a date range | `GET` | `/customers/{id}/transactions/cost?from=...&to=...` |
| 8 | Top-N customers by spend | `GET` | `/stats/top-customers?limit=5&from=...&to=...` |
| 9 | Channel usage breakdown | `GET` | `/stats/channels?from=...&to=...` |
| 10 | A customer's transactions | `GET` | `/customers/{id}/transactions` |

The filter query parameters on operation 1 are all optional. Dates are compared
as ISO-8601 text (e.g. `2026-01-01` or `2026-01-01T00:00:00Z`).

---

## Test it

### In a browser (GET only)

Open:
```
http://localhost:8181/2356784/transactions
http://localhost:8181/2356784/transactions/TXN-100013
http://localhost:8181/2356784/stats/top-customers?limit=5
```

### With curl (all methods)

```bash
# 1. List everything
curl http://localhost:8181/2356784/transactions

# 1b. Filter
curl "http://localhost:8181/2356784/transactions?channel=web&type=vehicle_tax"

# 2. Get one
curl http://localhost:8181/2356784/transactions/TXN-100013

# 3. Create (POST) - the server invents the new id
curl -X POST http://localhost:8181/2356784/transactions ^
  -H "Content-Type: application/json" ^
  -d "{\"customerId\":\"CUST-4414\",\"channel\":\"web\",\"transactionType\":\"vehicle_tax\",\"vehicleType\":\"car\",\"vehicleReg\":\"AB12 CDE\",\"transactionDate\":\"2026-06-01T09:15:00Z\",\"amount\":180.00,\"currency\":\"GBP\",\"status\":\"pending\"}"

# 4. Replace (PUT)
curl -X PUT http://localhost:8181/2356784/transactions/TXN-100013 ^
  -H "Content-Type: application/json" ^
  -d "{\"customerId\":\"CUST-4414\",\"channel\":\"web\",\"transactionType\":\"vehicle_tax\",\"vehicleType\":\"motorcycle\",\"vehicleReg\":\"UD73 BQO\",\"transactionDate\":\"2026-02-28T15:26:00Z\",\"amount\":190.00,\"currency\":\"GBP\",\"status\":\"completed\"}"

# 5. Update status only (PUT)
curl -X PUT http://localhost:8181/2356784/transactions/TXN-100013/status ^
  -H "Content-Type: application/json" -d "{\"status\":\"refunded\"}"

# 6. Delete
curl -X DELETE http://localhost:8181/2356784/transactions/TXN-100000

# 7. Customer cost over a range
curl "http://localhost:8181/2356784/customers/CUST-4414/transactions/cost?from=2026-01-01&to=2026-12-31"

# 8. Top 5 customers by spend
curl "http://localhost:8181/2356784/stats/top-customers?limit=5"

# 9. Channel breakdown
curl "http://localhost:8181/2356784/stats/channels"

# 10. One customer's transactions
curl http://localhost:8181/2356784/customers/CUST-4414/transactions
```

> On Windows the `^` is the line-continuation character for `cmd`. In PowerShell
> use a backtick `` ` `` instead, or put the whole command on one line.

### With PowerShell

```powershell
Invoke-WebRequest "http://localhost:8181/2356784/transactions" -UseBasicParsing | Select-Object -ExpandProperty Content
```

---

## Status codes you will see

| Code | When |
|------|------|
| `200 OK` | A successful read or update that returns data. |
| `201 Created` | A new transaction was created (POST). |
| `204 No Content` | A successful delete (DELETE), no body returned. |
| `400 Bad Request` | The request body/params were invalid (e.g. bad JSON, bad `limit`). |
| `404 Not Found` | The transaction, customer or path does not exist. |
| `405 Method Not Allowed` | Right path, wrong HTTP method. |

---

## How concurrency is handled (coursework Task 5)

`WebServer` starts a **new thread for every request**, so many clients are
served at once. All of those threads share one `TransactionStore`. To stop two
threads from corrupting the data at the same time (a *race condition*), every
method in `TransactionStore` that touches the shared data is marked
`synchronized`, so Java runs only one at a time. This is the simplest safe
approach; the design document discusses finer-grained alternatives.

---

## Notes / simplifications (kept beginner-friendly on purpose)

- Data lives **in memory**. Changes (POST/PUT/DELETE) last only while the server
  is running; restarting reloads the original CSV.
- The CSV reader uses a simple comma split, which is fine because no value in the
  dataset contains a comma.
- The JSON reader in `RequestHandler` is a small helper, not a full JSON parser.
  It expects the simple flat request bodies shown above.
