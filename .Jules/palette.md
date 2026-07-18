## 2024-07-17 - Missing/Static Content Descriptions in Compose IconButtons
**Learning:** Found that `IconButton` components in Jetpack Compose, like clear search buttons, often lack proper `contentDescription`s (passing `null`), hindering screen reader accessibility. Additionally, toggle buttons (like payment status) sometimes use static descriptions (e.g., "Marcar como pago") regardless of their state, which misleads users when the state is active.
**Action:** Always provide meaningful, localized strings for `contentDescription` in interactive `Icon`s. For toggleable states, ensure the description dynamically reflects the action that will occur if pressed (e.g., `if (state) "Desmarcar" else "Marcar"`).

## 2024-07-17 - Accessible Date Picker Overlays
**Learning:** Using `Spacer` with a `.clickable` modifier to create touch overlays (like for opening date pickers over text fields) results in unlabelled, generic buttons for screen reader users. The screen reader only announces "Button" and the user doesn't know its intent.
**Action:** When using `Spacer` (or any non-text/icon component) as an interactive overlay, explicitly set `onClickLabel` and `role = Role.Button` in the `.clickable` modifier so that the screen reader correctly announces the action (e.g. "Selecionar data de vencimento, Button").
