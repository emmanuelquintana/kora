---
version: 1
slug: "com-emmanuelquintana-kora-mainactivity-kt-a3edec4f"
primary_target: "app/src/main/java/com/emmanuelquintana/kora/MainActivity.kt"
related_targets: []
---

# Surface: Kora app shell (all postures)

Mode: Operate. Audience: one person with diabetes + hypertension logging their own readings and doses (see PRODUCT.md).
Job: record a reading or dose in seconds; review trends before a doctor visit.
Constraints: Material 3 navigation and controls; Compact = NavigationBar, Expanded = NavigationRail; Flex keeps controls off the hinge.

## Direction contract

THESIS: Kora is a tear-off day sheet (calendario de taco). Home is today's sheet; every dose and reading files into it. Refuses the category default of a dashboard of identical metric cards with icon tiles and gradient rings.
OWN-WORLD: Warm white paper ground (#FBFAF7 light / #121514 dark), near-black ink, petrol teal accent (#0E6B6B) for actions and the main data series, calendar red reserved for alerts only, amber for caution. Barlow for text, Barlow Condensed SemiBold for the big date numerals and every clinical value, tabular figures. Surfaces separate by one tone step, never shadows; hairline rules between rows like a printed sheet.
STORY: Open Kora, see today's date large, what is pending and the last two readings with their status in words; tap once to log; leave.
FIRST VIEWPORT: Compact: top 30% is the sheet head (weekday + huge day numeral + month, status line beneath in words); then pending doses as rows with fixed-cell marks (filled=taken, hollow=pending, struck=skipped); then last glucose and BP as large condensed numerals with unit and category word; Registrar actions sit in the thumb zone above the NavigationBar.
FORM: Calendario de taco (Mexican tear-off day calendar), grounded list position 6 of 7. seed:bb1853f6783abea3
RAISES: from rw-timetable-slide-rack: dose state is a mark in a fixed cell, not a colour. From operate-c-emission-line-rail: missing data is a dashed line, never interpolated. From japanese-high-density-web: Expanded desk is allowed real density. From pop-culture vertical feed: the quick-entry sheet gives one value the whole sheet, next field one step away.
SIGNATURE: the day sheet (Home) and stock drawn as the thickness of remaining sheets.
FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
