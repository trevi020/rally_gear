# Rally Gear (addon per AAIdrive)

**Rally Gear** è un addon per [AAIdrive](https://github.com/BimmerGestalt/AAIdrive) per MINI e BMW. Trasforma lo smartphone Android in un display da rally: **marcia inserita** con font a 7 segmenti e una barra **Shift Light a 5 LED** basata sui giri motore (RPM).

---

## 🏎️ Caratteristiche

* **Indicatore marcia a 7 segmenti** (font `DSEG7`): `N`, `R`, `P`, `1`, `2`, ... oppure `-` se il dato non c'è.
* **Shift light a 5 LED** (verde → giallo → rosso) in base agli RPM.
* **Schermo sempre acceso**, a tutto schermo, in orizzontale.
* **Riga di stato** in basso al centro (grigio scuro): dice se i dati di AAIdrive arrivano.
* **Valori grezzi di debug** in basso a destra (`gear=… rpm=…`): i numeri esattamente come li invia l'auto, senza interpretazione.

---

## 🔌 Come ottiene i dati dall'auto

AAIdrive espone i dati CDS (Car Data Service) dell'auto tramite un **Content Provider Android**:

```
content://io.bimmergestalt.cardata.provider/cds/<proprietà>
```

Rally Gear legge due proprietà, aggiornate in tempo reale tramite `ContentObserver`:

| Proprietà CDS      | Valore JSON          | Uso                       |
|--------------------|----------------------|---------------------------|
| `driving.gear`     | `{"gear": N}`        | marcia                    |
| `engine.RPMSpeed`  | `{"RPMSpeed": N}`    | giri motore → LED         |

Requisiti lato app (già configurati nel `AndroidManifest.xml`):

* permesso `io.bimmergestalt.permission.CDS_normal` (dichiarato da AAIdrive, livello "normal");
* `<queries>` sul provider `io.bimmergestalt.cardata.provider` (obbligatorio da Android 11);
* un servizio con action `io.bimmergestalt.cardata.service`, che rende l'app visibile nella scheda **Addons** di AAIdrive.

È necessaria una versione recente di AAIdrive che includa il content provider CDS.

Riferimenti: [documentazione CDS](https://bimmergestalt.github.io/BMWConnectedAnalysis/cds/), [IDriveConnectAddons](https://github.com/hufman/IDriveConnectAddons) (esempi ufficiali).

### Conversione della marcia

Secondo la documentazione CDS, `driving.gear` vale `1 = N`, `2 = R`, `3 = P`, `4 e oltre = D1, D2, D3...`. Rally Gear mostra quindi `valore − 3` per i valori ≥ 4. Se sulla tua auto le marce risultano sfasate, guarda il valore grezzo in basso a destra e correggi la funzione `MainModel.gearLabel()`.

---

## 🛠️ Struttura del progetto

* `CdsLiveData.kt`: legge una proprietà CDS dal content provider di AAIdrive (su thread dedicato) e la espone come `LiveData`.
* `MainModel.kt`: ViewModel con marcia, RPM e testo di stato.
* `MainActivity.kt`: interfaccia a tutto schermo, font custom e LED.
* `RallyGearService.kt`: servizio vuoto, serve solo alla scoperta da parte di AAIdrive.

---

## 🚦 Soglie shift light (RPM)

Modificabili in `MainActivity.kt` (`RPM_THRESHOLDS`):

* **LED 1 (verde):** ≥ 3500 RPM
* **LED 2 (verde):** ≥ 4200 RPM
* **LED 3 (giallo):** ≥ 5000 RPM
* **LED 4 (giallo):** ≥ 5700 RPM
* **LED 5 (rosso):** ≥ 6500 RPM

---

## 📲 Installazione e uso

1. Installa **prima AAIdrive**, poi Rally Gear (così il permesso CDS viene concesso all'installazione; se hai fatto il contrario, reinstalla Rally Gear).
2. Apri AAIdrive e controlla che Rally Gear compaia nella scheda **Addons**.
3. Collega il telefono all'auto e verifica in AAIdrive che la connessione sia attiva.
4. Apri **Rally Gear** e mettilo sul supporto. La riga di stato in basso indica se i dati arrivano.

**Se non arriva nulla**, la riga di stato dice cosa manca: AAIdrive non raggiungibile (non installato o troppo vecchio), permesso negato (reinstalla Rally Gear) oppure attesa dei dati dall'auto (auto non ancora collegata).

---

## 📄 Licenza

Rilasciato sotto licenza MIT. Il protocollo del content provider CDS è quello documentato e implementato da [BimmerGestalt](https://github.com/BimmerGestalt) (anch'esso MIT).
