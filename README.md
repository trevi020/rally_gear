<div align="center">

# Rally Gear

**Display da rally per MINI e BMW: marcia, shift light, velocità e temperature sul tuo smartphone Android.**

![Platform](https://img.shields.io/badge/platform-Android%207.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF?logo=kotlin&logoColor=white)
![AAIdrive](https://img.shields.io/badge/addon%20for-AAIdrive-0066B1)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

</div>

---

## Panoramica

**Rally Gear** è un addon per [AAIdrive](https://github.com/BimmerGestalt/AAIdrive) che trasforma lo smartphone in un cruscotto sportivo a schermo nero, pensato per i telefoni OLED:

- **Marcia inserita** con font a 7 segmenti (DSEG7) e glow rosso;
- **Shift light** a LED verde → giallo → rosso in base ai giri motore;
- **Velocità** (km/h o mph) con glow verde;
- **Temperatura motore e olio**.

### Dati CAN bus via USB, senza dongle OBD

Il telefono si collega all'auto **via cavo USB** tramite AAIdrive, che espone i dati CDS (Car Data Service) letti direttamente dalla centralina dell'auto. Rispetto a un dongle OBD-II Bluetooth/Wi-Fi:

- **nessun dongle** da acquistare, accoppiare o alimentare;
- **latenza molto bassa** e **alta frequenza di aggiornamento** (fino a circa 10 Hz per giri, marcia e velocità);
- connessione stabile, e il telefono si ricarica mentre è in uso.

## Caratteristiche

| | |
|---|---|
| **Marcia** | `N`, `R`, `P`, `0`, `1`, `2`, … in 7 segmenti, `-` se il dato manca |
| **Shift light** | 5 LED in verticale, 10 in orizzontale (accesi a coppie), soglie personalizzabili |
| **Velocità** | km/h o mph, a scelta |
| **Temperature** | `ENG T` (motore) e `OIL T` (olio), in °C |
| **Orientamento** | 4 rotazioni (0°, 90°, 180°, 270°) scelte a mano, per adattarsi a qualsiasi supporto |
| **Schermo** | nero puro `#000000`, sempre acceso, a tutto schermo |
| **Debug** | valori grezzi e frequenza di aggiornamento dei giri in basso a destra |

## Installazione

1. Installa **AAIdrive** (versione recente, con content provider CDS) e collega il telefono all'auto.
2. Installa l'APK di Rally Gear dalla sezione [Releases](../../releases) o dagli artefatti del repository. Installa **prima AAIdrive**, così il permesso CDS viene concesso automaticamente (altrimenti reinstalla Rally Gear).
3. Apri AAIdrive e verifica che *Rally Gear* compaia nella scheda **Addons**.
4. Apri Rally Gear e metti il telefono sul supporto.

La riga di stato in basso indica se i dati arrivano: AAIdrive non raggiungibile, permesso negato oppure attesa dei dati dall'auto.

## Utilizzo

L'interfaccia si controlla con **pressioni prolungate** (senza muovere il dito), così non ci sono tocchi accidentali durante la guida. L'azione dipende dalla zona premuta:

| Zona | Pressione | Azione |
|---|---|---|
| **Fascia dei LED** (parte alta) | 2 secondi | Apre il popup per modificare le soglie dei LED |
| **Velocità** (layout orizzontale) | 2 secondi | Passa da km/h a mph e viceversa |
| **Qualsiasi altro punto** | 3,4 secondi | Ruota lo schermo di 90° |

### Rotazione

L'app **non ruota col sensore**: parte in verticale e cambia orientamento solo a mano, in sequenza **0° → 90° → 180° → 270° → 0°**, con vibrazione e messaggio di conferma. La scelta viene ricordata alla riapertura. Verticale (0°, 180°) e orizzontale (90°, 270°) usano due layout dedicati:

- **Verticale:** 5 LED in alto, marcia al centro.
- **Orizzontale:** 10 LED su tutto il bordo superiore, marcia al centro, velocità a destra, temperature a sinistra.

### Unità di velocità

L'auto fornisce la velocità solo in km/h; l'app converte in mph (× 0,621371). La scelta viene salvata.

### Shift light personalizzabili

Le soglie sono 5, uguali in verticale e in orizzontale (qui i 10 LED si accendono a coppie). Valori predefiniti:

| Soglia | LED verticale | LED orizzontale | Colore |
|---|---|---|---|
| ≥ 3500 giri/min | 1 | 1 e 2 | verde |
| ≥ 4200 giri/min | 2 | 3 e 4 | verde |
| ≥ 5000 giri/min | 3 | 5 e 6 | giallo |
| ≥ 5700 giri/min | 4 | 7 e 8 | giallo |
| ≥ 6500 giri/min | 5 | 9 e 10 | rosso |

Tenendo premuta la fascia dei LED per 2 secondi si apre il popup di modifica: i pulsanti **−** e **+** cambiano la soglia a passi di 100 giri/min (tenendoli premuti si ripete), le modifiche sono **subito attive e salvate**, e **Ripristina** riporta i valori predefiniti. Le soglie restano sempre crescenti.

## Dati CDS utilizzati

AAIdrive espone le proprietà CDS come Content Provider Android (`content://io.bimmergestalt.cardata.provider/cds/<proprietà>`).

| Proprietà | Valore | Uso | Aggiornamento |
|---|---|---|---|
| `driving.gear` | `{"gear": N}` | marcia | tempo reale (~10 Hz) |
| `engine.RPMSpeed` | `{"RPMSpeed": N}` | giri motore → LED | tempo reale (~10 Hz) |
| `driving.speedActual` | `{"speedActual": N}` (km/h) | velocità | tempo reale (~10 Hz) |
| `engine.temperature` | `{"temperature": {"engine": N, "oil": N}}` (°C) | ENG T / OIL T | ogni 10 secondi |

Le temperature cambiano lentamente e vengono rilette ogni 10 secondi, lasciando tutta la banda ai dati veloci. La marcia mostrata è `valore − 4` da 4 in su (`4 → 0`, `5 → 1`, …), mentre `1 = N`, `2 = R`, `3 = P`.

Il valore in **Hz** nei dati grezzi indica quante notifiche al secondo AAIdrive invia per i giri motore (media su 2 secondi).

Riferimenti: [documentazione CDS](https://bimmergestalt.github.io/BMWConnectedAnalysis/cds/) · [IDriveConnectAddons](https://github.com/hufman/IDriveConnectAddons).

## Struttura del progetto

```
app/src/main/
├── java/…/rallygear/
│   ├── MainActivity.kt          interfaccia, LED, gesti a pressione prolungata
│   ├── MainModel.kt             ViewModel: marcia, giri, velocità, temperature, stato
│   ├── CdsLiveData.kt           lettura dei dati CDS (tempo reale o polling)
│   ├── ShiftLightSettings.kt    soglie dei LED salvate
│   ├── ThresholdsDialog.kt      popup di modifica delle soglie
│   ├── RateMeter.kt             misura degli Hz
│   └── RallyGearService.kt      scoperta dell'addon da parte di AAIdrive
└── res/
    ├── layout/ , layout-land/   layout verticale e orizzontale
    └── values/dimens.xml        dimensioni dei testi
```

## Licenza

Distribuito con licenza [MIT](LICENSE). Il protocollo del content provider CDS è quello documentato e implementato da [BimmerGestalt](https://github.com/BimmerGestalt) (anch'esso MIT).
