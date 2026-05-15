# NotaSegura Roadmap: Receipts & Payment Reminders (2026)

## Background & Motivation
The project currently supports tracking warranty expirations using a simple entity model. To modernize the app for 2026 and meet new user requirements, we are expanding it to handle physical/digital receipt storage and payment reminders. The user has opted for a **Separate Entities** approach to keep the logic for receipts/warranties distinct from payment tracking.

## Scope & Impact
*   **Database:** Room schema will be updated. Existing `WarrantyItem` will be extended, and a new `Payment` entity will be introduced. Migration logic is required.
*   **Storage:** Images (receipts) captured via camera or gallery will be copied to internal app storage for persistence.
*   **UI:** Navigation and screens will be updated to accommodate two primary domains: Receipts/Warranties and Payments.
*   **Background Tasks:** The existing WorkManager implementation will be expanded to notify users of upcoming payments.

## Proposed Solution

### 1. Documentation
*   Create a `docs/` folder in the project root.
*   Save this roadmap as `docs/ROADMAP.md` for future reference.

### 2. Data Layer Changes
*   **Receipt Handling:**
    *   Rename `WarrantyItem` to `Receipt` (optional, but semantically better) or simply add an `imagePath: String?` field to the existing `WarrantyItem` to store the local file path of the saved receipt image.
*   **Payment Reminders:**
    *   Create a new `@Entity` named `Payment` containing: `id`, `title`, `amount` (Double), `dueDate` (LocalDate), `isPaid` (Boolean), and optionally `isRecurring` (Boolean).
    *   Create a `PaymentDao`.
*   **Database Update:**
    *   Update `AppDatabase` version from `1` to `2`.
    *   Write a Room Migration to preserve existing data.

### 3. Storage Layer
*   Implement a helper (e.g., `FileStorageManager`) to securely copy `Uri` contents from the `ActivityResultContracts` (Camera/Gallery) into `context.filesDir` to ensure the app retains access to the image even if the original is deleted or permission is lost.

### 4. UI Layer Changes
*   **Navigation:** Introduce a `BottomNavigationBar` or `TabRow` on the main screen to toggle between the "Receipts" timeline and the "Payments" timeline.
*   **Receipts UI:** Update `WarrantyItemCard` to display a small thumbnail of the `imagePath` using a library like Coil. Update `AddEditItemScreen` to show the persisted image.
*   **Payments UI:** Create `PaymentsScreen` and `AddEditPaymentScreen`.
*   **Dependencies:** Add [Coil for Compose](https://coil-kt.github.io/coil/compose/) for image loading.

### 5. Background Work
*   Modify `ExpirationCheckWorker` (or create a new `PaymentReminderWorker`) to query the `PaymentDao` for unpaid items due in the next few days and trigger notifications.

## Implementation Steps
1.  **Phase 1: Project Structure & Storage**
    *   Create `docs/ROADMAP.md`.
    *   Implement image saving utility to internal storage.
    *   Add Coil dependency.
2.  **Phase 2: Data Model & Migrations**
    *   Create `Payment` entity and DAO.
    *   Update `WarrantyItem` with `imagePath`.
    *   Increment DB version and implement Migration.
3.  **Phase 3: UI Implementation**
    *   Build the Payments timeline and Add/Edit screens.
    *   Implement main screen navigation (Tabs/BottomNav).
    *   Update Receipt screens to display images.
4.  **Phase 4: Notifications**
    *   Update WorkManager logic to include payment alerts.

## Verification
*   Test Room migration from v1 to v2 to ensure existing warranties are not lost.
*   Verify images taken via camera or gallery remain visible after app restarts.
*   Verify payment notifications trigger at the correct intervals.
