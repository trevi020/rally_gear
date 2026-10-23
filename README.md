# Rally Gear (BimmerGestalt Addon)

**Rally Gear** è un'estensione per l'ecosistema [AAIdrive](https://github.com/BimmerGestalt/AAIdrive) progettata per vetture MINI e BMW. Trasforma lo smartphone Android in un display di telemetria da competizione in stile rally.
L'interfaccia visualizza in tempo reale sul display dello smartphone la **marcia inserita** con un font a 7 segmenti digitali e una barra **Shift Light a 5 LED** per il cambio marcia basata sui giri motore (RPM).

---

## 🏎️ Caratteristiche Principali

* **Indicatore Marcia A 7 Segmenti:** Numerazione gigante ad altissima visibilità con font digitale retrò (`DSEG7`).
* **Barra LED Progressiva (Shift Light):** 5 indicatori circolari che cambiano colore in base al regime di rotazione del motore (Verde $\rightarrow$ Giallo $\rightarrow$ Rosso fuorigiri).
* **Modalità Immersiva Fullscreen:** Mantieni lo schermo sempre acceso ed esente da distrazioni durante la guida sportiva.
* **Integrazione CDS Native:** Si collega direttamente al bus dati dell'auto tramite il Car Data Service (CDS) gestito dall'app AAIdrive.
* **Supporto Standalone su Telefono:** Nessun rendering grafico lento sul display dell'iDrive; la grafica viene renderizzata direttamente sul display dello smartphone montato sul cruscotto.

---

## 🛠️ Architettura del Progetto

Il progetto estende l'infrastruttura di `IDriveConnectAddons` di BimmerGestalt:

* `RallyGearService.kt`: Servizio Android registrato con l'Intent `me.hacker.co.idriveconnectaddons.CONNECT` per ricevere i pacchetti CDS in tempo reale da AAIdrive.
* `MainModel.kt`: ViewModel/DataLayer che riceve ed estrae i dati di `driving.gear` e `engine.rpm`.
* `MainActivity.kt`: Interfaccia grafica nativa in orientamento orizzontale (Landscape) con gestione del font custom e reattività sui LED.

---

## 🚦 Configurazione Soglie Shift Light (RPM)

È possibile personalizzare i regimi d'intervento dei LED modificando i valori nel file `MainActivity.kt`:

* **LED 1 (Verde):** $\ge 3500 \text{ RPM}$
* **LED 2 (Verde):** $\ge 4200 \text{ RPM}$
* **LED 3 (Giallo):** $\ge 5000 \text{ RPM}$
* **LED 4 (Giallo):** $\ge 5700 \text{ RPM}$
* **LED 5 (Rosso / Fuorigiri):** $\ge 6500 \text{ RPM}$

---

## ⚙️ Compilazione e Installazione

Il progetto è configurato con **GitHub Actions** per compilare automaticamente l'APK senza richiedere l'installazione locale di Android Studio.

1. Fai il **Push** del codice sulla repository master/main.
2. Vai nella scheda **Actions** del repository GitHub.
3. Seleziona l'ultima build completata con successo.
4. Scarica il file ZIP dalla sezione **Artifacts** (`rally-gear-apk`).
5. Estrai e installa il file `app-debug.apk` sul tuo dispositivo Android.

---

## 📲 Attivazione dell'Addon

1. Apri l'applicazione **AAIdrive** sullo smartphone.
2. Vai nel menu **Estensioni / Addons**.
3. Spunta la voce **Rally Gear** per concedere i permessi di lettura della telemetria.
4. Collega il telefono alla tua MINI / BMW tramite cavo USB.
5. Apri l'app **Rally Gear** sul telefono e posizionalo sul supporto del cruscotto.

---

## 📄 Licenza

Rilasciato sotto licenza MIT. Parte del codice di integrazione fa riferimento alla struttura open-source di [BimmerGestalt IDriveConnectAddons](https://github.com/BimmerGestalt/IDriveConnectAddons).
