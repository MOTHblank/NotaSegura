# Top 5 Missing Features for NotaSegura

Based on the goal of facilitating financial organization for elderly users and modernizing the app for 2026, here are the top 5 missing features that would add the most value:

## 1. Cloud Backup & Sync (Data Safety)
*   **Problem:** Currently, all data is stored locally. If the user loses their phone, they lose all receipts and payment records.
*   **Solution:** Integrate **Google Drive API** or **Firebase** to allow users to back up their database and receipt images. For elderly users, this should be "set and forget."

## 2. Advanced OCR for Payments (Automation)
*   **Problem:** While warranties have OCR for dates, payments still require manual typing of the title and amount.
*   **Solution:** Use ML Kit to scan **Boleto (Barcode/QR)** or utility bills (Light, Water, Internet). Extract the amount, due date, and company name automatically to minimize typing errors.

## 3. Recurring Payment Logic (Automation)
*   **Problem:** Users have to manually create a new payment every month for recurring bills.
*   **Solution:** Implement a logic where, upon marking a "Monthly" payment as paid, the app automatically generates the next month's entry.

## 4. Search, Filter & Statistics (Organization)
*   **Problem:** As the list of receipts grows over the years, finding a specific one becomes difficult.
*   **Solution:** 
    *   Add a **Search Bar**.
    *   Add **Filtering** (e.g., "Show only Electronics," "Show only unpaid bills").
    *   Add a **Monthly Spending Summary** (simple pie chart or total sum) to help with financial awareness.

## 5. Export for Insurance/Accounting (Utility)
*   **Problem:** If the user needs to provide proof of all assets for insurance or a tax audit, they can't easily get the data out of the app.
*   **Solution:** A "Generate Report" button that creates a **PDF or CSV file** with all receipts and warranty status, which can be sent via email or WhatsApp.

---
*Generated: May 2026*
