# Wired Bug Audit

## 1. Scopo

Questo documento raccoglie i **potenziali bug**, le **aree fragili** e le **incoerenze architetturali** emerse durante l’analisi del sistema wired.

Non tutti i punti qui sotto sono bug già riprodotti al 100%, ma sono:

- problemi già visti in comportamento reale
- incongruenze tra runtime e UI
- zone del codice che possono generare regressioni o risultati non deterministici

Riferimenti principali (il progetto ora si chiama Polaris; percorsi aggiornati al layout attuale `Gameserver/...`):

- `Gameserver/Emulator/src/main/java/polaris/hotel/wired/core/WiredManager.java`
- `Gameserver/Emulator/src/main/java/polaris/hotel/wired/core/WiredEngine.java`
- `Gameserver/Emulator/src/main/java/polaris/hotel/wired/WiredHandler.java`
- `Gameserver/Emulator/src/main/java/polaris/hotel/wired/core/WiredMoveCarryHelper.java`
- `Gameserver/Emulator/src/main/java/polaris/hotel/items/interactions/wired`

I numeri di riga nelle sezioni "Evidenze" si riferiscono al codice al momento dell'audit originale;
i blocchi "Stato (verifica 2026-09-23)" riportano dove si trova oggi il codice citato.

---

## 2. Sintesi Priorità

| Priorità | Tema | Stato | Stato attuale (verifica 2026-09-23) |
|---|---|---|---|
| Alta | `context` variabili esposto ma non implementato davvero | Incoerenza forte | **Risolto** |
| Alta | Doppio runtime (`WiredManager` vs `WiredHandler`) | Rischio architetturale | **Risolto** |
| Alta | Ordine effect non sempre garantito senza extra esplicito | Rischio comportamentale | **Risolto** |
| Alta | Path movimento legacy può ancora far trapelare update intermedi | Già osservato in stanza | **Parzialmente risolto** |
| Media | Tick a `50ms` ma delay wired in step da `500ms` | Semantica non uniforme | Aperto |
| Media | Polling realtime `:wired` a `50ms` | Rischio carico/runtime noise | Mitigato lato client, ancora polling |
| Media | `click furni` ora immediato, queue/cancel svuotati | Possibile regressione | Aperto |
| Media | Semantica timestamp variabili non uniforme tra target types | Possibile confusione logica | Aperto (regola ora esplicita nel codice) |
| Media | Pagina del log stanza (3882) in ordine inverso | Bug confermato nel codice | Risolto (3.9) |

---

## 3. Audit Dettagliato

## 3.1 `context` nelle variabili: esposto ma non veramente supportato

- **Gravità:** Alta
- **Confidenza:** Alta
- **Area:** effetti/condizioni/extras variabili

### Problema

Nel layout e in parte della serializzazione compare il target `context`, ma in più punti il runtime lo rifiuta esplicitamente oppure restituisce direttamente `false`.

Questo crea una situazione pericolosa:

- il designer pensa che la feature esista
- il box si salva o si configura parzialmente
- ma poi in esecuzione non produce il comportamento atteso

### Evidenze

- `WiredEffectGiveVariable.java:197`
  - il save rifiuta `TARGET_CONTEXT`
- `WiredConditionVariableValueMatch.java:181`
  - `case TARGET_CONTEXT -> false`
- `WiredConditionVariableAgeMatch.java:146`
  - `case TARGET_CONTEXT -> false`
- `WiredExtraTextOutputVariable.java:83`
  - il save rifiuta `TARGET_CONTEXT`

### Impatto pratico

- stack che sembrano validi in UI ma non funzionano a runtime
- falsi negativi nelle condition variabili
- placeholder testuali variabili non disponibili quando l’utente si aspetta il target context

### Fix suggerito

Scegliere una direzione netta:

1. **o** implementare davvero `context` in tutti i flow variabili
2. **o** rimuoverlo completamente da UI, save e runtime finché non è pronto

La seconda opzione è la più sicura nel breve periodo.

### Stato (verifica 2026-09-23): RISOLTO

È stata scelta la prima strada: `context` è implementato in tutti i flow variabili.

- `WiredEffectGiveVariable`: `execute` gestisce `TARGET_CONTEXT` (`executeContextVariables`) e il
  save lo accetta, rifiutando solo definizioni mancanti o in sola lettura
  (`WiredContextVariableSupport.getDefinitionInfo`)
- `WiredEffectChangeVariableValue` e `WiredEffectRemoveVariable` gestiscono `TARGET_CONTEXT`
- `WiredConditionVariableValueMatch` e `WiredConditionVariableAgeMatch` usano
  `evaluateContextTarget` invece di `false`; anche `WiredConditionHasVariable` gestisce il context
- `WiredExtraTextOutputVariable` gestisce `TARGET_CONTEXT`
- supporto runtime in `core/WiredContextVariableSupport.java` e `core/WiredContextVariableScope.java`;
  la scheda Variables del client Octane ha l'elemento `Context`

---

## 3.2 Doppio runtime wired ancora presente

- **Gravità:** Alta
- **Confidenza:** Alta
- **Area:** architettura core

### Problema

`WiredManager` dichiara di essere il runtime esclusivo e tratta i vecchi flag come sola compatibilità, ma `WiredHandler` esiste ancora con entrypoint completi e logica propria.

### Evidenze

- `WiredManager.java:136`
  - warning esplicito: `wired.engine.enabled / wired.engine.exclusive are now compatibility-only flags`
- `WiredManager.java:174`
  - `isEnabled()` dipende solo dall’inizializzazione del manager
- `WiredManager.java:182`
  - `isExclusive()` ritorna sempre `true`
- `WiredHandler.java:63`
  - entrypoint legacy completo `handle(...)`
- `WiredHandler.java:114`
  - supporto separato per `handleCustomTrigger(...)`

### Impatto pratico

Se qualunque pezzo di codice, plugin o path legacy entra ancora in `WiredHandler`, si possono avere:

- ordine effect diverso
- scheduling delay diverso
- condition flow diverso
- diagnostica/monitor non coerente col nuovo engine

### Fix suggerito

- definire un solo entrypoint runtime ufficiale
- se `WiredHandler` deve restare, trasformarlo in adapter minimo che inoltra sempre al nuovo engine
- aggiungere log o metriche per rilevare qualsiasi ingresso nel path legacy

### Stato (verifica 2026-09-23): RISOLTO

`WiredHandler.java` è ora una facciata di compatibilità binaria per i plugin, senza logica propria:
ogni entrypoint (`handle(...)`, `handleCustomTrigger(...)`, `executeEffectsAtTiles(...)`,
`dropRewards`, `getReward`, `resetTimers`) registra l'ingresso in `WiredLegacyUsageTelemetry` (primo
uso e campioni a potenze di due per operazione, senza trattenere riferimenti) e inoltra a
`WiredLegacyCompatibilityAdapter`, che passa per `WiredManager` e quindi per l'unico engine
sorvegliato (`WiredManager.triggerFromLegacy`). Ordine effect, delay, condition e diagnostica sono
quindi quelli del nuovo engine. `isExclusive()`/i flag `wired.engine.*` restano solo per
compatibilità.

---

## 3.3 Ordine degli effect non sempre deterministico senza `wf_xtra_exec_in_order`

- **Gravità:** Alta
- **Confidenza:** Alta
- **Area:** esecuzione stack

### Problema

Nel path legacy, l’ordinamento stabile viene applicato chiaramente solo in presenza di `wf_xtra_exec_in_order` oppure in casi specifici (`unseen`).

Negli altri casi, l’ordine si appoggia alla collezione che arriva dal runtime.

### Evidenze

- `WiredHandler.java:224`
  - rileva `hasExtraExecuteInOrder`
- `WiredHandler.java:230`
  - ordina con `WiredExecutionOrderUtil.sort(effects)` solo in alcuni casi
- `WiredHandler.java:249`
  - usa direttamente `effectList` in ordered mode

### Impatto pratico

Stack come:

- `move_rotate` + `match_to_sshot`
- `toggle` + `reset`
- `give_var` + `change_var_val`

possono produrre risultati diversi se si assume implicitamente un ordine che il runtime non promette davvero.

### Fix suggerito

- decidere se l’ordine stack deve essere sempre stabile di default
- in alternativa, mantenere la regola attuale ma documentarla in modo molto esplicito
- se si lascia la regola attuale, conviene segnalare in UI che l’ordine è garantito solo con `wf_xtra_exec_in_order`

### Stato (verifica 2026-09-23): RISOLTO

L'ordine è ora stabile di default. `RoomWiredStackIndex.collectEffects` (e `collectConditions`)
ordina sempre con `WiredExecutionOrderUtil.sort` (altezza `z`, poi id), e in modalità normale
`WiredEngine` esegue gli effect in quest'ordine fisico ("Normal mode: preserve the physical stack
order"). `wf_xtra_exec_in_order` ora raggruppa gli effect per delay
(`WiredEffectPlanner.orderedDelayBatches`); random e unseen restano selezioni esplicite degli extra.
Il path legacy citato nelle evidenze non esiste più (vedi 3.2).

---

## 3.4 Il path movimento legacy può ancora far vedere movimenti intermedi

- **Gravità:** Alta
- **Confidenza:** Alta
- **Area:** movement pipeline

### Problema

Il helper legacy di movimento usa ancora un fallback che, se il collector non è attivo, invia subito `FloorItemOnRollerComposer`.

Questo può far trapelare al client uno stato intermedio che in teoria avrebbe dovuto essere nascosto da batching o restore finale.

### Evidenze

- `WiredMoveCarryHelper.java:163`
  - metodo `moveFurniLegacy(...)`
- `WiredMoveCarryHelper.java:179`
  - usa il collector se disponibile
- `WiredMoveCarryHelper.java:196`
  - fallback diretto a `FloorItemOnRollerComposer`

### Impatto pratico

È coerente con il tipo di bug già visto:

- oggetto che “si vede muovere”
- poi viene riportato nello stato corretto
- ma il client ha già ricevuto un update intermedio

### Fix suggerito

- evitare qualsiasi composer diretto nel path legacy quando la logica wired moderna è attiva
- centralizzare tutti i movement update in un unico collector finale
- aggiungere test specifici per:
  - `move_rotate` + `match_to_sshot`
  - stacked move effects nello stesso tick

### Stato (verifica 2026-09-23): PARZIALMENTE RISOLTO

- Risolto per il caso comune: `WiredEngine` apre una raccolta movimenti
  (`WiredMoveCarryHelper.beginMovementCollection`) attorno a tutti gli effect immediati di una
  firing (modalità normale, random e unseen) e attorno a ogni batch ordinato, anche ritardato
  (`executeOrderedEffectBatch`); anche il ciclo stanza la apre in `RoomCycleManager` attorno agli
  aggiornamenti di stato degli utenti. I movimenti escono come un solo `WiredMovementsComposer` alla
  fine.
- Anche i move-style hint (header 5110) di una firing sono ora raccolti e inviati come un pacchetto
  per stile (`WiredMoveStyleHelper.finishCollection`), prima dei movimenti.
- Ancora aperto: `WiredMoveCarryHelper.moveFurniLegacy` ha ancora il fallback diretto a
  `FloorItemOnRollerComposer` quando nessuna raccolta è attiva, e un singolo effect ritardato
  (`WiredEngine.executeDelayedEffect`) gira senza raccolta, quindi un move ritardato da solo può
  ancora mandare il suo update direttamente.

---

## 3.5 Tick a `50ms`, ma delay wired ancora a step da `500ms`

- **Gravità:** Media
- **Confidenza:** Alta
- **Area:** semantica temporale

### Problema

Il sistema oggi ha due granularità temporali diverse:

- repeaters / tickables a `50ms`
- delay wired classico a `delay * 500ms`

### Evidenze

- `WiredTickService.java:48`
  - `DEFAULT_TICK_INTERVAL_MS = 50`
- `WiredTickService.java:175`
  - `scheduleAtFixedRate(...)`
- `WiredEngine.java:753`
  - `long delayMs = delay * 500L`
- `WiredHandler.java:369`
  - stesso schema `delay * 500L`

### Impatto pratico

Non è per forza un bug, ma può creare:

- aspettative sbagliate nel builder dei wired
- sensazione di desync tra repeater e delay
- stack “velocissimi” su tick ma “grossolani” sugli effect ritardati

### Fix suggerito

- o si accetta questa doppia semantica e la si documenta ovunque
- o si introduce una nuova famiglia di delay high-resolution separata dal delay classico

### Stato (verifica 2026-09-23): APERTO

Invariato nella sostanza; il codice si è solo spostato:

- `WiredTickService.java:29` — `DEFAULT_TICK_INTERVAL_MS = 50`
- `WiredDelayedScheduler.java:158` — `long delayMs = delay * 500L` (lo scheduling ritardato è uscito
  da `WiredEngine`)
- il secondo schema in `WiredHandler` non esiste più (vedi 3.2)

---

## 3.6 `:wired` realtime a `50ms` può diventare rumoroso/pesante

- **Gravità:** Media
- **Confidenza:** Alta
- **Area:** tooling monitor/inspection

### Problema

Le request di monitor e variabili ora sono rate-limitate a `50ms`.

### Evidenze

- `WiredMonitorRequestEvent.java:39`
  - `return 50`
- `WiredUserVariablesRequestEvent.java:20`
  - `return 50`

### Impatto pratico

Su una stanza attiva o con più client staff aperti:

- carico rete maggiore
- più rumore sul server
- rischio di mascherare problemi reali con spam di refresh

### Fix suggerito

- spostare dove possibile a push/event driven
- lasciare `50ms` solo per il minimo indispensabile
- differenziare:
  - monitor heavy/debug
  - inspection live
  - variables snapshot

### Stato (verifica 2026-09-23): MITIGATO LATO CLIENT, ANCORA POLLING

- Server invariato: `WiredMonitorRequestEvent` e `WiredUserVariablesRequestEvent` accettano ancora
  una richiesta ogni `50ms` (così anche `WiredAllVariablesRequestEvent`, `WiredVariableHashesEvent`,
  `WiredUserSelectedEvent`).
- Il client Octane (`WiredCreatorTools.constants.ts`) differenzia già come suggerito:
  - monitor: `WIRED_MONITOR_POLL_MS = 250`, solo con la scheda Monitor aperta
  - variables snapshot: `WIRED_VARIABLES_POLL_MS = 250`, solo a finestra aperta, con diritti di
    ispezione e refresh non in pausa
  - inspection live: `WIRED_INSPECTION_REFRESH_MS = 50`, ma legge solo lo stato locale del room
    engine e non manda pacchetti
  - log stanza (`WiredRoomLogsView`): auto refresh ogni 2,5 s, server limitato a `250ms`
- Il passaggio a push/event driven resta da fare.

---

## 3.7 `click furni` ora è immediato: queue/cancel svuotati

- **Gravità:** Media
- **Confidenza:** Alta
- **Area:** eventi click furni

### Problema

La queue dei click furni è stata semplificata: ora il click parte subito, e il cancel path è vuoto.

### Evidenze

- `WiredManager.java:274`
  - `queueUserClicksFurni(...)` chiama subito `triggerUserClicksFurni(...)`
- `WiredManager.java:282`
  - `cancelPendingUserClicksFurni(...)` non fa nulla

### Impatto pratico

Se qualche comportamento vecchio dipendeva da:

- debounce
- cancel
- click differito

ora può cambiare senza che il mapping sia ovvio.

### Fix suggerito

- decidere se il comportamento immediato è quello definitivo
- se sì, documentarlo come breaking behavior
- se no, reintrodurre una queue reale con semantica esplicita

### Stato (verifica 2026-09-23): APERTO

Invariato: `WiredManager.java:473` `queueUserClicksFurni(...)` chiama ancora subito
`triggerUserClicksFurni(...)` e `WiredManager.java:481` `cancelPendingUserClicksFurni(...)` è ancora
vuoto ("Click furni triggers are now executed immediately"). Il comportamento immediato non è
ancora documentato come definitivo.

---

## 3.8 Semantica timestamp variabili non uniforme tra target type

- **Gravità:** Media
- **Confidenza:** Media
- **Area:** sistema variabili

### Problema

Le variabili utente e furni hanno senso come “assegnazione con creation/update time”, mentre le room/global variables hanno soprattutto senso sul solo `update time`.

Questo può diventare ambiguo quando si usano:

- `wf_cnd_var_age_match`
- sorting per creation/update
- UI manage/inspection

### Evidenze

- `WiredConditionVariableAgeMatch.java`
  - il target room/global vive soprattutto come valore di update
- le scelte di prodotto già fatte in `:wired` vanno in questa direzione

### Impatto pratico

- il builder può pensare che “tempo di creazione” sulle global sia forte quanto sulle user/furni
- condition o sort possono essere semanticamente strani anche se “funzionano”

### Fix suggerito

- trattare esplicitamente `room/global` come `updated-only`
- disabilitare in UI le opzioni che non hanno senso forte
- o documentare in modo molto chiaro la differenza

### Stato (verifica 2026-09-23): APERTO (regola ora esplicita nel codice)

Il primo punto è di fatto applicato: in `WiredConditionVariableAgeMatch` il target room legge
l'update time (`readRoomAgeMs` → `getRoomVariableManager().getUpdatedAt`), mentre user, furni e
context leggono la creation time (`getCreatedAt`). La finestra dei possessori
(`WiredVariableOwnersView`) mostra creation e last update time per ogni possessore. Manca ancora un
segnale in UI (o una nota nel dialog della condition) che per le variabili room "età" significa
"tempo dall'ultimo aggiornamento".

---

## 3.9 Pagina del log stanza (3882) in ordine inverso

- **Gravità:** Media
- **Confidenza:** Alta
- **Area:** tooling monitor / log stanza
- **Aggiunto:** verifica 2026-09-23

### Problema

La history di `WiredRoomDiagnostics` è già ordinata dalla più recente: `record(...)` inserisce con
`history.addFirst(...)` e `snapshot(...)` la copia nello stesso ordine, quindi l'indice `0` è la
voce più nuova (lo conferma `WiredRoomDiagnosticsTest`). `WiredRoomLogsPageEvent.collect(...)` però
la scorre da `history.size() - 1` a `0` con il commento "Newest first, like the official log
list": il risultato è dalla più vecchia alla più nuova.

### Evidenze

- `WiredRoomDiagnostics.java` — `record(...)`: `this.history.addFirst(new HistoryEntry(...))`
- `WiredRoomLogsPageEvent.java` — `collect(...)`: `for (int index = history.size() - 1; index >= 0; index--)`
- `WiredMonitorDataComposer` invia la stessa history nell'ordine della deque (più nuova prima),
  quindi la finestra "View full logs" e la finestra "Room logs" mostrano ordini opposti
- `WiredRoomLogsView.tsx` dice all'utente "Every line the wired engine wrote for this room, newest first"

### Impatto pratico

- la pagina 1 mostra le righe più vecchie delle ultime 200; le nuove finiscono sull'ultima pagina
- con l'auto refresh la pagina aperta non mostra quello che è appena successo
- anche gli id delle righe partono da `1` sulla più vecchia

### Fix suggerito

- scorrere la history da `0` in su in `WiredRoomLogsPageEvent.collect(...)`

### Stato (verifica 2026-09-23)

- **Risolto:** `collect(...)` ora scorre la history nel suo ordine (più nuova prima);
  coperto da `WiredRoomLogsPageEventTest`
- aggiungere un test sulla pagina (prima riga = voce più recente)

---

## 4. Backlog Consigliato

Ordine suggerito di intervento:

1. **Chiudere il target `context`** — fatto (implementato, vedi 3.1)
   - o implementarlo davvero
   - o toglierlo da UI/save/runtime
2. **Unificare il runtime** — fatto (`WiredHandler` è una facciata, vedi 3.2)
   - lasciare un solo entrypoint ufficiale
3. **Stabilire la regola sull’ordine effect** — fatto (default stabile per altezza e id, vedi 3.3)
   - default stabile o ordine esplicito con extra
4. **Chiudere il leak dei movement update legacy** — in parte (resta il fallback in
   `moveFurniLegacy` e l'effect ritardato singolo, vedi 3.4)
   - niente composer fuori collector quando wired moderno è attivo
5. **Ripensare il realtime di `:wired`** — aperto (polling rallentato lato client, vedi 3.6)
   - spostare il più possibile da polling a push
6. **Correggere l'ordine della pagina del log stanza** — risolto (vedi 3.9)
   - una riga da cambiare in `WiredRoomLogsPageEvent.collect(...)`

---

## 5. Nota Finale

Il sistema wired attuale è già molto più potente del modello classico, soprattutto per:

- variabili
- signal routing
- selectors avanzati
- monitor
- manage/inspection

Proprio per questo, le zone fragili oggi non sono tanto i box semplici, ma:

- la coesistenza di due runtime
- la semantica temporale
- i movement stack
- le feature variabili ancora “mezze esposte”

Questi sono i punti che più probabilmente spiegano i bug strani o intermittenti.

Aggiornamento 2026-09-23: la coesistenza dei due runtime e le variabili context "mezze esposte" sono
chiuse, e l'ordine degli effect è stabile. Restano aperti la semantica temporale (3.5, 3.8), il
residuo dei movement update (3.4), il click furni immediato (3.7), e il polling (3.6). L'ordine della
pagina del log stanza (3.9) è corretto.
