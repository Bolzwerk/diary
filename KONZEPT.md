# Konzept: Sprachgesteuertes Notiz- & Tagebuch-App mit KI

> Arbeitstitel: **„Tagwerk“** – Android-App zum Diktieren von Notizen und Tagebucheinträgen, mit KI-gestützter Transkription, Ausformulierung, Aufgaben-Extraktion, Kategorisierung und Achtsamkeitsfunktionen.

Stand: 10.10.2026 · Status: Entwurf v0.2 (Entscheidungen siehe Abschnitt 13, Frontend siehe Abschnitt 7)

---

## 1. Ziel und Leitidee

Die App soll das Festhalten von Gedanken so reibungsarm wie möglich machen: **Handy nehmen, sprechen, fertig.** Die KI übernimmt Abschreiben, Ordnen und Ausformulieren. Dabei gibt es zwei klar getrennte Modi:

| Modus | Was passiert mit der Eingabe? | Ergebnis |
|---|---|---|
| **Notiz** | Transkription, unverändert gespeichert. KI schlägt nur Kategorie(n) vor. | Original-Notiz (offen/erledigt, kategorisiert) |
| **Tagebuch** | Transkription → Dialog (Rückfragen, Befinden) → KI-Verarbeitung | 1. Prosa-Tagebuchtext · 2. Tagesbericht · 3. extrahierte Aufgaben/Merkposten als neue Notizen · 4. Stimmungsdaten |

Leitprinzipien:

- **Original bleibt immer erhalten.** Jede KI-Ausgabe ist eine abgeleitete Version; das Transkript (und optional die Audiodatei) wird nie überschrieben.
- **Der Mensch hat das letzte Wort.** KI-Vorschläge (Kategorien, Aufgaben) sind sichtbar markiert und mit einem Tipp annehmbar, änderbar oder verwerfbar.
- **Lokal zuerst.** Alle Daten liegen verschlüsselt auf dem Gerät; nur der jeweils nötige Text geht zur Verarbeitung an die KI-API.
- **Achtsam, nicht therapeutisch.** Die App reflektiert und ermutigt, stellt aber keine Diagnosen.

---

## 2. Zentrale Anwendungsfälle (User Stories)

1. **Schnelle Notiz:** „Ich drücke den Mikrofon-Button (oder das Homescreen-Widget), sage ‚Reifenwechsel bis Ende Oktober buchen‘ – die Notiz liegt danach als *offen* in der Kategorie *Auto*.“
2. **Tagebuch per Dialog:** „Abends tippe ich auf ‚Tagebuch‘. Die App fragt, wie es mir geht. Ich erzähle frei vom Tag. Die App stellt ein, zwei Rückfragen (‚Wie lief das Gespräch mit deinem Chef?‘). Danach bekomme ich einen schön formulierten Tagebuchtext und einen kompakten Tagesbericht.“
3. **Aufgaben aus dem Tag:** „Im Tagesbericht stehen erkannte Aufgaben (‚Steuerberater zurückrufen‘) und Merkposten (‚Annas Geburtstag am 3.11.‘). Ich bestätige sie mit einem Tipp, und sie landen als Notizen in meiner Liste.“
4. **Überblick:** „Ich filtere meine Notizen nach Kategorie und Status und hake Erledigtes ab.“
5. **Achtsamkeit:** „Nach drei anstrengenden Tagen in Folge weist mich die App freundlich darauf hin und schlägt eine kurze Atemübung vor. Nach einem guten Tag hebt sie hervor, was gut lief.“
6. **Rückblick:** „Ich sehe im Kalender/Verlauf, wie meine Stimmung sich über Wochen entwickelt hat, und kann alte Einträge nachlesen.“

---

## 3. Funktionsumfang im Detail

### 3.1 Spracheingabe & Transkription

- **Aufnahme:** großer Mikrofon-Button auf dem Startbildschirm; zusätzlich Homescreen-Widget und Quick-Settings-Kachel für Notizen „in 1 Sekunde“.
- **Modus-Wahl:** vor der Aufnahme (zwei Buttons „Notiz“ / „Tagebuch“) – alternativ erkennt die KI den Modus selbst („Notiz: …“ als Sprachpräfix).
- **Transkription über OpenAI Audio-API** (z. B. `gpt-4o-transcribe` bzw. `whisper-1`, konfigurierbar). Sprache Deutsch, mit optionalem Glossar (Namen, Fachbegriffe) als Prompt-Hinweis für bessere Erkennung.
- **Offline-Fallback:** Aufnahme wird lokal gespeichert und in eine Warteschlange gelegt; Transkription erfolgt, sobald Netz da ist (WorkManager). Optional: Android-eigene Spracherkennung (`SpeechRecognizer`) als sofortige, kostenlose Vorschau.
- **Korrektur:** Transkript ist immer editierbar, bevor/nachdem die KI weiterarbeitet.

### 3.2 Notizen

- Gespeichert wird das **Original-Transkript** (plus optional Audio).
- Eigenschaften: Titel (von KI vorgeschlagen, kurz), Text, Status (**offen / erledigt**), Kategorien (1..n), optional Fälligkeitsdatum, Herkunft (manuell / aus Tagebuch vom TT.MM.).
- **KI-Kategorisierung:**
  - Die KI erhält die Liste vorhandener Kategorien und wählt passende aus oder schlägt eine neue vor.
  - Bestehende Kategorien werden bevorzugt, um Wildwuchs zu vermeiden.
  - Modus einstellbar: *automatisch vergeben* oder *nur vorschlagen* (Bestätigung per Chip-Tipp).
  - Kategorien sind vom Nutzer anlegbar, umbenennbar, zusammenführbar, mit Farbe/Icon.
- Listen-/Filteransicht: nach Status, Kategorie, Datum, Volltextsuche.
- Optional (später): Erinnerung/Benachrichtigung bei Fälligkeit.

### 3.3 Tagebuch (Dialog-Modus)

Ablauf eines Eintrags als geführter Dialog:

1. **Check-in (Achtsamkeit):** „Wie geht es dir gerade?“ – Antwort per Sprache *oder* schnell per Skala (1–5, Emojis) plus optional Energielevel und Schlaf.
2. **Freies Erzählen:** Nutzer diktiert in einem oder mehreren Abschnitten (Aufnahmen können ergänzt werden, auch über den Tag verteilt).
3. **Rückfragen (optional, max. 2–3):** Die KI erkennt Lücken oder emotional relevante Stellen und fragt nach. Rückfragen sind überspringbar. Fragen werden per Text und optional per Sprachausgabe (TTS) gestellt.
4. **Verarbeitung:** Ein KI-Aufruf mit strukturierter Ausgabe (JSON-Schema) erzeugt:
   - **Prosa-Tagebuchtext** – Ich-Perspektive, im Stil des Nutzers, nichts hinzuerfinden; Tonalität einstellbar (nüchtern / erzählerisch / poetisch).
   - **Tagesbericht** – stichpunktartig: Ereignisse, Erfolge, Belastungen, Personen, Stimmung, Erkenntnisse.
   - **Extrahierte Elemente** – Aufgaben (mit evtl. Datum), Merkposten/Wichtiges für die Zukunft, Ideen; jeweils mit Kategorie-Vorschlag.
   - **Stimmungsanalyse** – Belastungsgrad des Tages (z. B. 1–5), Hauptthemen, positive Momente.
5. **Review-Bildschirm:** Nutzer sieht alle drei Ergebnisse, kann Texte bearbeiten und die extrahierten Aufgaben einzeln übernehmen (→ werden als **Notizen** angelegt, verlinkt mit dem Tagebucheintrag).
6. **Achtsamkeits-Abschluss:** kurzer Hinweis/Impuls passend zum Tag (siehe 3.4).

Ein Tag = ein Tagebucheintrag. Spätere Ergänzungen am selben Tag werden angehängt und können eine Neugenerierung auslösen (bereits übernommene Aufgaben werden dabei nicht doppelt angelegt).

### 3.4 Achtsamkeitsfunktionen

| Funktion | Beschreibung |
|---|---|
| **Befindens-Check-in** | Zu Beginn jedes Tagebucheintrags; Skala + freie Antwort. |
| **Tages-Einordnung** | KI bewertet aus Text + Check-in, ob der Tag anstrengend, ausgeglichen oder leicht war. |
| **Hinweis nach anstrengendem Tag** | z. B. „Das klingt nach einem fordernden Tag. Magst du dir kurz Zeit für eine Atemübung nehmen?“ – mit 1–3-Minuten-Übung (geführtes Atmen, Body-Scan als Text/TTS). |
| **Hinweis nach leichtem/gutem Tag** | Positives verstärken: „Was hat heute dazu beigetragen, dass es gut lief?“ – Möglichkeit, das als „Kraftquelle“ zu speichern. |
| **Muster-Erkennung** | Mehrere belastende Tage in Folge → sanfter Hinweis, ggf. Vorschlag einer Pause / Wochenreflexion. Wiederkehrende Belastungsthemen werden im Wochenrückblick sichtbar. |
| **Dankbarkeit** | Optionale Frage „Wofür bist du heute dankbar?“ (abschaltbar, rotierende Impulsfragen). |
| **Stimmungsverlauf** | Kalender-Heatmap und Wochenkurve von Stimmung/Energie. |
| **Erinnerung** | Konfigurierbare abendliche Benachrichtigung „Zeit für deinen Tagesrückblick?“. |
| **Sicherheitsnetz** | Bei Hinweisen auf ernsthafte Krisen keine KI-Ratschläge, sondern dezenter Hinweis auf Hilfsangebote (z. B. TelefonSeelsorge 0800 111 0 111). |

Grundsatz: Hinweise sind kurz, freundlich, nie belehrend, und lassen sich in den Einstellungen dämpfen oder abschalten.

---

## 4. Technische Architektur

### 4.1 Überblick

```
┌──────────────────────── Android-App (Kotlin) ────────────────────────┐
│  UI (Jetpack Compose)                                                 │
│   ├─ Home / Aufnahme   ├─ Notizen   ├─ Tagebuch   ├─ Verlauf/Stimmung │
│   └─ Einstellungen                                                    │
│                                                                       │
│  ViewModels (MVVM, StateFlow)                                         │
│                                                                       │
│  Domain / Use Cases                                                   │
│   ├─ RecordAudio  ├─ Transcribe  ├─ CategorizeNote                    │
│   ├─ RunDiaryDialog  ├─ GenerateDiaryOutputs  ├─ ExtractTasks         │
│   └─ MindfulnessEngine (Regeln + KI-Ergebnis)                         │
│                                                                       │
│  Data                                                                 │
│   ├─ Room-DB (verschlüsselt, SQLCipher)                               │
│   ├─ Audio-Dateien (verschlüsselt, app-privat)                        │
│   ├─ AiProvider-Interface ──► OpenAiProvider (Retrofit/Ktor + OkHttp) │
│   └─ WorkManager (Offline-Warteschlange, Retry)                       │
└───────────────────────────────────────────────────────────────────────┘
                     │ HTTPS (API-Key des Nutzers)
                     ▼
        OpenAI API (oder kompatibler Endpunkt)
         ├─ /v1/audio/transcriptions  (Sprache → Text)
         ├─ Chat/Responses API mit Structured Outputs (JSON-Schema)
         └─ /v1/audio/speech (optional, TTS für Rückfragen/Übungen)
```

### 4.2 Tech-Stack

| Bereich | Wahl | Begründung |
|---|---|---|
| Sprache | **Kotlin** | Standard für native Android-Entwicklung |
| UI | **Jetpack Compose**, Material 3 (inkl. Dynamic Color, Dark Mode) | moderne, deklarative UI |
| Architektur | MVVM + Clean-Architecture-Light (UI / Domain / Data) | testbar, austauschbare KI-Anbindung |
| DI | Hilt | Standard, gut integriert |
| Datenbank | **Room** + **SQLCipher** | lokale, verschlüsselte Speicherung |
| Schlüsselverwaltung | Android Keystore, EncryptedSharedPreferences/DataStore für API-Key | API-Key nie im Klartext |
| Netzwerk | Retrofit oder Ktor-Client + OkHttp, kotlinx.serialization | JSON-Schema-Antworten typisiert parsen |
| Hintergrundjobs | WorkManager | Offline-Queue, Retries, Abend-Erinnerung |
| Audio | `MediaRecorder` (AAC/m4a, 16–32 kbit/s mono) | kleine Dateien, von der API unterstützt |
| Widget | Jetpack Glance | Homescreen-Aufnahme-Widget |
| Sperre | BiometricPrompt | App-Sperre per Fingerabdruck/PIN |
| Min. Android | API 26 (Android 8) – Ziel API 35+ | breite Abdeckung |
| Verteilung | signierte **APK** (Sideload), gebaut per CI | nur für den Eigengebrauch, kein Store nötig |

### 4.3 KI-Anbindung (OpenAI, austauschbar)

- **`AiProvider`-Interface** mit Methoden `transcribe()`, `categorize()`, `diaryFollowUp()`, `processDiary()`, `speak()`.
- **Konfigurierbar in den Einstellungen:** API-Key, **Base-URL** (damit auch OpenAI-kompatible Endpunkte wie Azure OpenAI, ein eigener Proxy oder ein lokales Modell nutzbar sind), Modellnamen je Aufgabe (Transkription / Text / TTS).
- **Structured Outputs:** Alle Textverarbeitungen liefern JSON nach festem Schema → robustes Parsen, keine Freitext-Interpretation.
- **Prompt-Vorlagen** versioniert im Code (später ggf. editierbar für Power-User).
- **Kosten-Transparenz:** Token-/Minutenverbrauch pro Monat in den Einstellungen anzeigen; Budgetwarnung optional.

Beispiel-Schema für die Tagebuch-Verarbeitung (vereinfacht):

```json
{
  "diary_prose": "string",
  "day_report": {
    "summary": "string",
    "events": ["string"],
    "achievements": ["string"],
    "stressors": ["string"],
    "people": ["string"],
    "insights": ["string"]
  },
  "extracted_items": [
    {
      "type": "task | reminder | idea",
      "text": "string",
      "due_date": "YYYY-MM-DD | null",
      "category_suggestion": "string",
      "is_new_category": "boolean"
    }
  ],
  "mood": {
    "day_load": "1-5",
    "valence": "1-5",
    "themes": ["string"],
    "positive_moments": ["string"],
    "crisis_signal": "boolean"
  },
  "mindful_hint": "string"
}
```

Kern-Anweisungen im System-Prompt (Auszug):
- „Erfinde keine Fakten. Verwende nur, was im Transkript steht.“
- „Schreibe den Tagebuchtext in der Ich-Form, auf Deutsch, im Ton: {tonalität}.“
- „Extrahiere nur Aufgaben, die eindeutig als zu erledigen formuliert sind.“
- „Bevorzuge bestehende Kategorien: {kategorienliste}.“

---

## 5. Datenmodell

```
Category
  id, name, color, icon, createdBy (USER|AI), createdAt

Note
  id, title, text (Original), status (OPEN|DONE), dueDate?,
  source (MANUAL|DIARY), diaryEntryId?, audioRecordingId?,
  createdAt, updatedAt, completedAt?
NoteCategory (n:m)
  noteId, categoryId, assignedBy (USER|AI), confirmed (bool)

DiaryEntry
  id, date (eindeutig pro Tag), rawTranscript, prose, dayReportJson,
  generationVersion, createdAt, updatedAt
DiaryTurn                     ← der Dialog
  id, diaryEntryId, role (APP|USER), text, audioRecordingId?, order
MoodCheckIn
  id, diaryEntryId, mood (1-5), energy (1-5)?, sleep?, freeText?
DayAssessment                 ← KI-Ergebnis
  diaryEntryId, dayLoad, valence, themesJson, positiveMomentsJson
ExtractedItem
  id, diaryEntryId, type, text, dueDate?, suggestedCategory,
  state (PENDING|ACCEPTED|REJECTED), createdNoteId?

AudioRecording
  id, filePath (verschlüsselt), durationMs, transcriptionState
  (QUEUED|DONE|FAILED), createdAt

UsageLog
  id, operation, model, inputTokens, outputTokens, audioSeconds, createdAt
```

---

## 6. Wichtige Abläufe

### 6.1 Notiz diktieren
```
[Mic] → Audio lokal speichern → Transkription (API)
     → Notiz anlegen (Original-Text, Status OPEN)
     → Kategorisierung (API, mit Liste vorhandener Kategorien)
     → Kategorien vergeben bzw. als Vorschlag anzeigen
```

### 6.2 Tagebucheintrag
```
[Tagebuch] → Check-in (Skala/Sprache)
          → Erzählen (1..n Aufnahmen) → Transkription
          → Rückfragen (KI, optional, max. 3) → Antworten
          → processDiary (KI, Structured Output)
          → Review: Prosa | Tagesbericht | Aufgaben übernehmen?
          → angenommene Items → neue Notizen (+ Kategorien)
          → MindfulnessEngine → passender Hinweis / Übung
```

### 6.3 MindfulnessEngine (Regeln, lokal)
- Eingaben: Check-in-Werte, `day_load`/`valence` der KI, Verlauf der letzten 7 Tage.
- Regeln (Beispiele):
  - `day_load ≥ 4` → Entlastungs-Hinweis + Atemübung anbieten.
  - `valence ≥ 4 && day_load ≤ 2` → positiver Impuls, „Kraftquelle“ speichern.
  - 3 Tage in Folge `day_load ≥ 4` → Muster-Hinweis.
  - `crisis_signal = true` → nur Hilfsangebote anzeigen, keine weiteren KI-Ratschläge.
- Regeln lokal und deterministisch → nachvollziehbar und ohne zusätzliche API-Kosten.

---

## 7. Frontend-Konzept

### 7.1 Grundentscheidungen

| Thema | Entscheidung |
|---|---|
| UI-Toolkit | **Jetpack Compose** mit **Material 3** – kein XML, keine WebView, kein Cross-Platform-Framework (nur Android, nur ein Nutzer → native ist am einfachsten und schnellsten) |
| Struktur | **Single-Activity**, Navigation per **Navigation Compose** (typsichere Routen) |
| State | **Unidirektionaler Datenfluss:** ViewModel hält einen `UiState` (StateFlow), Screens sind zustandslose Composables und melden Aktionen als Events zurück → jeder Screen ist mit `@Preview` und Fake-Daten testbar |
| Theme | ruhige, warme Farbpalette (Eigenes Theme, optional Dynamic Color), **Dark Mode**, große, gut lesbare Typografie für Tagebuchtexte (Serifenschrift für Prosa, Sans für UI) |
| Bedienprinzip | **Voice-first:** Jeder Hauptscreen erreicht die Aufnahme mit einem Tipp; Tippen/Bearbeiten ist immer möglich, aber zweitrangig |
| Rückmeldung | Haptik beim Start/Stopp der Aufnahme, dezente Animationen, klare Zustände (aufnehmen → transkribieren → verarbeiten) |
| Barrierefreiheit | TalkBack-Labels, Mindest-Touch-Größe 48 dp, skalierbare Schrift |

### 7.2 Navigation

Untere Navigationsleiste mit vier Bereichen, Einstellungen über das Zahnrad oben rechts:

```
┌─────────────────────────────────────┐
│  Heute   Notizen   Tagebuch  Verlauf│
└─────────────────────────────────────┘
```

Zusätzliche Einstiege ohne die App zu öffnen:
- **Homescreen-Widget** (Glance): zwei Buttons „Notiz“ / „Tagebuch“ + Anzahl offener Aufgaben
- **Launcher-Shortcuts** (lange auf das App-Icon drücken): „Neue Notiz“, „Tagebuch starten“
- **Quick-Settings-Kachel**: Notiz diktieren direkt aus der Benachrichtigungsleiste
- **Benachrichtigung** für die Abend-Erinnerung mit Direkt-Aktion „Tagebuch starten“

### 7.3 Screens

**Heute (Startbildschirm)**
```
┌──────────────────────────────┐
│ Samstag, 10. Oktober     ⚙   │
│                              │
│  Wie war dein Tag bisher?    │
│  ┌────────────────────────┐  │
│  │  ✎ Tagebuch starten    │  │  ← wird nach Eintrag zu
│  └────────────────────────┘  │     „Eintrag ergänzen“
│                              │
│  Offene Aufgaben (3)         │
│  ○ Steuerberater anrufen     │
│  ○ Reifenwechsel buchen      │
│  ○ Geschenk für Anna         │
│                              │
│  Zu bestätigen (2) ›         │  ← aus Tagebuch extrahiert
│                              │
│            ( 🎤 )            │  ← großer Button: Notiz
│     halten = sofort Notiz    │
└──────────────────────────────┘
```
- Großer Mikrofon-Button: **Tippen** startet/stoppt Aufnahme, **Halten** = Push-to-talk (loslassen speichert).
- Karte „Zu bestätigen“ sammelt KI-Vorschläge (Aufgaben, Kategorien), damit nichts ungefragt angelegt wird.

**Aufnahme (Bottom Sheet über dem aktuellen Screen)**
- Live-Wellenform, Timer, Buttons *Abbrechen / Pause / Fertig*.
- Danach Statuswechsel im selben Sheet: „Wird transkribiert …“ → Transkript erscheint → „Gespeichert in *Auto* (KI-Vorschlag) – ändern?“ als Snackbar mit Rückgängig.
- Ohne Netz: „Gespeichert, wird transkribiert, sobald du online bist.“

**Notizen**
- Segmentierte Auswahl *Offen / Erledigt / Alle*, darunter horizontal scrollbare **Kategorie-Chips** als Filter, Suchfeld oben.
- Listeneinträge: Titel, erste Textzeile, Kategorie-Punkte, Datum, Herkunfts-Symbol (🎤 diktiert / 📖 aus Tagebuch).
- **Wischen nach rechts = erledigt**, nach links = löschen (mit Rückgängig).
- KI-vorgeschlagene, noch nicht bestätigte Kategorien werden als **umrandete Chips mit ✨** dargestellt; Tipp = bestätigen, langer Druck = ändern.

**Notiz-Detail**
- Original-Transkript (editierbar), Audio-Player, Kategorien, Status-Schalter, Link „aus Tagebuch vom 08.10.“.

**Tagebuch – Dialog (Herzstück)**
```
┌──────────────────────────────┐
│ ←  Tagebuch · 10. Oktober    │
│                              │
│ ┌──────────────────────┐     │
│ │ Wie geht es dir      │ 🔊  │  ← App (links), wird vorgelesen
│ │ gerade?              │     │
│ └──────────────────────┘     │
│   😞  🙁  😐  🙂  😄          │  ← Schnellantwort
│                              │
│        ┌─────────────────┐   │
│        │ Ganz okay, etwas│   │  ← eigene Antwort (rechts)
│        │ müde …          │   │
│        └─────────────────┘   │
│ ┌──────────────────────┐     │
│ │ Erzähl mir von       │     │
│ │ deinem Tag.          │     │
│ └──────────────────────┘     │
│                              │
│ [Freihändig ◉]   ( 🎤 )  [Fertig]
└──────────────────────────────┘
```
- Chat-Darstellung: Fragen der App links, eigene (transkribierte) Antworten rechts; jede Antwort antippbar zum Korrigieren.
- **Freihändig-Modus** (Schalter): Die App liest jede Frage vor (TTS), startet danach automatisch die Aufnahme und beendet sie nach einigen Sekunden Stille (Stille-Erkennung über die Aufnahmepegel). Damit ist ein kompletter Eintrag ohne Bildschirmberührung möglich, z. B. beim Spazierengehen.
- Sprachbefehle im Freihändig-Modus: „weiter“, „überspringen“, „fertig“.
- **Fertig** startet die Verarbeitung → Fortschrittsanzeige mit Schritten (Transkribieren · Schreiben · Aufgaben finden).

**Tagebuch – Review**
- Drei Tabs: **Tagebuch** (Prosa, Serifenschrift, Lesedarstellung, Stift-Symbol zum Bearbeiten) · **Tagesbericht** (Abschnitte: Ereignisse, Erfolge, Belastungen, Erkenntnisse) · **Aufgaben (n)**.
- Aufgaben-Tab als Karten-Liste: je Karte Text, Datum, Kategorie-Vorschlag, Buttons **✓ übernehmen / ✕ verwerfen / ✎ bearbeiten**, oben „Alle übernehmen“.
- Darunter die **Achtsamkeits-Karte** mit dem passenden Hinweis (z. B. „Anstrengender Tag – 2 Minuten durchatmen?“ → startet Übung).
- Button „Neu formulieren“ mit Tonalitätsauswahl.

**Verlauf**
- Monatskalender, Tage eingefärbt nach Stimmung (Farbskala + Symbol, damit es nicht nur über Farbe funktioniert); Tipp öffnet den Eintrag.
- Darunter Wochenkurve Stimmung/Belastung und eine Liste der Einträge mit Suchfunktion.

**Achtsamkeits-Übung (Vollbild)**
- Animierter Atemkreis (einatmen 4 s · halten · ausatmen 6 s), optional mit Sprachansage; 1/2/3 Minuten wählbar. Bildschirm bleibt an.

**Einstellungen**
- *KI-Verbindung:* Base-URL, API-Key (maskiert), Modell je Aufgabe, Button „Verbindung testen“.
- *Sprache & Stimme:* TTS-Quelle (Android-Systemstimme offline / OpenAI-Stimme), Stimme, Sprechtempo, Stille-Dauer für Auto-Stopp.
- *Notizen:* Kategorien verwalten (umbenennen, zusammenführen, Farbe), Auto-Kategorisierung *vergeben / nur vorschlagen*.
- *Tagebuch:* Tonalität, Rückfragen an/aus, Dankbarkeitsfrage an/aus, Erinnerungszeit.
- *Datenschutz:* App-Sperre, Audio-Aufbewahrung, Export/Import, Verbrauch & Kosten.

### 7.4 Ersteinrichtung (Onboarding, einmalig)
1. Kurzer Hinweis, welche Daten an den konfigurierten KI-Endpunkt gehen.
2. Base-URL + API-Key eingeben → „Verbindung testen“.
3. Mikrofon- und Benachrichtigungs-Berechtigung.
4. Optional: App-Sperre, Erinnerungszeit, erste Kategorien (Vorschläge: Arbeit, Privat, Haushalt, Gesundheit, Finanzen, Ideen).

### 7.5 Technische Umsetzung im Frontend
- Paketstruktur je Feature: `ui/home`, `ui/notes`, `ui/diary`, `ui/history`, `ui/mindfulness`, `ui/settings`, gemeinsame Bausteine in `ui/components` (RecordButton, Waveform, CategoryChip, MoodPicker, ChatBubble).
- Aufnahme läuft in einem **Foreground Service**, damit sie bei gesperrtem Bildschirm (Freihändig-Modus) weiterläuft; die UI beobachtet den Service-Zustand über einen Flow.
- TTS über ein `SpeechOutput`-Interface mit zwei Implementierungen: Android `TextToSpeech` (offline, sofort) und OpenAI-TTS (natürlicher). Standard: Systemstimme, umschaltbar.
- Lange KI-Verarbeitung läuft über WorkManager; die UI zeigt den Fortschritt und kann geschlossen werden – das Ergebnis erscheint später unter „Zu bestätigen“.

---

## 8. Datenschutz & Sicherheit

Tagebuchdaten sind hochsensibel. Daher:

- **Lokale Speicherung, verschlüsselt** (SQLCipher, Schlüssel im Android Keystore); Audio-Dateien ebenfalls verschlüsselt.
- **App-Sperre** via Biometrie/PIN.
- **Transparenz:** Beim Einrichten klarer Hinweis, dass Audio/Text zur Verarbeitung an OpenAI (bzw. den konfigurierten Endpunkt) gesendet werden. OpenAI verwendet API-Daten laut eigener Policy standardmäßig nicht zum Training; eine Aufbewahrung zu Missbrauchszwecken ist aber möglich – das sollte der Nutzer wissen.
- **Datenminimierung:** Nur der aktuell benötigte Text wird gesendet (keine kompletten Historien); für die Kategorisierung nur Kategorienamen.
- **Eigener Endpunkt:** Base-URL frei wählbar (z. B. EU-gehosteter oder lokaler OpenAI-kompatibler Dienst).
- **Audio löschen** nach erfolgreicher Transkription als Option (Standard: behalten 30 Tage).
- **Export/Backup:** verschlüsselter Export (JSON + Markdown) in eine Datei; kein Cloud-Zwang.
- `android:allowBackup` nur für den verschlüsselten Export, keine unverschlüsselten Auto-Backups.

---

## 9. Kostenabschätzung KI (grobe Größenordnung)

Annahme: täglich 5 kurze Notizen (à 20 s) + 1 Tagebucheintrag (5 min Sprache).

- Transkription: ca. 7 min Audio/Tag ≈ 210 min/Monat → bei rund 0,3–0,6 ct/min ≈ **1–1,50 € / Monat**.
- Textverarbeitung (Kategorisierung + Tagebuch-Verarbeitung + Rückfragen): wenige tausend Tokens pro Tag → mit einem kleinen/mittleren Modell **deutlich unter 1 € / Monat**.

→ Realistisch **ca. 1–3 € pro Monat** bei persönlicher Nutzung. (Preise ändern sich; die App zeigt den tatsächlichen Verbrauch an.)

---

## 10. Umsetzungsplan (Phasen)

**Phase 0 – Setup (≈ 1 Woche)**
Projektgerüst (Compose, Hilt, Room/SQLCipher), CI-Build einer signierten APK, Einstellungen mit API-Key.

**Phase 1 – MVP Notizen (≈ 2 Wochen)**
Aufnahme, Transkription, Notiz speichern, offen/erledigt, Kategorien inkl. KI-Vorschlag, Liste mit Filtern.

**Phase 2 – Tagebuch (≈ 3 Wochen)**
Dialog-Flow mit Check-in, Rückfragen, Sprachausgabe & Freihändig-Modus, Structured-Output-Verarbeitung, Review-Screen, Aufgaben → Notizen (nach Bestätigung).

**Phase 3 – Achtsamkeit (≈ 2 Wochen)**
MindfulnessEngine, Hinweise, Atemübungen, Stimmungsverlauf/Kalender, Abend-Erinnerung.

**Phase 4 – Komfort & Härtung (≈ 2 Wochen)**
Widget, Quick-Settings-Kachel, Offline-Queue, App-Sperre, Export/Backup, Verbrauchsanzeige.

**Später / Ideen**
Wochen- und Monatsrückblicke, Erinnerungen bei Fälligkeit, semantische Suche („Wann hatte ich zuletzt Rückenschmerzen?“) über Embeddings, Fotos zu Einträgen, Wear-OS-Diktat, Sync zwischen Geräten (Ende-zu-Ende-verschlüsselt), Desktop-/Web-Ansicht.

---

## 11. Qualitätssicherung

- Unit-Tests für Use Cases und MindfulnessEngine (Regeln deterministisch testbar).
- KI-Antworten in Tests per Fake-`AiProvider` simuliert; eine kleine Sammlung echter Beispiel-Transkripte als Regressions-Set für Prompt-Änderungen.
- UI-Tests (Compose Testing) für die Kernflüsse Notiz und Tagebuch.
- Robustheit: Timeouts, Retries, Verhalten bei ungültigem API-Key, leerer Aufnahme, Netzwerkabbruch.

---

## 12. Risiken

| Risiko | Gegenmaßnahme |
|---|---|
| KI „erfindet“ Inhalte im Tagebuchtext | strikte Prompts, Original immer sichtbar, Bearbeiten/Neu generieren |
| Falsch extrahierte Aufgaben | Bestätigung pro Aufgabe statt automatischer Übernahme (Standard) |
| Kategorien-Wildwuchs | bestehende Kategorien bevorzugen, Zusammenführen-Funktion |
| Datenschutzbedenken | lokale Verschlüsselung, Transparenz, austauschbarer Endpunkt |
| Achtsamkeits-Hinweise wirken aufdringlich | sparsam, abschaltbar, Tonalität testen |
| API-Kosten/-Ausfall | Verbrauchsanzeige, Offline-Queue, Retry |

---

## 13. Entscheidungen

| Frage | Entscheidung |
|---|---|
| Nutzerkreis | **Nur für mich** – kein Store, kein Mehrbenutzer-Konzept, Verteilung als signierte APK |
| KI-Anbindung | **Eigene Base-URL und eigener API-Key**, in der App konfigurierbar (kein Backend) |
| Sync | **Kein Sync** – nur lokale Speicherung, manueller verschlüsselter Export als Backup |
| Aufgaben aus dem Tagebuch | **Erst nach Bestätigung** als Notiz anlegen |
| Sprachausgabe | **Ja** – Fragen und Übungen werden vorgelesen, inkl. Freihändig-Modus |
| Umsetzung | **Kotlin / Jetpack Compose**, Umsetzung in diesem Repository |

Noch offen (bis zur Klärung gelten die Standardwerte):
- Audio-Aufbewahrung – Standard: 30 Tage, danach automatisch löschen.
- Fälligkeitsdaten & Erinnerungen für Notizen – Standard: optionales Datum, Erinnerungen erst in einer späteren Phase.
- Tonalität des Tagebuchtexts – Standard: „erzählerisch“, in den Einstellungen änderbar.
