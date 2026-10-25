# Rally Gear (addon per AAIdrive)

**Rally Gear** è un addon per [AAIdrive](https://github.com/BimmerGestalt/AAIdrive) per MINI e BMW. Trasforma lo smartphone Android in un display da rally: **marcia inserita** con font a 7 segmenti, **shift light** basata sui giri motore (RPM), **velocità** e **temperature** di motore e olio.

---

## 🏎️ Caratteristiche

* **Indicatore marcia a 7 segmenti** (font `DSEG7`) con glow rosso: `N`, `R`, `P`, `0`, `1`, `2`, ... oppure `-` se il dato non c'è.
* **Shift light** verde → giallo → rosso in base agli RPM: 5 LED in verticale, 10 LED in orizzontale (si accendono a coppie), sempre distribuiti su tutta la larghezza dello schermo.
* **Layout verticale:** 5 LED in alto e marcia al centro.
* **Layout orizzontale:** 10 LED sul bordo superiore, marcia al centro, **velocità** (verde, con glow, km/h) a destra e due righe gialle **ENG T** (motore) e **OIL T** (olio) a sinistra.
* **Sfondo nero puro** (`#000000`) ovunque: layout, finestra, barre di sistema e schermata di avvio. Su uno schermo OLED i pixel restano spenti.
* **Schermo sempre acceso**, a tutto schermo.
* **Riga di stato** in basso (grigio scuro): dice se i dati di AAIdrive arrivano.
* **Valori grezzi di debug** in basso a destra (`gear=… rpm=…`): i numeri esattamente come li invia l'auto.

---

## 🔄 Rotazione dello schermo

L'app **non ruota col sensore**: parte in verticale e l'orientamento si cambia solo a mano.

* **Tieni premuto lo schermo per 4 secondi** (senza muovere il dito): l'orientamento passa al successivo, con una vibrazione e un messaggio.
* Ordine: **0° → 90° → 180° → 270° → 0°** ... Servono fino a 3 pressioni per tornare a un orientamento precedente.
* La scelta viene **salvata** e ripristinata alla riapertura dell'app.
* Verticale (0° e 180°) usa il layout verticale; 90° e 270° usano quello orizzontale.

Se il sistema Android del telefono non supporta un orientamento (alcuni telefoni ignorano i 180°), quell'orientamento non cambia nulla.

---

## 🔌 Come ottiene i dati dall'auto

AAIdrive espone i dati CDS (Car Data Service) dell'auto tramite un **Content Provider Android**:

```
content://io.bimmergestalt.cardata.provider/cds/<proprietà>
```

| Proprietà CDS         | Valore JSON                                      | Uso                           | Aggiornamento            |
|-----------------------|--------------------------------------------------|-------------------------------|--------------------------|
| `driving.gear`        | `{"gear": N}`                                    | marcia                        | tempo reale (~10 Hz)     |
| `engine.RPMSpeed`     | `{"RPMSpeed": N}`                                | giri motore → LED             | tempo reale (~10 Hz)     |
| `driving.speedActual` | `{"speedActual": N}` (km/h)                      | velocità (solo orizzontale)   | tempo reale (~10 Hz)     |
| `engine.temperature`  | `{"temperature": {"engine": N, "oil": N}}` (°C)  | ENG T / OIL T (solo orizz.)   | polling ogni 10 secondi  |

Marcia, giri e velocità usano un `ContentObserver` e si aggiornano a ogni notifica di AAIdrive (al massimo ogni ~100 ms). Le temperature cambiano piano, quindi vengono rilette solo ogni 10 secondi (`MainModel.TEMP_POLL_INTERVAL_MS`). Velocità e temperature vengono richieste all'auto solo mentre il layout orizzontale è a schermo.

AAIdrive tiene sottoscritta una proprietà finché viene interrogata e la rilascia dopo circa 10 secondi senza richieste: con un polling di 10 secondi la sottoscrizione resta praticamente sempre attiva; per farla rilasciare tra una lettura e l'altra alza `TEMP_POLL_INTERVAL_MS` (ad esempio a 20 secondi).

Requisiti lato app (già configurati nel `AndroidManifest.xml`):

* permesso `io.bimmergestalt.permission.CDS_normal` (dichiarato da AAIdrive, livello "normal");
* `<queries>` sul provider `io.bimmergestalt.cardata.provider` (obbligatorio da Android 11);
* un servizio con action `io.bimmergestalt.cardata.service`, che rende l'app visibile nella scheda **Addons** di AAIdrive.

È necessaria una versione recente di AAIdrive che includa il content provider CDS.

Riferimenti: [documentazione CDS](https://bimmergestalt.github.io/BMWConnectedAnalysis/cds/), [IDriveConnectAddons](https://github.com/hufman/IDriveConnectAddons) (esempi ufficiali).

### Conversione della marcia

`driving.gear` vale `1 = N`, `2 = R`, `3 = P`; da 4 in su l'app mostra `valore − 4` (`4 → 0`, `5 → 1`, `6 → 2`, ...), come calibrato sulle prove in auto. La funzione è `MainModel.gearLabel()`; il valore grezzo è sempre visibile in basso a destra.

---

## 🚦 Soglie shift light (RPM)

Le soglie sono 5, uguali in verticale e in orizzontale (`MainActivity.kt`, `THRESHOLDS`). In verticale ogni LED ha la sua soglia; in orizzontale i 10 LED si accendono a coppie:

| Soglia      | LED verticale | LED orizzontale | Colore |
|-------------|---------------|-----------------|--------|
| ≥ 3500 RPM  | 1             | 1 e 2           | verde  |
| ≥ 4200 RPM  | 2             | 3 e 4           | verde  |
| ≥ 5000 RPM  | 3             | 5 e 6           | giallo |
| ≥ 5700 RPM  | 4             | 7 e 8           | giallo |
| ≥ 6500 RPM  | 5             | 9 e 10          | rosso  |

---

## 🛠️ Struttura del progetto

* `CdsLiveData.kt`: legge una proprietà CDS dal content provider di AAIdrive (su thread dedicato, in tempo reale o a polling) e la espone come `LiveData`.
* `MainModel.kt`: ViewModel con marcia, RPM, velocità, temperature e testi di stato.
* `MainActivity.kt`: interfaccia a tutto schermo, font custom, LED, rotazione manuale con pressione di 4 secondi.
* `RallyGearService.kt`: servizio vuoto, serve solo alla scoperta da parte di AAIdrive.
* `res/layout/activity_main.xml` (verticale) e `res/layout-land/activity_main.xml` (orizzontale).
* `res/values/dimens.xml`: dimensioni dei testi del layout orizzontale (marcia, temperature, velocità).
* `res/values/themes.xml` e `res/values-v31/themes.xml`: tema nero (finestra, barre di sistema, splash).

---

## ⚙️ Compilazione con GitHub Actions

1. Fai **push** sul branch `main`.
2. Apri la scheda **Actions** del repository e seleziona l'ultima esecuzione.
3. Scarica lo ZIP `rally-gear-apk` dalla sezione **Artifacts**.
4. Estrai e installa `app-debug.apk` sul telefono.

Il workflow usa JDK 17 e Gradle 8.5 e lancia `gradle assembleDebug` dalla radice del progetto (non serve il gradle wrapper).

---

## 📲 Installazione e uso

1. Installa **prima AAIdrive**, poi Rally Gear (così il permesso CDS viene concesso all'installazione; se hai fatto il contrario, reinstalla Rally Gear).
2. Apri AAIdrive e controlla che Rally Gear compaia nella scheda **Addons**.
3. Collega il telefono all'auto e verifica in AAIdrive che la connessione sia attiva.
4. Apri **Rally Gear**, metti il telefono sul supporto e, se serve, ruotalo con la pressione di 4 secondi.

**Se non arriva nulla**, la riga di stato dice cosa manca: AAIdrive non raggiungibile (non installato o troppo vecchio), permesso negato (reinstalla Rally Gear) oppure attesa dei dati dall'auto (auto non ancora collegata).

---

## 📄 Licenza

Rilasciato sotto licenza MIT. Il protocollo del content provider CDS è quello documentato e implementato da [BimmerGestalt](https://github.com/BimmerGestalt) (anch'esso MIT).
