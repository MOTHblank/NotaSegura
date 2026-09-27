## 2024-07-17 - Missing/Static Content Descriptions in Compose IconButtons
**Learning:** Found that `IconButton` components in Jetpack Compose, like clear search buttons, often lack proper `contentDescription`s (passing `null`), hindering screen reader accessibility. Additionally, toggle buttons (like payment status) sometimes use static descriptions (e.g., "Marcar como pago") regardless of their state, which misleads users when the state is active.
**Action:** Always provide meaningful, localized strings for `contentDescription` in interactive `Icon`s. For toggleable states, ensure the description dynamically reflects the action that will occur if pressed (e.g., `if (state) "Desmarcar" else "Marcar"`).

## 2024-07-17 - Accessible Date Picker Overlays
**Learning:** Using `Spacer` with a `.clickable` modifier to create touch overlays (like for opening date pickers over text fields) results in unlabelled, generic buttons for screen reader users. The screen reader only announces "Button" and the user doesn't know its intent.
**Action:** When using `Spacer` (or any non-text/icon component) as an interactive overlay, explicitly set `onClickLabel` and `role = Role.Button` in the `.clickable` modifier so that the screen reader correctly announces the action (e.g. "Selecionar data de vencimento, Button").

## 2024-07-17 - Accessible Interactive Rows in Compose
**Learning:** When building list items or rows that contain both text and a `Checkbox`, placing `clickable` on the `Row` and `onCheckedChange` on the `Checkbox` creates confusing, nested touch targets for screen readers. Users might hear redundant actions or generic "Checkbox" labels.
**Action:** To make the entire row a single, accessible touch target, set the `Checkbox`'s `onCheckedChange` to `null`. Apply `.clickable(role = Role.Checkbox, onClick = { ... }, onClickLabel = "Alternar [estado]")` to the parent `Row` instead.

## 2024-07-17 - Keyboard Options for Form Usability
**Learning:** For older users (or any user completing forms on mobile devices), navigating between fields using the soft keyboard's actions (e.g., "Next" to go to the next field, "Done" to finish) is significantly faster than tapping the screen to select each field. Similarly, text inputs for names and categories should have automatic word capitalization to reduce typing effort.
**Action:** Always provide `keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)` for sequential form text fields, and `ImeAction.Done` for the last field.

## 2024-07-17 - Context-Aware Empty States
**Learning:** Generic empty states like "No items found" are confusing when a user is actively searching or filtering, as it implies the entire database is empty rather than just the current search results. Users need to know whether the list is truly empty or if their filter criteria just yielded no results.
**Action:** Always make empty state messages context-aware. If a search query or filter is active, display "No results found". If the list is truly empty, display the standard empty message along with a helpful call-to-action (e.g., "Tap 'New Item' to start").

## 2024-05-18 - Improved Contrast and Status Clarity for Elderly Users
**Learning:** Default Material design colors (like standard Amber/Yellow for warnings) often lack sufficient contrast against light backgrounds, making it hard for elderly users to distinguish critical status indicators (e.g., "Vence em breve" or "Atrasado"). Furthermore, static labels without context require more cognitive load.
**Action:** Always verify semantic colors against accessibility contrast guidelines. Use darker shades for warnings (e.g., `#F57F17` instead of `#xFFFFC107`). Pair color changes with dynamic, explicit text labels (e.g., changing "Vencimento" to "Atrasado" when overdue) to reinforce status changes beyond just color.
